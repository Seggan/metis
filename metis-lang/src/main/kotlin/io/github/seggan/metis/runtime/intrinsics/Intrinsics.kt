@file:JvmName("Intrinsics")

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
        _intrinsics["loadChunk"] = twoArgFunction { name, chunk ->
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