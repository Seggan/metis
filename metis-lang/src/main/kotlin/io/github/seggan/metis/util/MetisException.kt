package io.github.seggan.metis.util

import io.github.seggan.metis.parsing.Span
import java.io.Serial

/**
 * An exception thrown by Metis by the parser, compiler, or runtime.
 *
 * @param message The error message.
 * @param backtrace The backtrace of the error.
 * @param cause The cause of the error.
 */
abstract class MetisException(
    message: String,
    val backtrace: MutableList<Span>,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * Returns a string representation of this exception.
     *
     * @param sourceName The name of the source file.
     * @return A string representation of this exception.
     */
    fun report(sourceName: String): String {
        if (backtrace.isEmpty()) return "Error in ${sourceName}: ${message ?: "Unknown error"}"
        val mainSb = StringBuilder("Error ")
        var first = true
        for (span in backtrace) {
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
            mainSb.appendLine(span.fancyToString())
        }
        return mainSb.toString()
    }

    /**
     * Adds a stack frame to the backtrace.
     *
     * @param span The span to add.
     */
    fun addStackFrame(span: Span) {
        backtrace.add(span)
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = 3614478819340206164L
    }
}