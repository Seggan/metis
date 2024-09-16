package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.runtime.intrinsics.threeArgFunction
import io.github.seggan.metis.runtime.intrinsics.twoArgFunction
import io.github.seggan.metis.util.LazyVar
import java.io.Serial
import java.nio.charset.Charset
import java.util.*

class StringValue private constructor(val value: String) : Value {

    override var metatable: TableValue? by LazyVar { Companion.metatable }

    override fun getDirect(key: Value): Value? {
        if (key is NumberValue.Int) {
            val index = key.intValue.intValueExact()
            if (index < 0 || index >= value.length) return null
            return value[index].toString().metis()
        }
        return null
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -2065581470768471818L

        private val internMap = WeakHashMap<String, StringValue>()

        operator fun invoke(value: String): StringValue {
            return internMap.computeIfAbsent(value, ::StringValue)
        }

        val metatable by buildTableLazy { table ->
            table.useNativeToString()
            table.useNativeEquality()
            table[Metamethod.PLUS] = twoArgFunction(true) { self, other ->
                (self.stringValue + other.stringValue).metis()
            }
            table["size"] = oneArgFunction(true) { self -> self.stringValue.length.metis() }
            table[Metamethod.CONTAINS] = twoArgFunction(true) { self, other ->
                self.stringValue.contains(other.stringValue).metis()
            }
            table[Metamethod.COMPARE] = twoArgFunction(true) { self, other ->
                self.stringValue.compareTo(other.stringValue).metis()
            }
            table["encode"] = twoArgFunction(true) { self, encoding ->
                val trueEncoding = Charset.forName(encoding.orNull()?.stringValue ?: "UTF-8")
                self.stringValue.toByteArray(trueEncoding).metis()
            }
            table["remove"] = threeArgFunction(true) { self, start, end ->
                val selfString = self.stringValue
                val trueEnd = end.orNull()?.intValue?.intValueExact() ?: selfString.length
                selfString.removeRange(start.intValue.intValueExact(), trueEnd).metis()
            }
            table["replace"] = threeArgFunction(true) { self, target, replacement ->
                self.stringValue.replace(target.stringValue, replacement.stringValue).metis()
            }
            table["split"] = twoArgFunction(true) { self, delimiter ->
                self.stringValue.split(delimiter.stringValue).map(String::metis).metis()
            }
            table["sub"] = threeArgFunction(true) { self, start, end ->
                val selfString = self.stringValue
                val trueEnd = end.orNull()?.intValue?.intValueExact() ?: selfString.length
                selfString.substring(start.intValue.intValueExact(), trueEnd).metis()
            }
            table["equalsIgnoreCase"] = twoArgFunction(true) { self, other ->
                self.stringValue.equals(other.stringValue, ignoreCase = true).metis()
            }

            table["uppercase"] = oneArgFunction(true) { self -> self.stringValue.uppercase().metis() }
            table["lowercase"] = oneArgFunction(true) { self -> self.stringValue.lowercase().metis() }

            table["isDigit"] = oneArgFunction(true) { self -> self.stringValue.all(Char::isDigit).metis() }
            table["isLetter"] = oneArgFunction(true) { self -> self.stringValue.all(Char::isLetter).metis() }
            table["isLetterOrDigit"] =
                oneArgFunction(true) { self -> self.stringValue.all(Char::isLetterOrDigit).metis() }
            table["isLowercase"] = oneArgFunction(true) { self -> self.stringValue.all(Char::isLowerCase).metis() }
            table["isUppercase"] = oneArgFunction(true) { self -> self.stringValue.all(Char::isUpperCase).metis() }
            table["isWhitespace"] = oneArgFunction(true) { self -> self.stringValue.all(Char::isWhitespace).metis() }
            table["isBlank"] = oneArgFunction(true) { self -> self.stringValue.isBlank().metis() }
        }
    }

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is StringValue && value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}

fun String.metis() = StringValue(this)

val Value.stringValue get() = convertTo<StringValue>().value