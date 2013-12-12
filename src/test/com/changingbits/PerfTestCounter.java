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
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

// ant compile; javac -cp build/java src/test/com/changingbits/PerfTestCounter.java; java -cp build/java:src/test:lib/asm-4.1.jar:lib/asm-commons-4.1.jar  com.changingbits.PerfTestCounter

public class PerfTestCounter {

  private static int DATA_COUNT = 10000000;
  private static int RANGE_COUNT = 10;
  private static int MAX_VALUE = 10000;
  private static int MAX_RANGE_VALUE = 1000;

  public static void main(String[] args) {
    int[] values = new int[DATA_COUNT];
    long seed = 17;
    Random r = new Random(seed);

    for(int i=0;i<values.length;i++) {
      values[i] = r.nextInt(MAX_VALUE);
    }

    for(int iter=0;iter<10;iter++) {

      System.out.println("\n\niter=" + iter);

      LongRange[] ranges = new LongRange[RANGE_COUNT];
      boolean overlap = (iter & 1) == 1;
      double inc = ((double) MAX_RANGE_VALUE) / RANGE_COUNT;
      for(int i=0;i<RANGE_COUNT;i++) {
        long min;
        if (overlap) {
          min = 0;
        } else {
          min = (long) (inc * i);
        }
        ranges[i] = new LongRange("range " + i,
                                  min,
                                  true,
                                  (long) (inc * (i+1)),
                                  false);
        System.out.println("  range " + i + ": " + ranges[i]);
      }

      System.out.println("\nTEST: java segment tree");
      testSegmentTree(values, ranges, false, false);
      System.out.println("\nTEST: java array segment tree");
      testSegmentTree(values, ranges, false, true);
      System.out.println("\nTEST: asm segment tree");
      testSegmentTree(values, ranges, true, false);
      System.out.println("\nTEST: linear search");
      testSimpleLinear(values, ranges);
      System.out.println("\nTEST: java counter");
      testCounter(values, ranges, true, false);
      System.out.println("\nTEST: asm counter, trained");
      testCounter(values, ranges, true, true);
      System.out.println("\nTEST: asm counter, un-trained");
      testCounter(values, ranges, false, true);
      System.out.println("\nTEST: asm counter2, trained");
      testCounter2(values, ranges, true);
      System.out.println("\nTEST: asm counter2, un-trained");
      testCounter2(values, ranges, false);
    }
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

    Builder b = new Builder(ranges, 0, 10000);
    for(int i=0;i<values.length;i++) {
      b.record(values[i]);
    }
    LongRangeMultiSet set = b.getMultiSet(useAsm, useArrayImpl);

    start();
    for(int iter=0;iter<100;iter++) {
      //LongRangeMultiSet tree = new Test1();
      //LongRangeMultiSet tree = new Test2();
      int[] matchedRanges = new int[ranges.length];
      int[] counts = new int[ranges.length];
      iterStart();
      for(int i=0;i<values.length;i++) {
        int count = set.lookup(values[i], matchedRanges);
        for(int j=0;j<count;j++) {
          counts[matchedRanges[j]]++;
        }
      }
      long sum = 0;
      for(int i=0;i<ranges.length;i++) {
        sum += counts[i];
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
      int[] counts = new int[ranges.length];
      iterStart();
      for(int i=0;i<values.length;i++) {
        int count = set.lookup(values[i], matchedRanges);
        for(int j=0;j<count;j++) {
          counts[matchedRanges[j]]++;
        }
      }
      long sum = 0;
      for(int i=0;i<ranges.length;i++) {
        sum += counts[i];
      }
      iterEnd(iter, sum);
    }

    end();
  }

  private static void testCounter(int[] values, LongRange[] ranges, boolean doTrain, boolean useAsm) {

    Builder b = new Builder(ranges, 0, 10000);
    // Training w/ java impl has no effect:
    if (useAsm && doTrain) {
      for(int i=0;i<values.length;i++) {
        b.record(values[i]);
      }
    }

    start();
    for(int iter=0;iter<100;iter++) {
      LongRangeCounter counter = b.getCounter(useAsm);
      int[] matchedRanges = new int[ranges.length];
      iterStart();
      for(int i=0;i<values.length;i++) {
        counter.add(values[i]);
      }
      int[] counts = counter.getCounts();
      long sum = 0;
      for(int i=0;i<ranges.length;i++) {
        sum += counts[i];
      }
      iterEnd(iter, sum);
    }
    end();
  }

  private static void testCounter2(int[] values, LongRange[] ranges, boolean doTrain) {

    Builder b = new Builder(ranges, 0, 10000);
    // Training w/ java impl has no effect:
    if (doTrain) {
      for(int i=0;i<values.length;i++) {
        b.record(values[i]);
      }
    }

    start();
    for(int iter=0;iter<100;iter++) {
      LongRangeCounter counter = b.getCounter2();
      int[] matchedRanges = new int[ranges.length];
      iterStart();
      for(int i=0;i<values.length;i++) {
        counter.add(values[i]);
      }
      int[] counts = counter.getCounts();
      long sum = 0;
      for(int i=0;i<ranges.length;i++) {
        sum += counts[i];
      }
      iterEnd(iter, sum);
    }
    end();
  }
}
