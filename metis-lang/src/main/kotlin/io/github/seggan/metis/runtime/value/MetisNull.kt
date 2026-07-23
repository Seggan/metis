package io.github.seggan.metis.runtime.value

/**
 * A null value.
 */
data object MetisNull : Value {

    override var metatable: MetisTable? = MetisTable()

    override fun toString() = "null"
}