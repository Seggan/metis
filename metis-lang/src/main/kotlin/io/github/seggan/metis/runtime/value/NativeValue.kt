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

fun <T : Any> Value.asNativeObj(clazz: Class<T>): T {
    val value = nativeValue.value
    if (clazz.isAssignableFrom(value::class.java)) return clazz.cast(value)
    throw MetisTypeError(clazz.name, value::class.qualifiedName!!)
}

inline fun <reified T : Any> Value.asNativeObj(): T = nativeValue.asObj<T>()

val Value.nativeValue get() = into<NativeValue>()