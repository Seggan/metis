package io.github.seggan.metis.runtime.value

import java.io.Serial
import java.io.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface Value : Serializable

object NullValue : Value {

    @Serial
    private const val serialVersionUID: Long = 3286383784003129982L

    private fun readResolve(): Any = NullValue
    override fun toString(): String = "null"
}

@OptIn(ExperimentalContracts::class)
inline fun <reified T : Value> Value.convertTo(): T {
    contract {
        returns() implies (this@convertTo is T)
    }
    if (this is T) return this
    throw MetisRuntimeException("TypeError", "Expected ${T::class.simpleName}, got ${this::class.simpleName}")
}