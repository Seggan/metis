package io.github.seggan.metis.runtime.value

/**
 * A table.
 *
 * @param value The backing map of the table.
 * @param metatable The metatable of the table.
 */
data class MetisTable(
    val value: MutableMap<Value, Value> = mutableMapOf(),
    override var metatable: MetisTable? = Companion.metatable
) : Value, MutableMap<Value, Value> by value {

    override fun lookUpDirect(key: Value) = value[key]

    override fun setDirect(key: Value, value: Value): Boolean {
        this.value[key] = value
        return true
    }

    companion object {

        /**
         * The shared super-metatable for all tables.
         */
        val metatable: Nothing = TODO()
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