package io.github.seggan.metis.runtime

import io.github.seggan.metis.BinOp
import io.github.seggan.metis.UnOp
import io.github.seggan.metis.runtime.values.Value

sealed interface Insn {
    data class Push(val value: Value) : Insn {
        override fun toString(): String {
            return "Push:\n" + value.toString().prependIndent("  ")
        }
    }

    data object Pop : Insn
    data class CopyUnder(val index: Int) : Insn

    data class BinaryOp(val op: BinOp) : Insn
    data class UnaryOp(val op: UnOp) : Insn

    data object GetGlobals : Insn
    data class GetLocal(val index: Int) : Insn

    data object Index : Insn
    data object Set : Insn
    data class IndexImm(val key: String) : Insn
    data class ListIndexImm(val key: Int) : Insn
    data class SetImm(val key: String, val allowNew: Boolean = true) : Insn
    data class Call(val nargs: Int) : Insn
    data object Return : Insn
}