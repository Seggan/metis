package io.github.seggan.metis.runtime.values

import io.github.seggan.metis.MetisRuntimeException
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.StepResult
import kotlin.math.roundToInt

interface Value {

    var metatable: Table?

    class Number private constructor(val value: Double) : Value {

        override var metatable: Table? = Companion.metatable

        companion object {
            val NAN = Number(Double.NaN)
            val POS_INF = Number(Double.POSITIVE_INFINITY)
            val NEG_INF = Number(Double.NEGATIVE_INFINITY)

            val metatable = initNumber()

            private const val CACHE_SIZE = 128
            private val cache = Array(CACHE_SIZE * 2) {
                Number(it - CACHE_SIZE.toDouble())
            }

            fun from(value: Double): Number {
                if (value % 1 == 0.0 && value <= CACHE_SIZE && value >= -CACHE_SIZE) {
                    return cache[(value + CACHE_SIZE).toInt()]
                }
                return Number(value)
            }
        }

        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            return other is Number && other.value == value
        }

        override fun hashCode() = value.hashCode()

        override fun toString(): kotlin.String {
            return "Number(value=$value)"
        }
    }

    data class String(val value: kotlin.String) : Value {

        override var metatable: Table? = Companion.metatable

        companion object {
            val metatable = initString()
        }
    }

    class Boolean private constructor(val value: kotlin.Boolean) : Value {
        override var metatable: Table? = Companion.metatable

        companion object {
            val TRUE = Boolean(true)
            val FALSE = Boolean(false)

            val metatable = initBoolean()

            fun from(value: kotlin.Boolean) = if (value) TRUE else FALSE
        }

        override fun toString(): kotlin.String = "Boolean(value=$value)"
    }

    data class Table(val value: MutableMap<Value, Value>, override var metatable: Table? = null) : Value,
        MutableMap<Value, Value> by value {
        operator fun get(key: kotlin.String) = value[String(key)]
        operator fun set(key: kotlin.String, value: Value) = this.set(String(key), value)
    }

    data class List(val value: MutableList<Value>, override var metatable: Table? = null) : Value,
        MutableList<Value> by value

    data class Bytes(val value: ByteArray, override var metatable: Table? = null) : Value {
        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            return other is Bytes && value.contentEquals(other.value)
        }

        override fun hashCode() = value.contentHashCode()
    }

    data class Native(val value: Any, override var metatable: Table? = null) : Value

    data object Null : Value {
        override var metatable: Table? = initNull()
    }
}

interface CallableValue : Value {

    interface Executor {
        fun step(state: State): StepResult
    }

    fun call(nargs: Int): Executor

    val arity: Arity
}

data class Arity(val required: Int, val isVarargs: Boolean = false) {
    companion object {
        val ZERO = Arity(0)
        val ONE = Arity(1)
        val VARARGS = Arity(0, true)
    }
}

fun Value.lookUp(key: Value): Value? {
    if (this is Value.Table) {
        val value = this[key]
        if (value != null) {
            return value
        }
    } else if (key is Value.Number) {
        when (this) {
            is Value.List -> {
                val value = this.getOrNull(key.value.roundToInt())
                if (value != null) {
                    return value
                }
            }

            is Value.String -> {
                val value = this.value.getOrNull(key.value.roundToInt())
                if (value != null) {
                    return Value.String(value.toString())
                }
            }

            is Value.Bytes -> {
                val value = this.value.getOrNull(key.value.roundToInt())
                if (value != null) {
                    return Value.Number.from(value.toDouble())
                }
            }
        }
    }
    return this.metatable?.lookUp(key)
}

fun Value.lookUp(key: String): Value? = lookUp(Value.String(key))

fun Value?.orNull() = this ?: Value.Null

inline fun <reified T : Value> Value.convertTo(): T {
    if (this is T) {
        return this
    }
    throw MetisRuntimeException("Cannot convert ${this::class.simpleName} to ${T::class.simpleName}")
}