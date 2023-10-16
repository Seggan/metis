@file:JvmName("NativeObjects")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

inline fun <T> translateIoError(block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw MetisRuntimeException("IoError", e.message ?: "Unknown IO error")
}

internal val outStreamMetatable = buildTable { table ->
    table["write"] = twoArgFunction { self, value ->
        val toBeWritten = value.convertTo<Value.Bytes>().value
        translateIoError { self.asObj<OutputStream>().write(toBeWritten) }
        toBeWritten.size.toDouble().metisValue()
    }
    table["flush"] = oneArgFunction { self ->
        translateIoError { self.asObj<OutputStream>().flush() }
        Value.Null
    }
    table["close"] = oneArgFunction { self ->
        translateIoError { self.asObj<OutputStream>().close() }
        Value.Null
    }
    table["__str__"] = oneArgFunction {
        "an output stream".metisValue()
    }
}

fun wrapOutStream(stream: OutputStream): Value = Value.Native(stream, outStreamMetatable)

internal val inStreamMetatable = buildTable { table ->
    table["read"] = twoArgFunction { self, buffer ->
        if (buffer == Value.Null) {
            // Read a single byte
            self.asObj<InputStream>().read().toDouble().metisValue()
        } else {
            // Read into a buffer
            val toBeRead = buffer.convertTo<Value.Bytes>().value
            val read = self.asObj<InputStream>().read(toBeRead)
            if (read == -1) {
                Value.Null
            } else {
                read.toDouble().metisValue()
            }
        }
    }
    table["close"] = oneArgFunction { self ->
        self.asObj<InputStream>().close()
        Value.Null
    }
    table["__str__"] = oneArgFunction {
        "an input stream".metisValue()
    }
}

fun wrapInStream(stream: InputStream): Value = Value.Native(stream, inStreamMetatable)

private val sbMetatable = buildTable { table ->
    table["__append"] = twoArgFunction { self, value ->
        self.asObj<StringBuilder>().append(value.stringValue())
        self
    }
    table["__index__"] = twoArgFunction { self, value ->
        if (value is Value.Number) {
            self.asObj<StringBuilder>()[value.intValue()].toString().metisValue()
        } else {
            self.lookUp(value) ?: throw MetisRuntimeException(
                "KeyError",
                "Key not found: ${stringify(value)}",
                buildTable { table ->
                    table["key"] = value
                    table["value"] = self
                }
            )
        }
    }
    table["__set"] = threeArgFunction { self, index, value ->
        if (value is Value.Number) {
            self.asObj<StringBuilder>()[index.intValue()] = value.stringValue()[0]
        } else {
            self.setOrError(index, value)
        }
        self
    }
    table["delete"] = threeArgFunction { self, start, end ->
        self.asObj<StringBuilder>().delete(start.intValue(), end.intValue())
        self
    }
    table["delete_at"] = twoArgFunction { self, index ->
        self.asObj<StringBuilder>().deleteCharAt(index.intValue())
        self
    }
    table["clear"] = oneArgFunction { self ->
        self.asObj<StringBuilder>().clear()
        self
    }
    table["__len__"] = oneArgFunction { self ->
        Value.Number.of(self.asObj<StringBuilder>().length)
    }
    table["__str__"] = oneArgFunction {
        it.asObj<StringBuilder>().toString().metisValue()
    }
}

fun wrapStringBuilder(sb: StringBuilder): Value = Value.Native(sb, sbMetatable)