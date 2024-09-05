package io.github.seggan.metis.runtime.value

import java.io.Serial

data class BytesValue(val value: ByteArray) : Value {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 3755254969161231440L
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BytesValue && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

fun ByteArray.metis() = BytesValue(this)