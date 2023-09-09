@file:JvmName("StreamUtils")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.MetisRuntimeException
import io.github.seggan.metis.runtime.values.Value
import io.github.seggan.metis.runtime.values.convertTo
import java.io.OutputStream

private val outStreamMetatable = Value.Table(mutableMapOf()).also { table ->
    table["write"] = TwoArgFunction { self, value ->
        val toBeWritten = value.convertTo<Value.Bytes>().value
        asOutStream(self).write(toBeWritten)
        Value.Number.from(toBeWritten.size.toDouble())
    }
    table["flush"] = OneArgFunction { self ->
        asOutStream(self).flush()
        Value.Null
    }
    table["__str__"] = OneArgFunction {
        Value.String("an output stream")
    }
}

private fun asOutStream(value: Value): OutputStream = value.convertTo<Value.Native>().value as? OutputStream
    ?: throw MetisRuntimeException("Invalid stream")

fun wrapOutStream(stream: OutputStream): Value {
    return Value.Native(stream, outStreamMetatable)
}