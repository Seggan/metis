package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.StepResult

interface NativeScope {

    val state: State

    suspend fun stepWith(result: StepResult)
}