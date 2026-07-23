package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.compilation.Compiler
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.parsing.Parser
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.value.Arity
import java.util.*

/**
 * A chunk is a compiled piece of code.
 *
 * @property name The name of the chunk.
 * @property insns The instructions of the chunk.
 * @property arity The arity of the chunk.
 * @property spans The spans of the chunk. Must be the same size as [insns].
 */
class Chunk(
    val name: String,
    val insns: List<Insn>,
    val registers: Int,
    val arity: Arity,
    private val id: UUID,
    val spans: List<Span>
) {

    /**
     * Provides a dissasembly of the chunk.
     */
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

    companion object {

        /**
         * Loads a chunk from a [CodeSource], performing lexing, parsing and compilation.
         *
         * @param source The source to load from.
         * @return The loaded chunk.
         */
        fun load(source: CodeSource): Chunk {
            val parser = Parser(Lexer.lex(source), source)
            return Compiler.compile(source.name, parser.parse())
        }
    }
}
