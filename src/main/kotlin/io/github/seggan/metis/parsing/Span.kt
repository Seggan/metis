package io.github.seggan.metis.parsing

import kotlin.math.max
import kotlin.math.min

class Span(val start: Int, val end: Int, val file: Pair<String, String>? = null) {
    val length = end - start

    operator fun plus(other: Span) = Span(min(start, other.start), max(end, other.end), file ?: other.file)

    override fun equals(other: Any?): Boolean {
        return other is Span && other.start == start && other.end == end && other.file == file
    }

    override fun hashCode(): Int {
        var result = start
        result = 31 * result + end
        result = 31 * result + (file?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Span(start=$start, end=$end)"
    }
}
