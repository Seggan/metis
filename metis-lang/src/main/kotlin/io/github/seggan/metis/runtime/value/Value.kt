package io.github.seggan.metis.runtime.value

import java.io.Serializable

sealed interface Value : Serializable {
}

object NullValue : Value {
    private fun readResolve(): Any = NullValue
    override fun toString(): String = "null"
}