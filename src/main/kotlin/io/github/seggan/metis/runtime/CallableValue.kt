package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.chunk.StepResult

interface CallableValue : Value {

    interface Executor {

        fun step(state: State): StepResult

        fun handleError(state: State, error: MetisRuntimeException): Boolean = false
    }

    fun call(nargs: Int): Executor

    val arity: Arity
}

data class Arity(val required: Int, val isVarargs: Boolean = false) {
    companion object {
        val ZERO = Arity(0)
        val ONE = Arity(1)
        val TWO = Arity(2)
        val THREE = Arity(3)
        val VARARGS = Arity(0, true)
    }
}