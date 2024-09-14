package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.runtime.intrinsics.threeArgFunction
import io.github.seggan.metis.runtime.intrinsics.twoArgFunction
import io.github.seggan.metis.util.LazyVar
import java.io.Serial

data class TableValue(
    val value: MutableMap<Value, Value> = mutableMapOf(),
    override var metatable: TableValue? = Companion.metatable,
) : Value, MutableMap<Value, Value> by value {

    operator fun get(key: String) = value[key.metis()]
    operator fun set(key: String, value: Value) = set(key.metis(), value)
    operator fun contains(key: String) = containsKey(key.metis())

    override fun getDirect(key: Value): Value? = value[key]

    override fun setDirect(key: Value, value: Value): Boolean {
        this[key] = value
        return true
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -1467686496628948080L

        val metatable = TableValue(mutableMapOf(), null).also { table ->
            table.useNativeEquality()
            table["__str__"] = oneArgFunction(true) { self ->
                val sb = StringBuilder()
                sb.append('{')
                for ((key, value) in self.tableValue.entries) {
                    sb.append(", ")
                    sb.append(key)
                    sb.append(" = ")
                    sb.append(value)
                }
                sb.delete(sb.length - 2, sb.length)
                sb.append('}')
                sb.toString().metis()
            }
            table["__get__"] = twoArgFunction(true) { self, key ->
                self.getInHierarchy(key) ?: throw MetisRuntimeException(
                    "KeyError",
                    "Key '${key}' not found in table",
                    mapOf("obj" to self, "key" to key).metis()
                )
            }
            table["__set__"] = threeArgFunction(true) { self, key, value ->
                self.setOrError(key, value)
                NullValue
            }
            table["size"] = oneArgFunction(true) { self -> self.tableValue.size.metis() }
            table["__contains__"] = twoArgFunction(true) { self, key ->
                (key in self.tableValue).metis()
            }
            table["remove"] = twoArgFunction(true) { self, key ->
                self.tableValue.remove(key) ?: throw MetisRuntimeException(
                    "KeyError",
                    "Key '${key}' not found in table",
                    mapOf("obj" to self, "key" to key).metis()
                )
            }

            table.metatable = table
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TableValue) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

fun Map<Value, Value>.metis() = TableValue(toMutableMap())
fun Map<String, Value>.metis() = TableValue(mapKeys { it.key.metis() }.toMutableMap())

val Value.tableValue get() = convertTo<TableValue>()

inline fun buildTable(builder: (TableValue) -> Unit) =
    TableValue()
        .apply(builder)
        .also {
            if (it.metatable == null) throw AssertionError("Table metatable is null")
        }

fun buildTableLazy(builder: (TableValue) -> Unit): LazyVar<TableValue?> = LazyVar { buildTable(builder) }