package io.github.seggan.metis.runtime.value

/**
 * A table.
 */
class MetisTable private constructor(
    val value: MutableMap<Value, Value>,
    @Suppress("unused") dummy: Unit
) : Value, MutableMap<Value, Value> by value {

    override var metatable = this

    /**
     * Constructs a new table with the given metatable
     *
     * @param value The backing map of the table.
     * @param metatable The metatable of the table.
     */
    constructor(value: MutableMap<Value, Value> = mutableMapOf(), metatable: MetisTable = Companion.metatable) : this(
        value,
        Unit
    ) {
        this.metatable = metatable
    }

    override fun lookUpDirect(key: Value) = value[key]

    override fun setDirect(key: Value, value: Value): Boolean {
        this.value[key] = value
        return true
    }

    companion object {

        /**
         * The shared super-metatable for all tables.
         */
        val metatable = MetisTable(mutableMapOf(), Unit)
    }

    override fun toString(): String {
        return entries.joinToString(
            prefix = "{ ",
            postfix = " }"
        ) {
            if (it.key === this@MetisTable) {
                "{...} = ${it.value}"
            } else if (it.value === this@MetisTable) {
                "${it.key} = {...}"
            } else {
                "${it.key} = ${it.value}"
            }
        }
    }

    override fun equals(other: Any?) = other is MetisTable && value == other.value

    override fun hashCode() = value.hashCode()
}