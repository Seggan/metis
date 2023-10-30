package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.chunk.StepResult

/**
 * A [Value] that can be called.
 */
interface CallableValue : Value {

    /**
     * An executor for a [CallableValue]. To help with sandboxing, the executor may take
     * multiple steps to execute the function.
     */
    interface Executor {

        /**
         * Executes the next step of the function.
         *
         * @param state The state to execute in.
         * @return The result of the step.
         */
        fun step(state: State): StepResult

        /**
         * Handles an error that occurred during execution.
         *
         * @param state The state to execute in.
         * @param error The error that occurred.
         * @return Whether the error was handled.
         */
        fun handleError(state: State, error: MetisRuntimeException): Boolean = false

        /**
         * Handles a `finally` block. Generally, you should use this to clean up resources.
         *
         * @param state The state to execute in.
         * @return Whether the `finally` block was handled.
         */
        fun handleFinally(state: State): Boolean = false
    }

    /**
     * Calls the function with the given number of arguments and creates an executor for it.
     *
     * @param nargs The number of arguments to call with.
     * @return The executor for the function.
     */
    fun call(nargs: Int): Executor

    /**
     * The arity of the function.
     */
    val arity: Arity
}

/**
 * The argument information for a [CallableValue].
 *
 * @property required The number of arguments the function requires.
 * @property requiresSelf Whether the function requires a `self` argument.
 * @property isVarargs Whether the function takes a variable number of arguments on top of the
 *  required ones. Currently not implemented.
 */
data class Arity(val required: Int, val requiresSelf: Boolean = false, val isVarargs: Boolean = false) {
    companion object {
        val ZERO = Arity(0)
        val ONE = Arity(1)
        val TWO = Arity(2)
        val THREE = Arity(3)
        val VARARGS = Arity(0, true)
    }

    fun requiresSelf() = Arity(required, true, isVarargs)
}