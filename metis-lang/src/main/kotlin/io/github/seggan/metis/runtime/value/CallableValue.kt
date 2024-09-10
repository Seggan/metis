package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.StepResult
import java.io.Serializable

interface CallableValue : Value {

    val arity: Arity

    fun call(): Executor

    fun interface Executor : Serializable {
        fun step(state: State): StepResult
    }

    data class Arity(val nargs: Int, val needsSelf: Boolean)
}