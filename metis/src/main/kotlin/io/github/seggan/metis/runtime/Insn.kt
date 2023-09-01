package io.github.seggan.metis.runtime

import io.github.seggan.metis.BinOp
import io.github.seggan.metis.UnOp

sealed interface Insn {
    data class Push(val value: Value) : Insn
    data object Pop : Insn

    data class BinaryOp(val op: BinOp) : Insn
    data class UnaryOp(val op: UnOp) : Insn

    data object GetGlobals : Insn
    data class GetLocal(val name: String) : Insn

    data object Index : Insn
    data object Set : Insn
    data class IndexImm(val key: String) : Insn
    data class ListIndexImm(val key: Int) : Insn
    data class SetImm(val key: String, val allowNew: Boolean = true) : Insn
    data object Call : Insn
}