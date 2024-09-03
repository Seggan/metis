package io.github.seggan.metis.runtime.value

import java.io.Serial
import java.math.BigDecimal
import java.math.BigInteger

sealed interface NumberValue : Value, Comparable<NumberValue> {

    operator fun plus(other: NumberValue): NumberValue
    operator fun minus(other: NumberValue): NumberValue
    operator fun times(other: NumberValue): NumberValue
    operator fun div(other: NumberValue): NumberValue
    operator fun rem(other: NumberValue): NumberValue
    operator fun unaryMinus(): NumberValue

    fun toInteger(): Int
    fun toFloat(): Float

    class Int(private val value: BigInteger) : NumberValue {
        override fun plus(other: NumberValue): NumberValue = when (other) {
            is Int -> Int(value + other.value)
            is Float -> toFloat() + other
        }

        override fun minus(other: NumberValue): NumberValue = when (other) {
            is Int -> Int(value - other.value)
            is Float -> toFloat() - other
        }

        override fun times(other: NumberValue): NumberValue = when (other) {
            is Int -> Int(value * other.value)
            is Float -> toFloat() * other
        }

        override fun div(other: NumberValue): NumberValue = when (other) {
            is Int -> Int(value / other.value)
            is Float -> toFloat() / other
        }

        override fun rem(other: NumberValue): NumberValue = when (other) {
            is Int -> Int(value % other.value)
            is Float -> toFloat() % other
        }

        override fun unaryMinus(): NumberValue = Int(-value)

        override fun compareTo(other: NumberValue): kotlin.Int = when (other) {
            is Int -> value.compareTo(other.value)
            is Float -> toFloat().compareTo(other)
        }

        override fun toString(): String = value.toString()

        override fun toInteger(): Int = this
        override fun toFloat(): Float = value.toBigDecimal().metis()

        companion object {
            @Serial
            private const val serialVersionUID: Long = -3192086186905085639L
        }
    }

    class Float(private val value: BigDecimal) : NumberValue {
        override fun plus(other: NumberValue): NumberValue = when (other) {
            is Int -> this + other.toFloat()
            is Float -> Float(value + other.value)
        }

        override fun minus(other: NumberValue): NumberValue = when (other) {
            is Int -> this - other.toFloat()
            is Float -> Float(value - other.value)
        }

        override fun times(other: NumberValue): NumberValue = when (other) {
            is Int -> this * other.toFloat()
            is Float -> Float(value * other.value)
        }

        override fun div(other: NumberValue): NumberValue = when (other) {
            is Int -> this / other.toFloat()
            is Float -> Float(value / other.value)
        }

        override fun rem(other: NumberValue): NumberValue = when (other) {
            is Int -> this % other.toFloat()
            is Float -> Float(value % other.value)
        }

        override fun unaryMinus(): NumberValue = Float(-value)

        override fun compareTo(other: NumberValue): kotlin.Int = when (other) {
            is Int -> compareTo(other.toFloat())
            is Float -> value.compareTo(other.value)
        }

        override fun toString(): String = value.toString()

        override fun toInteger(): Int = value.toBigInteger().metis()
        override fun toFloat(): Float = this

        companion object {
            @Serial
            private const val serialVersionUID: Long = 8095133059564862993L
        }
    }
}

fun BigInteger.metis() = NumberValue.Int(this)
fun BigDecimal.metis() = NumberValue.Float(this)

fun Int.metis() = toBigInteger().metis()
fun Long.metis() = toBigInteger().metis()
fun Float.metis() = toBigDecimal().metis()
fun Double.metis() = toBigDecimal().metis()