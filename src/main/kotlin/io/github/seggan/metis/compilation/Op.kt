package io.github.seggan.metis.compilation

import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.chunk.Label

enum class BinOp(internal val generateCode: InsnsBuilder.(List<FullInsn>, List<FullInsn>) -> Unit) {
    PLUS("__plus__"),
    MINUS("__minus__"),
    TIMES("__times__"),
    DIV("__div__"),
    MOD("__mod__"),
    POW("__pow__"),
    EQ("__eq__"),
    NOT_EQ({ left, right ->
        +left
        +right
        generateMetaCall("__eq__", 1)
        +Insn.Not
    }),
    LESS(-1),
    LESS_EQ(1, true),
    GREATER(1),
    GREATER_EQ(-1, true),
    AND({ left, right ->
        +left
        val end = Label()
        +Insn.JumpIf(end, bool = false, consume = false)
        +Insn.Pop
        +right
        +end
    }),
    OR({ left, right ->
        +left
        val end = Label()
        +Insn.JumpIf(end, bool = true, consume = false)
        +Insn.Pop
        +right
        +end
    });

    constructor(metamethod: String) : this({ left, right ->
        +left
        +right
        generateMetaCall(metamethod, 1)
    })

    constructor(number: Int, inverse: Boolean = false) : this({ left, right ->
        +left
        +right
        generateMetaCall("__cmp__", 1)
        +Insn.Push(number)
        +Insn.CopyUnder(1)
        +Insn.Push("__eq__")
        +Insn.Index
        +Insn.Call(2)
        if (inverse) {
            +Insn.Not
        }
    })
}

enum class UnOp {
    NOT,
    NEG,
}