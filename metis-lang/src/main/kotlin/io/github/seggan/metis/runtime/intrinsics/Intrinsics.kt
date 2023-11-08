package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.util.MutableLazy
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import kotlin.collections.set

/**
 * An object which contains all the global intrinsic functions.
 */
object Intrinsics {

    private val _intrinsics = mutableMapOf<String, CallableValue>()

    /**
     * All registered intrinsic functions.
     */
    val intrinsics: Map<String, CallableValue> get() = _intrinsics

    fun register(name: String, value: CallableValue) {
        _intrinsics[name] = value
    }

    internal fun registerDefault() {
        _intrinsics["load_chunk"] = twoArgFunction { name, chunk ->
            val source = CodeSource.constant(name.stringValue(), chunk.stringValue())
            Chunk.load(source).Instance(this)
        }
        _intrinsics["type"] = oneArgFunction { value ->
            typeToName(value::class).metisValue()
        }
        _intrinsics["globals"] = zeroArgFunction { globals }
        _intrinsics["yield"] = object : CallableValue {
            override var metatable: Value.Table? by MutableLazy {
                Value.Table(mutableMapOf("__str__".metisValue() to oneArgFunction { "OneShot".metisValue() }))
            }
            override val arity = Arity.ONE
            override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
                private var yielded = false
                override fun step(state: State): StepResult {
                    return if (yielded) {
                        state.stack.push(state.yieldComm)
                        StepResult.FINISHED
                    } else {
                        yielded = true
                        state.yieldComm = state.stack.pop()
                        StepResult.YIELDED
                    }
                }
            }
        }
    }
}


/**
 * A function that is guaranteed to finish in one step. Optimization for this is implemented.
 *
 * @param arity The arity of the function.
 */
abstract class OneShotFunction(override val arity: Arity) : CallableValue {

    override var metatable: Value.Table? by MutableLazy {
        Value.Table(mutableMapOf("__str__".metisValue() to oneArgFunction { "OneShot".metisValue() }))
    }

    final override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
        override fun step(state: State): StepResult {
            execute(state, nargs)
            return StepResult.FINISHED
        }
    }

    abstract fun execute(state: State, nargs: Int)
}

/**
 * Creates a [OneShotFunction] with zero arguments.
 *
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun zeroArgFunction(crossinline fn: State.() -> Value): OneShotFunction = object : OneShotFunction(Arity.ZERO) {
    override fun execute(state: State, nargs: Int) {
        state.stack.push(state.fn())
    }
}

/**
 * Creates a [OneShotFunction] with one argument.
 *
 * @param requiresSelf Whether the function requires a `self` argument.
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun oneArgFunction(
    requiresSelf: Boolean = false,
    crossinline fn: State.(Value) -> Value
): OneShotFunction = object : OneShotFunction(Arity(1, requiresSelf)) {
    override fun execute(state: State, nargs: Int) {
        state.stack.push(state.fn(state.stack.pop()))
    }
}

/**
 * Creates a [OneShotFunction] with two arguments.
 *
 * @param requiresSelf Whether the function requires a `self` argument.
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun twoArgFunction(
    requiresSelf: Boolean = false,
    crossinline fn: State.(Value, Value) -> Value
): OneShotFunction = object : OneShotFunction(Arity(2, requiresSelf)) {
    override fun execute(state: State, nargs: Int) {
        val b = state.stack.pop()
        val a = state.stack.pop()
        state.stack.push(state.fn(a, b))
    }
}

/**
 * Creates a [OneShotFunction] with three arguments.
 *
 * @param requiresSelf Whether the function requires a `self` argument.
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun threeArgFunction(
    requiresSelf: Boolean = false,
    crossinline fn: State.(Value, Value, Value) -> Value
): OneShotFunction = object : OneShotFunction(Arity(3, requiresSelf)) {
    override fun execute(state: State, nargs: Int) {
        val c = state.stack.pop()
        val b = state.stack.pop()
        val a = state.stack.pop()
        state.stack.push(state.fn(a, b, c))
    }
}

/**
 * Creates a [OneShotFunction] with four arguments.
 *
 * @param requiresSelf Whether the function requires a `self` argument.
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun fourArgFunction(
    requiresSelf: Boolean = false,
    crossinline fn: State.(Value, Value, Value, Value) -> Value
): OneShotFunction = object : OneShotFunction(Arity(4, requiresSelf)) {
    override fun execute(state: State, nargs: Int) {
        val d = state.stack.pop()
        val c = state.stack.pop()
        val b = state.stack.pop()
        val a = state.stack.pop()
        state.stack.push(state.fn(a, b, c, d))
    }
}