package io.github.seggan.metis.runtime

import io.github.seggan.metis.MetisException
import io.github.seggan.metis.runtime.intrinsics.initError

class MetisRuntimeException(
    val type: String,
    message: String,
    private val companionData: Value.Table = Value.Table()
) : MetisException("$type: $message", mutableListOf()), Value {

    override var metatable: Value.Table? = Companion.metatable

    override fun lookUpDirect(key: Value): Value? {
        if (key == messageString) return Value.String(message!!)
        return companionData.lookUpDirect(key)
    }

    override fun setDirect(key: Value, value: Value): Boolean {
        if (key == messageString) throw MetisRuntimeException("IndexError", "Cannot set message of error")
        return companionData.setDirect(key, value)
    }

    companion object {
        val metatable = initError()
    }
}

private val messageString = Value.String("message")