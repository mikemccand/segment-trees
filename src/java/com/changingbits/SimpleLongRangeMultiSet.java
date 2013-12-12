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

/** Basic impl that uses general purpose java sources
 *  (no asm); this is used if you pass false to {@link
 *  Builder#getMultiSet}. */
class SimpleLongRangeMultiSet extends LongRangeMultiSet {

  private final Node root;
    
  SimpleLongRangeMultiSet(Node root) {
    this.root = root;
  }

  @Override
  public int lookup(long v, int[] answers) {
    return lookup(root, v, answers, 0);
  }

  private int lookup(Node node, long v, int[] answers, int upto) {
    if (node.outputs != null) {
      for(int range : node.outputs) {
        answers[upto++] = range;
      }
    }
    if (node.left != null) {
      if (v <= node.left.end) {
        upto = lookup(node.left, v, answers, upto);
      } else {
        upto = lookup(node.right, v, answers, upto);
      }
    }

    return upto;
  }
}
