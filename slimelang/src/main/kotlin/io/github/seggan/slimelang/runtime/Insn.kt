package io.github.seggan.slimelang.runtime

import io.github.seggan.slimelang.BinOp
import io.github.seggan.slimelang.UnOp

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
    data class SetImm(val key: String, val allowNew: Boolean = true) : Insn
}