package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.Value

sealed interface Insn {
    data class Push(val value: Value) : Insn {
        constructor(value: Int) : this(Value.Number.of(value))
        constructor(value: Double) : this(Value.Number.of(value))
        constructor(value: Boolean) : this(Value.Boolean.of(value))
        constructor(value: String) : this(Value.String(value))
    }

    data class PushClosure(val chunk: Chunk) : Insn {
        override fun toString(): String {
            return "PushClosure:\n" + chunk.toString().prependIndent("  ")
        }
    }

    data class PushList(val size: Int) : Insn
    data class PushTable(val size: Int) : Insn
    data class PushError(val type: String) : Insn

    data object Pop : Insn
    data class CloseUpvalue(val upvalue: Upvalue) : Insn

    data class CopyUnder(val index: Int) : Insn
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

    /**
     * Used to mark a value as being used, so that it doesn't get popped. Used in the implementations of
     * `return` and `raise`.
     */
    data object ToBeUsed : Insn
    data object Return : Insn

    data object Raise : Insn
    data class PushErrorHandler(val handler: ErrorHandler) : Insn
    data class PushFinally(val label: Label) : Insn
    data object PopErrorHandler : Insn
    data object PopFinally : Insn

    class Label : Insn {
        override fun toString(): String = "Label@${hashCode().toString(16)}"
        override fun equals(other: Any?) = other === this
        override fun hashCode() = System.identityHashCode(this)
    }

    data class Jump(val offset: Int) : Insn
    data class RawJump(val label: Label) : Insn
    data class JumpIf(val offset: Int, val condition: Boolean, val consume: Boolean = true) : Insn
    data class RawJumpIf(val label: Label, val condition: Boolean, val consume: Boolean = true) : Insn

    data object Not : Insn
    data object Is : Insn
}