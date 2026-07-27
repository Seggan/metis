package io.github.seggan.metis.runtime

import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.ChunkInstance
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.value.*
import io.github.seggan.metis.runtime.value.intrinsics.IntrinsicFunctions
import io.github.seggan.metis.runtime.value.intrinsics.OneShotFunction
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

class Interpreter {

    val globals = buildTable {
        for (prop in IntrinsicFunctions::class.declaredMemberProperties) {
            if (prop.returnType.isSubtypeOf(typeOf<OneShotFunction>())) {
                put(prop.name, prop.get(IntrinsicFunctions) as OneShotFunction)
            }
        }
    }

    private val callStack = ArrayDeque<CallableValue.Executor>()

    var returnValue: Value? = null

    fun loadChunk(chunk: Chunk) {
        call(ChunkInstance(chunk), emptyList())
    }

    fun call(callable: CallableValue, args: List<Value>) {
        val executor = callable.call(padArguments(callable, args))
        callStack.push(executor)
    }

    fun step(): StepResult {
        if (callStack.isEmpty()) return StepResult.Finished(MetisNull)
        var result = callStack.peek().step(this)
        if (result is StepResult.Finished) {
            returnValue = result.result
            callStack.pop()
            result = StepResult.Continue
        }
        return result
    }
}