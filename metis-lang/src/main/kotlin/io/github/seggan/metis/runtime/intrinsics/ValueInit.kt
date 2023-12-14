package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import java.nio.charset.Charset
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.experimental.xor
import kotlin.math.pow

internal fun initString() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { it }
    table["__plus__"] = twoArgFunction(true) { self, other ->
        Value.String(self.stringValue() + other.stringValue())
    }
    table["size"] = oneArgFunction(true) { self ->
        self.stringValue().length.toDouble().metisValue()
    }
    table["__index__"] = twoArgFunction(true) { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException(
            "IndexError",
            "Character not found: ${stringify(key)}",
            buildTable { table ->
                table["index"] = key
                table["value"] = self
            }
        )
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        self.stringValue().contains(key.stringValue()).metisValue()
    }
    table["__eq__"] = twoArgFunction(true) { self, other ->
        if (other !is Value.String) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.stringValue() == other.stringValue())
        }
    }
    table["__cmp__"] = twoArgFunction(true) { self, other ->
        self.stringValue().compareTo(other.stringValue()).metisValue()
    }
    table["encode"] = twoArgFunction(true) { self, encoding ->
        val actualEncoding =
            if (encoding == Value.Null) Charsets.UTF_8
            else Charset.forName(encoding.stringValue())
        Value.Bytes(self.stringValue().toByteArray(actualEncoding))
    }
    table["remove"] = threeArgFunction { self, start, end ->
        if (end == Value.Null) {
            self.stringValue().removeRange(start.intValue(), self.stringValue().length).metisValue()
        } else {
            self.stringValue().removeRange(start.intValue(), end.intValue()).metisValue()
        }
    }
    table["replace"] = threeArgFunction(true) { self, value, toReplace ->
        self.stringValue().replace(value.stringValue(), toReplace.stringValue()).metisValue()
    }
    table["split"] = twoArgFunction(true) { self, delimiter ->
        self.stringValue().split(delimiter.stringValue()).map(String::metisValue).metisValue()
    }
    table["sub"] = threeArgFunction(true) { self, start, end ->
        if (end == Value.Null) {
            self.stringValue().substring(start.intValue()).metisValue()
        } else {
            self.stringValue().substring(start.intValue(), end.intValue()).metisValue()
        }
    }
    table["equalIgnoreCase"] = twoArgFunction(true) { self, other ->
        self.stringValue().equals(other.stringValue(), ignoreCase = true).metisValue()
    }

    table["lowercase"] = oneArgFunction(true) { self ->
        self.stringValue().lowercase().metisValue()
    }
    table["uppercase"] = oneArgFunction(true) { self ->
        self.stringValue().uppercase().metisValue()
    }

    table["isDigit"] = oneArgFunction(true) { self ->
        self.stringValue().all(Char::isDigit).metisValue()
    }
    table["isLetter"] = oneArgFunction(true) { self ->
        self.stringValue().all(Char::isLetter).metisValue()
    }
    table["isLetterOrDigit"] = oneArgFunction(true) { self ->
        self.stringValue().all(Char::isLetterOrDigit).metisValue()
    }
    table["isLowercase"] = oneArgFunction(true) { self ->
        self.stringValue().all(Char::isLowerCase).metisValue()
    }
    table["isUppercase"] = oneArgFunction(true) { self ->
        self.stringValue().all(Char::isUpperCase).metisValue()
    }
    table["isWhitespace"] = oneArgFunction(true) { self ->
        self.stringValue().all(Char::isWhitespace).metisValue()
    }
    table["isBlank"] = oneArgFunction(true) { self ->
        self.stringValue().isBlank().metisValue()
    }

    table["builder"] = oneArgFunction { init ->
        when (init) {
            Value.Null -> wrapStringBuilder(StringBuilder())
            is Value.String -> wrapStringBuilder(StringBuilder(init.stringValue()))
            else -> wrapStringBuilder(StringBuilder(init.intValue()))
        }
    }
}

internal fun initNumber() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.toString().metisValue()
    }
    table["__plus__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() + other.doubleValue())
    }
    table["__minus__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() - other.doubleValue())
    }
    table["__times__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() * other.doubleValue())
    }
    table["__div__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() / other.doubleValue())
    }
    table["__floordiv__"] = twoArgFunction(true) { self, other ->
        Value.Number.of((self.doubleValue() / other.doubleValue()).toInt())
    }
    table["__mod__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.doubleValue() % other.doubleValue())
    }
    table["__pow__"] = twoArgFunction(true) { self, other ->
        self.doubleValue().pow(other.doubleValue()).metisValue()
    }
    table["__eq__"] = twoArgFunction(true) { self, other ->
        if (other !is Value.Number) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.doubleValue() == other.doubleValue())
        }
    }
    table["__cmp__"] = twoArgFunction(true) { self, other ->
        self.doubleValue().compareTo(other.doubleValue()).metisValue()
    }
    table["__neg__"] = oneArgFunction(true) { self ->
        (-self.doubleValue()).metisValue()
    }
    table["__band__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.intValue() and other.intValue())
    }
    table["__bor__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.intValue() or other.intValue())
    }
    table["__bxor__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.intValue() xor other.intValue())
    }
    table["__shl__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.intValue() shl other.intValue())
    }
    table["__shr__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.intValue() shr other.intValue())
    }
    table["__shru__"] = twoArgFunction(true) { self, other ->
        Value.Number.of(self.intValue() ushr other.intValue())
    }
    table["__bnot__"] = oneArgFunction(true) { self ->
        Value.Number.of(self.intValue().inv())
    }
    table["stringWithRadix"] = twoArgFunction(true) { self, radix ->
        self.intValue().toString(radix.intValue()).metisValue()
    }

    table["parse"] = twoArgFunction { s, radix ->
        try {
            if (radix == Value.Null) {
                s.stringValue().toDouble().metisValue()
            } else {
                s.stringValue().toInt(radix.intValue()).metisValue()
            }
        } catch (e: NumberFormatException) {
            throw MetisRuntimeException("ValueError", "Invalid number: ${s.stringValue()}")
        }
    }
    table["nan"] = Value.Number.NAN
    table["inf"] = Value.Number.INF
    table["negInf"] = Value.Number.NEG_INF
}

internal fun initBoolean() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.toString().metisValue()
    }
    table["__eq__"] = twoArgFunction(true) { self, other ->
        if (other !is Value.Boolean) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.convertTo<Value.Boolean>().value == other.convertTo<Value.Boolean>().value)
        }
    }

    table["parse"] = oneArgFunction { s ->
        s.stringValue().toBoolean().metisValue()
    }
}

internal fun initTable() = Value.Table(mutableMapOf(), null).also { table ->
    table["__index__"] = twoArgFunction(true) { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException(
            "KeyError",
            "Key '${stringify(key)}' not found on ${stringify(self)}",
            buildTable { table ->
                table["key"] = key
                table["value"] = self
            }
        )
    }
    table["__set__"] = threeArgFunction(true) { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["size"] = oneArgFunction(true) { self ->
        self.tableValue().size.metisValue()
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        Value.Boolean.of(key in self.tableValue())
    }
    table["keys"] = oneArgFunction(true) { self ->
        self.tableValue().keys.metisValue()
    }
    table["values"] = oneArgFunction(true) { self ->
        self.tableValue().values.metisValue()
    }
    table["remove"] = twoArgFunction(true) { self, key ->
        self.tableValue().remove(key).orNull()
    }

    table.metatable = table
}

internal fun initList() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.listValue().toString().metisValue()
    }
    table["__index__"] = twoArgFunction(true) { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException(
            "IndexError",
            "Index out of bounds: ${stringify(key)}",
            buildTable { table ->
                table["index"] = key
                table["value"] = self
            }
        )
    }
    table["__set__"] = threeArgFunction(true) { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["size"] = oneArgFunction(true) { self ->
        self.listValue().size.metisValue()
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        Value.Boolean.of(key in self.listValue())
    }
    table["append"] = twoArgFunction(true) { self, value ->
        self.listValue().add(value)
        Value.Null
    }
    table["clear"] = oneArgFunction(true) { self ->
        self.listValue().clear()
        Value.Null
    }
    table["remove"] = twoArgFunction(true) { self, value ->
        self.listValue().remove(value)
        Value.Null
    }
    table["removeAt"] = twoArgFunction(true) { self, index ->
        self.listValue().removeAt(index.intValue())
    }
    table["slice"] = threeArgFunction(true) { self, start, end ->
        if (start.intValue() < 0) {
            throw MetisRuntimeException(
                "ValueError",
                "Start index cannot be negative",
                buildTable { table ->
                    table["start"] = start
                    table["end"] = end
                }
            )
        }
        if (end == Value.Null) {
            self.listValue().subList(start.intValue(), self.listValue().size).metisValue()
        } else {
            if (end.intValue() > self.listValue().size) {
                throw MetisRuntimeException(
                    "ValueError",
                    "End index cannot be greater than list size",
                    buildTable { table ->
                        table["start"] = start
                        table["end"] = end
                    }
                )
            }
            self.listValue().subList(start.intValue(), end.intValue()).metisValue()
        }
    }

    table["new"] = oneArgFunction { size ->
        if (size == Value.Null) {
            Value.List()
        } else {
            Value.List(ArrayList(size.intValue()))
        }
    }
}

internal fun initBytes() = buildTable { table ->
    table["__str__"] = oneArgFunction(true) { self ->
        self.bytesValue().toString(Charsets.UTF_8).metisValue()
    }
    table["__index__"] = twoArgFunction(true) { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException(
            "IndexError",
            "Byte not found: ${stringify(key)}",
            buildTable { table ->
                table["index"] = key
                table["value"] = self
            }
        )
    }
    table["__set__"] = threeArgFunction(true) { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["size"] = oneArgFunction(true) { self ->
        self.bytesValue().size.metisValue()
    }
    table["__contains__"] = twoArgFunction(true) { self, key ->
        Value.Boolean.of(key.intValue().toByte() in self.bytesValue())
    }
    table["__band__"] = twoArgFunction(true) { self, other ->
        val selfBytes = self.bytesValue()
        val otherBytes = other.bytesValue()
        if (selfBytes.size < otherBytes.size) {
            throw MetisRuntimeException(
                "ValueError",
                "Cannot perform bitwise and; self is smaller than other",
                buildTable { table ->
                    table["self"] = self
                    table["other"] = other
                }
            )
        }
        ByteArray(selfBytes.size) { i -> selfBytes[i] and otherBytes[i] }.metisValue()
    }
    table["__bor__"] = twoArgFunction(true) { self, other ->
        val selfBytes = self.bytesValue()
        val otherBytes = other.bytesValue()
        if (selfBytes.size < otherBytes.size) {
            throw MetisRuntimeException(
                "ValueError",
                "Cannot perform bitwise or; self is smaller than other",
                buildTable { table ->
                    table["self"] = self
                    table["other"] = other
                }
            )
        }
        ByteArray(selfBytes.size) { i -> selfBytes[i] or otherBytes[i] }.metisValue()
    }
    table["__bxor__"] = twoArgFunction(true) { self, other ->
        val selfBytes = self.bytesValue()
        val otherBytes = other.bytesValue()
        if (selfBytes.size < otherBytes.size) {
            throw MetisRuntimeException(
                "ValueError",
                "Cannot perform bitwise xor; self is smaller than other",
                buildTable { table ->
                    table["self"] = self
                    table["other"] = other
                }
            )
        }
        ByteArray(selfBytes.size) { i -> selfBytes[i] xor otherBytes[i] }.metisValue()
    }
    table["__bnot__"] = oneArgFunction(true) { self ->
        val selfBytes = self.bytesValue()
        ByteArray(selfBytes.size) { i -> selfBytes[i].inv() }.metisValue()
    }
    table["decode"] = twoArgFunction(true) { self, encoding ->
        val actualEncoding =
            if (encoding == Value.Null) Charsets.UTF_8
            else Charset.forName(encoding.stringValue())
        self.bytesValue().toString(actualEncoding).metisValue()
    }
    table["slice"] = threeArgFunction(true) { self, start, len ->
        val newBytes = ByteArray(len.intValue())
        self.bytesValue().copyInto(
            newBytes,
            0,
            start.intValue(),
            start.intValue() + len.intValue()
        )
        newBytes.metisValue()
    }

    table["allocate"] = oneArgFunction { size ->
        Value.Bytes(ByteArray(size.intValue()))
    }
    table["concat"] = oneArgFunction { list ->
        val bytes = list.listValue().map { it.bytesValue() }
        val size = bytes.sumOf { it.size }
        val newBytes = ByteArray(size)
        var offset = 0
        bytes.forEach {
            it.copyInto(newBytes, offset)
            offset += it.size
        }
        newBytes.metisValue()
    }
}