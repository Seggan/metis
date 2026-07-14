package io.github.seggan.metis.runtime.chunk

/**
 * A bytecode instruction.
 */
sealed interface Insn {
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
    data class JumpIf(val offset: Int, val condition: Boolean, val consume: Boolean = true) : Insn
    data class RawJumpIf(val label: Label, val condition: Boolean, val consume: Boolean = true) : IllegalInsn

    data object NoOp : IllegalInsn
}