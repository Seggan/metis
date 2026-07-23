package io.github.seggan.metis.compilation.op

import io.github.seggan.metis.compilation.FullInsn
import io.github.seggan.metis.compilation.InsnsBuilder
import io.github.seggan.metis.compilation.Register
import io.github.seggan.metis.compilation.RegisterHolder
import io.github.seggan.metis.runtime.chunk.Insn

enum class BinOp(internal val generateCode: (InsnsBuilder).(RegisterHolder, () -> Pair<List<FullInsn>, Register>, () -> Pair<List<FullInsn>, Register>) -> Register) {
    PLUS(Metamethod.PLUS),
    MINUS(Metamethod.MINUS),
    TIMES(Metamethod.TIMES),
    DIV(Metamethod.DIV),
    FLOORDIV(Metamethod.FLOORDIV),
    MOD(Metamethod.MOD),
    POW(Metamethod.POW),
    RANGE(Metamethod.RANGE),
    INCLUSIVE_RANGE(Metamethod.INCLRANGE),
    BAND(Metamethod.BAND),
    BOR(Metamethod.BOR),
    BXOR(Metamethod.BXOR),
    SHL(Metamethod.SHL),
    SHR(Metamethod.SHR),
    SHRU(Metamethod.SHRU),
    IN(Metamethod.CONTAINS),
    NOT_IN(IN),
    IS({ holder, left, right ->
        val left = +left()
        val right = +right()
        holder.freeRegisters(left, right)
        +Insn.Is(holder.nextFreeRegister(), left, right)
    }),
    IS_NOT(IS),
    EQ(Metamethod.EQ),
    NOT_EQ(EQ),
    LESS(-1),
    LESS_EQ(1, true),
    GREATER(1),
    GREATER_EQ(-1, true),
    AND({ holder, left, right ->
        val left = +left()
        holder.freeRegisters(left)
        val dest = holder.nextFreeRegister()
        +Insn.Move(dest, left)
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = false, dest)
        val right = +right()
        holder.freeRegisters(right)
        +Insn.Move(dest, right)
        +end
        dest
    }),
    OR({ holder, left, right ->
        val left = +left()
        holder.freeRegisters(left)
        val dest = holder.nextFreeRegister()
        +Insn.Move(dest, left)
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = true, dest)
        val right = +right()
        holder.freeRegisters(right)
        +Insn.Move(dest, right)
        +end
        dest
    }),
    ELVIS({ holder, left, right ->
        val left = +left()
        holder.freeRegisters(left)
        val dest = holder.nextFreeRegister()
        +Insn.Move(dest, left)

        val chk = holder.nextFreeRegister()
        +Insn.SetValue(chk, null)
        +Insn.MetaCall(chk, dest, Metamethod.EQ, listOf(chk))

        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = false, chk)
        holder.freeRegisters(chk)

        val right = +right()
        holder.freeRegisters(right)
        +Insn.Move(dest, right)
        +end

        dest
    });

    constructor(metamethod: String) : this({ holder, left, right ->
        val left = +left()
        val right = +right()
        holder.freeRegisters(left, right)
        +Insn.MetaCall(holder.nextFreeRegister(), left, metamethod, listOf(right))
    })

    constructor(op: BinOp) : this({ holder, left, right ->
        val value = op.generateCode(this, holder, left, right)
        holder.freeRegisters(value)
        +Insn.Not(holder.nextFreeRegister(), value)
    })

    constructor(number: Int, inverse: Boolean = false) : this({ holder, left, right ->
        val left = +left()
        val right = +right()
        holder.freeRegisters(left, right)
        val dest = holder.nextFreeRegister()
        +Insn.MetaCall(dest, left, Metamethod.CMP, listOf(right))
        val numReg = holder.nextFreeRegister()
        +Insn.SetValue(numReg, number)
        +Insn.MetaCall(dest, dest, Metamethod.EQ, listOf(numReg))
        if (inverse) {
            +Insn.Not(dest, dest)
        }
        holder.freeRegisters(numReg)
        dest
    })
}