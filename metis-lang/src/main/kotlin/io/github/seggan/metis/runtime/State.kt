package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.value.*
import io.github.seggan.metis.util.Stack
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

class State {

    val stack = Stack()

    private val callStack = ArrayDeque<CallFrame>()
    val stackBottom get() = callStack.peek().stackBottom

    val globals = TableValue()

    fun loadCoreGlobals() {
        // TODO
    }

    fun loadStandardLibrary() {
        // TODO
    }

    fun loadChunk(chunk: Chunk) {
        stack.push(chunk)
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

    fun call(nargs: Int, selfProvided: Boolean = false) {
        val callable = stack.pop().convertTo<CallableValue>()
        callStack.push(CallFrame(callable.call(), stack.size))
    }

    fun not() {
        stack[0] = stack[0].convertTo<BooleanValue>().value.not().metis()
    }
}

private data class CallFrame(val executor: CallableValue.Executor, val stackBottom: Int)