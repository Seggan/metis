package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.ChunkInstance
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.value.CallableValue
import io.github.seggan.metis.runtime.value.MetisNull
import io.github.seggan.metis.runtime.value.MetisTable
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

class State {

    val globals = MetisTable()

    private val callStack = ArrayDeque<CallableValue.Executor>()

    var returnValue: Value? = null

    fun loadChunk(chunk: Chunk) {
        call(ChunkInstance(chunk), emptyList())
    }

    fun call(callable: CallableValue, args: List<Value>) {
        val executor = callable.call(args)
        callStack.push(executor)
    }

    fun step(): StepResult {
        if (callStack.isEmpty()) return StepResult.Finished(MetisNull)
        val result = callStack.peek().step(this)
        if (result is StepResult.Finished) {
            returnValue = result.result
            callStack.pop()
        }
        return result
    }
}