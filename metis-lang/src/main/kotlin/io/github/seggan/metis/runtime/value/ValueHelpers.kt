@file:JvmName("ValueHelpers")

package io.github.seggan.metis.runtime.value

import kotlin.reflect.KClass


/**
 * Look up a value in this value, possibly using the metatable.
 *
 * @param key The key to look up.
 * @return The value, or null if it doesn't exist.
 */
fun Value.lookUp(key: Value): Value? {
    if (this === metatable) return lookUpDirect(key)
    return lookUpDirect(key) ?: metatable.lookUp(key)
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
    return setDirect(key, value) || (metatable as? Value)?.set(key, value) ?: false
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
 * If this value is null, return [MetisNull], otherwise return this value.
 */
fun Value?.orNull() = this ?: MetisNull

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
    throw MetisTypeError("Cannot convert ${typeToName(this::class)} to ${typeToName(T::class)}")
}

/**
 * Converts this value to a [Int], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.intValue() = this.convertTo<MetisNumber>().value.toInt()

/**
 * Converts this value to a [Double], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.doubleValue() = this.convertTo<MetisNumber>().value

/**
 * Converts this value to a [MetisString], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.stringValue() = this.convertTo<MetisString>().value

/**
 * Converts this value to a [Boolean], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.booleanValue() = this.convertTo<MetisBoolean>().value

/**
 * Converts this value to a [MetisTable], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.tableValue() = this.convertTo<MetisTable>().value

/**
 * Converts this value to a [MetisList], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.listValue() = this.convertTo<MetisList>().value

/**
 * Converts this value to a [MetisBytes], or throws an error if it cannot be converted.
 *
 * @throws MetisRuntimeException If the value cannot be converted.
 */
fun Value.bytesValue() = this.convertTo<MetisBytes>().value

operator fun MutableMap<Value, Value>.set(key: String, value: Value) {
    this[MetisString(key)] = value
}

operator fun MutableMap<Value, Value>.get(key: String): Value? {
    return this[MetisString(key)]
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
 * Builds a [MetisTable]
 *
 * @param init The function to initialize the table.
 */
inline fun buildTable(init: MutableMap<String, Value>.() -> Unit): MetisTable {
    val map = mutableMapOf<String, Value>()
    init(map)
    return MetisTable(map.mapKeysTo(mutableMapOf()) { MetisString(it.key) }).also {
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
    MetisNumber::class -> "number"
    MetisString::class -> "string"
    MetisBoolean::class -> "boolean"
    MetisTable::class -> "table"
    MetisList::class -> "list"
    MetisBytes::class -> "bytes"
    MetisNull::class -> "null"
    MetisRuntimeException::class -> "error"
    else if (CallableValue::class.java.isAssignableFrom(clazz.java)) -> "callable"
    else -> clazz.simpleName ?: "unknown"
}

/**
 * Converts an [Int] to a [MetisNumber]
 */
fun Int.metisValue() = MetisNumber.of(this)

/**
 * Converts a [Double] to a [MetisNumber]
 */
fun Double.metisValue() = MetisNumber.of(this)

/**
 * Converts a [MetisString] to a [MetisString]
 */
fun String.metisValue() = MetisString(this)

/**
 * Converts a [Boolean] to a [MetisBoolean]
 */
fun Boolean.metisValue() = MetisBoolean.of(this)

/**
 * Converts a [Collection] of [Value]s to a [MetisList]
 */
fun Collection<Value>.metisValue() = MetisList(this.toMutableList())

/**
 * Converts a [ByteArray] to a [MetisBytes]
 */
fun ByteArray.metisValue() = MetisBytes(this)

/**
 * Converts null to [MetisNull]
 */
@Suppress("UnusedReceiverParameter")
fun Nothing?.metisValue() = MetisNull