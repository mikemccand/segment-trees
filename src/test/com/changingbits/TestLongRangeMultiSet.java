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

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TestLongRangeMultiSet {

  private static Random random;
  private static boolean VERBOSE;

  @BeforeClass
  public static void beforeClass() {
    long seed = new Random().nextLong();
    System.out.println("NOTE: random seed=" + seed);
    random = new Random(seed);
  }

  @Test
  public void testOverlappingSimple() {
    LongRange[] ranges = new LongRange[] {
        new LongRange("< 1", 0, true, 1, false),
        new LongRange("< 2", 0, true, 2, false),
        new LongRange("< 5", 0, true, 5, false),
        new LongRange("< 10", 0, true, 10, false)
    };

    Builder b = new Builder(ranges);

    maybeTrain(b, 0, 200);

    LongRangeMultiSet set = b.getMultiSet(true, random.nextBoolean());
    
    for(long x = -10; x < 100; x++) {
      verify(ranges, set, x);
    }
  }

  @Test
  public void testBasic() {
    LongRange[] ranges = new LongRange[] {
        new LongRange("a", 0, true, 10, false),
        new LongRange("b", 0, true, 20, false),
        new LongRange("c", 10, true, 30, false),
        new LongRange("d", 15, true, 50, false),
        new LongRange("e", 40, true, 70, false),
    };

    Builder b = new Builder(ranges);

    maybeTrain(b, 0, 200);

    LongRangeMultiSet set = b.getMultiSet(true, random.nextBoolean());
    
    for(long x = -10; x < 100; x++) {
      verify(ranges, set, x);
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

    Builder b = new Builder(ranges, 0, 100);

    maybeTrain(b, 0, 100);

    LongRangeMultiSet set = b.getMultiSet(true, random.nextBoolean());
    
    for(long x = 0; x < 100; x++) {
      verify(ranges, set, x);
    }
  }

  @Test
  public void testLongMinMax() {
    // Closed on both:
    LongRange[] ranges = new LongRange[] {
      new LongRange("all", Long.MIN_VALUE, true, Long.MAX_VALUE, true)};
    LongRangeMultiSet set = new Builder(ranges).getMultiSet(true, random.nextBoolean());
    verify(ranges, set, Long.MIN_VALUE);
    verify(ranges, set, Long.MAX_VALUE);
    verify(ranges, set, 0);

    // Open on min, closed on max:
    ranges = new LongRange[] {
      new LongRange("all", Long.MIN_VALUE, false, Long.MAX_VALUE, true)};
    set = new Builder(ranges).getMultiSet(true, random.nextBoolean());
    verify(ranges, set, Long.MIN_VALUE);
    verify(ranges, set, Long.MAX_VALUE);
    verify(ranges, set, 0);

    // Closed on min, open on max:
    ranges = new LongRange[] {
      new LongRange("all", Long.MIN_VALUE, true, Long.MAX_VALUE, false)};
    set = new Builder(ranges).getMultiSet(true, random.nextBoolean());
    verify(ranges, set, Long.MIN_VALUE);
    verify(ranges, set, Long.MAX_VALUE);
    verify(ranges, set, 0);

    // Open on min, open on max:
    ranges = new LongRange[] {
      new LongRange("all", Long.MIN_VALUE, false, Long.MAX_VALUE, false)};
    set = new Builder(ranges).getMultiSet(true, random.nextBoolean());
    verify(ranges, set, Long.MIN_VALUE);
    verify(ranges, set, Long.MAX_VALUE);
    verify(ranges, set, 0);
  }

  private void verify(LongRange[] ranges, LongRangeMultiSet set, long v) {
    int[] result = new int[ranges.length];
    int actualCount = set.lookup(v, result);
    int[] actual = new int[actualCount];
    System.arraycopy(result, 0, actual, 0, actualCount);
    Arrays.sort(actual);

    // Count slowly but hopefully correctly!
    int expectedCount = 0;
    for(int i=0;i<ranges.length;i++) {
      LongRange range = ranges[i];
      if (range.accept(v)) {
        result[expectedCount++] = i;
      }
    }
    int[] expected = new int[expectedCount];
    System.arraycopy(result, 0, expected, 0, expectedCount);
    assertTrue("v=" + v + " expected=" + Arrays.toString(expected) + " vs actual=" + Arrays.toString(actual),
               Arrays.equals(expected, actual));
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

    Builder b = new Builder(ranges);

    maybeTrain(b, 0, 200);

    LongRangeMultiSet set = b.getMultiSet(true, random.nextBoolean());
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

    Builder b = new Builder(ranges, 0, 200);

    maybeTrain(b, 0, 200);

    LongRangeMultiSet set = b.getMultiSet(true, random.nextBoolean());
    for(long x = 0; x < 200; x++) {
      verify(ranges, set, x);
    }
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

      Builder b = new Builder(ranges);
      maybeTrain(b, 0, 1000);
      boolean useAsm = random.nextBoolean();
      if (VERBOSE) {
        System.out.println("  useAsm=" + useAsm);
      }
      LongRangeMultiSet set = b.getMultiSet(useAsm, random.nextBoolean());

      int numPoints = 200;
      for(int i=0;i<numPoints;i++) {
        long v = random.nextInt(1100) - 50;
        if (VERBOSE) {
          System.out.println("  verify: v=" + v);
        }
        verify(ranges, set, v);
      }
    }
  }

  private void maybeTrain(Builder b, int min, int max) {
    // nocommit
    if (false && random.nextBoolean()) {
      int count = atLeast(200);
      if (VERBOSE) {
        System.out.println("  record " + count + " values");
      }
      for(int i=0;i<count;i++) {
        b.record(min + random.nextInt(max-min));
      }
    }
  }

  private int atLeast(int n) {
    return n + random.nextInt(n);
  }
}
