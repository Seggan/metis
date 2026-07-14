package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.util.MutableLazy

/**
 * A string.
 *
 * @param value The backing string of the value.
 */
data class MetisString(val value: String) : Value {

    override var metatable: MetisTable? by MutableLazy { Companion.metatable }

    override fun lookUpDirect(key: Value): Value? {
        if (key is MetisNumber) {
            val index = key.intValue()
            if (index >= 0 && index < value.length) {
                return MetisString(value[index].toString())
            }
        }
        return null
    }

    companion object {
        /**
         * The shared metatable for all strings.
         */
        // lazy because of a mutual dependency between initTable and String
        val metatable by lazy(::initString)
    }

    override fun toString() = "\"$value\""
}