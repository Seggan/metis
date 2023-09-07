package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.StepResult
import io.github.seggan.metis.runtime.pop
import io.github.seggan.metis.runtime.push
import io.github.seggan.metis.runtime.values.Arity
import io.github.seggan.metis.runtime.values.CallableValue
import io.github.seggan.metis.runtime.values.Value

object Intrinsics {

    private val _intrinsics = mutableMapOf<String, CallableValue>()

    val intrinsics: Map<String, CallableValue> get() = _intrinsics

    fun register(name: String, value: CallableValue) {
        _intrinsics[name] = value
    }

    fun registerOneShot(name: String, arity: Arity, fn: State.(Int) -> Unit) {
        _intrinsics[name] = OneShotFunction(arity, fn)
    }

    fun registerOneArg(name: String, fn: (Value) -> Value) {
        _intrinsics[name] = OneArgFunction(fn)
    }

    fun registerDefault() {
        // TODO: intrinsics
    }
}

class OneShotFunction(override val arity: Arity, private val fn: State.(Int) -> Unit) : CallableValue {

    override var metatable: Value.Table? = null
    override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
        override fun step(state: State): StepResult {
            state.fn(nargs)
            return StepResult.FINISHED
        }
    }
}

class OneArgFunction(private val fn: (Value) -> Value) : CallableValue {

    override val arity = Arity.ONE

    override var metatable: Value.Table? = null

    override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
        override fun step(state: State): StepResult {
            state.stack.push(fn(state.stack.pop()))
            return StepResult.FINISHED
        }
    }
}

class TwoArgFunction(private val fn: (Value, Value) -> Value) : CallableValue {

    override val arity = Arity(2)

    override var metatable: Value.Table? = null

    override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
        override fun step(state: State): StepResult {
            val arg2 = state.stack.pop()
            val arg1 = state.stack.pop()
            state.stack.push(fn(arg1, arg2))
            return StepResult.FINISHED
        }
    }
}