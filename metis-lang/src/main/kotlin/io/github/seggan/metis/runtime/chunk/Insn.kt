package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.compilation.Register
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.runtime.value.metisValue

/**
 * A bytecode instruction.
 */
sealed interface Insn {

    data class Clear(val registers: List<Register>) : Insn

    data class Return(val register: Register) : Insn

    sealed interface DestInsn : Insn {
        val dest: Register
    }

    data class SetValue(override val dest: Register, val value: Value) : DestInsn {
        constructor(dest: Register, value: Int) : this(dest, value.metisValue())
        constructor(dest: Register, value: String) : this(dest, value.metisValue())
        constructor(dest: Register, value: Nothing?) : this(dest, value.metisValue())
    }

    data class ConstructList(override val dest: Register, val elements: List<Register>) : DestInsn
    data class ConstructTable(override val dest: Register, val elements: List<Pair<Register, Register>>) : DestInsn
    data class ConstructError(
        override val dest: Register,
        val type: String,
        val message: Register,
        val companionData: Register
    ) : DestInsn

    data class Move(override val dest: Register, val src: Register) : DestInsn

    data class Call(override val dest: Register, val target: Register, val args: List<Register>) : DestInsn
    data class MetaCall(
        override val dest: Register,
        val target: Register,
        val method: String,
        val args: List<Register>
    ) : DestInsn

    data class Is(override val dest: Register, val value1: Register, val value2: Register) : DestInsn
    data class Not(override val dest: Register, val value: Register) : DestInsn

    data class GetGlobal(override val dest: Register, val name: String) : DestInsn
    data class SetGlobal(val name: String, val value: Register) : Insn
    data class UpdateGlobal(val name: String, val value: Register) : Insn

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