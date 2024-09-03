package io.github.seggan.metis.runtime.chunk

sealed interface StepResult {

    /**
     * Continue execution.
     */
    data object Continue : StepResult

    /**
     * Execution has finished.
     */
    data object Finished : StepResult

    /**
     * Execution has yielded.
     */
    data object Yielded : StepResult

    /**
     * A breakpoint has been hit.
     */
    data object Breakpoint : StepResult
}