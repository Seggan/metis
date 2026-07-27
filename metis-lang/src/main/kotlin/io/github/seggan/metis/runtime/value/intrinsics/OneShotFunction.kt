package io.github.seggan.metis.runtime.value.intrinsics

import io.github.seggan.metis.runtime.Interpreter
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.value.CallableValue
import io.github.seggan.metis.runtime.value.MetisTable
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.util.MutableLazy

abstract class OneShotFunction(override val arity: Int) : CallableValue {

    override var metatable by MutableLazy { MetisTable() }

    override fun call(args: List<Value>) = object : CallableValue.Executor {
        override fun step(interpreter: Interpreter): StepResult {
            return StepResult.Finished(interpreter.execute(args))
        }
    }

    abstract fun Interpreter.execute(args: List<Value>): Value
}

inline fun oneShotFunction(arity: Int, crossinline func: Interpreter.(List<Value>) -> Value) =
    object : OneShotFunction(arity) {
        override fun Interpreter.execute(args: List<Value>) = func(args)
    }

inline fun oneShotFunction(crossinline func: Interpreter.(Value) -> Value) =
    oneShotFunction(1) { (value) -> func(value) }

inline fun oneShotFunction(crossinline func: Interpreter.(Value, Value) -> Value) =
    oneShotFunction(2) { (first, second) -> func(first, second) }

inline fun oneShotFunction(crossinline func: Interpreter.(Value, Value, Value) -> Value) =
    oneShotFunction(3) { (first, second, third) -> func(first, second, third) }