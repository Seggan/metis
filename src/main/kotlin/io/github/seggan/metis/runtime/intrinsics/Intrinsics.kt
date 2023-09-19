package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.StepResult
import kotlin.collections.Map
import kotlin.collections.mutableMapOf
import kotlin.collections.set

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


/**
 * A function that is guaranteed to finish in one step. Optimization for this is implemented.
 */
open class OneShotFunction(override val arity: Arity, private val fn: State.(Int) -> Unit) : CallableValue {

    override var metatable: Value.Table? = null
    override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
        override fun step(state: State): StepResult {
            state.fn(nargs)
            return StepResult.FINISHED
        }
    }
}

class ZeroArgFunction(fn: () -> Value) : OneShotFunction(Arity.ZERO, { stack.push(fn()) })
class OneArgFunction(fn: (Value) -> Value) : OneShotFunction(Arity.ONE, { stack.push(fn(stack.pop())) })
class TwoArgFunction(fn: (Value, Value) -> Value) : OneShotFunction(Arity.TWO, {
    val b = stack.pop()
    val a = stack.pop()
    stack.push(fn(a, b))
})

class ThreeArgFunction(fn: (Value, Value, Value) -> Value) : OneShotFunction(Arity.THREE, {
    val c = stack.pop()
    val b = stack.pop()
    val a = stack.pop()
    stack.push(fn(a, b, c))
})