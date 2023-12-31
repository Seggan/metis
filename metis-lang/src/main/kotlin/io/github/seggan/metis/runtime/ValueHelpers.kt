@file:JvmName("ValueHelpers")

package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.intrinsics.Coroutine
import kotlin.reflect.KClass


/**
 * Look up a value in this value, possibly using the metatable.
 *
 * @param key The key to look up.
 * @return The value, or null if it doesn't exist.
 */
fun Value.lookUp(key: Value): Value? {
    if (this === metatable) return lookUpDirect(key)
    return lookUpDirect(key) ?: metatable?.lookUp(key)
}

/**
 * Sets a value in this value, possibly using the metatable.
 *
 * @param key The key to set.
 * @param value The value to set.
 * @return Whether the value was set.
 */
fun Value.set(key: Value, value: Value): Boolean {
    if (this === metatable) return setDirect(key, value)
    return setDirect(key, value) || metatable?.set(key, value) ?: false
}

/**
 * Sets a value in this value, or throws an error if it cannot be set.
 *
 * @param key The key to set.
 * @param value The value to set.
 * @throws MetisRuntimeException If the value cannot be set.
 */
fun Value.setOrError(key: Value, value: Value) {
    if (!set(key, value)) {
        throw MetisRuntimeException("IndexError", "Cannot set ${typeToName(key::class)} on ${typeToName(this::class)}")
    }
}

/**
 * If this value is null, return [Value.Null], otherwise return this value.
 */
fun Value?.orNull() = this ?: Value.Null

/**
 * Converts this value to a [T], or throws an error if it cannot be converted.
 *
 * @param T The type to convert to.
 * @throws MetisRuntimeException If the value cannot be converted.
 */
inline fun <reified T : Value> Value.convertTo(): T {
    if (this is T) {
        return this
    }
    throw MetisRuntimeException("TypeError", "Cannot convert ${typeToName(this::class)} to ${typeToName(T::class)}")
}

/**
 * Converts this value to a [Int], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.intValue() = this.convertTo<Value.Number>().value.toInt()

/**
 * Converts this value to a [Double], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.doubleValue() = this.convertTo<Value.Number>().value

/**
 * Converts this value to a [String], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.stringValue() = this.convertTo<Value.String>().value

/**
 * Converts this value to a [kotlin.Boolean], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.booleanValue() = this.convertTo<Value.Boolean>().value

/**
 * Converts this value to a [Value.Table], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.tableValue() = this.convertTo<Value.Table>().value

/**
 * Converts this value to a [Value.List], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.listValue() = this.convertTo<Value.List>().value

/**
 * Converts this value to a [Value.Bytes], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.bytesValue() = this.convertTo<Value.Bytes>().value

operator fun MutableMap<Value, Value>.set(key: String, value: Value) {
    this[Value.String(key)] = value
}

operator fun MutableMap<Value, Value>.get(key: String): Value? {
    return this[Value.String(key)]
}

/**
 * Look up a successive sequence of strings in this value.
 *
 * @param keys The keys to look up.
 * @return The value, or null if it doesn't exist.
 */
fun Value.lookUpHierarchy(vararg keys: String): Value? {
    var value: Value = this
    for (key in keys) {
        value = value.lookUp(key.metisValue()) ?: return null
    }
    return value
}

/**
 * If this is a [Value.Native], returns the native object, otherwise throws an error.
 *
 * @param T The type to convert to.
 * @throws MetisRuntimeException If the value cannot be converted.
 */
inline fun <reified T> Value.asObj(): T {
    val value = convertTo<Value.Native>().value
    if (value is T) {
        return value
    }
    throw MetisRuntimeException(
        "TypeError",
        "Failed to unpack native object; expected ${T::class.qualifiedName}, got ${value::class.qualifiedName}"
    )
}

/**
 * Builds a [Value.Table]
 *
 * @param init The function to initialize the table.
 */
inline fun buildTable(init: (MutableMap<String, Value>) -> Unit): Value.Table {
    val map = mutableMapOf<String, Value>()
    init(map)
    return Value.Table(map.mapKeysTo(mutableMapOf()) { Value.String(it.key) }).also {
        if (it.metatable == null) {
            throw AssertionError("Null Table metatable on init; this shouldn't happen!")
        }
    }
}

/**
 * Converts a [Value]'s class to an end user-friendly name.
 *
 * @param clazz The class to convert.
 * @return The name of the class.
 */
fun typeToName(clazz: KClass<out Value>): String = when (clazz) {
    Value.Number::class -> "number"
    Value.String::class -> "string"
    Value.Boolean::class -> "boolean"
    Value.Table::class -> "table"
    Value.List::class -> "list"
    Value.Bytes::class -> "bytes"
    Value.Native::class -> "native"
    Value.Null::class -> "null"
    MetisRuntimeException::class -> "error"
    Coroutine::class -> "coroutine"
    else -> if (CallableValue::class.java.isAssignableFrom(clazz.java)) {
        "callable"
    } else {
        clazz.simpleName ?: "unknown"
    }
}

/**
 * Converts an [Int] to a [Value.Number]
 */
fun Int.metisValue() = Value.Number.of(this)

/**
 * Converts a [Double] to a [Value.Number]
 */
fun Double.metisValue() = Value.Number.of(this)

/**
 * Converts a [String] to a [Value.String]
 */
fun String.metisValue() = Value.String(this)

/**
 * Converts a [kotlin.Boolean] to a [Value.Boolean]
 */
fun Boolean.metisValue() = Value.Boolean.of(this)

/**
 * Converts a [Collection] of [Value]s to a [Value.List]
 */
fun Collection<Value>.metisValue() = Value.List(this.toMutableList())

/**
 * Converts a [ByteArray] to a [Value.Bytes]
 */
fun ByteArray.metisValue() = Value.Bytes(this)