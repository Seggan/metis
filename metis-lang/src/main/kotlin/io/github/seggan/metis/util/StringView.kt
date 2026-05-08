package io.github.seggan.metis.util

class StringView(private val string: String, val start: Int = 0, val end: Int = string.length) : CharSequence {

    init {
        require(start >= 0) { "Start index must be non-negative" }
        require(end <= string.length) { "End index must be less than or equal to string length" }
        require(start <= end) { "Start index must be less than or equal to end index" }
    }

    override val length: Int
        get() = end - start

    override fun get(index: Int): Char {
        if (index !in indices) {
            throw IndexOutOfBoundsException("Index: $index, Length: $length")
        }
        return string[start + index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex !in 0..length) {
            throw IndexOutOfBoundsException("Start index: $startIndex, Length: $length")
        }
        if (endIndex !in 0..length) {
            throw IndexOutOfBoundsException("End index: $endIndex, Length: $length")
        }
        if (startIndex > endIndex) {
            throw IllegalArgumentException("Start index must be less than or equal to end index")
        }
        return StringView(string, start + startIndex, start + endIndex)
    }

    override fun toString(): String {
        return string.substring(start, end)
    }
}