package io.github.seggan.metis.runtime.value

/**
 * A null value.
 */
data object MetisNull : Value {

    override var metatable: MetisTable? = buildTable { table ->
        table["__str__"] = oneArgFunction(true) {
            "null".metisValue()
        }
        table["__call__"] = oneArgFunction(true) {
            throw MetisRuntimeException("TypeError", "Cannot call null")
        }
        table["__eq__"] = twoArgFunction(true) { _, other ->
            MetisBoolean.of(other == MetisNull)
        }
        table["__set__"] = threeArgFunction(true) { _, _, _ ->
            throw MetisRuntimeException("TypeError", "Cannot set any key on null")
        }
    }

    override fun toString() = "null"
}