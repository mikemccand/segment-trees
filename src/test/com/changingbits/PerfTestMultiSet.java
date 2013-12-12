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

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;

// ant compile; javac -cp build/java src/test/com/changingbits/PerfTestMultiSet.java; java -cp build/java:src/test:lib/asm-4.1.jar:lib/asm-commons-4.1.jar  com.changingbits.PerfTestMultiSet

public class PerfTestMultiSet {
  private static int DATA_COUNT = 10000000;
  private static int RANGE_COUNT = 7;
  private static int MAX_VALUE = 1000;
  private static int MAX_RANGE_VALUE = 1000;
  private static boolean RANGE_OVERLAP = true;

  public static void main(String[] args) {
    int[] values = new int[DATA_COUNT];
    long seed = 17;
    Random r = new Random(seed);

    for(int i=0;i<values.length;i++) {
      values[i] = r.nextInt(MAX_VALUE);
    }

    LongRange[] ranges = new LongRange[RANGE_COUNT];
    double inc = ((double) MAX_RANGE_VALUE) / RANGE_COUNT;
    for(int i=0;i<RANGE_COUNT;i++) {
      long min;
      if (RANGE_OVERLAP) {
        min = 0;
      } else {
        min = (long) (inc * i);
      }
      ranges[i] = new LongRange("range " + i,
                                min,
                                true,
                                (long) (inc * (i+1)),
                                false);
      System.out.println("range " + i + ": " + ranges[i]);
    }

    System.out.println("\nTEST: java segment tree");
    testSegmentTree(values, ranges, false, false);
    System.out.println("\nTEST: java array segment tree");
    testSegmentTree(values, ranges, false, true);
    System.out.println("\nTEST: asm segment tree");
    testSegmentTree(values, ranges, true, false);
    System.out.println("\nTEST: asm segment tree, perfect binary");
    testSegmentTree(values, ranges, true, true);
    System.out.println("\nTEST: linear search");
    testSimpleLinear(values, ranges);
  }

  static long t0;
  static long iterSum;
  static long fastestTime;
  static final NumberFormat nf = NumberFormat.getInstance();
  static {
    nf.setMaximumFractionDigits(1);
  }

  private static void start() {
    fastestTime = Long.MAX_VALUE;
  }

  private static void end() {
    double dataPerSec = ((double) DATA_COUNT) / (fastestTime/1000000000.0);
    System.out.println(String.format(Locale.ROOT, "  best: %s mvals/sec, sum=%d", nf.format(dataPerSec/1000000.0), iterSum));
  }

  private static void iterStart() {
    t0 = System.nanoTime();
  }

  private static void iterEnd(int iter, long sum) {
    if (iter == 0) {
      iterSum = sum;
    } else if (sum != iterSum) {
      throw new RuntimeException("sum changed");
    }
    long delay = System.nanoTime()-t0;
    if (iter > 5 && delay < fastestTime) {
      fastestTime = delay;
      double dataPerSec = ((double) DATA_COUNT) / (fastestTime/1000000000.0);
      //System.out.println(String.format(Locale.ROOT, "  iter %d: best: %s mvals/sec", iter, nf.format(dataPerSec/1000000.0)));
    }
  }

  private static void testSegmentTree(int[] values, LongRange[] ranges, boolean useAsm, boolean useArrayImpl) {

    Builder b = new Builder(ranges, 0, Long.MAX_VALUE);
    for(int i=0;i<values.length;i++) {
      b.record(values[i]);
    }
    LongRangeMultiSet set = b.getMultiSet(useAsm, useArrayImpl);

    start();
    for(int iter=0;iter<100;iter++) {
      //LongRangeMultiSet tree = new Test1();
      //LongRangeMultiSet tree = new Test2();
      int[] matchedRanges = new int[ranges.length];
      iterStart();
      long sum = 0;
      for(int i=0;i<values.length;i++) {
        sum += set.lookup(values[i], matchedRanges);
      }
      iterEnd(iter, sum);
    }
    end();
  }

  private static void testSimpleLinear(int[] values, LongRange[] ranges) {

    LinearLongRangeMultiSet set = new LinearLongRangeMultiSet(ranges);

    start();
    for(int iter=0;iter<100;iter++) {
      int[] matchedRanges = new int[ranges.length];
      iterStart();
      long sum = 0;
      for(int i=0;i<values.length;i++) {
        sum += set.lookup(values[i], matchedRanges);
      }
      iterEnd(iter, sum);
    }
    end();
  }
}
