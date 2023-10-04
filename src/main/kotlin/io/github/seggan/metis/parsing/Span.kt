package io.github.seggan.metis.parsing

import kotlin.math.max
import kotlin.math.min

data class Span(val start: Int, val end: Int, val source: CodeSource) {

    val length = end - start

    private val lineAndCol by lazy {
        val lines = source.text.split("\n")
        var pos = start
        var line = 0
        while (pos > lines[line].length) {
            pos -= lines[line].length + 1
            line++
        }
        LineAndCol(line + 1, pos + 1)
    }

    val line get() = lineAndCol.line
    val col get() = lineAndCol.col

    operator fun plus(other: Span) = Span(min(start, other.start), max(end, other.end), source)

    fun fancyDisplay(): String {
        val lines = source.text.split("\n")
        val lineText = lines[line - 1]
        val sb = StringBuilder()
        sb.append("|> ").appendLine(lineText)
        sb.append(" ".repeat(col + 2))
        sb.append("^")
        if (length > 1) {
            sb.append("~".repeat(length - 2))
            sb.appendLine("^")
        }
        return sb.toString()
    }
}

private data class LineAndCol(val line: Int, val col: Int)
