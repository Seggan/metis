package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.util.push
import kotlin.collections.set

class Coroutine(globals: Value.Table? = null, chunk: Chunk.Instance, args: Value.List) : Value {

    private val innerState = State(globals)

    override var metatable: Value.Table? = Companion.metatable.also { it["globals"] = innerState.globals }

    private var lastResult = StepResult.CONTINUE
    private var lastYielded: Value = Value.Null

    init {
        if (chunk.upvalues.any { it.value == null }) {
            throw MetisRuntimeException("ValueError", "Cannot create a coroutine with any open upvalues")
        }
        for (arg in args) {
            innerState.stack.push(arg)
        }
        innerState.stack.push(chunk)
        innerState.call(args.size)
    }

    companion object {
        val metatable = buildTable { table ->
            table["__str__"] = oneArgFunction { _ ->
                "a coroutine".metisValue()
            }
            table["__eq__"] = twoArgFunction { self, other ->
                Value.Boolean.of(self === other)
            }
            table["__contains__"] = twoArgFunction { self, key ->
                Value.Boolean.of(self.lookUp(key) != null)
            }

            table["step"] = oneArgFunction { self ->
                val coroutine = self.convertTo<Coroutine>()
                if (coroutine.lastResult == StepResult.FINISHED) {
                    "finished".metisValue()
                } else {
                    val result = coroutine.innerState.step()
                    coroutine.lastResult = result
                    if (result == StepResult.YIELDED) {
                        coroutine.lastYielded = coroutine.innerState.yielded!!
                        coroutine.innerState.yielded = null
                    }
                    result.name.lowercase().metisValue()
                }
            }
            table["last_result"] = oneArgFunction { self ->
                self.convertTo<Coroutine>().lastResult.name.lowercase().metisValue()
            }
            table["last_yielded"] = oneArgFunction { self ->
                self.convertTo<Coroutine>().lastYielded
            }

            table["create"] = threeArgFunction { fn, args, globals ->
                Coroutine(
                    if (globals == Value.Null) this.globals else globals.convertTo<Value.Table>(),
                    fn.convertTo<Chunk.Instance>(),
                    if (args == Value.Null) Value.List() else args.convertTo<Value.List>()
                )
            }
        }
    }
}