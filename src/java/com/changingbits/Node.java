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

import java.util.ArrayList;
import java.util.List;

/** Holds one node of the segment tree */
class Node {
  Node left;
  Node right;

  // Our range:
  long start;
  long end;

  // Which ranges to output when a query goes through
  // this node:
  List<Integer> outputs;

  // True if we, or any of our descendents, have outputs:
  boolean hasOutputs;

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toString(sb, 0);
    return sb.toString();
  }

  void toString(StringBuilder sb, int depth) {
    Builder.indent(sb, depth);
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
