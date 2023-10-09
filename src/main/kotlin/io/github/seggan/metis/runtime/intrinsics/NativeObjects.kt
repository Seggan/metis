@file:JvmName("NativeObjects")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import java.io.OutputStream

private val outStreamMetatable = buildTable { table ->
    table["write"] = twoArgFunction { self, value ->
        val toBeWritten = value.convertTo<Value.Bytes>().value
        self.asObj<OutputStream>().write(toBeWritten)
        Value.Number.of(toBeWritten.size.toDouble())
    }
    table["flush"] = oneArgFunction { self ->
        self.asObj<OutputStream>().flush()
        Value.Null
    }
    table["close"] = oneArgFunction { self ->
        self.asObj<OutputStream>().close()
        Value.Null
    }
    table["__str__"] = oneArgFunction {
        "an output stream".metisValue()
    }
}

fun wrapOutStream(stream: OutputStream): Value = Value.Native(stream, outStreamMetatable)


private val iteratorMetatable = buildTable { table ->

    table["has_next"] = oneArgFunction { self ->
        self.asObj<Iterator<Value>>().hasNext().metisValue()
    }
    table["next"] = oneArgFunction { self ->
        self.asObj<Iterator<Value>>().next()
    }
    table["__iter__"] = oneArgFunction { it }
    table["__str__"] = oneArgFunction {
        "an iterator".metisValue()
    }
}

fun wrapIterator(iterator: Iterator<Value>): Value = Value.Native(iterator, iteratorMetatable)

private val sbMetatable = buildTable { table ->
    table["__append"] = twoArgFunction { self, value ->
        self.asObj<StringBuilder>().append(value.stringValue())
        self
    }
    table["__index__"] = twoArgFunction { self, value ->
        if (value is Value.Number) {
            self.asObj<StringBuilder>()[value.intValue()].toString().metisValue()
        } else {
            self.lookUp(value) ?: throw MetisRuntimeException("KeyError", "Key not found: $value")
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