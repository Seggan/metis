package io.github.seggan.metis.compilation.op

enum class UnOp(val metamethod: String? = null) {
    NOT,
    NEG(Metamethod.NEGATE),
    BIT_NOT(Metamethod.BIT_NOT),
}