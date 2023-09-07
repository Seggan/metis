package io.github.seggan.metis.runtime.values

import io.github.seggan.metis.runtime.intrinsics.TwoArgFunction
import java.nio.charset.Charset

internal fun initString() = Value.Table(mutableMapOf()).also { table ->
    table["encode"] = TwoArgFunction { self, encoding ->
        val actualEncoding =
            if (encoding is Value.Null) Charsets.UTF_8 else Charset.forName(encoding.convertTo<Value.String>().value)
        Value.Bytes(self.convertTo<Value.String>().value.toByteArray(actualEncoding))
    }
}