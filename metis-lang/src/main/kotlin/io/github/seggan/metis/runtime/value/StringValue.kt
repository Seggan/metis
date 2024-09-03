package io.github.seggan.metis.runtime.value

import java.io.Serial

class StringValue(val value: String) : Value {
    companion object {
        @Serial
        private const val serialVersionUID: Long = -2065581470768471818L
    }
}