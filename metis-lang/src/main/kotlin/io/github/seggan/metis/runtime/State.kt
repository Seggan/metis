package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.value.CallableValue
import io.github.seggan.metis.util.Stack
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop

class State {

    val stack = Stack()

    private val callStack = ArrayDeque<CallableValue.Executor>()

    fun loadCoreGlobals() {
        // TODO
    }

    fun loadStandardLibrary() {
        // TODO
    }

    fun loadChunk(chunk: Chunk) {
        // TODO
    }

    fun stepOnce(): StepResult {
        val result = with(callStack.peek()) { step() }
        if (result is StepResult.Finished) {
            callStack.pop()
            return if (callStack.isEmpty()) StepResult.Finished else StepResult.Continue
        }
        return result
    }
}