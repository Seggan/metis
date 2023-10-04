package io.github.seggan.metis

import io.github.seggan.metis.parsing.Span

abstract class MetisException(message: String, private val stacktrace: MutableList<Span>) : RuntimeException(message) {

    fun report(sourceName: String): String {
        if (stacktrace.isEmpty()) return "Error in ${sourceName}: ${message ?: "Unknown error"}"
        val mainSb = StringBuilder("Error ")
        var first = true
        for (span in stacktrace) {
            mainSb.append("in ")
                .append(span.source.name)
                .append(':')
                .append(span.line)
                .append(':')
                .append(span.col)
                .append(": ")
            if (first) {
                mainSb.appendLine(message ?: "Unknown error")
                first = false
            } else {
                mainSb.appendLine()
            }
            mainSb.appendLine()
            mainSb.append(span.fancyDisplay())
        }
        return mainSb.toString()
    }

    fun addStackFrame(span: Span) {
        stacktrace.add(span)
    }
}

class MetisRuntimeException(message: String) : MetisException(message, mutableListOf())