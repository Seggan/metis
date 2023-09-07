@file:JvmName("StreamUtils")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.MetisRuntimeException
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.popAs
import io.github.seggan.metis.runtime.push
import io.github.seggan.metis.runtime.values.Arity
import io.github.seggan.metis.runtime.values.Value
import java.io.OutputStream

private val outStreamMetatable = Value.Table(mutableMapOf()).also { table ->
    table["write"] = OneShotFunction(Arity(2)) {
        val value = stack.popAs<Value.Bytes>()
        val stream = stack.popAs<Value.Native>()
        val outStream = stream.value as? OutputStream
            ?: throw MetisRuntimeException("Invalid stream")
        outStream.write(value.value)
        stack.push(value.value.size.toDouble())
    }
}

fun wrapOutStream(state: State, stream: OutputStream): Value {
    return Value.Native(stream, outStreamMetatable)
}