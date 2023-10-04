package io.github.seggan.metis.debug

import io.github.seggan.metis.parsing.Span

data class Breakpoint(val line: Int, val file: String? = null) {

    fun isInSpan(span: Span) = (file == null || span.source.name == file)
            && line == span.line

    override fun toString() = "${file ?: "<any>"}:$line"
}
