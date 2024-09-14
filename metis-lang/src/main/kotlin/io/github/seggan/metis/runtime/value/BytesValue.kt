package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.runtime.intrinsics.threeArgFunction
import io.github.seggan.metis.runtime.intrinsics.twoArgFunction
import java.io.Serial
import java.nio.charset.Charset
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.experimental.xor

data class BytesValue(val value: ByteArray) : Value {

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
            table["size"] = oneArgFunction(true) { it.bytesValue.size.metis() }
            table["__contains__"] = twoArgFunction(true) { self, value ->
                self.bytesValue.contains(value.intValue.byteValueExact()).metis()
            }
            table["__band__"] = twoArgFunction(true) { self, other ->
                val selfBytes = self.bytesValue
                val otherBytes = other.bytesValue
                if (selfBytes.size != otherBytes.size) {
                    throw MetisValueError(self, "Cannot perform bitwise AND on bytes of different sizes")
                }
                ByteArray(selfBytes.size) { index -> selfBytes[index] and otherBytes[index] }.metis()
            }
            table["__bor__"] = twoArgFunction(true) { self, other ->
                val selfBytes = self.bytesValue
                val otherBytes = other.bytesValue
                if (selfBytes.size != otherBytes.size) {
                    throw MetisValueError(self, "Cannot perform bitwise OR on bytes of different sizes")
                }
                ByteArray(selfBytes.size) { index -> selfBytes[index] or otherBytes[index] }.metis()
            }
            table["__bxor__"] = twoArgFunction(true) { self, other ->
                val selfBytes = self.bytesValue
                val otherBytes = other.bytesValue
                if (selfBytes.size != otherBytes.size) {
                    throw MetisValueError(self, "Cannot perform bitwise XOR on bytes of different sizes")
                }
                ByteArray(selfBytes.size) { index -> selfBytes[index] xor otherBytes[index] }.metis()
            }
            table["__bnot__"] = oneArgFunction(true) { self ->
                val selfBytes = self.bytesValue
                ByteArray(selfBytes.size) { index -> selfBytes[index].inv() }.metis()
            }
            table["decode"] = twoArgFunction(true) { self, encoding ->
                val trueEncoding = Charset.forName(encoding.orNull()?.stringValue ?: "UTF-8")
                String(self.bytesValue, trueEncoding).metis()
            }
            table["slice"] = threeArgFunction(true) { self, start, len ->
                val selfBytes = self.bytesValue
                val trueStart = start.intValue.intValueExact()
                val trueLen = len.intValue.intValueExact()
                if (trueStart < 0 || trueLen < 0 || trueStart + trueLen > selfBytes.size) {
                    throw MetisValueError(self, "Index out of bounds")
                }
                selfBytes.copyOfRange(trueStart, trueStart + trueLen).metis()
            }

            table["allocate"] = oneArgFunction { size ->
                BytesValue(ByteArray(size.intValue.intValueExact()))
            }
            table["concat"] = oneArgFunction { list ->
                val bytesList = list.listValue.map { it.bytesValue }
                val size = bytesList.sumOf { it.size }
                val newBytes = ByteArray(size)
                var offset = 0
                for (bytes in bytesList) {
                    bytes.copyInto(newBytes, offset)
                    offset += bytes.size
                }
                newBytes.metis()
            }
        }
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