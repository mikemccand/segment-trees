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
import java.util.Random;

import junit.framework.TestCase;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestLongRangeMultiSet {

  private static Random random;
  private static boolean VERBOSE = false;

  @BeforeClass
  public static void beforeClass() {
    long seed = new Random().nextLong();
    System.out.println("NOTE: random seed=" + seed);
    TestLongRangeMultiSet.random = new Random(seed);
  }

  @Test
  public void testOverlappingSimple() {
    LongRange[] ranges = new LongRange[] {
        new LongRange("< 1", 0, true, 1, false),
        new LongRange("< 2", 0, true, 2, false),
        new LongRange("< 5", 0, true, 5, false),
        new LongRange("< 10", 0, true, 10, false)
    };

    LongRangeMultiSet.Builder b = new LongRangeMultiSet.Builder(ranges);

    maybeTrain(b, 0, 200);

    LongRangeMultiSet set = b.finish(true);
    
    for(long x = -10; x < 100; x++) {
      verify(ranges, set, x);
    }
  }

  private void maybeTrain(LongRangeMultiSet.Builder b, int min, int max) {
    if (random.nextBoolean()) {
      int count = atLeast(200);
      for(int i=0;i<count;i++) {
        b.record(min + random.nextInt(max-min));
      }
    }
  }

  @Test
  public void testOverlappingBoundedRange() {
    LongRange[] ranges = new LongRange[] {
        new LongRange("< 1", 0, true, 1, false),
        new LongRange("< 2", 0, true, 2, false),
        new LongRange("< 5", 0, true, 5, false),
        new LongRange("< 10", 0, true, 10, false)
    };

    LongRangeMultiSet.Builder b = new LongRangeMultiSet.Builder(ranges, 0, 100);

    maybeTrain(b, 0, 100);

    LongRangeMultiSet set = b.finish(true);
    
    for(long x = 0; x < 100; x++) {
      verify(ranges, set, x);
    }
  }

  @Test
  public void testLongMinMax() {
    // Closed on both:
    LongRange[] ranges = new LongRange[] {
      new LongRange("all", Long.MIN_VALUE, true, Long.MAX_VALUE, true)};
    LongRangeMultiSet set = new LongRangeMultiSet.Builder(ranges).finish(true);
    verify(ranges, set, Long.MIN_VALUE);
    verify(ranges, set, Long.MAX_VALUE);
    verify(ranges, set, 0);

    // Open on min, closed on max:
    ranges = new LongRange[] {
      new LongRange("all", Long.MIN_VALUE, false, Long.MAX_VALUE, true)};
    set = new LongRangeMultiSet.Builder(ranges).finish(true);
    verify(ranges, set, Long.MIN_VALUE);
    verify(ranges, set, Long.MAX_VALUE);
    verify(ranges, set, 0);

    // Closed on min, open on max:
    ranges = new LongRange[] {
      new LongRange("all", Long.MIN_VALUE, true, Long.MAX_VALUE, false)};
    set = new LongRangeMultiSet.Builder(ranges).finish(true);
    verify(ranges, set, Long.MIN_VALUE);
    verify(ranges, set, Long.MAX_VALUE);
    verify(ranges, set, 0);

    // Open on min, open on max:
    ranges = new LongRange[] {
      new LongRange("all", Long.MIN_VALUE, false, Long.MAX_VALUE, false)};
    set = new LongRangeMultiSet.Builder(ranges).finish(true);
    verify(ranges, set, Long.MIN_VALUE);
    verify(ranges, set, Long.MAX_VALUE);
    verify(ranges, set, 0);
  }

  private void verify(LongRange[] ranges, LongRangeMultiSet set, long v) {
    int[] actual = new int[ranges.length];
    set.increment(actual, v);

    // Count slowly but hopefully correctly!
    int[] expected = new int[ranges.length];
    for(int i=0;i<ranges.length;i++) {
      LongRange range = ranges[i];
      if (range.accept(v)) {
        expected[i]++;
      }
    }

    assertEquals("v=" + v, Arrays.toString(expected), Arrays.toString(actual));
  }

  @Test
  public void testNonOverlappingSimple() {
    LongRange[] ranges = new LongRange[] {
        new LongRange("< 10", 0, true, 10, false),
        new LongRange("10 - 20", 10, true, 20, false),
        new LongRange("20 - 30", 20, true, 30, false),
        new LongRange("30 - 40", 30, true, 40, false),
        new LongRange("40 - 50", 40, true, 50, false),
        new LongRange("50 - 60", 50, true, 60, false),
        new LongRange("60 - 70", 60, true, 70, false),
        new LongRange("70 - 80", 70, true, 80, false),
      };

    LongRangeMultiSet.Builder b = new LongRangeMultiSet.Builder(ranges);

    maybeTrain(b, 0, 200);

    LongRangeMultiSet set = b.finish(true);
    for(long x = -10; x < 100; x++) {
      verify(ranges, set, x);
    }
  }

  @Test
  public void testNonOverlappingBounded() {
    LongRange[] ranges = new LongRange[] {
        new LongRange("< 10", 0, true, 10, false),
        new LongRange("10 - 20", 10, true, 20, false),
        new LongRange("20 - 30", 20, true, 30, false),
        new LongRange("30 - 40", 30, true, 40, false),
        new LongRange("40 - 50", 40, true, 50, false),
        new LongRange("50 - 60", 50, true, 60, false),
        new LongRange("60 - 70", 60, true, 70, false),
        new LongRange("70 - 80", 70, true, 80, false),
      };

    LongRangeMultiSet.Builder b = new LongRangeMultiSet.Builder(ranges, 0, 200);

    maybeTrain(b, 0, 200);

    LongRangeMultiSet set = b.finish(true);
    for(long x = 0; x < 200; x++) {
      verify(ranges, set, x);
    }
  }

  private int atLeast(int n) {
    return n + random.nextInt(n);
  }

  @Test
  public void testRandom() {
    int iters = atLeast(100);
    for(int iter=0;iter<iters;iter++) {
      int numRange = 1+random.nextInt(9);
      LongRange[] ranges = new LongRange[numRange];
      if (VERBOSE) {
        System.out.println("\nTEST: iter=" + iter);
      }

      for(int i=0;i<numRange;i++) {
        long x = random.nextInt(1000);
        long y = random.nextInt(1000);
        if (x > y) {
          long t = x;
          x = y;
          y = t;
        }
        ranges[i] = new LongRange(""+i, x, random.nextBoolean(), y, random.nextBoolean());
        if (VERBOSE) {
          System.out.println("  range " + i + ": " + ranges[i]);
        }
      }

      LongRangeMultiSet.Builder b = new LongRangeMultiSet.Builder(ranges);
      maybeTrain(b, 0, 1000);
      LongRangeMultiSet set = b.finish(random.nextBoolean());

      int numPoints = 200;
      for(int i=0;i<numPoints;i++) {
        long v = random.nextInt(1100) - 50;
        verify(ranges, set, v);
      }
    }
  }
}
