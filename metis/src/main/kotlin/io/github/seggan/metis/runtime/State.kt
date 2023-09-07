package io.github.seggan.metis.runtime

import io.github.seggan.metis.MetisException
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.intrinsics.Intrinsics
import io.github.seggan.metis.runtime.intrinsics.wrapOutStream
import io.github.seggan.metis.runtime.values.*
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class State(val isChildState: Boolean = false) {

    val globals = Value.Table(mutableMapOf())

    val stack = Stack()

    private val callStack = ArrayDeque<CallFrame>()

    var stdout: OutputStream = System.out
    var stderr: OutputStream = System.err
    var stdin: InputStream = System.`in`

    val localsOffset: Int
        get() = callStack.peek().stackBottom

    var debugMode = false

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

        val io = Value.Table(mutableMapOf())
        io["stdout"] = wrapOutStream(this, stdout)
        io["stderr"] = wrapOutStream(this, stderr)

        globals["io"] = io
    }

    fun loadChunk(chunk: Chunk) {
        stack.push(chunk)
    }

    fun step(): StepResult {
        if (callStack.isEmpty()) return StepResult.FINISHED
        try {
            if (callStack.peek().executing.step(this) == StepResult.FINISHED) {
                callStack.removeLast()
            }
        } catch (e: MetisException) {
            if (e.span == null) {
                e.span = callStack.peek().span
            }
            throw e
        }
        if (debugMode) {
            println(stack)
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
        if (value is Value.List) {
            stack.push(value.getOrNull(key) ?: Value.Null)
        } else {
            stack.push(value.lookUp(Value.Number.from(key.toDouble())).orNull())
        }
    }

    fun set() {
        val value = stack.pop()
        val index = stack.pop()
        val target = stack.pop()
        if (target is Value.Table) {
            target[index] = value
        } else if (target is Value.List && index is Value.Number) {
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

    fun call(nargs: Int, span: Span? = null) {
        val callable = stack.pop()
        if (callable is CallableValue) {
            callValue(callable, nargs, span)
        } else {
            val possiblyCallable = callable.lookUp(Value.String("__call__"))
            if (possiblyCallable is CallableValue) {
                callValue(possiblyCallable, nargs, span)
            } else {
                throw MetisRuntimeException("Cannot call non-callable")
            }
        }
    }

    private fun callValue(value: CallableValue, nargs: Int, span: Span? = null) {
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
        callStack.push(CallFrame(value.call(argc), stackBottom, span))
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

private data class CallFrame(val executing: CallableValue.Executor, val stackBottom: Int, val span: Span?)

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

fun Stack.push(value: Double) = this.push(Value.Number.from(value))
fun Stack.push(value: String) = this.push(Value.String(value))
fun Stack.push(value: Boolean) = this.push(Value.Boolean.from(value))
fun Stack.push(value: Nothing?) = this.push(Value.Null)

inline fun <reified T : Value> Stack.popAs(): T = this.pop().convertTo()