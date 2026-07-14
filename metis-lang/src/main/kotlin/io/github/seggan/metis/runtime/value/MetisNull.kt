package io.github.seggan.metis.runtime.value

/**
 * A null value.
 */
data object MetisNull : Value {

    override var metatable = TODO()

    override fun toString() = "null"
}