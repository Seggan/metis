package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import java.io.Serial

data class BytesValue(val value: ByteArray) : Value {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 3755254969161231440L

        val metatable by buildTableLazy { table ->
            table.useNativeEquality()
            table["__str__"] = oneArgFunction(true) { self ->
                val sb = StringBuilder()
                sb.append('\'')
                for (byte in self.bytesValue) {
                    if (byte in 32..126) {
                        sb.append(byte.toInt().toChar())
                    } else {
                        sb.append("\\x")
                        sb.append(byte.toString(16).padStart(2, '0'))
                    }
                }
                sb.append('\'')
                sb.toString().metis()
            }
        }
    }

    override var metatable = Companion.metatable

    override fun getDirect(key: Value): Value? = when (key) {
        is NumberValue.Int -> value.getOrNull(key.intValue.intValueExact())?.toInt()?.metis()
        else -> null
    }

    override fun setDirect(key: Value, value: Value): Boolean {
        if (key !is NumberValue.Int) return false
        val index = key.intValue.intValueExact()
        if (index < 0 || index >= this.value.size) return false
        this.value[index] = value.intValue.toInt().toByte()
        return true
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

val Value.bytesValue get() = convertTo<BytesValue>().value