package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.MetisRuntimeException
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.intrinsics.initChunk

class Chunk(
    val name: String,
    val insns: List<Insn>,
    val arity: Arity,
    val upvalues: List<Upvalue>,
    spans: List<Span>,
    val file: Pair<String, String>
) {

    val spans = spans.map { it.copy(file = file) }

    override fun toString(): String {
        return buildString {
            append("=== ")
            append(name)
            appendLine(" ===")
            for ((i, insn) in insns.withIndex()) {
                append(i)
                append(": ")
                append(insn)
                append(" (")
                val span = spans[i]
                append(span.start)
                append("..")
                append(span.end)
                appendLine(")")
            }
        }
    }

    inner class Instance(state: State) : CallableValue {

        override var metatable: Value.Table? = Companion.metatable

        override val arity = this@Chunk.arity

        val upvalues = this@Chunk.upvalues.map { it.newInstance(state) }

        override fun call(nargs: Int): CallableValue.Executor = ChunkExecutor()

        override fun toString() = this@Chunk.toString()

        private inner class ChunkExecutor : CallableValue.Executor {

            private var ip = 0

            private var toReturn: Value? = null

            override fun step(state: State): StepResult {
                if (ip >= insns.size) {
                    if (toReturn != null) {
                        state.stack.push(toReturn!!)
                        return StepResult.FINISHED
                    }
                    state.stderr.write("Chunk finished without returning a value\n".toByteArray())
                    throw MetisRuntimeException("Chunk finished without returning a value")
                }
                try {
                    val insn = insns[ip++]
                    when (insn) {
                        is Insn.GetGlobal -> state.stack.push(state.globals[insn.name].orNull())
                        is Insn.SetGlobal -> state.globals[insn.name] = state.stack.pop()
                        is Insn.GetLocal -> state.stack.push(state.stack[state.localsOffset + insn.index])
                        is Insn.SetLocal -> state.stack[state.localsOffset + insn.index] = state.stack.pop()
                        is Insn.GetUpvalue -> upvalues[insn.index].get(state)
                        is Insn.SetUpvalue -> upvalues[insn.index].set(state)
                        is Insn.Index -> state.index()
                        is Insn.Set -> state.set()
                        is Insn.Pop -> state.stack.pop()
                        is Insn.CloseUpvalue -> {
                            val it = state.openUpvalues.iterator()
                            var hasMet = false
                            while (it.hasNext()) {
                                val next = it.next()
                                if (next.isInstanceOf(insn.upvalue)) {
                                    check(!hasMet) { "Closed more than 2 upvalues" }
                                    it.remove()
                                    next.close(state)
                                    hasMet = true
                                }
                            }
                        }

                        is Insn.Push -> state.stack.push(insn.value)
                        is Insn.PushClosure -> state.stack.push(insn.chunk.Instance(state))
                        is Insn.CopyUnder -> state.stack.push(state.stack.getFromTop(insn.index))
                        is Insn.Call -> state.call(insn.nargs, spans[ip - 1])
                        is Insn.Return -> toReturn = state.stack.pop()
                        is Insn.Finish -> ip = insns.size
                        is Insn.Jump -> ip += insn.offset
                        is Insn.JumpIfFalse -> {
                            val value = if (insn.consume) state.stack.pop() else state.stack.peek()
                            if (!value.convertTo<Value.Boolean>().value) {
                                ip += insn.offset
                            }
                        }

                        is Insn.JumpIfTrue -> {
                            val value = if (insn.consume) state.stack.pop() else state.stack.peek()
                            if (value.convertTo<Value.Boolean>().value) {
                                ip += insn.offset
                            }
                        }

                        is Insn.Not -> state.stack.push(
                            Value.Boolean.from(
                                !state.stack.pop().convertTo<Value.Boolean>().value
                            )
                        )
                    }
                    if (state.debugMode) {
                        println(insn)
                    }
                } catch (e: MetisRuntimeException) {
                    e.addStackFrame(spans[ip - 1])
                    throw e
                }
                return StepResult.CONTINUE
            }
        }
    }

    companion object {
        private val metatable = initChunk()
    }
}
