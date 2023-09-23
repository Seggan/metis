package io.github.seggan.metis

enum class BinOp(val metamethod: String) {
    PLUS("__add__"),
    MINUS("__sub__"),
    TIMES("__mul__"),
    DIV("__div__"),
    MOD("__mod__"),
    POW("__pow__"),
    EQ("__eq__"),
    NOT_EQ("__neq__"),
    LESS("__lt__"),
    LESS_EQ("__leq__"),
    GREATER("__gt__"),
    GREATER_EQ("__geq__"),
    AND(""),
    OR(""),
}

enum class UnOp {
    NOT,
    NEG,
}