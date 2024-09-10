package io.github.seggan.metis.runtime.value

import java.io.Serial

data class TableValue(
    val value: MutableMap<Value, Value> = mutableMapOf()
) : Value, MutableMap<Value, Value> by value {
    operator fun get(key: String) = value[StringValue(key)]
    operator fun set(key: String, value: Value) = set(StringValue(key), value)
    operator fun contains(key: String) = containsKey(StringValue(key))

    companion object {
        @Serial
        private const val serialVersionUID: Long = -1467686496628948080L
    }
}

fun Map<Value, Value>.metis() = TableValue(toMutableMap())

inline fun buildTable(builder: (TableValue) -> Unit) = TableValue().apply(builder)