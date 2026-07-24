package io.github.seggan.metis.runtime.value

/**
 * A value in the Metis runtime.
 */
interface Value {

    /**
     * The metatable of this value.
     */
    var metatable: MetisTable

    /**
     * Look up a value in this value.
     *
     * @param key The key to look up.
     */
    fun lookUpDirect(key: Value): Value? = null

    /**
     * Set a value in this value.
     *
     * @param key The key to set.
     * @param value The value to set.
     */
    fun setDirect(key: Value, value: Value): Boolean = false

}

