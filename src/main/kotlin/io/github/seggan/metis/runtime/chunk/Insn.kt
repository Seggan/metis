package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.Value
import kotlin.properties.Delegates

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
    data object Return : Insn
    data object Finish : Insn

    sealed interface Jumping : Insn {
        var label: Label
    }

    data class Jump(override var label: Label) : Jumping
    data class JumpIf(override var label: Label, val bool: Boolean, val consume: Boolean = true) : Jumping

    data object Not : Insn
}

class Label {
    var start: Int by Delegates.notNull()
    var end: Int by Delegates.notNull()

    val offset by lazy {
        end - start - 1
    }

    override fun toString(): String {
        return "Label(start=$start, end=$end, offset=$offset)"
    }
}