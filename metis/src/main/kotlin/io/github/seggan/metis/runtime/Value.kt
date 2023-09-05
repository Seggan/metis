package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.intrinsics.OneShotFunction
import java.math.BigDecimal
import kotlin.math.roundToInt

interface Value {

    var metatable: Table?

    data class Number(val value: Double) : Value {
        override var metatable: Table? = Companion.metatable

        companion object {
            val NAN = Number(Double.NaN)
            val POS_INF = Number(Double.POSITIVE_INFINITY)
            val NEG_INF = Number(Double.NEGATIVE_INFINITY)
            val ZERO = Number(0.0)
            val ONE = Number(1.0)
            val TWO = Number(2.0)
            val TEN = Number(10.0)
            val NEGATIVE_ONE = Number(-1.0)

            val metatable = Table(mutableMapOf())

            init {
                metatable["__str__"] = OneShotFunction(Arity.ONE) {
                    stack.push(
                        String(
                            BigDecimal(stack.pop().convertTo<Number>().value).stripTrailingZeros().toPlainString()
                        )
                    )
                }
            }
        }
    }

    data class String(val value: kotlin.String) : Value {
        override var metatable: Table? = Companion.metatable

        companion object {
            val metatable = Table(mutableMapOf())

            init {
                metatable["__str__"] = OneShotFunction(Arity.ONE) {}
            }
        }
    }

    class Boolean private constructor(val value: kotlin.Boolean) : Value {
        override var metatable: Table? = Companion.metatable

        companion object {
            val TRUE = Boolean(true)
            val FALSE = Boolean(false)

            val metatable = Table(mutableMapOf())

            fun from(value: kotlin.Boolean) = if (value) TRUE else FALSE

            init {
                metatable["__str__"] = OneShotFunction(Arity.ONE) {
                    stack.push(String(if (stack.pop().convertTo<Boolean>().value) "true" else "false"))
                }
            }
        }

        override fun toString(): kotlin.String = "Boolean(value=$value)"
    }

    data class Table(val value: MutableMap<Value, Value>, override var metatable: Table? = null) : Value,
        MutableMap<Value, Value> by value {
        operator fun get(key: kotlin.String) = value[String(key)]
        operator fun set(key: kotlin.String, value: Value) = this.set(String(key), value)
    }

    data class Array(val value: MutableList<Value>, override var metatable: Table? = null) : Value,
        MutableList<Value> by value

    data object Null : Value {
        override var metatable: Table? = null
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
    } else if (this is Value.Array && key is Value.Number) {
        val value = this.getOrNull(key.value.roundToInt())
        if (value != null) {
            return value
        }
    }
    return this.metatable?.lookUp(key)
}

fun Value.lookUp(key: kotlin.String): Value? = lookUp(Value.String(key))

fun Value?.orNull() = this ?: Value.Null

inline fun <reified T : Value> Value.convertTo(): T {
    if (this is T) {
        return this
    }
    throw MetisRuntimeException("Cannot convert $this to ${T::class.simpleName}")
}