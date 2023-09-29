package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.MetisRuntimeException
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import java.nio.charset.Charset
import kotlin.math.pow

internal fun initString() = buildTable { table ->
    table["__str__"] = oneArgFunction { it }
    table["encode"] = twoArgFunction { self, encoding ->
        val actualEncoding =
            if (encoding is Value.Null) Charsets.UTF_8 else Charset.forName(encoding.convertTo<Value.String>().value)
        Value.Bytes(self.convertTo<Value.String>().value.toByteArray(actualEncoding))
    }
    table["__plus__"] = twoArgFunction { self, other ->
        Value.String(self.convertTo<Value.String>().value + other.convertTo<Value.String>().value)
    }
}

internal fun initNumber() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        Value.String(self.toString())
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
        Value.Boolean.of(self.doubleValue() == other.doubleValue())
    }
    table["__cmp__"] = twoArgFunction { self, other ->
        Value.Number.of(
            self.doubleValue().compareTo(other.doubleValue()).toDouble()
        )
    }
}

internal fun initBoolean() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        Value.String(self.toString())
    }
    table["__eq__"] = twoArgFunction { self, other ->
        Value.Boolean.of(self.convertTo<Value.Boolean>().value == other.convertTo<Value.Boolean>().value)
    }
}

internal fun initTable() = Value.Table(mutableMapOf(), null).also { table ->
    table["__index__"] = twoArgFunction { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException("Key not found: $key")
    }
    table["__set__"] = threeArgFunction { self, key, value ->
        self.set(key, value)
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
        Value.String(self.convertTo<Value.List>().toString())
    }
    table["__index__"] = twoArgFunction { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException("Index not found: $key")
    }
    table["__set__"] = threeArgFunction { self, key, value ->
        self.set(key, value)
        Value.Null
    }
    table["__len__"] = oneArgFunction { self ->
        Value.Number.of(self.convertTo<Value.List>().size.toDouble())
    }
    table["__iter__"] = oneArgFunction { self ->
        wrapIterator(self.convertTo<Value.List>().iterator())
    }
}

internal fun initBytes() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        Value.String(self.convertTo<Value.Bytes>().value.toString(Charsets.UTF_8))
    }
    table["__index__"] = twoArgFunction { self, key ->
        self.convertTo<Value.Bytes>().value.getOrNull(key.intValue())?.let {
            Value.Number.of(it.toInt().toDouble())
        } ?: throw MetisRuntimeException("Key not found: $key")
    }
    table["__set__"] = threeArgFunction { self, key, value ->
        self.convertTo<Value.Bytes>().value[key.intValue()] = value.intValue().toByte()
        Value.Null
    }
    table["decode"] = twoArgFunction { self, encoding ->
        val actualEncoding =
            if (encoding is Value.Null) Charsets.UTF_8 else Charset.forName(encoding.convertTo<Value.String>().value)
        Value.String(self.convertTo<Value.Bytes>().value.toString(actualEncoding))
    }
}

internal fun initNull() = buildTable { table ->
    table["__str__"] = oneArgFunction {
        Value.String("null")
    }
    table["__call__"] = zeroArgFunction {
        throw MetisRuntimeException("Cannot call null")
    }
}

internal fun initChunk() = buildTable { table ->
    table["__str__"] = oneArgFunction { self ->
        Value.String(self.convertTo<Chunk.Instance>().toString())
    }
}

private val iteratorMetatable = buildTable { table ->

    @Suppress("UNCHECKED_CAST")
    fun Value.asIterator() = convertTo<Value.Native>().value as? Iterator<Value>
        ?: throw MetisRuntimeException("Invalid iterator")

    table["has_next"] = oneArgFunction { self ->
        Value.Boolean.of(self.asIterator().hasNext())
    }
    table["next"] = oneArgFunction { self ->
        self.asIterator().next()
    }
    table["__iter__"] = oneArgFunction { it }
    table["__str__"] = oneArgFunction {
        Value.String("an iterator")
    }
}

fun wrapIterator(iterator: Iterator<Value>): Value {
    return Value.Native(iterator, iteratorMetatable)
}