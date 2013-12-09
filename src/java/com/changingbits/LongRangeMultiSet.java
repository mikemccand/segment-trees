package com.changingbits;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

// TODO
//   - asm generator should output pseudo code too
//   - make the java-like code available in a .toString()?
//     and the ranges array?
//   - if there is an interval that no range ever covers
//     (which would be ... really weird), then we need not
//     cover the "full" number line?
//   - play w/ asm
//     - instance vars in the class vs incoming array
//     - which way to branch
//   - later
//     - allow changing what's done when a range matches
//       (it's hardwired to incrementing counters, now)

/** This class exposes only one method, {@link #increment},
 *  which for a given long value will increment the count by
 *  1 for each range containing that value.
 *
 *  <p> Create a {@link LongRangeMultiSet.Builder},
 *  optionally add "training data" via the {@link
 *  Builder#record} method, and then call {@link
 *  LongRangeMultiSet.Builder#finish} to get an instance. */

public abstract class LongRangeMultiSet {

  /** For a given value, increment the count by 1 for the
   *  index of each range that the value matches. */
  public abstract void increment(int[] counts, long v);

  static void indent(StringBuilder sb, int depth) {
    for(int i=0;i<depth;i++) {
      sb.append("  ");
    }
  }

  static final class Loader extends ClassLoader {
    Loader(ClassLoader parent) {
      super(parent);
    }

    public Class<? extends LongRangeMultiSet> define(String className, byte[] bytecode) {
      return defineClass(className, bytecode, 0, bytecode.length).asSubclass(LongRangeMultiSet.class);
    }
  }

  /** Holds one node of the segment tree */
  private static class Node {
    Node left;
    Node right;

    // Our range:
    long start;
    long end;

    // Which ranges to output when a query goes through
    // this node:
    List<Integer> outputs;

    // True if we, or any of our progeny, have outputs:
    boolean hasOutputs;

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      toString(sb, 0);
      return sb.toString();
    }

    void toString(StringBuilder sb, int depth) {
      indent(sb, depth);
      if (left == null) {
        assert right == null;
        sb.append("leaf: " + start + " to " + end);
      } else {
        sb.append("node: " + start + " to " + end);
      }
      if (outputs != null) {
        sb.append(" outputs=");
        sb.append(outputs);
      }
      sb.append('\n');

      if (left != null) {
        assert right != null;
        left.toString(sb, depth+1);
        right.toString(sb, depth+1);
      }
    }

    void add(int index, LongRange range) {
      if (start >= range.minIncl && end <= range.maxIncl) {
        // Our range is fully included in the incoming
        // range; add to our output list:
        if (outputs == null) {
          outputs = new ArrayList<Integer>();
        }
        outputs.add(index);
      } else if (left != null) {
        assert right != null;
        // Recurse:
        left.add(index, range);
        right.add(index, range);
      }
    }

    boolean setHasOutputs() {
      hasOutputs = outputs != null;
      if (left != null) {
        hasOutputs |= left.setHasOutputs();
        hasOutputs |= right.setHasOutputs();
      }

      return hasOutputs;
    }
  }

  /** Builds a new {@link LongRangeMultiSet}. */
  public static final class Builder {

    private static final String COMPILED_TREE_CLASS = LongRangeMultiSet.class.getName() + "$CompiledTree";
    private static final String COMPILED_TREE_INTERNAL = COMPILED_TREE_CLASS.replace('.', '/');
    private static final Method INCREMENT_METHOD = Method.getMethod("void increment(int[], long)");
    private static final Type LONG_SEGMENT_TREE_TYPE = Type.getType(LongRangeMultiSet.class);

    private final List<LongRange> elementaryIntervals;
    private final LongRange[] ranges;

    private final long[] elementaryCounts;

    /** Create a builder, accepting the full range of longs
     * ({@code Long.MIN_VALUE} to {@code Long.MAX_VALUE}.
     *
     * @param ranges Ranges to match. */
    public Builder(LongRange[] ranges) {
      this(ranges, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /** Create this, accepting the specified min/max range of
     *  all values.  In some cases, bounding the incoming range
     *  can result in faster code.  After creating this,
     *  you should optionally call {@link #record} multiple
     *  times (once per value you expect to encounter) so
     *  that the resulting code can be optimized for this
     *  data set, and finally call {@link #finish}.
     *
     *  @param ranges Ranges to match.
     *  @param hardMin The value passed to {@link
     *    LongRangeMultiSet#increment} will never be less than this.
     *  @param hardMax The value passed to {@link
     *    LongRangeMultiSet#increment} will never be greater
     *    than this. */
    public Builder(LongRange[] ranges, long hardMin, long hardMax) {

      this.ranges = ranges;

      // Compute the "elementary intervals" from the
      // incoming ranges:

      // Maps an endpoint to int flags; 1 = start of
      // interval, 2 = end of interval:
      Map<Long,Integer> endsMap = new HashMap<Long,Integer>();

      endsMap.put(hardMin, 1);
      endsMap.put(hardMax, 2);

      for(LongRange range : ranges) {
        Integer cur = endsMap.get(range.minIncl);
        if (range.minIncl < hardMin) {
          throw new IllegalArgumentException("range falls below hardMin");
        }
        if (range.maxIncl > hardMax) {
          throw new IllegalArgumentException("range falls above hardMax");
        }
        if (cur == null) {
          endsMap.put(range.minIncl, 1);
        } else {
          endsMap.put(range.minIncl, cur.intValue() | 1);
        }
        cur = endsMap.get(range.maxIncl);
        if (cur == null) {
          endsMap.put(range.maxIncl, 2);
        } else {
          endsMap.put(range.maxIncl, cur.intValue() | 2);
        }
      }

      List<Long> endsList = new ArrayList<Long>(endsMap.keySet());
      Collections.sort(endsList);
      //System.out.println("ends=" + endsMap);

      elementaryIntervals = new ArrayList<LongRange>();
      int upto0 = 1;
      long v = endsList.get(0);
      long prev;
      if (endsMap.get(v) == 3) {
        elementaryIntervals.add(new LongRange(null, v, true, v, true));
        prev = v+1;
      } else {
        prev = v;
      }
      while (upto0 < endsList.size()) {
        v = endsList.get(upto0);
        int flags = endsMap.get(v);
        //System.out.println("  v=" + v + " flags=" + flags);
        if (flags == 3) {
          // This point is both an end and a start; we need to
          // separate it:
          elementaryIntervals.add(new LongRange(null, prev, true, v-1, true));
          elementaryIntervals.add(new LongRange(null, v, true, v, true));
          prev = v+1;
        } else if (flags == 1) {
          // This point is only the start of an interval;
          // attach it to next interval:
          if (v > prev) {
            elementaryIntervals.add(new LongRange(null, prev, true, v-1, true));
          }
          prev = v;
        } else {
          assert flags == 2;
          // This point is only the end of an interval; attach
          // it to last interval:
          elementaryIntervals.add(new LongRange(null, prev, true, v, true));
          prev = v+1;
        }
        //System.out.println("    ints=" + elementaryIntervals);
        upto0++;
      }

      elementaryCounts = new long[elementaryIntervals.size()];
      //System.out.println("intervals: " + elementaryIntervals);
    }

    /** Call this many times, once per value in your
     *  "training data set"; the builder will use this to
     *  optimize the tree structure to minimize the
     *  computation required for each call to {@link
     *  #increment}. */
    public void record(long v) {

      int size = elementaryIntervals.size();
      int lo = 0;
      int hi = size - 1;
      while (true) {
        int mid = (lo + hi) >>> 1;
        LongRange r = elementaryIntervals.get(mid);
        if (v < r.minIncl) {
          hi = mid - 1;
        } else if (v > r.maxIncl) {
          lo = mid + 1;
        } else {
          elementaryCounts[mid]++;
          return;
        }
      }
    }

    /** Recursively splits the elementaryIntervals into
     *  tree, balanced according to how many times each
     *  interval was seen. */
    private Node split(int startIndex, int endIndex) {
      //System.out.println("split startIndex=" + startIndex + " endIndex=" + endIndex);
      Node n = new Node();
      n.start = elementaryIntervals.get(startIndex).minIncl;
      n.end = elementaryIntervals.get(endIndex-1).maxIncl;
      if (startIndex < endIndex-1) {
        long sum = 0;
        for(int i=startIndex;i<endIndex;i++) {
          sum += elementaryCounts[i];
        }
        long halfSum = sum / 2;
        long bestDistance = Long.MAX_VALUE;
        int bestIndex = 0;
        sum = 0;
        for(int i=startIndex;i<endIndex-1;i++) {
          sum += elementaryCounts[i];
          long distance = Math.abs(sum - halfSum);
          if (distance < bestDistance) {
            bestDistance = distance;
            bestIndex = i;
          }
        }
        //System.out.println("  bestIndex=" + bestIndex + " bestDistance=" + bestDistance + " sum=" + sum);

        n.left = split(startIndex, bestIndex+1);
        n.right = split(bestIndex+1, endIndex);
      }

      return n;
    }

    /** Build the {@link LongRangeMultiSet}, by calling
     *  {@code finish(true)}. */ 
    public LongRangeMultiSet finish() {
      return finish(true);
    }

    /** Build the {@link LongRangeMultiSet}. 
     *
     *  @param useAsm If true, the tree will be compiled to
     *  java bytecodes using the {@code asm} library; typically
     *  this results in a faster (~3X) implementation. */
    public LongRangeMultiSet finish(boolean useAsm) {

      int numLeaves = elementaryIntervals.size();

      for(int i=0;i<numLeaves;i++) {
        if (elementaryCounts[i] == 0) {
          // This will create a balanced binary tree, if no
          // training data was sent:
          elementaryCounts[i] = 1;
        }
      }
      //System.out.println("COUNTS: " + Arrays.toString(elementaryCounts));

      Node root = split(0, numLeaves);
      for(int i=0;i<ranges.length;i++) {
        root.add(i, ranges[i]);
      }
      root.setHasOutputs();
      //System.out.println(root);

      // Uncomment this to see the "rough" Java code for the tree:

      //StringBuilder sb = new StringBuilder();
      //buildJavaSource(root, 0, sb);
      //System.out.println("java source:\n" + sb);

      if (useAsm) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_7,
                          Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                          COMPILED_TREE_INTERNAL,
                          null, LONG_SEGMENT_TREE_TYPE.getInternalName(), null);

        // nocommit todo
        /*
        String clippedSourceText = (sourceText.length() <= MAX_SOURCE_LENGTH) ?
          sourceText : (sourceText.substring(0, MAX_SOURCE_LENGTH - 3) + "...");
        classWriter.visitSource(clippedSourceText, null);
        */
     
        Method m = Method.getMethod("void <init> ()");
        GeneratorAdapter constructor = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                                                            m, null, null, classWriter);
        constructor.loadThis();
        constructor.loadArgs();
        constructor.invokeConstructor(Type.getType(LongRangeMultiSet.class), m);
        constructor.returnValue();
        constructor.endMethod();

        GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                                                    INCREMENT_METHOD, null, null, classWriter);
        buildAsm(gen, root);
        gen.returnValue();
        gen.endMethod();
        classWriter.visitEnd();

        byte[] bytes = classWriter.toByteArray();

        // javap -c /x/tmp/my.class
        /*
        try {
          FileOutputStream fos = new FileOutputStream(new File("/x/tmp/my.class"));
          fos.write(bytes);
          fos.close();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        */

        // nocommit allow changing the class loader
        Class<? extends LongRangeMultiSet> treeClass = new Loader(LongRangeMultiSet.class.getClassLoader())
                  .define(COMPILED_TREE_CLASS, classWriter.toByteArray());
        try {
          return treeClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
          throw new RuntimeException(e);
        }

      } else {
        return new SimpleTree(root);
      }
    }

    private void buildAsm(GeneratorAdapter gen, Node node) {

      if (node.outputs != null) {
        // Increment any range outputs at the current node:
        for(int range : node.outputs) {
          // Load arg 0 (the int[] counts):
          gen.loadArg(0);
          // The int index we will increment:
          gen.push(range);
          // Dups both 0 and range:
          gen.dup2();
          // Load the int value at the index:
          gen.arrayLoad(Type.INT_TYPE);
          // Add 1:
          gen.push(1);
          gen.visitInsn(Opcodes.IADD);
          // Store it back:
          gen.arrayStore(Type.INT_TYPE);
        }
      }

      if (node.left != null && (node.left.hasOutputs || node.right.hasOutputs)) {
        assert node.left.end+1 == node.right.start;
        if (node.left.hasOutputs && node.right.hasOutputs) {
          // Recurse on either left or right
          Label labelLeft = new Label();
          Label labelEnd = new Label();
          gen.loadArg(1);
          gen.push(node.left.end);
          
          gen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.LE, labelLeft);
          buildAsm(gen, node.right);
          gen.goTo(labelEnd);
          gen.visitLabel(labelLeft);
          buildAsm(gen, node.left);
          gen.visitLabel(labelEnd);
        } else if (node.left.hasOutputs) {
          // Recurse only on left
          Label labelEnd = new Label();
          gen.loadArg(1);
          gen.push(node.left.end);
          
          gen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.GT, labelEnd);
          buildAsm(gen, node.left);
          gen.visitLabel(labelEnd);
        } else {
          // Recurse only on right
          Label labelEnd = new Label();
          gen.loadArg(1);
          gen.push(node.left.end);
          
          gen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.LE, labelEnd);
          buildAsm(gen, node.right);
          gen.visitLabel(labelEnd);
        }
      }
    }

    private void buildJavaSource(Node node, int depth, StringBuilder sb) {
      indent(sb, depth);
      sb.append("// node: " + node.start + " to " + node.end + "\n");
      if (node.outputs != null) {
        for(int range : node.outputs) {
          indent(sb, depth);
          sb.append("counts[" + range + "]++;\n");
        }
      }

      if (node.left != null && (node.left.hasOutputs || node.right.hasOutputs)) {
        indent(sb, depth);
        if (node.left.hasOutputs) {
          sb.append("if (v <= " + node.left.end + ") {\n");
          buildJavaSource(node.left, depth+1, sb);
          indent(sb, depth);
          if (node.right.hasOutputs) {
            sb.append("} else {\n");
            buildJavaSource(node.right, depth+1, sb);
            indent(sb, depth);
            sb.append("}\n");
          } else {
            sb.append("}\n");
          }
        } else {
          sb.append("if (v >= " + node.right.start + ") {\n");
          buildJavaSource(node.right, depth+1, sb);
          indent(sb, depth);
          sb.append("}\n");
        }
      }
    }
  }

  /** Basic impl that uses general purpose java sources
   *  (no asm); this is used if you pass false to {@link
   *  Builder#finish}. */
  private static class SimpleTree extends LongRangeMultiSet {

    private final Node root;
    
    SimpleTree(Node root) {
      this.root = root;
    }

    @Override
    public void increment(int[] counts, long v) {
      increment(root, counts, v);
    }

    private void increment(Node node, int[] counts, long v) {
      if (node.outputs != null) {
        for(int range : node.outputs) {
          //System.out.println("incr: node=" + node + " range=" + range);
          counts[range]++;
        }
      }
      if (node.left != null) {
        if (v >= node.left.start && v <= node.left.end) {
          increment(node.left, counts, v);
        }
        if (v >= node.right.start && v <= node.right.end) {
          increment(node.right, counts, v);
        }
      }
    }
  }
}
