@file:JvmName("StreamUtils")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.MetisRuntimeException
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.convertTo
import java.io.OutputStream

private val outStreamMetatable = Value.Table(mutableMapOf()).also { table ->

    fun Value.asOutStream() = convertTo<Value.Native>().value as? OutputStream
        ?: throw MetisRuntimeException("Invalid stream")

    table["write"] = twoArgFunction { self, value ->
        val toBeWritten = value.convertTo<Value.Bytes>().value
        self.asOutStream().write(toBeWritten)
        Value.Number.of(toBeWritten.size.toDouble())
    }
    table["flush"] = oneArgFunction { self ->
        self.asOutStream().flush()
        Value.Null
    }
    table["__str__"] = oneArgFunction {
        Value.String("an output stream")
    }
}

fun wrapOutStream(stream: OutputStream): Value {
    return Value.Native(stream, outStreamMetatable)
}