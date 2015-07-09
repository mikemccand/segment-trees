This project contains fast Java implementations of <a href='http://en.wikipedia.org/wiki/Segment_tree'>segment trees</a>, which are binary tree structures to efficiently locate all ranges overlapping a given point.

See <a href='http://blog.mikemccandless.com/2013/12/fast-range-faceting-using-segment-trees.html'>this blog post</a> for details.

Segment trees require O(log(N) + M) time for each query ("find all ranges overlapping a point"), where N is the total number of ranges and M is the number of ranges matching the current point.  After the crossover point (~10 ranges or so), segment trees are faster than a simple linear search.

The code is exploratory; there are various implementations, some slow, some fast.

Currently, only long values are supported, and there are different implementations of LongRangeMultiSet to look up all ranges overlapping a given values, as well LongRangeCounter to count the number of times each range occurs across a number of values.  Ranges are allowed to overlap, so multiple ranges can match a given point.

Under the hood I use the <a href='http://asm.ow2.org/'>ASM</a> library to create specialized Java bytecode (pass useAsm=true to Builder.getCounter and Builder.getMultiSet) to find matching ranges for a given point and increment counts; this simplifies the implementation (no more recursion, for loops, etc.) and makes it quite a bit faster in certain cases (up to 2.5X in the micro-benchmarks, PerfTestMultiSet and PerfTestCounter).