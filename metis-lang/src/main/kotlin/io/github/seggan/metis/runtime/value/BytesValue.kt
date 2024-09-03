package io.github.seggan.metis.runtime.value

import java.io.Serial

class BytesValue(val value: ByteArray) : Value {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 3755254969161231440L
    }
}