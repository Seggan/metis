package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.util.LazyVar
import java.io.Serial

class BooleanValue private constructor(val value: Boolean) : Value {

    override var metatable: TableValue? by LazyVar { Companion.metatable }

    companion object {
        @Serial
        private const val serialVersionUID: Long = 7665977906852438519L

        val TRUE = BooleanValue(true)
        val FALSE = BooleanValue(false)

        fun of(value: Boolean) = if (value) TRUE else FALSE

        val metatable = buildTable { table ->
            table.useNativeToString()
            table.useReferentialEquality()

            table["parse"] = oneArgFunction { it.stringValue.toBoolean().metis() }
        }
    }

    override fun toString() = value.toString()
    override fun equals(other: Any?) = other is BooleanValue && other.value == value
    override fun hashCode() = value.hashCode()

    private fun readResolve(): Any = of(value)
}

fun Boolean.metis() = BooleanValue.of(this)

val Value.booleanValue get() = into<BooleanValue>().value