package io.github.seggan.metis.runtime.value

/**
 * A number.
 */
class MetisNumber private constructor(val value: Double) : Value {

    override var metatable: MetisTable? = Companion.metatable

    companion object {

        /**
         * The shared metatable for all numbers.
         */
        val metatable = initNumber()

        /**
         * The [Value.MetisNumber] representing `inf`.
         */
        val INF = MetisNumber(Double.POSITIVE_INFINITY)

        /**
         * The [Value.MetisNumber] representing `-inf`.
         */
        val NEG_INF = MetisNumber(Double.NEGATIVE_INFINITY)

        /**
         * The [Value.MetisNumber] representing `nan`.
         */
        val NAN = MetisNumber(Double.NaN)

        private const val CACHE_SIZE = 128
        private val cache = Array(CACHE_SIZE * 2) {
            MetisNumber(it - CACHE_SIZE.toDouble())
        }

        /**
         * Turns a [Double] into a [MetisNumber], possibly using a cached value.
         *
         * @param value The value to turn into a [MetisNumber].
         * @return The [MetisNumber] representing the value.
         */
        fun of(value: Double): MetisNumber {
            if (value % 1 == 0.0 && value < CACHE_SIZE && value >= -CACHE_SIZE) {
                return cache[(value + CACHE_SIZE).toInt()]
            } else if (value.isNaN()) {
                return NAN
            } else if (value.isInfinite()) {
                return if (value > 0) INF else NEG_INF
            }
            return MetisNumber(value)
        }

        /**
         * Turns an [Int] into a [MetisNumber], possibly using a cached value.
         *
         * @param value The value to turn into a [MetisNumber].
         * @return The [MetisNumber] representing the value.
         */
        fun of(value: Int) = of(value.toDouble())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is MetisNumber && other.value == value
    }

    override fun hashCode() = value.hashCode()

    override fun toString(): String {
        return value.toBigDecimal().stripTrailingZeros().toPlainString()
    }
}