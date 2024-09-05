package io.github.seggan.metis.compilation.op

import io.github.seggan.metis.compilation.FullInsn
import io.github.seggan.metis.compilation.InsnsBuilder
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.value.NullValue
import io.github.seggan.metis.runtime.value.metis

enum class BinOp(internal val generateCode: InsnsBuilder.(List<FullInsn>, List<FullInsn>) -> Unit) {
    PLUS("__plus__"),
    MINUS("__minus__"),
    TIMES("__times__"),
    DIV("__div__"),
    FLOORDIV("__floordiv__"),
    MOD("__mod__"),
    POW("__pow__"),
    RANGE("__range__"),
    INCLUSIVE_RANGE("__inclRange__"),
    BAND("__band__"),
    BOR("__bor__"),
    BXOR("__bxor__"),
    SHL("__shl__"),
    SHR("__shr__"),
    SHRU("__shru__"),
    IN({ left, right ->
        +right
        +left
        +Insn.MetaCall(1, "__contains__")
    }),
    NOT_IN(IN),
    IS({ left, right ->
        +left
        +right
        +Insn.Is
    }),
    IS_NOT(IS),
    EQ("__eq__"),
    NOT_EQ(EQ),
    LESS(-1),
    LESS_EQ(1, true),
    GREATER(1),
    GREATER_EQ(-1, true),
    AND({ left, right ->
        +left
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = false, consume = false)
        +Insn.Pop
        +right
        +end
    }),
    OR({ left, right ->
        +left
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = true, consume = false)
        +Insn.Pop
        +right
        +end
    }),
    ELVIS({ left, right ->
        +left
        +Insn.CopyUnder(0)
        +Insn.Push(NullValue)
        +Insn.Is
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = false)
        +Insn.Pop
        +right
        +end
    });

    constructor(metamethod: String) : this({ left, right ->
        +left
        +right
        +Insn.MetaCall(1, metamethod)
    })

    constructor(op: BinOp) : this({ left, right ->
        op.generateCode(this, left, right)
        +Insn.Not
    })

    constructor(number: Int, inverse: Boolean = false) : this({ left, right ->
        +left
        +right
        +Insn.MetaCall(1, "__cmp__")
        +Insn.Push(number)
        +Insn.CopyUnder(1)
        +Insn.GetIndexDirect(eqString)
        +Insn.Call(2, true)
        if (inverse) {
            +Insn.Not
        }
    })
}

private val eqString = "__eq__".metis()