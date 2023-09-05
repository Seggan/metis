package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.intrinsics.Intrinsics
import kotlin.math.roundToInt

class State(val isChildState: Boolean = false) {

    val globals = Value.Table(mutableMapOf())

    val stack = Stack()

    private val callStack = ArrayDeque<CallFrame>()

    companion object {
        init {
            Intrinsics.registerDefault()
        }
    }

    init {
        globals["true"] = Value.Boolean.TRUE
        globals["false"] = Value.Boolean.FALSE
        globals["null"] = Value.Null

        for ((name, value) in Intrinsics.intrinsics) {
            globals[name] = value
        }
    }

    fun loadChunk(chunk: Chunk) {
        stack.push(chunk)
    }

    fun step(): StepResult {
        if (callStack.isEmpty()) return StepResult.FINISHED
        if (callStack.peek().executing.step(this) == StepResult.FINISHED) {
            callStack.removeLast()
        }
        return StepResult.CONTINUE
    }

    @Suppress("ControlFlowWithEmptyBody")
    fun runTillComplete() {
        while (step() != StepResult.FINISHED) {
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

    fun call(nargs: Int) {
        val callable = stack.pop()
        if (callable is CallableValue) {
            callValue(callable, nargs)
        } else {
            val possiblyCallable = callable.lookUp(Value.String("__call__"))
            if (possiblyCallable is CallableValue) {
                callValue(possiblyCallable, nargs)
            } else {
                throw MetisRuntimeException("Cannot call non-callable")
            }
        }
    }

    private fun callValue(value: CallableValue, nargs: Int) {
        val stackBottom = stack.size - nargs
        val (reqArgs, isVarargs) = value.arity
        var argc = nargs
        while (argc < reqArgs) {
            stack.push(Value.Null)
            argc++
        }
        if (!isVarargs) {
            while (argc > reqArgs) {
                stack.pop()
                argc--
            }
        }
        callStack.push(CallFrame(value.call(argc), stackBottom))
    }

    fun unwindStack() {
        while (stack.size > callStack.peek().stackBottom) {
            stack.pop()
        }
    }
}

fun State.callGlobal(name: String, nargs: Int) {
    stack.push(globals[name].orNull())
    call(nargs)
}

private data class CallFrame(val executing: CallableValue.Executor, val stackBottom: Int)

typealias Stack = ArrayDeque<Value>

fun <E> ArrayDeque<E>.push(value: E) = this.addLast(value)
fun <E> ArrayDeque<E>.pop() = this.removeLast()
fun <E> ArrayDeque<E>.popn(n: Int): List<E> {
    val list = ArrayDeque<E>(n)
    repeat(n) { list.addFirst(this.removeLast()) }
    return list
}

fun <E> ArrayDeque<E>.peek() = this.last()
fun <E> ArrayDeque<E>.getFromTop(index: Int): E = this[this.size - index - 1]