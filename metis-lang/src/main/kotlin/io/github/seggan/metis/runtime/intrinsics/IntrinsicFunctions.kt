@file:JvmName("IntrinsicFunctions")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.util.MutableLazy
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.InvalidPathException


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

/**
 * Translates an [IOException] into a [MetisRuntimeException].
 *
 * @param block The block to execute.
 */
inline fun <T> translateIoError(block: () -> T): T {
    val message = try {
        null to block()
    } catch (e: InvalidPathException) {
        "Invalid path: ${e.message}" to null
    } catch (e: FileNotFoundException) {
        "File not found: ${e.message}" to null
    } catch (e: NoSuchFileException) {
        "File not found: ${e.message}" to null
    } catch (e: FileAlreadyExistsException) {
        "File already exists: ${e.message}" to null
    } catch (e: SecurityException) {
        "Permission denied: ${e.message}" to null
    } catch (e: IOException) {
        e.message to null
    }
    if (message.first != null) {
        throw MetisRuntimeException("IoError", message.first!!)
    } else {
        return message.second!!
    }
}