package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*

object Intrinsics {

    private val _intrinsics = mutableMapOf<String, CallableValue>()

    val intrinsics: Map<String, CallableValue> get() = _intrinsics

    fun register(name: String, value: CallableValue) {
        _intrinsics[name] = value
    }

    fun register(name: String, arity: Arity, fn: State.(Int) -> Unit) {
        _intrinsics[name] = OneShotFunction(arity, fn)
    }

    fun registerDefault() {
        register("print", Arity.ONE) {
            val value = stack.pop()
            println(value)
            stack.push(null)
        }
    }
}

class OneShotFunction(override val arity: Arity, val fn: State.(Int) -> Unit) : CallableValue {
    override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
        override fun step(state: State): StepResult {
            state.fn(nargs)
            return StepResult.FINISHED
        }
    }

    override var metatable: Value.Table? = null
}