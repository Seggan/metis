package io.github.seggan.metis.compilation.op

import io.github.seggan.metis.parsing.Token
import java.util.EnumMap

enum class AssignType(val op: BinOp, private val token: Token.Type) {
    PLUS(BinOp.PLUS, Token.Type.PLUS_EQUALS),
    MINUS(BinOp.MINUS, Token.Type.MINUS_EQUALS),
    TIMES(BinOp.TIMES, Token.Type.STAR_EQUALS),
    DIV(BinOp.DIV, Token.Type.SLASH_EQUALS),
    FLOORDIV(BinOp.FLOORDIV, Token.Type.DOUBLE_SLASH_EQUALS),
    MOD(BinOp.MOD, Token.Type.PERCENT_EQUALS),
    POW(BinOp.POW, Token.Type.DOUBLE_STAR_EQUALS),
    BAND(BinOp.BIT_AND, Token.Type.AMP_EQUALS),
    BOR(BinOp.BIT_OR, Token.Type.PIPE_EQUALS),
    BXOR(BinOp.BIT_XOR, Token.Type.CARET_EQUALS),
    SHL(BinOp.SHL, Token.Type.SHL_EQUALS),
    SHR(BinOp.SHR, Token.Type.SHR_EQUALS),
    SHRU(BinOp.SHRU, Token.Type.SHRU_EQUALS),
    ELVIS(BinOp.ELVIS, Token.Type.ELVIS_EQUALS);

    companion object {
        private val map = entries.associateByTo(EnumMap(Token.Type::class.java)) { it.token }

        fun fromToken(token: Token.Type): AssignType? = map[token]
    }
}