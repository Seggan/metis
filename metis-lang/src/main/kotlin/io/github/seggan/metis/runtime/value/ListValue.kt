package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.runtime.intrinsics.threeArgFunction
import io.github.seggan.metis.runtime.intrinsics.twoArgFunction
import java.io.Serial

data class ListValue(
    val value: MutableList<Value> = mutableListOf(),
    override var metatable: TableValue? = Companion.metatable,
) : Value, MutableList<Value> by value {

    override fun getDirect(key: Value): Value? {
        if (key is NumberValue.Int) {
            val index = key.intValue.intValueExact()
            return getOrNull(index)
        }
        return null
    }

    override fun setDirect(key: Value, value: Value): Boolean {
        if (key !is NumberValue.Int) return false
        val index = key.intValue.intValueExact()
        if (index < 0 || index >= this.value.size) return false
        this.value[index] = value
        return true
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -2122588660077368725L

        val metatable by buildTableLazy { table ->
            table.useNativeEquality()
            table[Metamethod.TO_STRING] = oneArgFunction(true) { self ->
                val sb = StringBuilder()
                sb.append('[')
                for (element in self.listValue) {
                    sb.append(element.metisToString())
                    sb.append(", ")
                }
                if (self.listValue.isNotEmpty()) sb.delete(sb.length - 2, sb.length)
                sb.append(']')
                sb.toString().metis()
            }
            table[Metamethod.GET] = twoArgFunction(true) { self, key ->
                self.getInHierarchy(key) ?: throw MetisKeyError(self, key, "Index '${key.metisToString()}' not found")
            }
            table[Metamethod.SET] = threeArgFunction(true) { self, key, value ->
                if (!self.setDirect(key, value)) {
                    throw MetisKeyError(
                        self,
                        key,
                        "Cannot set index '${key.metisToString()}' on value of type ${metisTypeName(self::class)}"
                    )
                }
                null
            }
            table["size"] = oneArgFunction(true) { self -> self.listValue.size.metis() }
            table[Metamethod.CONTAINS] = twoArgFunction(true) { self, key ->
                (key in self.listValue).metis()
            }
            table["append"] = twoArgFunction(true) { self, value ->
                self.listValue.add(value)
                value
            }
            table["clear"] = oneArgFunction(true) { self ->
                self.listValue.clear()
                null
            }
            table["pop"] = oneArgFunction(true) { self ->
                self.listValue.removeLastOrNull() ?: throw MetisValueError(self, "Cannot pop from empty list")
            }
            table["remove"] = twoArgFunction(true) { self, key ->
                self.listValue.remove(key).metis()
            }
            table["removeAt"] = twoArgFunction(true) { self, key ->
                val index = key.intValue.intValueExact()
                val selfList = self.listValue
                if (index < 0 || index >= selfList.size) {
                    throw MetisKeyError(self, key, "Index out of bounds")
                }
                selfList.removeAt(key.intValue.intValueExact())
            }
            table["slice"] = threeArgFunction(true) { self, start, end ->
                val selfList = self.listValue
                val trueStart = start.intValue.intValueExact()
                val trueEnd = end.orNull()?.intValue?.intValueExact() ?: selfList.size
                if (trueStart < 0 || trueEnd < 0 || trueStart >= selfList.size || trueEnd >= selfList.size) {
                    throw MetisValueError(self, "Index out of bounds")
                }
                selfList.subList(trueStart, trueEnd).metis()
            }

            table["new"] = oneArgFunction { size ->
                if (size == Value.Null) {
                    ListValue()
                } else {
                    ListValue(ArrayList(size.intValue.intValueExact()))
                }
            }
        }
    }
}

fun Collection<Value>.metis() = ListValue(this.toMutableList())

val Value.listValue get() = into<ListValue>()
