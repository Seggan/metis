package io.github.seggan.metis.runtime

import kotlin.math.roundToInt

class State {
    val globals = Value.Table(mutableMapOf())

    val stack = Stack()

    private val callStack = ArrayDeque<Chunk>()
    private var currentExecutor: CallableValue.Executor? = null

    init {
        globals["true"] = Value.Boolean.TRUE
        globals["false"] = Value.Boolean.FALSE
        globals["null"] = Value.Null
    }

    fun loadChunk(chunk: Chunk) {
        callStack.addLast(chunk)
    }

    fun step(): Boolean {
        if (currentExecutor == null) {
            if (callStack.isEmpty()) {
                return true
            }
            val chunk = callStack.removeLast()
            currentExecutor = chunk.call()
        }
        val executor = currentExecutor!!
        if (executor.step(this)) {
            currentExecutor = null
        }
        return false
    }

    @Suppress("ControlFlowWithEmptyBody")
    fun runTillComplete() {
        while (!step()) {
        }
    }

    fun index() {
        val index = stack.pop()
        val value = stack.pop()
        stack.push(value.lookUp(index).orNull())
    }

    fun indexImm(key: String) {
        val value = stack.pop()
        stack.push(value.lookUp(Value.String(key)).orNull())
    }

    fun listIndexImm(key: Int) {
        val value = stack.pop()
        if (value is Value.Array) {
            stack.push(value.getOrNull(key) ?: Value.Null)
        } else {
            stack.push(value.lookUp(Value.Number(key.toDouble())).orNull())
        }
    }

    fun set() {
        val value = stack.pop()
        val index = stack.pop()
        val target = stack.pop()
        if (target is Value.Table) {
            target[index] = value
        } else if (target is Value.Array && index is Value.Number) {
            target[index.value.roundToInt()] = value
        } else {
            throw MetisRuntimeException("Cannot set index on non-table or non-array")
        }
    }

    fun setImm(key: String, allowNew: Boolean = true) {
        val value = stack.pop()
        val target = stack.pop()
        if (target is Value.Table) {
            val key = Value.String(key)
            if (allowNew || target.containsKey(key)) {
                target[key] = value
            } else {
                throw MetisRuntimeException("Cannot set index on non-table")
            }
        } else {
            throw MetisRuntimeException("Cannot set index on non-table")
        }
    }
}

typealias Stack = ArrayDeque<Value>

fun Stack.push(value: Value) = this.addLast(value)
fun Stack.pop() = this.removeLast()
fun Stack.peek() = this.last()
fun Stack.getFromTop(index: Int) = this[this.size - index - 1]