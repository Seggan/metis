package io.github.seggan.metis.parsing

import kotlin.math.max
import kotlin.math.min

data class Span(val start: Int, val end: Int, val file: Pair<String, String>? = null) {
    val length = end - start

    operator fun plus(other: Span) = Span(min(start, other.start), max(end, other.end), file)
}
