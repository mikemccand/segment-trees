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

  public static void main(String[] args) {
    int[] values = new int[DATA_COUNT];
    long seed = 17;
    Random r = new Random(seed);

    for(int i=0;i<values.length;i++) {
      values[i] = r.nextInt(1000);
    }

    for(int i=0;i<10;i++) {
      LongRange[] ranges;
      if ((i & 1) != 0) {
        ranges = new LongRange[] {
          new LongRange("< 5", 0, true, 5, false),
          new LongRange("< 10", 0, true, 10, false),
          new LongRange("< 20", 0, true, 20, false),
          new LongRange("< 40", 0, true, 40, false),
          new LongRange("< 100", 0, true, 100, false)};
      } else {
        /*
        ranges = new LongRange[] {
          new LongRange("< 10", 0, true, 10, false),
          new LongRange("10 - 20", 10, true, 20, false),
          new LongRange("20 - 30", 20, true, 30, false),
          new LongRange("30 - 40", 30, true, 40, false),
          new LongRange("40 - 50", 40, true, 50, false),
          new LongRange("50 - 60", 50, true, 60, false),
          new LongRange("60 - 70", 60, true, 70, false),
          new LongRange("70 - 80", 70, true, 80, false)};
        */

        ranges = new LongRange[] {
          new LongRange("< 20", 0, true, 20, false),
          new LongRange("20 - 40", 20, true, 40, false),
          new LongRange("40 - 60", 40, true, 60, false),
          new LongRange("60 - 80", 60, true, 80, false),
          new LongRange("80 - 100", 80, true, 100, false),
          new LongRange(">= 100", 100, true, 200, false),
        };
      }

      /*
      ranges = new LongRange[30];
      for(int j=0;j<ranges.length;j++) {
        long x = r.nextInt(1000);
        long y = r.nextInt(1000);
        if (x > y) {
          long t = x;
          x = y;
          y = t;
        }
        ranges[j] = new LongRange("foo", x, true, y, true);
      }
      */
      System.out.println("\n\ni=" + i + " ranges=" + Arrays.toString(ranges));

      System.out.println("\nTEST: java segment tree");
      testSegmentTree(values, ranges, false);
      System.out.println("\nTEST: asm segment tree");
      testSegmentTree(values, ranges, true);
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
    System.out.println(String.format(Locale.ROOT, "  best: %s mvals/sec", nf.format(dataPerSec/1000000.0)));
  }

  private static void iterStart() {
    t0 = System.nanoTime();
  }

  private static void iterEnd(int iter, long sum) {
    long delay = System.nanoTime()-t0;
    if (iter > 5 && delay < fastestTime) {
      fastestTime = delay;
      double dataPerSec = ((double) DATA_COUNT) / (fastestTime/1000000000.0);
      //System.out.println(String.format(Locale.ROOT, "  iter %d: best: %s mvals/sec", iter, nf.format(dataPerSec/1000000.0)));
    }
  }

  private static void testSegmentTree(int[] values, LongRange[] ranges, boolean useAsm) {

    Builder b = new Builder(ranges, 0, 10000);
    for(int i=0;i<values.length;i++) {
      b.record(values[i]);
    }
    LongRangeMultiSet set = b.getMultiSet(useAsm);

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
