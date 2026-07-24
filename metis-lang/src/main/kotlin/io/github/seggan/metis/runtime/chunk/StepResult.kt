package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.value.CallableValue
import io.github.seggan.metis.runtime.value.Value

/**
 * The result of stepping through a [CallableValue.Executor]
 *
 * @see CallableValue.Executor
 */
sealed interface StepResult {

    /**
     * Continue execution.
     */
    data object Continue : StepResult

    /**
     * Execution has finished.
     */
    data class Finished(val result: Value) : StepResult

    /**
     * Execution has yielded.
     */
    data object Yielded : StepResult

    /**
     * A breakpoint has been hit.
     */
    data object Breakpoint : StepResult
}