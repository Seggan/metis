package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.Arity
import io.github.seggan.metis.runtime.CallableValue
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import kotlin.collections.set

object Intrinsics {

    private val _intrinsics = mutableMapOf<String, CallableValue>()

    val intrinsics: Map<String, CallableValue> get() = _intrinsics

    fun register(name: String, value: CallableValue) {
        _intrinsics[name] = value
    }

    fun registerDefault() {
        // TODO: intrinsics
    }
}


/**
 * A function that is guaranteed to finish in one step. Optimization for this is implemented.
 */
abstract class OneShotFunction(override val arity: Arity) : CallableValue {

    override var metatable: Value.Table? = null
    final override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
        override fun step(state: State): StepResult {
            execute(state, nargs)
            return StepResult.FINISHED
        }
    }

    abstract fun execute(state: State, nargs: Int)
}

inline fun zeroArgFunction(crossinline fn: () -> Value): OneShotFunction = object : OneShotFunction(Arity.ZERO) {
    override fun execute(state: State, nargs: Int) {
        state.stack.push(fn())
    }
}

inline fun oneArgFunction(crossinline fn: (Value) -> Value): OneShotFunction = object : OneShotFunction(Arity.ONE) {
    override fun execute(state: State, nargs: Int) {
        state.stack.push(fn(state.stack.pop()))
    }
}

inline fun twoArgFunction(crossinline fn: (Value, Value) -> Value): OneShotFunction =
    object : OneShotFunction(Arity.TWO) {
    override fun execute(state: State, nargs: Int) {
        val b = state.stack.pop()
        val a = state.stack.pop()
        state.stack.push(fn(a, b))
    }
}

inline fun threeArgFunction(crossinline fn: (Value, Value, Value) -> Value): OneShotFunction =
    object : OneShotFunction(Arity.THREE) {
    override fun execute(state: State, nargs: Int) {
        val c = state.stack.pop()
        val b = state.stack.pop()
        val a = state.stack.pop()
        state.stack.push(fn(a, b, c))
    }
}