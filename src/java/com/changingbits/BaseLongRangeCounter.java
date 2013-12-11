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

import java.util.Arrays;
import java.util.List;

/** Base class for asm LongRangeCounter impl. */
public abstract class BaseLongRangeCounter extends LongRangeCounter {
  private final Node root;
  private final int numRanges;
  protected final int[] elementaryCounts;

  protected BaseLongRangeCounter(Node root, int numLeaves, int numRanges) {
    this.root = root;
    this.numRanges = numRanges;
    elementaryCounts = new int[numLeaves];
  }

  @Override
  public abstract void add(long v);

  @Override
  public int[] getCounts() {
    int[] counts = new int[numRanges];
    rollup(root, new int[1], counts);
    return counts;
  }

  private int rollup(Node node, int[] leafUpto, int[] counts) {
    int count;
    if (node.left != null) {
      count = rollup(node.left, leafUpto, counts);
      count += rollup(node.right, leafUpto, counts);
    } else {
      // We are a leaf:
      count = elementaryCounts[leafUpto[0]];
      leafUpto[0]++;
    }
    if (node.outputs != null) {
      for(int range : node.outputs) {
        counts[range] += count;
      }
    }
    return count;
  }
}
