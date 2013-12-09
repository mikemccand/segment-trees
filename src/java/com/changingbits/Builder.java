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

/** Build a {@link LongRangeMultiSet}.  First, create this
 *  and pass the ranges.  Then, optionally, add "training
 *  data" by calling {@link #record} many times.  Finally,
 *  call {@link getMultiSet}. */

public final class Builder {

  private static final String COMPILED_TREE_CLASS = LongRangeMultiSet.class.getName() + "$CompiledTree";
  private static final String COMPILED_TREE_INTERNAL = COMPILED_TREE_CLASS.replace('.', '/');
  private static final Method LOOKUP_METHOD = Method.getMethod("int lookup(long, int[])");
  private static final Type LONG_SEGMENT_TREE_TYPE = Type.getType(LongRangeMultiSet.class);

  private final List<LongRange> elementaryIntervals;
  private final LongRange[] ranges;

  private final long[] elementaryCounts;

  // Set in finish:
  private Node root;

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
   *    LongRangeMultiSet#lookup} will never be less than this.
   *  @param hardMax The value passed to {@link
   *    LongRangeMultiSet#lookup} will never be greater
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
   *  #lookup}. */
  public void record(long v) {
    if (root != null) {
      throw new IllegalStateException("Builder is already finished");
    }

    // nocommit is this working correctly :)
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
    Node left, right;

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

      left = split(startIndex, bestIndex+1);
      right = split(bestIndex+1, endIndex);
    } else {
      left = right = null;
    }

    Node n = new Node(elementaryIntervals.get(startIndex).minIncl, elementaryIntervals.get(endIndex-1).maxIncl, left, right);

    return n;
  }

  /** Build the {@link LongRangeMultiSet}, by calling
   *  {@code finish(true)}. */ 
  private void finish() {
    if (root == null) {
      int numLeaves = elementaryIntervals.size();
      for(int i=0;i<numLeaves;i++) {
        if (elementaryCounts[i] == 0) {
          // This will create a balanced binary tree, if no
          // training data was sent:
          elementaryCounts[i] = 1;
        }
      }
      //System.out.println("COUNTS: " + Arrays.toString(elementaryCounts));

      Map<Node,List<Integer>> byNode = new HashMap<>();
      root = split(0, numLeaves);
      for(int i=0;i<ranges.length;i++) {
        addOutputs(root, i, ranges[i], byNode);
      }
      setHasOutputs(root, byNode);
      //System.out.println("ROOT: " + root);
    }
  }

  /** Recursively assigns range outputs to each node. */
  void addOutputs(Node node, int index, LongRange range, Map<Node,List<Integer>> byNode) {
    if (node.start >= range.minIncl && node.end <= range.maxIncl) {
      // Our range is fully included in the incoming
      // range; add to our output list:
      List<Integer> nodeOutputs = byNode.get(node);
      if (nodeOutputs == null) {
        nodeOutputs = new ArrayList<Integer>();
        byNode.put(node, nodeOutputs);
      }
      nodeOutputs.add(index);
    } else if (node.left != null) {
      assert node.right != null;
      // Recurse:
      addOutputs(node.left, index, range, byNode);
      addOutputs(node.right, index, range, byNode);
    }
  }

  void setHasOutputs(Node node, Map<Node,List<Integer>> byNode) {
    List<Integer> outputs = byNode.get(node);
    if (outputs != null) {
      node.outputs = new int[outputs.size()];
      for(int i=0;i<outputs.size();i++) {
        node.outputs[i] = outputs.get(i);
      }
      node.hasOutputs = true;
    } else {
      node.hasOutputs = false;
    }

    if (node.left != null) {
      setHasOutputs(node.left, byNode);
      setHasOutputs(node.right, byNode);
      node.hasOutputs |= node.left.hasOutputs;
      node.hasOutputs |= node.right.hasOutputs;
    }
  }


  /** Build a {@link LongRangeMultiSet} implementation to
   *  lookup intervals for a given point.
   *
   *  @param useAsm If true, the tree will be compiled to
   *  java bytecodes using the {@code asm} library; typically
   *  this results in a faster (~3X) implementation. */
  public LongRangeMultiSet getMultiSet(boolean useAsm) {

    finish();

    StringBuilder sb = new StringBuilder();
    sb.append('\n');
    int count = 0;
    for(LongRange range : ranges) {
      sb.append("// range ");
      sb.append(count++);
      sb.append(": ");
      sb.append(range);
      sb.append('\n');
    }
    sb.append("int upto = 0;\n");
    buildJavaSource(root, 0, sb);
    String javaSource = sb.toString();

    if (useAsm) {
      ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
      classWriter.visit(Opcodes.V1_7,
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                        COMPILED_TREE_INTERNAL,
                        null, LONG_SEGMENT_TREE_TYPE.getInternalName(), null);
      classWriter.visitSource(javaSource, null);
     
      Method m = Method.getMethod("void <init> ()");
      GeneratorAdapter constructor = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                                                          m, null, null, classWriter);
      constructor.loadThis();
      constructor.loadArgs();
      constructor.invokeConstructor(Type.getType(LongRangeMultiSet.class), m);
      constructor.returnValue();
      constructor.endMethod();

      GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                                                  LOOKUP_METHOD, null, null, classWriter);
      //Label labelTop = new Label();
      //Label labelEnd = new Label();
      //gen.visitLabel(labelTop);
      int uptoLocal = gen.newLocal(Type.INT_TYPE);
      //System.out.println("uptoLocal=" + uptoLocal);
      // nocommit is this not needed!?
      //gen.visitLocalVariable("upto", "I", null, labelTop, labelEnd, uptoLocal);
      gen.push(0);
      gen.storeLocal(uptoLocal, Type.INT_TYPE);
      buildAsm(gen, root, uptoLocal);
      // Return upto:
      gen.loadLocal(uptoLocal, Type.INT_TYPE);
      gen.returnValue();
      //gen.visitLabel(labelEnd);
      gen.endMethod();
      classWriter.visitEnd();

      byte[] bytes = classWriter.toByteArray();

      // javap -c /x/tmp/my.class
      try {
        FileOutputStream fos = new FileOutputStream(new File("/x/tmp/my.class"));
        fos.write(bytes);
        fos.close();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      // nocommit allow changing the class loader
      Class<? extends LongRangeMultiSet> treeClass = new Loader(LongRangeMultiSet.class.getClassLoader())
        .define(COMPILED_TREE_CLASS, classWriter.toByteArray());
      try {
        return treeClass.getConstructor().newInstance();
      } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }

    } else {
      return new SimpleLongRangeMultiSet(root);
    }
  }

  private void buildAsm(GeneratorAdapter gen, Node node, int uptoLocal) {

    if (node.outputs != null) {
      //System.out.println("gen outputs=" + node.outputs);
      // Increment any range outputs at the current node:
      for(int range : node.outputs) {
        // Load arg 1 (the int[] matchedRanges):
        gen.loadArg(1);
        // Load the index we will store to
        gen.loadLocal(uptoLocal, Type.INT_TYPE);
        // The range value we will store:
        gen.push(range);
        // Store it
        gen.arrayStore(Type.INT_TYPE);
        // Increment our upto:
        gen.iinc(uptoLocal, 1);
      }
    }

    if (node.left != null && (node.left.hasOutputs || node.right.hasOutputs)) {
      assert node.left.end+1 == node.right.start;
      if (node.left.hasOutputs && node.right.hasOutputs) {
        // Recurse on either left or right
        Label labelLeft = new Label();
        Label labelEnd = new Label();
        gen.loadArg(0);
        gen.push(node.left.end);
          
        gen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.LE, labelLeft);
        buildAsm(gen, node.right, uptoLocal);
        gen.goTo(labelEnd);
        gen.visitLabel(labelLeft);
        buildAsm(gen, node.left, uptoLocal);
        gen.visitLabel(labelEnd);
      } else if (node.left.hasOutputs) {
        // Recurse only on left
        Label labelEnd = new Label();
        gen.loadArg(0);
        gen.push(node.left.end);
          
        gen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.GT, labelEnd);
        buildAsm(gen, node.left, uptoLocal);
        gen.visitLabel(labelEnd);
      } else {
        // Recurse only on right
        Label labelEnd = new Label();
        gen.loadArg(0);
        gen.push(node.left.end);
          
        gen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.LE, labelEnd);
        buildAsm(gen, node.right, uptoLocal);
        gen.visitLabel(labelEnd);
      }
    }
  }

  static void indent(StringBuilder sb, int depth) {
    for(int i=0;i<depth;i++) {
      sb.append("  ");
    }
  }

  private void buildJavaSource(Node node, int depth, StringBuilder sb) {
    indent(sb, depth);
    sb.append("// node: " + node.start + " to " + node.end + "\n");
    if (node.outputs != null) {
      for(int range : node.outputs) {
        indent(sb, depth);
        sb.append("matchedRanges[upto++] = " + range + ";\n");
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

  static final class Loader extends ClassLoader {
    Loader(ClassLoader parent) {
      super(parent);
    }

    public Class<? extends LongRangeMultiSet> define(String className, byte[] bytecode) {
      return defineClass(className, bytecode, 0, bytecode.length).asSubclass(LongRangeMultiSet.class);
    }
  }

  public LongRangeCounter getCounter(boolean useAsm) {
    finish();
    return new SimpleLongRangeCounter(root, elementaryIntervals, ranges.length);
  }
}
