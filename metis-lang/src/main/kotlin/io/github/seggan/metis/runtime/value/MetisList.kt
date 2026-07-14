package io.github.seggan.metis.runtime.value

/**
 * A list.
 *
 * @param value The backing list of the list.
 * @param metatable The metatable of the list.
 */
data class MetisList(
    val value: MutableList<Value> = mutableListOf(),
    override var metatable: MetisTable? = Companion.metatable
) : Value, MutableList<Value> by value {

    override fun lookUpDirect(key: Value): Value? {
        if (key is MetisNumber) {
            return getOrNull(key.intValue())
        }
        return null
    }

    override fun setDirect(key: Value, value: Value): Boolean {
        if (key is MetisNumber) {
            this[key.intValue()] = value
            return true
        }
        return false
    }

    override fun toString(): String {
        return value.joinToString(
            prefix = "[",
            postfix = "]"
        ) { if (it === this@MetisList) "[...]" else it.toString() }
    }

    companion object {

        /**
         * The shared metatable for all lists.
         */
        val metatable = initList()
    }
}