package io.github.seggan.metis.compilation

import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.Insn

class InsnsBuilder(val span: Span) {

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

    fun build(): List<FullInsn> {
        return list
    }
}

fun InsnsBuilder.generateMetaCall(name: String, nargs: Int) {
    +Insn.CopyUnder(nargs)
    +Insn.Push("metatable")
    +Insn.Index
    +Insn.Push(name)
    +Insn.Index
    +Insn.Call(nargs + 1)
}

inline fun buildInsns(span: Span, block: InsnsBuilder.() -> Unit): List<FullInsn> {
    return InsnsBuilder(span).apply(block).build()
}

typealias FullInsn = Pair<Insn, Span>