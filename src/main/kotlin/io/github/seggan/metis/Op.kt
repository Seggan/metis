package io.github.seggan.metis

import io.github.seggan.metis.compilation.Compiler
import io.github.seggan.metis.compilation.FullInsn
import io.github.seggan.metis.compilation.InsnsBuilder
import io.github.seggan.metis.runtime.Value
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
        +Compiler.generateColonCall(
            left,
            "__eq__",
            listOf(right),
            span
        )
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
        +Compiler.generateColonCall(
            left,
            metamethod,
            listOf(right),
            span
        )
    })

    constructor(number: Int, inverse: Boolean = false) : this({ left, right ->
        +Compiler.generateColonCall(
            left,
            "__cmp__",
            listOf(right),
            span
        )
        +Insn.Push(Value.Number.of(number))
        +Insn.CopyUnder(1)
        +Insn.Push(Value.String("__eq__"))
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