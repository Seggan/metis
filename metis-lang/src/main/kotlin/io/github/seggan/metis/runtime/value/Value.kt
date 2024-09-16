package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.runtime.intrinsics.twoArgFunction
import java.io.Serial
import java.io.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass

sealed interface Value : Serializable {

    var metatable: TableValue?

    fun getDirect(key: Value): Value? = null
    fun setDirect(key: Value, value: Value): Boolean = false

    object Null : Value {

        override var metatable: TableValue? = buildTable { table ->
            table[Metamethod.TO_STRING] = oneArgFunction(true) { selfString }
            table.useReferentialEquality()
        }

        private val selfString = "null".metis()

        @Serial
        private const val serialVersionUID: Long = 3286383784003129982L

        private fun readResolve(): Any = Null
        override fun toString(): String = "null"
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <reified T : Value> Value.convertTo(): T {
    contract {
        returns() implies (this@convertTo is T)
    }
    if (this is T) return this
    throw MetisTypeError(metisTypeName(T::class), metisTypeName(this::class))
}

fun Value.getInHierarchy(key: Value): Value? {
    if (this === metatable) return getDirect(key)
    return getDirect(key) ?: metatable?.getInHierarchy(key)
}

fun Value.orNull(): Value? = if (this === Value.Null) null else this

internal fun TableValue.useNativeToString() {
    this[Metamethod.TO_STRING] = oneArgFunction(true) { it.toString().metis() }
}

internal fun TableValue.useReferentialEquality() {
    this[Metamethod.EQUALS] = twoArgFunction(true) { self, other ->
        (self === other).metis()
    }
}

internal fun TableValue.useNativeEquality() {
    this[Metamethod.EQUALS] = twoArgFunction(true) { self, other ->
        (self == other).metis()
    }
}

fun metisTypeName(clazz: KClass<out Value>): String = when (clazz) {
    NumberValue.Int::class -> "int"
    NumberValue.Float::class -> "float"
    StringValue::class -> "string"
    TableValue::class -> "table"
    BooleanValue::class -> "boolean"
    Value.Null::class -> "null"
    MetisRuntimeException::class -> "error"
    else -> if (CallableValue::class.java.isAssignableFrom(clazz.java)) "function"
    else clazz.simpleName ?: "unknown"
}