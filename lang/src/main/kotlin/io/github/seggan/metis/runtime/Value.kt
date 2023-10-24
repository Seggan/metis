package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.intrinsics.*
import io.github.seggan.metis.util.MutableLazy
import kotlin.reflect.KClass

/**
 * A value in the Metis runtime.
 */
interface Value {

    /**
     * The metatable of this value.
     */
    var metatable: Table?

    /**
     * Look up a value in this value.
     *
     * @param key The key to look up.
     */
    fun lookUpDirect(key: Value): Value? = null

    /**
     * Set a value in this value.
     *
     * @param key The key to set.
     * @param value The value to set.
     */
    fun setDirect(key: Value, value: Value): kotlin.Boolean = false

    /**
     * A number.
     */
    class Number private constructor(val value: Double) : Value {

        override var metatable: Table? = Companion.metatable

        companion object {

            /**
             * The shared metatable for all numbers.
             */
            val metatable = initNumber()

            private const val CACHE_SIZE = 128
            private val cache = Array(CACHE_SIZE * 2) {
                Number(it - CACHE_SIZE.toDouble())
            }

            /**
             * Turns a [Double] into a [Number], possibly using a cached value.
             *
             * @param value The value to turn into a [Number].
             * @return The [Number] representing the value.
             */
            fun of(value: Double): Number {
                if (value % 1 == 0.0 && value < CACHE_SIZE && value >= -CACHE_SIZE) {
                    return cache[(value + CACHE_SIZE).toInt()]
                }
                return Number(value)
            }

            /**
             * Turns an [Int] into a [Number], possibly using a cached value.
             *
             * @param value The value to turn into a [Number].
             * @return The [Number] representing the value.
             */
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

    /**
     * A string.
     *
     * @param value The backing string of the value.
     */
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
            /**
             * The shared metatable for all strings.
             */
            // lazy because of a mutual dependency between initTable and String
            val metatable by lazy(::initString)
        }

        override fun toString() = value
    }

    /**
     * A boolean.
     */
    class Boolean private constructor(val value: kotlin.Boolean) : Value {

        override var metatable: Table? by MutableLazy { Companion.metatable }

        companion object {

            /**
             * The [Value.Boolean] representing `true`.
             */
            val TRUE = Boolean(true)

            /**
             * The [Value.Boolean] representing `false`.
             */
            val FALSE = Boolean(false)

            /**
             * Turns a [kotlin.Boolean] into a [Value.Boolean].
             *
             * @param value The value to turn into a [Value.Boolean].
             * @return The [Value.Boolean] representing the value.
             */
            fun of(value: kotlin.Boolean) = if (value) TRUE else FALSE

            /**
             * The shared metatable for all booleans.
             */
            // lazy because of a mutual dependency between initTable and Boolean
            val metatable by lazy(::initBoolean)
        }

        override fun toString() = value.toString()
    }

    /**
     * A table.
     *
     * @param value The backing map of the table.
     * @param metatable The metatable of the table.
     */
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

            /**
             * The shared super-metatable for all tables.
             */
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

    /**
     * A list.
     *
     * @param value The backing list of the list.
     * @param metatable The metatable of the list.
     */
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

            /**
             * The shared metatable for all lists.
             */
            val metatable = initList()
        }
    }

    /**
     * A byte array.
     *
     * @param value The backing byte array of the value.
     * @param metatable The metatable of the value.
     */
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

            /**
             * The shared metatable for all byte arrays.
             */
            val metatable = initBytes()
        }
    }

    /**
     * Wraps a native object.
     *
     * @param value The native object to wrap.
     * @param metatable The metatable of the value.
     */
    data class Native(val value: Any, override var metatable: Table? = null) : Value {
        override fun toString() = "Native(value=$value)"
    }

    /**
     * A null value.
     */
    data object Null : Value {

        override var metatable: Table? = initNull()

        override fun toString() = "null"
    }
}

/**
 * Look up a value in this value, possibly using the metatable.
 *
 * @param key The key to look up.
 * @return The value, or null if it doesn't exist.
 */
fun Value.lookUp(key: Value): Value? {
    if (this === metatable) return lookUpDirect(key)
    return lookUpDirect(key) ?: metatable?.lookUp(key)
}

/**
 * Sets a value in this value, possibly using the metatable.
 *
 * @param key The key to set.
 * @param value The value to set.
 * @return Whether the value was set.
 */
fun Value.set(key: Value, value: Value): Boolean {
    if (this === metatable) return setDirect(key, value)
    return setDirect(key, value) || metatable?.set(key, value) ?: false
}

/**
 * Sets a value in this value, or throws an error if it cannot be set.
 *
 * @param key The key to set.
 * @param value The value to set.
 * @throws MetisRuntimeException If the value cannot be set.
 */
fun Value.setOrError(key: Value, value: Value) {
    if (!set(key, value)) {
        throw MetisRuntimeException("IndexError", "Cannot set ${typeToName(key::class)} on ${typeToName(this::class)}")
    }
}

/**
 * If this value is null, return [Value.Null], otherwise return this value.
 */
fun Value?.orNull() = this ?: Value.Null

/**
 * Converts this value to a [T], or throws an error if it cannot be converted.
 *
 * @param T The type to convert to.
 * @throws MetisRuntimeException If the value cannot be converted.
 */
inline fun <reified T : Value> Value.convertTo(): T {
    if (this is T) {
        return this
    }
    throw MetisRuntimeException("TypeError", "Cannot convert ${typeToName(this::class)} to ${typeToName(T::class)}")
}

/**
 * Converts this value to a [Int], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.intValue() = this.convertTo<Value.Number>().value.toInt()

/**
 * Converts this value to a [Double], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.doubleValue() = this.convertTo<Value.Number>().value

/**
 * Converts this value to a [String], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.stringValue() = this.convertTo<Value.String>().value

/**
 * Look up a successive sequence of strings in this value.
 *
 * @param keys The keys to look up.
 * @return The value, or null if it doesn't exist.
 */
fun Value.lookUpHierarchy(vararg keys: String): Value? {
    var value: Value = this
    for (key in keys) {
        value = value.lookUp(key.metisValue()) ?: return null
    }
    return value
}

/**
 * If this is a [Value.Native], returns the native object, otherwise throws an error.
 *
 * @param T The type to convert to.
 * @throws MetisRuntimeException If the value cannot be converted.
 */
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

/**
 * Builds a [Value.Table]
 *
 * @param init The function to initialize the table.
 */
inline fun buildTable(init: (MutableMap<String, Value>) -> Unit): Value.Table {
    val map = mutableMapOf<String, Value>()
    init(map)
    return Value.Table(map.mapKeysTo(mutableMapOf()) { Value.String(it.key) }).also {
        if (it.metatable == null) {
            throw AssertionError("Null Table metatable on init; this shouldn't happen!")
        }
    }
}

/**
 * Converts a [Value]'s class to an end user-friendly name.
 *
 * @param clazz The class to convert.
 * @return The name of the class.
 */
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
    Coroutine::class -> "coroutine"
    else -> if (CallableValue::class.java.isAssignableFrom(clazz.java)) {
        "callable"
    } else {
        clazz.simpleName ?: "unknown"
    }
}

/**
 * Converts an [Int] to a [Value.Number]
 */
fun Int.metisValue() = Value.Number.of(this)

/**
 * Converts a [Double] to a [Value.Number]
 */
fun Double.metisValue() = Value.Number.of(this)

/**
 * Converts a [String] to a [Value.String]
 */
fun String.metisValue() = Value.String(this)

/**
 * Converts a [kotlin.Boolean] to a [Value.Boolean]
 */
fun Boolean.metisValue() = Value.Boolean.of(this)

/**
 * Converts a [Collection] of [Value]s to a [Value.List]
 */
fun Collection<Value>.metisValue() = Value.List(this.toMutableList())

/**
 * Converts a [ByteArray] to a [Value.Bytes]
 */
fun ByteArray.metisValue() = Value.Bytes(this)