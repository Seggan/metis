package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.compilation.Register
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.runtime.value.metisValue

/**
 * A bytecode instruction.
 */
sealed interface Insn {

    data class Return(val register: Register) : Insn

    sealed interface DestInsn {
        val dest: Register
    }

    data class SetValue(override val dest: Register, val value: Value) : DestInsn {
        constructor(dest: Register, value: Int) : this(dest, value.metisValue())
        constructor(dest: Register, value: Nothing?) : this(dest, value.metisValue())
    }

    data class Move(val src: Register, override val dest: Register) : DestInsn

    data class Index(override val dest: Register, val target: Register, val index: Register) : DestInsn

    data class Call(override val dest: Register, val target: Register, val args: List<Register>) : DestInsn
    data class MetaCall(
        override val dest: Register,
        val target: Register,
        val method: String,
        val args: List<Register>
    ) : DestInsn

    data class Is(override val dest: Register, val value1: Register, val value2: Register) : DestInsn
    data class Not(override val dest: Register, val value: Register) : DestInsn

    class Label : Insn {
        override fun toString(): String = "Label@${hashCode().toString(16)}"
        override fun equals(other: Any?) = other === this
        override fun hashCode() = System.identityHashCode(this)
    }

    /**
     * Used to mark instructions that should only be used internally by the compiler, and should never be
     * emitted.
     */
    sealed interface IllegalInsn : Insn

    data class Jump(val offset: Int) : Insn
    data class RawJump(val label: Label) : IllegalInsn
    data class JumpIf(val offset: Int, val condition: Boolean, val register: Register) : Insn
    data class RawJumpIf(val label: Label, val condition: Boolean, val register: Register) : IllegalInsn

    data object NoOp : IllegalInsn
}