package io.github.seggan.metis.runtime.value

import java.io.Serial

class NativeValue(val value: Any, override var metatable: TableValue?) : Value {

    inline fun <reified T : Any> asObj(): T {
        if (value is T) return value
        throw MetisTypeError(T::class.qualifiedName!!, value::class.qualifiedName!!)
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -1318005099258487221L
    }
}