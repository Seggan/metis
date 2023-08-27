package io.github.seggan.slimelang.parsing

import kotlin.math.max
import kotlin.math.min

data class Span(val start: Int, val end: Int) {
    val length = end - start

    operator fun plus(other: Span) = Span(min(start, other.start), max(end, other.end))
}
