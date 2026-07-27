package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.runtime.Interpreter
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
         * @param interpreter The state to execute in.
         * @return The result of the step.
         */
        fun step(interpreter: Interpreter): StepResult

        /**
         * Handles an error that occurred during execution.
         *
         * @param interpreter The state to execute in.
         * @param error The error that occurred.
         * @return Whether the error was handled.
         */
        fun handleError(interpreter: Interpreter, error: MetisRuntimeException): Boolean = false

        /**
         * Handles a `finally` block. Generally, you should use this to clean up resources.
         *
         * @param interpreter The state to execute in.
         * @return Whether the `finally` block was handled.
         */
        fun handleFinally(interpreter: Interpreter): Boolean = false
    }

    /**
     * Calls the function with the given arguments and creates an executor for it.
     *
     * @param args The arguments to call with.
     * @return The executor for the function.
     */
    fun call(args: List<Value>): Executor

    /**
     * The arity of the function.
     */
    val arity: Int
}

fun padArguments(callable: CallableValue, args: List<Value>): List<Value> {
    val args = args.toMutableList()
    while (args.size > callable.arity) {
        args.removeLast()
    }
    while (args.size < callable.arity) {
        args.add(MetisNull)
    }
    return args
}