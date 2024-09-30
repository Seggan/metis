package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.runtime.value.metis
import java.io.Serial
import java.io.Serializable

/**
 * A bytecode instruction.
 */
sealed interface Insn : Serializable {
    data class Push(val value: Value) : Insn {
        constructor(value: Boolean) : this(value.metis())
        constructor(value: Int) : this(value.metis())
        constructor(value: String) : this(value.metis())

        companion object {
            @Serial
            private const val serialVersionUID: Long = 1660780603372264743L
        }
    }

    data class PushList(val size: Int) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -5364502813247120988L
        }
    }

    data class PushTable(val size: Int) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 6621803040666534871L
        }
    }

    data class PushError(val type: String) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 5115800999210780055L
        }
    }

    data class PushClosure(val chunk: Chunk) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 8291564599520902997L
        }
    }

    data object Pop : Insn {
        @Serial
        private const val serialVersionUID: Long = -6739265494872448178L
        private fun readResolve(): Any = Pop
    }

    data class CopyUnder(val index: Int) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -7273450874315180788L
        }
    }

    data class GetLocal(val index: Int) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -2073873469412344841L
        }
    }

    data class SetLocal(val index: Int) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 5697746354967294939L
        }
    }

    data class GetUpvalue(val index: Int) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 938595845341997197L
        }
    }

    data class SetUpvalue(val index: Int) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -1665520782012138969L
        }
    }

    data class GetGlobal(val name: String) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -7952843147133482401L
        }
    }

    data class SetGlobal(val name: String, val define: Boolean) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -696320893756374921L
        }
    }

    data object GetIndex : Insn {
        @Serial
        private const val serialVersionUID: Long = 8772037543541626775L
        private fun readResolve(): Any = GetIndex
    }

    data object SetIndex : Insn {
        @Serial
        private const val serialVersionUID: Long = -3869441033433834753L
        private fun readResolve(): Any = SetIndex
    }

    data class Call(val nargs: Int, val selfProvided: Boolean) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 2154317318972609742L
        }
    }

    data class MetaCall(val nargs: Int, val meta: String) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -906224048770950010L
        }
    }

    sealed interface IllegalInsn : Insn

    class Label : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 4453813557689089786L
        }

        override fun toString(): String = "Label"
    }

    sealed interface RawJump : IllegalInsn {
        fun backpatch(insns: List<Insn>): Insn
    }

    data class RawDirectJump(val label: Label) : RawJump {
        override fun backpatch(insns: List<Insn>) = DirectJump(insns.indexOf(label))

        companion object {
            @Serial
            private const val serialVersionUID: Long = -8232992148583217641L
        }
    }

    data class DirectJump(val target: Int) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = 6237834199750913410L
        }
    }

    data class RawJumpIf(val label: Label, val condition: Boolean, val consume: Boolean = true) : RawJump {
        override fun backpatch(insns: List<Insn>) = JumpIf(insns.indexOf(label), condition, consume)

        companion object {
            @Serial
            private const val serialVersionUID: Long = 120223192455000585L
        }
    }

    data class JumpIf(val target: Int, val condition: Boolean, val consume: Boolean) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -1431705558968004771L
        }
    }

    data object Save : Insn {
        @Serial
        private const val serialVersionUID: Long = 5644027842921583616L
        private fun readResolve(): Any = Save
    }

    data object Return : Insn {
        @Serial
        private const val serialVersionUID: Long = -4225107689346185678L
        private fun readResolve(): Any = Return
    }

    data object Is : Insn {
        @Serial
        private const val serialVersionUID: Long = 7543847006013955920L
        private fun readResolve(): Any = Is
    }

    data object Not : Insn {
        @Serial
        private const val serialVersionUID: Long = 5946180141308243873L
        private fun readResolve(): Any = Not
    }

    data class Import(val module: String) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -5424849480784621833L
        }
    }

    data class CloseUpvalue(val upvalue: Upvalue) : Insn {
        companion object {
            @Serial
            private const val serialVersionUID: Long = -8172957473561045375L
        }
    }
}