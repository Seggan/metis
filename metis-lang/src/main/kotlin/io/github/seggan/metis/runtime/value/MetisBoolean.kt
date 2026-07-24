package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.util.MutableLazy

/**
 * A boolean.
 */
class MetisBoolean private constructor(val value: Boolean) : Value {

    override var metatable: MetisTable by MutableLazy { Companion.metatable }

    companion object {

        /**
         * The [MetisBoolean] representing `true`.
         */
        val TRUE = MetisBoolean(true)

        /**
         * The [MetisBoolean] representing `false`.
         */
        val FALSE = MetisBoolean(false)

        /**
         * Turns a [Boolean] into a [MetisBoolean].
         *
         * @param value The value to turn into a [MetisBoolean].
         * @return The [MetisBoolean] representing the value.
         */
        fun of(value: Boolean) = if (value) TRUE else FALSE

        /**
         * The shared metatable for all booleans.
         */
        // lazy because of a mutual dependency between initTable and Boolean
        val metatable by lazy { MetisTable() }
    }

    override fun toString() = value.toString()
}