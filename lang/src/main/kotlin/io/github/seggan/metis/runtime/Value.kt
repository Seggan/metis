package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.intrinsics.*
import io.github.seggan.metis.util.MutableLazy
import kotlin.reflect.KClass

interface Value {

    var metatable: Table?

    fun lookUpDirect(key: Value): Value? = null

    fun setDirect(key: Value, value: Value): kotlin.Boolean = false

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

            fun of(value: Double): Number {
                if (value % 1 == 0.0 && value < CACHE_SIZE && value >= -CACHE_SIZE) {
                    return cache[(value + CACHE_SIZE).toInt()]
                }
                return Number(value)
            }

            fun of(value: Int) = of(value.toDouble())
        }

        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            return other is Number && other.value == value
        }

        override fun hashCode() = value.hashCode()

        override fun toString(): kotlin.String {
            return value.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }

    data class String(val value: kotlin.String) : Value {

        override var metatable: Table? by MutableLazy { Companion.metatable }

        override fun lookUpDirect(key: Value): Value? {
            if (key is Number) {
                val index = key.intValue()
                if (index >= 0 && index < value.length) {
                    return String(value[index].toString())
                }
            }
            return null
        }

        companion object {
            // lazy because of a mutual dependency between initTable and String
            val metatable by lazy(::initString)
        }

        override fun toString() = value
    }

    class Boolean private constructor(val value: kotlin.Boolean) : Value {

        override var metatable: Table? by MutableLazy { Companion.metatable }

        companion object {
            val TRUE = Boolean(true)
            val FALSE = Boolean(false)

            fun of(value: kotlin.Boolean) = if (value) TRUE else FALSE

            val metatable by lazy(::initBoolean)
        }

        override fun toString() = value.toString()
    }

    data class Table(
        val value: MutableMap<Value, Value> = mutableMapOf(),
        override var metatable: Table? = Companion.metatable
    ) : Value, MutableMap<Value, Value> by value {

        override fun lookUpDirect(key: Value) = value[key]

        override fun setDirect(key: Value, value: Value): kotlin.Boolean {
            this.value[key] = value
            return true
        }

        operator fun get(key: kotlin.String) = value[String(key)]
        operator fun set(key: kotlin.String, value: Value) = this.setOrError(String(key), value)

        companion object {
            val metatable = initTable()
        }

        override fun toString(): kotlin.String {
            return values.joinToString(
                prefix = "{ ",
                postfix = " }"
            ) { if (it === this@Table) "{...}" else it.toString() }
        }

        override fun equals(other: Any?) = other is Table && value == other.value

        override fun hashCode() = value.hashCode()
    }

    data class List(
        val value: MutableList<Value> = mutableListOf(),
        override var metatable: Table? = Companion.metatable
    ) : Value, MutableList<Value> by value {

        override fun lookUpDirect(key: Value): Value? {
            if (key is Number) {
                return getOrNull(key.intValue())
            }
            return null
        }

        override fun setDirect(key: Value, value: Value): kotlin.Boolean {
            if (key is Number) {
                this[key.intValue()] = value
                return true
            }
            return false
        }

        override fun toString(): kotlin.String {
            return value.joinToString(
                prefix = "[",
                postfix = "]"
            ) { if (it === this@List) "[...]" else it.toString() }
        }

        companion object {
            val metatable = initList()
        }
    }

    data class Bytes(val value: ByteArray, override var metatable: Table? = Companion.metatable) : Value {

        override fun lookUpDirect(key: Value): Value? {
            if (key is Number) {
                return Number.of(value[key.intValue()].toDouble())
            }
            return null
        }

        override fun setDirect(key: Value, value: Value): kotlin.Boolean {
            if (key is Number && value is Number) {
                this.value[key.intValue()] = value.intValue().toByte()
                return true
            }
            return false
        }

        override fun equals(other: Any?): kotlin.Boolean {
            if (this === other) return true
            return other is Bytes && value.contentEquals(other.value)
        }

        override fun hashCode() = value.contentHashCode()

        companion object {
            val metatable = initBytes()
        }
    }

    data class Native(val value: Any, override var metatable: Table? = null) : Value {
        override fun toString() = "Native(value=$value)"
    }

    data object Null : Value {

        override var metatable: Table? = initNull()

        override fun toString() = "null"
    }
}

fun Value.lookUp(key: Value): Value? {
    if (this === metatable) return lookUpDirect(key)
    return lookUpDirect(key) ?: metatable?.lookUp(key)
}

fun Value.set(key: Value, value: Value): Boolean {
    if (this === metatable) return setDirect(key, value)
    return setDirect(key, value) || metatable?.set(key, value) ?: false
}

fun Value.setOrError(key: Value, value: Value) {
    if (!set(key, value)) {
        throw MetisRuntimeException("IndexError", "Cannot set ${typeToName(key::class)} on ${typeToName(this::class)}")
    }
}

fun Value?.orNull() = this ?: Value.Null

inline fun <reified T : Value> Value.convertTo(): T {
    if (this is T) {
        return this
    }
    throw MetisRuntimeException("TypeError", "Cannot convert ${typeToName(this::class)} to ${typeToName(T::class)}")
}

fun Value.intValue() = this.convertTo<Value.Number>().value.toInt()
fun Value.doubleValue() = this.convertTo<Value.Number>().value
fun Value.stringValue() = this.convertTo<Value.String>().value

inline fun <reified T> Value.asObj(): T {
    val value = convertTo<Value.Native>().value
    if (value is T) {
        return value
    }
    throw MetisRuntimeException(
        "TypeError",
        "Failed to unpack native object; expected ${T::class.qualifiedName}, got ${value::class.qualifiedName}"
    )
}

inline fun buildTable(init: (MutableMap<String, Value>) -> Unit): Value.Table {
    val map = mutableMapOf<String, Value>()
    init(map)
    return Value.Table(map.mapKeysTo(mutableMapOf()) { Value.String(it.key) }).also {
        if (it.metatable == null) {
            throw AssertionError("Null Table metatable on init; this shouldn't happen!")
        }
    }
}

fun typeToName(clazz: KClass<out Value>): String = when (clazz) {
    Value.Number::class -> "number"
    Value.String::class -> "string"
    Value.Boolean::class -> "boolean"
    Value.Table::class -> "table"
    Value.List::class -> "list"
    Value.Bytes::class -> "bytes"
    Value.Native::class -> "native"
    Value.Null::class -> "null"
    MetisRuntimeException::class -> "error"
    else -> if (CallableValue::class.java.isAssignableFrom(clazz.java)) {
        "callable"
    } else {
        clazz.simpleName ?: "unknown"
    }
}

fun Int.metisValue() = Value.Number.of(this)
fun Double.metisValue() = Value.Number.of(this)
fun String.metisValue() = Value.String(this)
fun Boolean.metisValue() = Value.Boolean.of(this)
fun Collection<Value>.metisValue() = Value.List(this.toMutableList())
fun ByteArray.metisValue() = Value.Bytes(this)