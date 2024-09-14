package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.runtime.value.stringValue
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

interface NativeScope {

    val state: State

    suspend fun stepWith(result: StepResult)

    suspend fun yield() = stepWith(StepResult.Yielded)

    suspend fun Value.metisToString(): String {
        state.stack.push(this)
        state.metaCall(0, "__str__")
        stepWith(StepResult.Continue)
        return state.stack.pop().stringValue
    }
}