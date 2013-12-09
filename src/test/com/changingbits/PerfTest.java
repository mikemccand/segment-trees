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

import java.util.Random;

//import com.google.common.collect.ImmutableRangeSet;
//import com.google.common.collect.Range;

// ant compile; javac -cp build/java src/test/com/changingbits/PerfTest.java; java -cp build/java:src/test:lib/asm-4.1.jar:lib/asm-commons-4.1.jar  com.changingbits.PerfTest

public class PerfTest {

  public static void main(String[] args) {
    int[] values = new int[10*1000000];
    long seed = 17;
    Random r = new Random(seed);

    for(int i=0;i<values.length;i++) {
      values[i] = r.nextInt(1000);
    }

    testSegmentTree(values);
    //testSimpleLinear(values);
    //testRangeSet(values);
  }

  private static void testSegmentTree(int[] values) {

    /*
    LongRange[] ranges = new LongRange[] {
      new LongRange("< 1", 0, true, 1, false),
      new LongRange("< 2", 0, true, 2, false),
      new LongRange("< 5", 0, true, 5, false),
      new LongRange("< 10", 0, true, 10, false)};
    */
    LongRange[] ranges = new LongRange[] {
      new LongRange("< 10", 0, true, 10, false),
      new LongRange("10 - 20", 10, true, 20, false),
      new LongRange("20 - 30", 20, true, 30, false),
      new LongRange("30 - 40", 30, true, 40, false),
      new LongRange("40 - 50", 40, true, 50, false),
      new LongRange("50 - 60", 50, true, 60, false),
      new LongRange("60 - 70", 60, true, 70, false),
      new LongRange("70 - 80", 70, true, 80, false)};

    Builder b = new Builder(ranges, 0, 1000);
    for(int i=0;i<values.length;i++) {
      b.record(values[i]);
    }
    LongRangeMultiSet set = b.getMultiSet(true);

    for(int iter=0;iter<100;iter++) {
      //LongRangeMultiSet tree = new Test1();
      //LongRangeMultiSet tree = new Test2();
      int[] matchedRanges = new int[ranges.length];
      long t0 = System.nanoTime();
      long sum = 0;
      for(int i=0;i<values.length;i++) {
        sum += set.lookup(values[i], matchedRanges);
      }
      long t1 = System.nanoTime();
      System.out.println("iter " + iter + ": " + (t1-t0)/1000000. + " msec; count=" + sum);
    }
  }

  /*
  private static void testRangeSet(int[] values) {
    ImmutableRangeSet.Builder<Long> b = new ImmutableRangeSet.Builder<Long>();
    b.add(Range.closedOpen(0L, 10L));
    b.add(Range.closedOpen(10L, 20L));
    b.add(Range.closedOpen(20L, 30L));
    b.add(Range.closedOpen(30L, 40L));
    b.add(Range.closedOpen(40L, 50L));
    b.add(Range.closedOpen(50L, 60L));
    b.add(Range.closedOpen(60L, 70L));
    b.add(Range.closedOpen(70L, 80L));

    ImmutableRangeSet<Long> s = b.build();
    System.out.println("set=" + s);
    for(int iter=0;iter<100;iter++) {
      //int[] counts = new int[ranges.length];
      long t0 = System.nanoTime();
      int count = 0;
      for(int i=0;i<values.length;i++) {
        Range<Long> r = s.rangeContaining((long) values[i]);
        if (r != null) {
          count++;
        }
      }
      long t1 = System.nanoTime();
      System.out.println("iter " + iter + ": " + (t1-t0)/1000000. + " msec; count=" + count);
    }
  }
  */

  private static void testSimpleLinear(int[] values) {
    LongRange[] ranges = new LongRange[] {
      new LongRange("< 10", 0, true, 10, false),
      new LongRange("10 - 20", 10, true, 20, false),
      new LongRange("20 - 30", 20, true, 30, false),
      new LongRange("30 - 40", 30, true, 40, false),
      new LongRange("40 - 50", 40, true, 50, false),
      new LongRange("50 - 60", 50, true, 60, false),
      new LongRange("60 - 70", 60, true, 70, false),
      new LongRange("70 - 80", 70, true, 80, false)};

    LinearLongRangeMultiSet set = new LinearLongRangeMultiSet(ranges);

    for(int iter=0;iter<100;iter++) {
      int[] matchedRanges = new int[ranges.length];
      long t0 = System.nanoTime();
      long sum = 0;
      for(int i=0;i<values.length;i++) {
        sum += set.lookup(values[i], matchedRanges);
      }
      long t1 = System.nanoTime();
      System.out.println("iter " + iter + ": " + (t1-t0)/1000000. + " msec; count=" + sum);
    }
  }
}
