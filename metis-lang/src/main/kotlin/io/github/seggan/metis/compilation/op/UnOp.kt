package io.github.seggan.metis.compilation.op

enum class UnOp(val metamethod: String? = null) {
    NOT,
    NEG("__neg__"),
    BNOT("__bnot__"),
}