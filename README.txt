segment-trees

This project contains fast Java implementations of <a
href="http://en.wikipedia.org/wiki/Segment_tree">segment trees</a>
which are data structures to efficiently locate all ranges overlapping
a given point.

Segment trees do so in O(log(N) + M) time, where N is the total number
of ranges and M is the number of ranges matching the current point.

Under the hood the <a href="http://asm.ow2.org/">asm</a> library is
used to compile the segment tree into custom java bytecode to
increment counts for all ranges matching a given point.

This is not currently general purpose!  The set exposes only one
method, increment, which will increment provided int[] counters for
each range that matches the incoming value.

Run "ant dist" to create the JAR (dist/segment-trees-0.1.jar).
