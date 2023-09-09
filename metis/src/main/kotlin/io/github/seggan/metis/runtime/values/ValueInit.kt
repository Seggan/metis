package io.github.seggan.metis.runtime.values

import io.github.seggan.metis.runtime.Chunk
import io.github.seggan.metis.runtime.MetisRuntimeException
import io.github.seggan.metis.runtime.intrinsics.OneArgFunction
import io.github.seggan.metis.runtime.intrinsics.TwoArgFunction
import io.github.seggan.metis.runtime.intrinsics.ZeroArgFunction
import java.math.BigDecimal
import java.nio.charset.Charset

internal fun initString() = Value.Table(mutableMapOf()).also { table ->
    table["__str__"] = OneArgFunction { it }
    table["encode"] = TwoArgFunction { self, encoding ->
        val actualEncoding =
            if (encoding is Value.Null) Charsets.UTF_8 else Charset.forName(encoding.convertTo<Value.String>().value)
        Value.Bytes(self.convertTo<Value.String>().value.toByteArray(actualEncoding))
    }
}

internal fun initNumber() = Value.Table(mutableMapOf()).also { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(BigDecimal(self.convertTo<Value.Number>().value).stripTrailingZeros().toPlainString())
    }
}

internal fun initBoolean() = Value.Table(mutableMapOf()).also { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(self.convertTo<Value.Boolean>().value.toString())
    }
}

internal fun initNull() = Value.Table(mutableMapOf()).also { table ->
    table["__str__"] = OneArgFunction {
        Value.String("null")
    }
    table["__call__"] = ZeroArgFunction {
        throw MetisRuntimeException("Cannot call null")
    }
}

internal fun initChunk() = Value.Table(mutableMapOf()).also { table ->
    table["__str__"] = OneArgFunction { self ->
        Value.String(self.convertTo<Chunk>().toString())
    }
}