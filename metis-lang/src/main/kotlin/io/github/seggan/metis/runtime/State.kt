package io.github.seggan.metis.runtime

import io.github.seggan.metis.debug.Breakpoint
import io.github.seggan.metis.debug.DebugInfo
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.value.*
import io.github.seggan.metis.util.Stack
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

class State {

    val stack = Stack()

    internal val callStack = ArrayDeque<CallFrame>()
    val stackBottom get() = callStack.peek().stackBottom

    val globals = TableValue()

    var debugInfo: DebugInfo? = null
    var debugMode = false
    val breakpoints = mutableListOf<Breakpoint>()

    fun loadCoreGlobals() {
        // TODO
    }

    fun loadStandardLibrary() {
        // TODO
    }

    fun stepOnce(): StepResult {
        if (callStack.isEmpty()) return StepResult.Finished
        val result = callStack.peek().executor.step(this)
        if (result is StepResult.Finished) {
            callStack.pop()
            return if (callStack.isEmpty()) StepResult.Finished else StepResult.Continue
        }
        return result
    }

    fun runTillComplete() {
        while (stepOnce() != StepResult.Finished) {
            // side effects go brr
        }
    }

    fun call(nargs: Int, selfProvided: Boolean = false, span: Span? = null) {
        val callable = stack.pop().convertTo<CallableValue>()
        callStack.push(CallFrame(callable.call(), stack.size, span))
    }

    fun metaCall(nargs: Int, metamethod: String) {
        TODO()
    }

    fun not() {
        stack[0] = stack[0].convertTo<BooleanValue>().value.not().metis()
    }
}

internal data class CallFrame(
    val executor: CallableValue.Executor,
    val stackBottom: Int,
    val span: Span?
)