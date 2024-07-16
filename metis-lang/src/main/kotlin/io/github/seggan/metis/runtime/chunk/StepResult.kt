package io.github.seggan.metis.runtime.chunk

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