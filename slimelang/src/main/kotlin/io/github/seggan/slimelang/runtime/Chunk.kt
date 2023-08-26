package io.github.seggan.slimelang.runtime

import io.github.seggan.slimelang.parsing.Span

class Chunk(val name: String, private val insns: List<Insn>, val spans: List<Span>) : Value {
    override val metatable = null

    override fun toString(): String {
        return buildString {
            append("=== ")
            append(name)
            appendLine(" ===")
            for ((i, insn) in insns.withIndex()) {
                append(i)
                append(": ")
                append(insn)
                append(" (")
                val span = spans[i]
                append(span.start)
                append("..")
                append(span.end)
                appendLine(")")
            }
        }
    }
}
