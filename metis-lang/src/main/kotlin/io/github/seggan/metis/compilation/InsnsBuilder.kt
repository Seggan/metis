package io.github.seggan.metis.compilation

import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.Insn

/**
 * A builder for [Insn] lists. Allows for a more readable syntax when building instructions.
 *
 * @property span The [Span] of the instructions being built.
 */
class InsnsBuilder(val span: Span) {

    private val list = mutableListOf<FullInsn>()

    /**
     * Adds an instruction to the list.
     */
    operator fun Insn.unaryPlus() {
        list.add(this to span)
    }

    /**
     * Adds an instruction-[Span] pair to the list.
     */
    operator fun FullInsn.unaryPlus() {
        list.add(this)
    }

    /**
     * Adds a list of instructions to the list.
     */
    operator fun List<FullInsn>.unaryPlus() {
        list.addAll(this)
    }

    /**
     * Converts the builder to a list of instructions.
     */
    fun build(): List<FullInsn> {
        return list
    }
}

/**
 * Utility function for building instructions.
 *
 * @param span The [Span] of the instructions being built.
 * @param block The block to build the instructions in.
 * @return The list of instructions.
 */
inline fun buildInsns(span: Span, block: InsnsBuilder.() -> Unit): List<FullInsn> {
    return InsnsBuilder(span).apply(block).build()
}

inline fun buildInsns(node: AstNode, block: InsnsBuilder.() -> Unit): List<FullInsn> {
    return buildInsns(node.span, block)
}

/**
 * A pair of an [Insn] and a [Span].
 */
typealias FullInsn = Pair<Insn, Span>