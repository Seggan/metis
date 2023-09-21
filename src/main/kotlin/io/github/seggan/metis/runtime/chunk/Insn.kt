package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.BinOp
import io.github.seggan.metis.UnOp
import io.github.seggan.metis.runtime.Value

sealed interface Insn {
    data class Push(val value: Value) : Insn
    data class PushClosure(val chunk: Chunk) : Insn {
        override fun toString(): String {
            return "PushClosure:\n" + chunk.toString().prependIndent("  ")
        }
    }

    data object Pop : Insn
    data class CloseUpvalue(val upvalue: Upvalue) : Insn

    data class CopyUnder(val index: Int) : Insn

    data class BinaryOp(val op: BinOp) : Insn
    data class UnaryOp(val op: UnOp) : Insn

    data class GetGlobal(val name: String) : Insn
    data class SetGlobal(val name: String) : Insn
    data class GetLocal(val index: Int) : Insn
    data class SetLocal(val index: Int) : Insn
    data class GetUpvalue(val index: Int) : Insn {
        init {
            require(index >= 0)
        }
    }

    data class SetUpvalue(val index: Int) : Insn {
        init {
            require(index >= 0)
        }
    }

    data object Index : Insn
    data object Set : Insn
    data class Call(val nargs: Int) : Insn
    data object Return : Insn
    data object Finish : Insn
}