package io.github.seggan.metis.runtime.value

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.StepResult
import java.io.Serializable

interface CallableValue : Value {

    val arity: Arity

    fun call(nargs: Int): Executor

    fun interface Executor : Serializable {
        fun State.step(): StepResult
    }

    data class Arity(val nargs: Int, val needsSelf: Boolean)
}