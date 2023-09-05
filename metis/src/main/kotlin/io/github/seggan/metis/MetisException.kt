package io.github.seggan.metis

import io.github.seggan.metis.parsing.Span

abstract class MetisException(message: String, var span: Span? = null) : RuntimeException(message)

fun MetisException.report(code: String, filename: String): String {
    val span = span ?: return "Error in $filename: ${message ?: "Unknown error"}"
    val lines = code.split("\n")
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
    return """
        |Error in $filename:$lineNum:$colNum: ${message ?: "Unknown error"}
        |
        ||> $lineText
        |   $locationString""".trimMargin()
}