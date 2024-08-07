package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.util.push
import kotlin.collections.set

/**
 * An instance of a coroutine.
 *
 * @param state The parent state of the coroutine.
 * @param chunk The chunk to run.
 * @param args The arguments to pass to the chunk.
 */
class Coroutine(state: State, chunk: Chunk.Instance, args: List<Value>) : Value {

    private val innerState = State(state)

    override var metatable: Value.Table? = Companion.metatable.also { it["globals"] = innerState.globals }

    private var lastResult = StepResult.CONTINUE
    private var lastYielded: Value = Value.Null

    init {
        for (arg in args) {
            innerState.stack.push(arg)
        }
        innerState.stack.push(chunk)
        innerState.call(args.size)
    }

    companion object {

        /**
         * The global metatable for coroutines.
         */
        val metatable = buildTable { table ->
            table["__str__"] = oneArgFunction(true) { _ ->
                "a coroutine".metisValue()
            }
            table["__eq__"] = twoArgFunction(true) { self, other ->
                Value.Boolean.of(self === other)
            }
            table["__contains__"] = twoArgFunction(true) { self, key ->
                Value.Boolean.of(self.lookUp(key) != null)
            }

            table["step"] = oneArgFunction(true) { self ->
                val coroutine = self.convertTo<Coroutine>()
                if (coroutine.lastResult == StepResult.FINISHED) {
                    "finished".metisValue()
                } else {
                    val result = coroutine.innerState.step()
                    coroutine.lastResult = result
                    if (result == StepResult.YIELDED) {
                        coroutine.lastYielded = coroutine.innerState.yieldComm
                        coroutine.innerState.yieldComm = Value.Null
                    }
                    result.name.lowercase().metisValue()
                }
            }
            table["lastResult"] = oneArgFunction(true) { self ->
                self.convertTo<Coroutine>().lastResult.name.lowercase().metisValue()
            }
            table["lastYielded"] = oneArgFunction(true) { self ->
                self.convertTo<Coroutine>().lastYielded
            }

            table["create"] = twoArgFunction { fn, args ->
                Coroutine(
                    this,
                    fn.convertTo<Chunk.Instance>(),
                    if (args == Value.Null) Value.List() else args.listValue()
                )
            }
        }
    }
}