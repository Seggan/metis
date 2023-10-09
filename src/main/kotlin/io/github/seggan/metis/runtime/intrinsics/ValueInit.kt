package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import java.nio.charset.Charset
import kotlin.math.pow

internal fun initString() = buildTable { table ->
    table["__str__"] = oneArgFunction { it }
    table["__plus__"] = twoArgFunction { self, other ->
        (self.stringValue() + other.stringValue()).metisValue()
    }
    table["__eq__"] = twoArgFunction { self, other ->
        if (other !is Value.String) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.stringValue() == other.stringValue())
        }
    }
    table["encode"] = twoArgFunction { self, encoding ->
        val actualEncoding =
            if (encoding == Value.Null) Charsets.UTF_8
            else Charset.forName(encoding.stringValue())
        Value.Bytes(self.stringValue().toByteArray(actualEncoding))
    }
    table["replace"] = threeArgFunction { self, value, toReplace ->
        self.stringValue().replace(value.stringValue(), toReplace.stringValue()).metisValue()
    }

    table["builder"] = oneArgFunction { init ->
        if (init == Value.Null) {
            wrapStringBuilder(StringBuilder())
        } else {
            wrapStringBuilder(StringBuilder(init.stringValue()))
        }
    }
}

internal fun initNumber() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        self.toString().metisValue()
    }
    table["__plus__"] = twoArgFunction { self, other ->
        Value.Number.of(self.doubleValue() + other.doubleValue())
    }
    table["__minus__"] = twoArgFunction { self, other ->
        Value.Number.of(self.doubleValue() - other.doubleValue())
    }
    table["__times__"] = twoArgFunction { self, other ->
        Value.Number.of(self.doubleValue() * other.doubleValue())
    }
    table["__div__"] = twoArgFunction { self, other ->
        Value.Number.of(self.doubleValue() / other.doubleValue())
    }
    table["__mod__"] = twoArgFunction { self, other ->
        Value.Number.of(self.doubleValue() % other.doubleValue())
    }
    table["__pow__"] = twoArgFunction { self, other ->
        Value.Number.of(self.doubleValue().pow(other.doubleValue()))
    }
    table["__eq__"] = twoArgFunction { self, other ->
        if (other !is Value.Number) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.doubleValue() == other.doubleValue())
        }
    }
    table["__cmp__"] = twoArgFunction { self, other ->
        Value.Number.of(
            self.doubleValue().compareTo(other.doubleValue()).toDouble()
        )
    }
    table["__neg__"] = oneArgFunction { self ->
        Value.Number.of(-self.doubleValue())
    }
}

internal fun initBoolean() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        self.toString().metisValue()
    }
    table["__eq__"] = twoArgFunction { self, other ->
        if (other !is Value.Boolean) {
            Value.Boolean.FALSE
        } else {
            Value.Boolean.of(self.convertTo<Value.Boolean>().value == other.convertTo<Value.Boolean>().value)
        }
    }
}

internal fun initTable() = Value.Table(mutableMapOf(), null).also { table ->
    table["__index__"] = twoArgFunction { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException("KeyError", "Key not found: $key")
    }
    table["__set__"] = threeArgFunction { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["__len__"] = oneArgFunction { self ->
        Value.Number.of(self.convertTo<Value.Table>().size.toDouble())
    }
    table["keys"] = oneArgFunction { self ->
        Value.List(self.convertTo<Value.Table>().keys.toMutableList())
    }
    table["values"] = oneArgFunction { self ->
        Value.List(self.convertTo<Value.Table>().values.toMutableList())
    }
}

internal fun initList() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        self.convertTo<Value.List>().toString().metisValue()
    }
    table["__index__"] = twoArgFunction { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException("IndexError", "Index not found: $key")
    }
    table["__set__"] = threeArgFunction { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["__len__"] = oneArgFunction { self ->
        Value.Number.of(self.convertTo<Value.List>().size.toDouble())
    }
    table["__iter__"] = oneArgFunction { self ->
        wrapIterator(self.convertTo<Value.List>().iterator())
    }
    table["append"] = twoArgFunction { self, value ->
        self.convertTo<Value.List>().add(value)
        Value.Null
    }
}

internal fun initBytes() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        self.convertTo<Value.Bytes>().value.toString(Charsets.UTF_8).metisValue()
    }
    table["__index__"] = twoArgFunction { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException("IndexError", "Byte not found: $key")
    }
    table["__set__"] = threeArgFunction { self, key, value ->
        self.setOrError(key, value)
        Value.Null
    }
    table["decode"] = twoArgFunction { self, encoding ->
        val actualEncoding =
            if (encoding == Value.Null) Charsets.UTF_8
            else Charset.forName(encoding.stringValue())
        self.convertTo<Value.Bytes>().value.toString(actualEncoding).metisValue()
    }

    table["allocate"] = oneArgFunction { size ->
        Value.Bytes(ByteArray(size.intValue()))
    }
}

internal fun initNull() = buildTable { table ->
    table["__str__"] = oneArgFunction {
        "null".metisValue()
    }
    table["__call__"] = zeroArgFunction {
        throw MetisRuntimeException("TypeError", "Cannot call null")
    }
    table["__eq__"] = twoArgFunction { _, other ->
        Value.Boolean.of(other == Value.Null)
    }
}

internal fun initChunk() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        self.convertTo<Chunk.Instance>().verboseToString().metisValue()
    }
}

internal fun initError() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        self.convertTo<MetisRuntimeException>().message!!.metisValue()
    }
    table["__call__"] = zeroArgFunction {
        throw MetisRuntimeException("TypeError", "Cannot call error")
    }
    table["__eq__"] = twoArgFunction { self, other ->
        Value.Boolean.of(self === other)
    }
}