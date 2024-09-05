package io.github.seggan.metis.runtime.value

import java.io.Serial

class BooleanValue private constructor(val value: Boolean) : Value {

    companion object {
        @Serial
        private const val serialVersionUID: Long = 7665977906852438519L

        val TRUE = BooleanValue(true)
        val FALSE = BooleanValue(false)

        fun of(value: Boolean) = if (value) TRUE else FALSE
    }

    override fun toString() = value.toString()
    override fun equals(other: Any?) = other is BooleanValue && other.value == value
    override fun hashCode() = value.hashCode()

    private fun readResolve(): Any = of(value)
}

fun Boolean.metis() = BooleanValue.of(this)