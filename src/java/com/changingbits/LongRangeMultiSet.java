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

/** This class exposes only one method, {@link #increment},
 *  which for a given long value will increment the count by
 *  1 for each range containing that value.
 *
 *  <p> See {@link Builder#getMultiSet} for creating an
 *  instance of this. */

public abstract class LongRangeMultiSet {

  /** For a given value, lookup the range indices that it
   *  matches.  This places each matched range index into
   *  answers and returns the number of matched
   *  ranges. */
  public abstract int lookup(long v, int[] answers);
}
