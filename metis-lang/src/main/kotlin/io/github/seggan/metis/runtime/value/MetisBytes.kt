package io.github.seggan.metis.runtime.value

/**
 * A byte array.
 *
 * @param value The backing byte array of the value.
 * @param metatable The metatable of the value.
 */
data class MetisBytes(val value: ByteArray, override var metatable: MetisTable = Companion.metatable) : Value {

    override fun lookUpDirect(key: Value): Value? {
        if (key is MetisNumber) {
            return MetisNumber.of(value[key.intValue()].toDouble())
        }
        return null
    }

    override fun setDirect(key: Value, value: Value): Boolean {
        if (key is MetisNumber && value is MetisNumber) {
            this.value[key.intValue()] = value.intValue().toByte()
            return true
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is MetisBytes && value.contentEquals(other.value)
    }

    override fun hashCode() = value.contentHashCode()

    override fun toString(): String {
        return value.joinToString(prefix = "[", postfix = "]")
    }

    companion object {

        /**
         * The shared metatable for all byte arrays.
         */
        val metatable = MetisTable()
    }
}