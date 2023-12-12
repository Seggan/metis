@file:JvmName("NativeObjects")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.InvalidPathException

/**
 * Translates an [IOException] into a [MetisRuntimeException].
 *
 * @param block The block to execute.
 */
inline fun <T> translateIoError(block: () -> T): T {
    val message = try {
        null to block()
    } catch (e: InvalidPathException) {
        "Invalid path: ${e.message}" to null
    } catch (e: FileNotFoundException) {
        "File not found: ${e.message}" to null
    } catch (e: NoSuchFileException) {
        "File not found: ${e.message}" to null
    } catch (e: FileAlreadyExistsException) {
        "File already exists: ${e.message}" to null
    } catch (e: SecurityException) {
        "Permission denied: ${e.message}" to null
    } catch (e: IOException) {
        e.message to null
    }
    if (message.first != null) {
        throw MetisRuntimeException("IoError", message.first!!)
    } else {
        return message.second!!
    }
}

internal val outStreamMetatable = buildTable { table ->
    table["write"] = fourArgFunction(true) { self, buffer, off, len ->
        val toBeWritten = buffer.bytesValue()
        val offset = if (off == Value.Null) 0 else off.intValue()
        val length = if (len == Value.Null) toBeWritten.size else len.intValue()
        translateIoError { self.asObj<OutputStream>().write(toBeWritten, offset, length) }
        toBeWritten.size.toDouble().metisValue()
    }
    table["flush"] = oneArgFunction(true) { self ->
        translateIoError { self.asObj<OutputStream>().flush() }
        Value.Null
    }
    table["close"] = oneArgFunction(true) { self ->
        translateIoError { self.asObj<OutputStream>().close() }
        Value.Null
    }
    table["__str__"] = oneArgFunction(true) {
        "an output stream".metisValue()
    }
}

fun interface OutputStreamWrapper {
    fun wrap(stream: OutputStream): Value
}

internal val inStreamMetatable = buildTable { table ->
    table["read"] = twoArgFunction(true) { self, buffer ->
        if (buffer == Value.Null) {
            // Read a single byte
            self.asObj<InputStream>().read().toDouble().metisValue()
        } else {
            // Read into a buffer
            val toBeRead = buffer.bytesValue()
            val read = self.asObj<InputStream>().read(toBeRead)
            if (read == -1) {
                Value.Null
            } else {
                read.toDouble().metisValue()
            }
        }
    }
    table["close"] = oneArgFunction(true) { self ->
        self.asObj<InputStream>().close()
        Value.Null
    }
    table["__str__"] = oneArgFunction(true) {
        "an input stream".metisValue()
    }
}

fun interface InputStreamWrapper {
    fun wrap(stream: InputStream): Value
}

private val sbMetatable = buildTable { table ->
    table["__append"] = twoArgFunction(true) { self, value ->
        self.asObj<StringBuilder>().append(value.stringValue())
        self
    }
    table["__index__"] = twoArgFunction(true) { self, value ->
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
    table["__set"] = threeArgFunction(true) { self, index, value ->
        if (value is Value.Number) {
            self.asObj<StringBuilder>()[index.intValue()] = value.stringValue()[0]
        } else {
            self.setOrError(index, value)
        }
        self
    }
    table["delete"] = threeArgFunction(true) { self, start, end ->
        self.asObj<StringBuilder>().delete(start.intValue(), end.intValue())
        self
    }
    table["deleteAt"] = twoArgFunction(true) { self, index ->
        self.asObj<StringBuilder>().deleteCharAt(index.intValue())
        self
    }
    table["clear"] = oneArgFunction(true) { self ->
        self.asObj<StringBuilder>().clear()
        self
    }
    table["__len__"] = oneArgFunction(true) { self ->
        Value.Number.of(self.asObj<StringBuilder>().length)
    }
    table["__str__"] = oneArgFunction(true) { self ->
        self.asObj<StringBuilder>().toString().metisValue()
    }
}

/**
 * Wraps a [StringBuilder] in a [Value].
 */
fun wrapStringBuilder(sb: StringBuilder): Value = Value.Native(sb, sbMetatable)