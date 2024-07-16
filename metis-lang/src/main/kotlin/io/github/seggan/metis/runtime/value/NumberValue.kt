package io.github.seggan.metis.runtime.value

import java.math.BigDecimal

class NumberValue private constructor(private val value: BigDecimal) : Number(), Value, Comparable<NumberValue> {

    companion object {

        private const val CACHE_SIZE = 256
        private const val HALF_CACHE = CACHE_SIZE / 2
        private val cache = Array(CACHE_SIZE) { NumberValue(BigDecimal(it - HALF_CACHE)) }

        fun of(value: BigDecimal): NumberValue = NumberValue(value)

        fun of(value: Long): NumberValue = when {
            value in -HALF_CACHE..HALF_CACHE -> cache[(value + HALF_CACHE).toInt()]
            else -> NumberValue(BigDecimal(value))
        }
    }

    operator fun plus(other: NumberValue): NumberValue = NumberValue(value + other.value)
    operator fun minus(other: NumberValue): NumberValue = NumberValue(value - other.value)
    operator fun times(other: NumberValue): NumberValue = NumberValue(value * other.value)
    operator fun div(other: NumberValue): NumberValue = NumberValue(value / other.value)
    operator fun rem(other: NumberValue): NumberValue = NumberValue(value % other.value)
    operator fun unaryPlus(): NumberValue = this
    operator fun unaryMinus(): NumberValue = NumberValue(-value)
    override operator fun compareTo(other: NumberValue): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean = other is NumberValue && value == other.value
    override fun hashCode(): Int = value.hashCode()

    override fun toByte(): Byte = value.toByte()
    override fun toDouble(): Double = value.toDouble()
    override fun toFloat(): Float = value.toFloat()
    override fun toInt(): Int = value.toInt()
    override fun toLong(): Long = value.toLong()
    override fun toShort(): Short = value.toShort()
    fun toBigDecimal(): BigDecimal = value
}

fun Int.metis(): NumberValue = NumberValue.of(this.toLong())
fun Long.metis(): NumberValue = NumberValue.of(this)
fun Float.metis(): NumberValue = NumberValue.of(this.toBigDecimal())
fun Double.metis(): NumberValue = NumberValue.of(this.toBigDecimal())
fun BigDecimal.metis(): NumberValue = NumberValue.of(this)