@file:JvmName("NativeObjects")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.MetisRuntimeException
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
    table["__str__"] = oneArgFunction {
        Value.String("an output stream")
    }
}

fun wrapOutStream(stream: OutputStream): Value = Value.Native(stream, outStreamMetatable)


private val iteratorMetatable = buildTable { table ->

    table["has_next"] = oneArgFunction { self ->
        Value.Boolean.of(self.asObj<Iterator<Value>>().hasNext())
    }
    table["next"] = oneArgFunction { self ->
        self.asObj<Iterator<Value>>().next()
    }
    table["__iter__"] = oneArgFunction { it }
    table["__str__"] = oneArgFunction {
        Value.String("an iterator")
    }
}

fun wrapIterator(iterator: Iterator<Value>): Value = Value.Native(iterator, iteratorMetatable)

private val sbMetatable = buildTable { table ->
    table["__append"] = twoArgFunction { self, value ->
        self.asObj<StringBuilder>().append(value.convertTo<Value.String>().value)
        self
    }
    table["__index__"] = twoArgFunction { self, value ->
        if (value is Value.Number) {
            Value.String(self.asObj<StringBuilder>()[value.intValue()].toString())
        } else {
            self.lookUp(value) ?: throw MetisRuntimeException("Key not found: $value")
        }
    }
    table["__set"] = threeArgFunction { self, index, value ->
        if (value is Value.Number) {
            self.asObj<StringBuilder>()[index.intValue()] = value.convertTo<Value.String>().value[0]
        } else {
            self.set(index, value)
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
        Value.String(it.asObj<StringBuilder>().toString())
    }
}

fun wrapStringBuilder(sb: StringBuilder): Value = Value.Native(sb, sbMetatable)