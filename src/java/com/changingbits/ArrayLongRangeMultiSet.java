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

/** Converts tree structure into parallel arrays up front. */
public class ArrayLongRangeMultiSet extends LongRangeMultiSet {

  // Start/end for each node:
  private final long[] starts;
  private final long[] ends;

  // Slice (address + count) into rangeIndices array for
  // each node's outputs:
  private final int[] rangeAddress;
  private final int[] rangeCounts;

  // Range indices (outputs):
  private final int[] rangeIndices;

  ArrayLongRangeMultiSet(Node root) {
    int count = countNodes(root);
    starts = new long[count+1];
    ends = new long[count+1];
    rangeAddress = new int[count+1];
    rangeCounts = new int[count+1];
    int outputCount = setStartEnds(root, 1);
    rangeIndices = new int[outputCount];
    fillOutputs(root, 1, 0);
    /*
    System.out.println("starts: " + Arrays.toString(starts));
    System.out.println("ends: " + Arrays.toString(ends));
    System.out.println("rangeAddress: " + Arrays.toString(rangeAddress));
    System.out.println("rangeIndices: " + Arrays.toString(rangeIndices));
    */
  }

  private int countNodes(Node node) {
    int count = 1;
    if (node.left != null) {
      count += countNodes(node.left);
      count += countNodes(node.right);
    }
    return count;
  }

  private int setStartEnds(Node node, int nodeID) {
    starts[nodeID] = node.start;
    ends[nodeID] = node.end;
    int count = node.outputs == null ? 0 : node.outputs.length;
    if (node.left != null) {
      count += setStartEnds(node.left, 2*nodeID);
      count += setStartEnds(node.right, 2*nodeID+1);
    }
    return count;
  }

  private int fillOutputs(Node node, int nodeID, int outputUpto) {
    if (node.outputs != null) {
      rangeAddress[nodeID] = outputUpto;
      rangeCounts[nodeID] = node.outputs.length;
      for(int i=0;i<node.outputs.length;i++) {
        rangeIndices[outputUpto++] = node.outputs[i];
      }
    }
    if (node.left != null) {
      outputUpto = fillOutputs(node.left, nodeID*2, outputUpto);
      outputUpto = fillOutputs(node.right, nodeID*2+1, outputUpto);
    }

    return outputUpto;
  }

  @Override
  public int lookup(long v, int[] matchedRanges) {
    return lookup(1, v, matchedRanges, 0);
  }

  private int lookup(int nodeID, long v, int[] matchedRanges, int upto) {
    int outputCount = rangeCounts[nodeID];
    if (outputCount != 0) {
      int start = rangeAddress[nodeID];
      int limit = start + outputCount;
      for(int i=start;i<limit;i++) {
        matchedRanges[upto++] = rangeIndices[i];
      }
    }
    int left = 2*nodeID;
    if (left < starts.length) {
      if (v <= ends[left]) {
        upto = lookup(left, v, matchedRanges, upto);
      } else {
        upto = lookup(left+1, v, matchedRanges, upto);
      }
    }

    return upto;
  }
}
