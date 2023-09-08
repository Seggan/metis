package io.github.seggan.metis

import io.github.seggan.metis.parsing.Span

abstract class MetisException(message: String, val stacktrace: MutableList<Span>) : RuntimeException(message) {

    fun report(code: String, filename: String): String {
        if (stacktrace.isEmpty()) return "Error in $filename: ${message ?: "Unknown error"}"
        val mainSb = StringBuilder("Error ")
        var first = true
        for (span in stacktrace) {
            val lines = (span.file?.second ?: code).split("\n")
            var pos = span.start
            var line = 0
            while (pos > lines[line].length) {
                pos -= lines[line].length + 1
                line++
            }
            val lineText = lines[line]
            val lineNum = line + 1
            val colNum = pos + 1
            val locationString = StringBuilder(span.length)
            locationString.append(" ".repeat(pos))
            locationString.append("^")
            if (span.length > 1) {
                locationString.append("~".repeat(span.length - 2))
                locationString.append("^")
            }
            mainSb.append("in ")
                .append(span.file?.first ?: filename)
                .append(':')
                .append(lineNum)
                .append(':')
                .append(colNum)
                .append(": ")
            if (first) {
                mainSb.appendLine(message ?: "Unknown error")
                first = false
            } else {
                mainSb.appendLine()
            }
            mainSb.appendLine()
            mainSb.append("|> ")
                .appendLine(lineText)
            mainSb.append("   ")
                .appendLine(locationString)
            mainSb.appendLine()
        }
        return mainSb.toString()
    }

    fun addStackFrame(span: Span) {
        stacktrace.add(span)
    }
}