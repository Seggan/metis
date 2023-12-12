package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.intrinsics.*
import io.github.seggan.metis.util.MutableLazy

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

            /**
             * The [Value.Number] representing `inf`.
             */
            val INF = Number(Double.POSITIVE_INFINITY)

            /**
             * The [Value.Number] representing `-inf`.
             */
            val NEG_INF = Number(Double.NEGATIVE_INFINITY)

            /**
             * The [Value.Number] representing `nan`.
             */
            val NAN = Number(Double.NaN)

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
                } else if (value.isNaN()) {
                    return NAN
                } else if (value.isInfinite()) {
                    return if (value > 0) INF else NEG_INF
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

        override fun toString() = "\"$value\""
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

        companion object {

            /**
             * The shared super-metatable for all tables.
             */
            val metatable = initTable()
        }

        override fun toString(): kotlin.String {
            return entries.joinToString(
                prefix = "{ ",
                postfix = " }"
            ) {
                if (it.key === this@Table) {
                    "{...} = ${it.value}"
                } else if (it.value === this@Table) {
                    "${it.key} = {...}"
                } else {
                    "${it.key} = ${it.value}"
                }
            }
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

        override var metatable: Table? = buildTable { table ->
            table["__str__"] = oneArgFunction(true) {
                "null".metisValue()
            }
            table["__call__"] = oneArgFunction(true) {
                throw MetisRuntimeException("TypeError", "Cannot call null")
            }
            table["__eq__"] = twoArgFunction(true) { _, other ->
                Boolean.of(other == Null)
            }
            table["__set__"] = threeArgFunction(true) { _, _, _ ->
                throw MetisRuntimeException("TypeError", "Cannot set any key on null")
            }
        }

        override fun toString() = "null"
    }
}