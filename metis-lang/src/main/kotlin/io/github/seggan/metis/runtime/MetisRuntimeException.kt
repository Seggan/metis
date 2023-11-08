package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.intrinsics.initError
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
    private val companionData: Value.Table = Value.Table(),
    cause: Throwable? = null
) : MetisException("$type: $actualMessage", mutableListOf(), cause), Value {

    override var metatable: Value.Table? = Companion.metatable

    override fun lookUpDirect(key: Value): Value? {
        if (key == messageString) return actualMessage.metisValue()
        return companionData.lookUpDirect(key)
    }

    override fun setDirect(key: Value, value: Value): Boolean {
        if (key == messageString) throw MetisRuntimeException("IndexError", "Cannot set message of error")
        return companionData.setDirect(key, value)
    }

    companion object {
        val metatable = initError()
    }

    internal class Finally : MetisRuntimeException("INTERNAL ERROR, THIS IS A BUG", "Finally block not popped") {
        override val message = "Finally"
    }
}

private val messageString = Value.String("message")