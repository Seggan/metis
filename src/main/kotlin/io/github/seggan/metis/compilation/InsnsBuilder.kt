package io.github.seggan.metis.compilation

import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.chunk.Label

internal class InsnsBuilder(val span: Span) {

    private val list = mutableListOf<FullInsn>()

    operator fun Insn.unaryPlus() {
        list.add(this to span)
    }

    operator fun FullInsn.unaryPlus() {
        list.add(this)
    }

    operator fun List<FullInsn>.unaryPlus() {
        list.addAll(this)
    }

    operator fun Label.unaryPlus() {
        end = list.size
    }

    operator fun Insn.Jumping.unaryPlus() {
        label.start = list.size
        list.add(this to span)
    }

    fun build(): List<FullInsn> {
        return list
    }
}

internal inline fun buildInsns(span: Span, block: InsnsBuilder.() -> Unit): List<FullInsn> {
    return InsnsBuilder(span).apply(block).build()
}

internal typealias FullInsn = Pair<Insn, Span>