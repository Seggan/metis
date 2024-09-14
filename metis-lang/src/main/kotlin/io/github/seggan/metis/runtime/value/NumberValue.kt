package io.github.seggan.metis.runtime.value

import ch.obermuhlner.math.big.BigDecimalMath
import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.runtime.intrinsics.twoArgFunction
import io.github.seggan.metis.util.isInteger
import java.io.Serial
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

sealed interface NumberValue : Value, Comparable<NumberValue> {

    operator fun plus(other: NumberValue): NumberValue
    operator fun minus(other: NumberValue): NumberValue
    operator fun times(other: NumberValue): NumberValue
    operator fun div(other: NumberValue): NumberValue
    operator fun rem(other: NumberValue): NumberValue
    fun pow(other: NumberValue): NumberValue
    operator fun unaryMinus(): NumberValue

    fun toInteger(): Int
    fun toFloat(): Float

    class Int private constructor(val value: BigInteger) : NumberValue {

        override var metatable = Companion.metatable

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
            is Int -> {
                val bd = value.toBigDecimal().divide(other.value.toBigDecimal(), MathContext.DECIMAL128)
                if (bd.isInteger) Int(bd.toBigInteger()) else Float(bd)
            }
            is Float -> toFloat() / other
        }

        override fun rem(other: NumberValue): NumberValue = when (other) {
            is Int -> Int(value % other.value)
            is Float -> toFloat() % other
        }

        override fun pow(other: NumberValue): NumberValue = when (other) {
            is Int -> {
                Int(
                    BigDecimalMath.pow(
                        value.toBigDecimal(),
                        other.value.toBigDecimal(),
                        MathContext.DECIMAL128
                    ).toBigInteger()
                )
            }

            is Float -> toFloat().pow(other)
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

            private const val CACHE_SIZE = 256
            private val CACHE = Array(CACHE_SIZE) { (it - CACHE_SIZE / 2).metis() }

            operator fun invoke(value: BigInteger): Int = when {
                value >= (-CACHE_SIZE / 2).toBigInteger() && value < (CACHE_SIZE / 2).toBigInteger() -> CACHE[value.toInt() + CACHE_SIZE / 2]
                else -> Int(value)
            }

            val metatable by buildTableLazy { table ->
                superOps(table)
                table["__floordiv__"] = twoArgFunction(true) { self, other ->
                    Int(self.intValue / other.intValue)
                }
                table["__band__"] = twoArgFunction(true) { self, other ->
                    Int(self.intValue and other.intValue)
                }
                table["__bor__"] = twoArgFunction(true) { self, other ->
                    Int(self.intValue or other.intValue)
                }
                table["__bxor__"] = twoArgFunction(true) { self, other ->
                    Int(self.intValue xor other.intValue)
                }
                table["__bnot__"] = oneArgFunction(true) { self ->
                    Int(self.intValue.inv())
                }
                table["__shl__"] = twoArgFunction(true) { self, other ->
                    Int(self.intValue shl other.intValue.intValueExact())
                }
                table["__shr__"] = twoArgFunction(true) { self, other ->
                    Int(self.intValue shr other.intValue.intValueExact())
                }
                table["__shru__"] = twoArgFunction(true) { self, other ->
                    val start = self.intValue
                    if (start.signum() == 0) return@twoArgFunction self

                    val shift = other.intValue.intValueExact()
                    val result = start shr shift
                    if (start.signum() > 0) {
                        Int(result)
                    } else {
                        var mask = BigInteger.TWO.pow(shift) - BigInteger.ONE
                        mask = mask shl (result.bitLength() + 1 - shift)
                        Int(result and mask.inv())
                    }
                }
                table["stringWithRadix"] = twoArgFunction(true) { self, radix ->
                    self.intValue.toString(radix.intValue.intValueExact()).metis()
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NumberValue) return false
            return when (other) {
                is Int -> value == other.value
                is Float -> value.toBigDecimal().compareTo(other.value) == 0
            }
        }

        override fun hashCode(): kotlin.Int = value.hashCode()
    }

    class Float(val value: BigDecimal) : NumberValue {

        override var metatable = Companion.metatable

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

        override fun pow(other: NumberValue): NumberValue =
            Float(BigDecimalMath.pow(value, other.toFloat().value, MathContext.DECIMAL128))

        override fun unaryMinus(): NumberValue = Float(-value)

        override fun compareTo(other: NumberValue): kotlin.Int = when (other) {
            is Int -> compareTo(other.toFloat())
            is Float -> value.compareTo(other.value)
        }

        override fun toString(): String = value.stripTrailingZeros().toPlainString()

        override fun toInteger(): Int = value.toBigInteger().metis()
        override fun toFloat(): Float = this

        companion object {
            @Serial
            private const val serialVersionUID: Long = 8095133059564862993L

            val metatable by buildTableLazy { table ->
                superOps(table)
                table["__floordiv__"] = twoArgFunction(true) { self, other ->
                    Float(self.floatValue.divideToIntegralValue(other.floatValue))
                }
                table["parse"] = oneArgFunction(true) { self ->
                    try {
                        self.stringValue.toBigDecimal().metis()
                    } catch (e: NumberFormatException) {
                        throw MetisValueError(self, "Invalid number format: ${self.metisToString()}")
                    }
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NumberValue) return false
            return when (other) {
                is Int -> value.compareTo(other.value.toBigDecimal()) == 0
                is Float -> value.compareTo(other.value) == 0
            }
        }

        override fun hashCode(): kotlin.Int = value.hashCode()
    }

    companion object {
        private fun superOps(table: TableValue) {
            table.useNativeEquality()
            table.useNativeToString()
            table["__plus__"] = twoArgFunction(true) { self, other ->
                self.numberValue + other.numberValue
            }
            table["__minus__"] = twoArgFunction(true) { self, other ->
                self.numberValue - other.numberValue
            }
            table["__times__"] = twoArgFunction(true) { self, other ->
                self.numberValue * other.numberValue
            }
            table["__div__"] = twoArgFunction(true) { self, other ->
                self.numberValue / other.numberValue
            }
            table["__mod__"] = twoArgFunction(true) { self, other ->
                self.numberValue % other.numberValue
            }
            table["__neg__"] = oneArgFunction(true) { self ->
                -self.numberValue
            }
            table["__cmp__"] = twoArgFunction(true) { self, other ->
                self.numberValue.compareTo(other.numberValue).metis()
            }
        }
    }
}

fun BigInteger.metis() = NumberValue.Int(this)
fun BigDecimal.metis() = NumberValue.Float(this)

val Value.intValue get() = convertTo<NumberValue>().toInteger().value
val Value.floatValue get() = convertTo<NumberValue>().toFloat().value
val Value.numberValue get() = convertTo<NumberValue>()

fun Int.metis() = toBigInteger().metis()
fun Long.metis() = toBigInteger().metis()
fun Float.metis() = toBigDecimal().metis()
fun Double.metis() = toBigDecimal().metis()