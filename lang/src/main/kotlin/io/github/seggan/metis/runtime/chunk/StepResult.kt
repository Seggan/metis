package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.CallableValue

/**
 * The result of stepping through a [CallableValue.Executor]
 *
 * @see CallableValue.Executor
 */
enum class StepResult {
    /**
     * Continue execution.
     */
    CONTINUE,

    /**
     * Execution has finished.
     */
    FINISHED,

    /**
     * Execution has yielded.
     */
    YIELDED,

    /**
     * A breakpoint has been hit.
     */
    BREAKPOINT
}