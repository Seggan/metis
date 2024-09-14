@file:Suppress("serial")

package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.value.CallableValue
import io.github.seggan.metis.runtime.value.TableValue
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.runtime.value.metis
import io.github.seggan.metis.util.pop

inline fun nArgFunction(
    nargs: Int,
    needsSelf: Boolean = false,
    crossinline block: suspend NativeScope.(List<Value>) -> Value?
): CallableValue = object : CallableValue {
    override var metatable: TableValue? = mapOf("__str__" to "nArgFunction($nargs)".metis()).metis()
    override val arity = CallableValue.Arity(nargs, needsSelf)
    override fun call() = object : SuspendingExecutor() {
        override suspend fun NativeScope.execute(): Value? {
            val args = MutableList(nargs) { state.stack.pop() }
            args.reverse()
            return block(args)
        }
    }
}

inline fun zeroArgFunction(crossinline block: suspend NativeScope.() -> Value?): CallableValue =
    object : CallableValue {
        override var metatable: TableValue? = mapOf("__str__" to "zeroArgFunction".metis()).metis()
    override val arity = CallableValue.Arity(0, false)
    override fun call() = object : SuspendingExecutor() {
        override suspend fun NativeScope.execute(): Value? = block()
    }
}

inline fun oneArgFunction(
    needsSelf: Boolean = false,
    crossinline block: suspend NativeScope.(Value) -> Value?
): CallableValue = object : CallableValue {
    override var metatable: TableValue? = mapOf("__str__" to "oneArgFunction".metis()).metis()
    override val arity = CallableValue.Arity(1, needsSelf)
    override fun call() = object : SuspendingExecutor() {
        override suspend fun NativeScope.execute(): Value? = block(state.stack.pop())
    }
}

inline fun twoArgFunction(
    needsSelf: Boolean = false,
    crossinline block: suspend NativeScope.(Value, Value) -> Value?
): CallableValue = object : CallableValue {
    override var metatable: TableValue? = mapOf("__str__" to "twoArgFunction".metis()).metis()
    override val arity = CallableValue.Arity(2, needsSelf)
    override fun call() = object : SuspendingExecutor() {
        override suspend fun NativeScope.execute(): Value? {
            val arg2 = state.stack.pop()
            val arg1 = state.stack.pop()
            return block(arg1, arg2)
        }
    }
}

inline fun threeArgFunction(
    needsSelf: Boolean = false,
    crossinline block: suspend NativeScope.(Value, Value, Value) -> Value?
): CallableValue = object : CallableValue {
    override var metatable: TableValue? = mapOf("__str__" to "threeArgFunction".metis()).metis()
    override val arity = CallableValue.Arity(3, needsSelf)
    override fun call() = object : SuspendingExecutor() {
        override suspend fun NativeScope.execute(): Value? {
            val arg3 = state.stack.pop()
            val arg2 = state.stack.pop()
            val arg1 = state.stack.pop()
            return block(arg1, arg2, arg3)
        }
    }
}

inline fun fourArgFunction(
    needsSelf: Boolean = false,
    crossinline block: suspend NativeScope.(Value, Value, Value, Value) -> Value?
): CallableValue = object : CallableValue {
    override var metatable: TableValue? = mapOf("__str__" to "fourArgFunction".metis()).metis()
    override val arity = CallableValue.Arity(4, needsSelf)
    override fun call() = object : SuspendingExecutor() {
        override suspend fun NativeScope.execute(): Value? {
            val arg4 = state.stack.pop()
            val arg3 = state.stack.pop()
            val arg2 = state.stack.pop()
            val arg1 = state.stack.pop()
            return block(arg1, arg2, arg3, arg4)
        }
    }
}