package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.util.MetisException

/**
 * A runtime exception that can be thrown from Metis code.
 *
 * @param type The type of the exception.
 * @param actualMessage The message of the exception.
 * @param companionData The companion data of the exception.
 * @param cause The cause of the exception.
 */
open class MetisRuntimeException(
    val type: String,
    private val actualMessage: String,
    private val companionData: MetisTable = MetisTable(),
    cause: Throwable? = null
) : MetisException("$type: $actualMessage", mutableListOf(), cause), Value {

    override var metatable = Companion.metatable

    override fun lookUpDirect(key: Value): Value? {
        if (key == messageString) return actualMessage.metisValue()
        return companionData.lookUpDirect(key)
    }

    override fun setDirect(key: Value, value: Value): Boolean {
        if (key == messageString) throw MetisRuntimeException("IndexError", "Cannot set message of error")
        return companionData.setDirect(key, value)
    }

    companion object {
        val metatable = MetisTable()
    }
}

private val messageString = MetisString("message")

@Suppress("FunctionName")
fun MetisGlobalError(message: String) = MetisRuntimeException("GlobalError", message)

@Suppress("FunctionName")
fun MetisInternalError(cause: Throwable) =
    MetisRuntimeException("InternalError", "${cause::class.qualifiedName}: ${cause.message}", cause = cause)

@Suppress("FunctionName")
fun MetisIndexError(message: String) = MetisRuntimeException("IndexError", message)

@Suppress("FunctionName")
fun MetisValueError(message: String) = MetisRuntimeException("ValueError", message)

@Suppress("FunctionName")
fun MetisTypeError(message: String) = MetisRuntimeException("TypeError", message)