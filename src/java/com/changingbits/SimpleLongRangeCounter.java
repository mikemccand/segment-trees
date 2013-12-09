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

/** Java impl that counts each value into its elementary
 *  interval, and in the end rolls up to the original
 *  ranges. */
class SimpleLongRangeCounter extends LongRangeCounter {
  private final Node root;
  private final int numRanges;
  private final int[] elementaryCounts;
  private final long[] boundaries;

  public SimpleLongRangeCounter(Node root, List<LongRange> elementaryIntervals, int numRanges) {
    this.root = root;
    this.numRanges = numRanges;
    boundaries = new long[elementaryIntervals.size()+1];
    boundaries[0] = Long.MIN_VALUE;
    for(int i=0;i<elementaryIntervals.size();i++) {
      boundaries[i+1] = elementaryIntervals.get(i).maxIncl;
    }
    elementaryCounts = new int[boundaries.length];
    //System.out.println("boundaries=" + Arrays.toString(boundaries));
  }

  @Override
  public void add(long v) {

    // Binary search to find matched elementary range; we
    // are guaranteed to find a match because the last
    // bounary is Long.MAX_VALUE (or whatever app had passed
    // as the max):

    int lo = 0;
    int hi = boundaries.length - 1;
    int count = 0;
    while (hi >= lo) {
      int mid = (lo + hi) >>> 1;
      if (v <= boundaries[mid]) {
        hi = mid - 1;
      } else if (v > boundaries[mid+1]) {
        lo = mid + 1;
      } else {
        elementaryCounts[mid+1]++;
        return;
      }
    }
    assert lo == 0;
    elementaryCounts[1]++;
  }

  @Override
  public int[] getCounts() {
    int[] counts = new int[numRanges];
    int[] leafUpto = new int[1];
    leafUpto[0] = 1;
    rollup(root, leafUpto, counts);
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
