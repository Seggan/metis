package io.github.seggan.metis.compilation.op

import io.github.seggan.metis.compilation.FullInsn
import io.github.seggan.metis.compilation.InsnsBuilder
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.value.Value

enum class BinOp(internal val generateCode: InsnsBuilder.(List<FullInsn>, List<FullInsn>) -> Unit) {
    PLUS(Metamethod.PLUS),
    MINUS(Metamethod.MINUS),
    TIMES(Metamethod.TIMES),
    DIV(Metamethod.DIV),
    FLOORDIV(Metamethod.FLOORDIV),
    MOD(Metamethod.MOD),
    POW(Metamethod.POW),
    RANGE(Metamethod.RANGE),
    INCLUSIVE_RANGE(Metamethod.INCLUSIVE_RANGE),
    BIT_AND(Metamethod.BIT_AND),
    BIT_OR(Metamethod.BIT_OR),
    BIT_XOR(Metamethod.BIT_XOR),
    SHL(Metamethod.SHL),
    SHR(Metamethod.SHR),
    SHRU(Metamethod.USHR),
    IN({ left, right ->
        +right
        +left
        +Insn.MetaCall(1, Metamethod.CONTAINS)
    }),
    NOT_IN(IN),
    IS({ left, right ->
        +left
        +right
        +Insn.Is
    }),
    IS_NOT(IS),
    EQ(Metamethod.EQUALS),
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
        +Insn.Push(Value.Null)
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
        +Insn.MetaCall(1, Metamethod.COMPARE)
        +Insn.Push(number)
        +Insn.MetaCall(1, Metamethod.EQUALS)
        if (inverse) {
            +Insn.Not
        }
    })
}

