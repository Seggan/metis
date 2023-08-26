package io.github.seggan.slimelang.runtime

class State {
    val globals = Value.Table(mutableMapOf())

    init {
        globals["_G"] = globals
        globals["true"] = Value.Boolean.TRUE
        globals["false"] = Value.Boolean.FALSE
        globals["null"] = Value.Null
    }
}

typealias Stack = ArrayDeque<Value>

fun Stack.push(value: Value) = this.addLast(value)
fun Stack.pop() = this.removeLast()
fun Stack.peek() = this.last()
fun Stack.getFromTop(index: Int) = this[this.size - index - 1]