package io.github.seggan.slimelang.runtime

sealed interface Value {
    data class Number(val value: Double) : Value
    data class String(val value: kotlin.String) : Value
    data class Boolean(val value: kotlin.Boolean) : Value
    data class Table(val tablePart: MutableMap<Value, Value>, val arrayPart: MutableList<Value>) : Value
    data class Function(val value: (List<Value>) -> Value) : Value
    data object Null : Value
}