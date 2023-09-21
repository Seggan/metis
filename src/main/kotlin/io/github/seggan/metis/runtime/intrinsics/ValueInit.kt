package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.MetisRuntimeException
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import java.math.BigDecimal
import java.nio.charset.Charset

internal fun initString() = buildTable { table ->
    table["__str__"] = OneArgFunction { it }
    table["encode"] = TwoArgFunction { self, encoding ->
        val actualEncoding =
            if (encoding is Value.Null) Charsets.UTF_8 else Charset.forName(encoding.convertTo<Value.String>().value)
        Value.Bytes(self.convertTo<Value.String>().value.toByteArray(actualEncoding))
    }
}

internal fun initNumber() = buildTable { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(BigDecimal(self.convertTo<Value.Number>().value).stripTrailingZeros().toPlainString())
    }
}

internal fun initBoolean() = buildTable { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(self.convertTo<Value.Boolean>().value.toString())
    }
}

internal fun initTable() = Value.Table(mutableMapOf(), null).also { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(self.convertTo<Value.Table>().toString())
    }
    table["__index__"] = TwoArgFunction { self, key ->
        self.lookUp(key) ?: throw MetisRuntimeException("Key not found: $key")
    }
    table["__set__"] = ThreeArgFunction { self, key, value ->
        self.set(key, value)
        Value.Null
    }
    table["__len__"] = OneArgFunction { self ->
        Value.Number.from(self.convertTo<Value.Table>().size.toDouble())
    }
}

internal fun initList() = buildTable { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(self.convertTo<Value.List>().toString())
    }
    table["__index__"] = TwoArgFunction { self, key ->
        self.convertTo<Value.List>()[key.intValue()]
    }
    table["__set__"] = ThreeArgFunction { self, key, value ->
        self.convertTo<Value.List>()[key.intValue()] = value
        Value.Null
    }
    table["__len__"] = OneArgFunction { self ->
        Value.Number.from(self.convertTo<Value.List>().size.toDouble())
    }
}

internal fun initBytes() = buildTable { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(self.convertTo<Value.Bytes>().value.toString(Charsets.UTF_8))
    }
    table["__index__"] = TwoArgFunction { self, key ->
        self.convertTo<Value.Bytes>().value.getOrNull(key.intValue())?.let {
            Value.Number.from(it.toInt().toDouble())
        } ?: throw MetisRuntimeException("Key not found: $key")
    }
    table["__set__"] = ThreeArgFunction { self, key, value ->
        self.convertTo<Value.Bytes>().value[key.intValue()] = value.intValue().toByte()
        Value.Null
    }
    table["decode"] = TwoArgFunction { self, encoding ->
        val actualEncoding =
            if (encoding is Value.Null) Charsets.UTF_8 else Charset.forName(encoding.convertTo<Value.String>().value)
        Value.String(self.convertTo<Value.Bytes>().value.toString(actualEncoding))
    }
}

internal fun initNull() = buildTable { table ->
    table["__str__"] = OneArgFunction {
        Value.String("null")
    }
    table["__call__"] = ZeroArgFunction {
        throw MetisRuntimeException("Cannot call null")
    }
}

internal fun initChunk() = buildTable { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(self.convertTo<Chunk.Instance>().toString())
    }
}

private inline fun buildTable(init: (MutableMap<String, Value>) -> Unit): Value.Table {
    val map = mutableMapOf<String, Value>()
    init(map)
    return Value.Table(map.mapKeysTo(mutableMapOf()) { Value.String(it.key) }).also {
        require(it.metatable != null)
    }
}