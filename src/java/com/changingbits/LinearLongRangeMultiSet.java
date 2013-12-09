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

/** Naive O(N) impl that scans all ranges on each lookup.
 *  This is typically slow!! */
class LinearLongRangeMultiSet extends LongRangeMultiSet {

  private final LongRange[] ranges;

  public LinearLongRangeMultiSet(LongRange[] ranges) {
    this.ranges = ranges;
  }

  @Override
  public int lookup(long v, int[] matchedRanges) {
    int upto = 0;
    for(int i=0;i<ranges.length;i++) {
      if (ranges[i].accept(v)) {
        matchedRanges[upto++] = i;
      }
    }
    return upto;
  }
}
