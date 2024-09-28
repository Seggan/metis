@file:Suppress("FunctionName")

package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.runtime.intrinsics.twoArgFunction
import io.github.seggan.metis.util.MetisException
import java.io.Serial

/**
 * A runtime exception that can be thrown from Metis code.
 *
 * @param type The type of the exception.
 * @param actualMessage The message of the exception.
 * @param companionData The companion data of the exception.
 * @param cause The cause of the exception.
 */
class MetisRuntimeException(
    val type: String,
    private val actualMessage: String,
    private val companionData: TableValue = TableValue(),
    cause: Throwable? = null
) : MetisException("$type: $actualMessage", mutableListOf(), cause), Value {

    override var metatable: TableValue? = Companion.metatable

    init {
        companionData["type"] = type.metis()
        companionData["message"] = actualMessage.metis()
    }

    override fun getDirect(key: Value): Value? = companionData[key]

    override fun setDirect(key: Value, value: Value): Boolean {
        if (key is StringValue) {
            if (key.value == "type") return false
            if (key.value == "message") return false
        }
        return companionData.setDirect(key, value)
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -2267358319541695837L

        val metatable by buildTableLazy { table ->
            table.useReferentialEquality()
            table[Metamethod.TO_STRING] = oneArgFunction(true) { self ->
                self.into<MetisRuntimeException>().message!!.metis()
            }
            table[Metamethod.CONTAINS] = twoArgFunction(true) { self, key ->
                (self.getInHierarchy(key) != null).metis()
            }
        }
    }
}

fun MetisValueError(
    obj: Value,
    message: String,
    companionData: TableValue = TableValue(),
    cause: Throwable? = null
) = MetisRuntimeException("ValueError", message, companionData.also { it["value"] = obj }, cause)

fun MetisTypeError(
    expected: String,
    actual: String,
    companionData: TableValue = TableValue(),
    cause: Throwable? = null
) = MetisRuntimeException(
    "TypeError",
    "Expected $expected, got $actual",
    companionData.also { it["expected"] = expected.metis(); it["actual"] = actual.metis() },
    cause
)

fun MetisKeyError(
    obj: Value,
    key: Value,
    message: String,
    companionData: TableValue = TableValue(),
    cause: Throwable? = null
) = MetisRuntimeException(
    "KeyError",
    message,
    companionData.also { it["obj"] = obj; it["key"] = key },
    cause
)

fun MetisInternalError(
    message: String,
    companionData: TableValue = TableValue(),
    cause: Throwable? = null
) = MetisRuntimeException("InternalError", message, companionData, cause)