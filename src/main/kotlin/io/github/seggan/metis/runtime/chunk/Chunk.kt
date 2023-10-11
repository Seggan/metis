package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.compilation.Compiler
import io.github.seggan.metis.debug.DebugInfo
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.parsing.Parser
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.intrinsics.initChunk
import io.github.seggan.metis.util.getFromTop
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

class Chunk(
    val name: String,
    val insns: List<Insn>,
    val arity: Arity,
    val upvalues: List<Upvalue>,
    val spans: List<Span>
) {

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

        override fun toString() = name

        fun dissasemble() = this@Chunk.toString()

        private inner class ChunkExecutor : CallableValue.Executor {

            private var ip = 0

            private var toBeUsed: Value? = null

            private var justHitBreakpoint = false

            private val errorHandlerStack = ArrayDeque<ErrorHandler>()
            private val finallyStack = ArrayDeque<Insn.Label>()

            override fun step(state: State): StepResult {
                if (ip >= insns.size) return StepResult.FINISHED
                try {
                    val insn = insns[ip]
                    if (state.debugMode) {
                        val span = spans[ip]
                        if (state.breakpoints.any { it.isInSpan(span) }) {
                            if (!justHitBreakpoint) {
                                println("Hit breakpoint at ${span.source.name}:${span.line}")
                                println(span.fancyToString())
                                justHitBreakpoint = true
                                return StepResult.BREAKPOINT
                            }
                        } else {
                            justHitBreakpoint = false
                        }
                        state.debugInfo = DebugInfo(
                            span,
                            insn
                        )
                    }
                    ip++
                    when (insn) {
                        is Insn.GetGlobal -> state.stack.push(
                            state.globals[insn.name] ?: throw MetisRuntimeException(
                                "NameError",
                                "Global '${insn.name}' not found"
                            )
                        )

                        is Insn.SetGlobal -> state.globals[insn.name] = state.stack.pop()
                        is Insn.GetLocal -> state.stack.push(state.stack[state.localsOffset + insn.index])
                        is Insn.SetLocal -> state.stack[state.localsOffset + insn.index] = state.stack.pop()
                        is Insn.GetUpvalue -> upvalues[insn.index].get(state)
                        is Insn.SetUpvalue -> upvalues[insn.index].set(state)
                        is Insn.Index -> state.index()
                        is Insn.Set -> state.set()
                        is Insn.Pop -> state.stack.pop()
                        is Insn.PopErrorHandler -> errorHandlerStack.pop()
                        is Insn.PopFinally -> finallyStack.pop()
                        is Insn.CloseUpvalue -> {
                            val it = state.openUpvalues.iterator()
                            var hasMet = false
                            while (it.hasNext()) {
                                val next = it.next()
                                if (next.template === insn.upvalue) {
                                    check(!hasMet) { "Closed more than 2 upvalues" }
                                    it.remove()
                                    next.close(state)
                                    hasMet = true
                                }
                            }
                        }

                        is Insn.Push -> state.stack.push(insn.value)
                        is Insn.PushClosure -> state.stack.push(insn.chunk.Instance(state))
                        is Insn.PushList -> state.wrapToList(insn.size)
                        is Insn.PushTable -> state.wrapToTable(insn.size)
                        is Insn.PushError -> state.newError(insn.type)
                        is Insn.PushErrorHandler -> errorHandlerStack.push(insn.handler)
                        is Insn.PushFinally -> finallyStack.push(insn.label)
                        is Insn.CopyUnder -> state.stack.push(state.stack.getFromTop(insn.index))
                        is Insn.Call -> state.call(insn.nargs, spans[ip - 1])
                        is Insn.ToBeUsed -> toBeUsed = state.stack.pop()
                        is Insn.Return -> {
                            ip = insns.size
                            state.stack.push(toBeUsed!!)
                        }

                        is Insn.Raise -> throw toBeUsed!!.convertTo<MetisRuntimeException>()
                        is Insn.Jump -> ip += insn.offset
                        is Insn.JumpIf -> {
                            val value = if (insn.consume) state.stack.pop() else state.stack.peek()
                            if (value.convertTo<Value.Boolean>().value == insn.condition) {
                                ip += insn.offset
                            }
                        }

                        is Insn.Not -> state.not()

                        is Insn.Label -> {}
                        is Insn.RawJump, is Insn.RawJumpIf -> error("Unbackpatched jump at $ip")
                    }
                } catch (e: MetisRuntimeException) {
                    e.addStackFrame(spans[ip - 1])
                    throw e
                }
                return StepResult.CONTINUE
            }

            override fun handleError(state: State, error: MetisRuntimeException): Boolean {
                val handler = errorHandlerStack.firstOrNull { it.errorName == error.type } ?: return false
                state.stack.push(error)
                val marker = insns.indexOf(handler.label)
                check(marker != -1) { "Could not find marker for error handler" }
                ip = marker
                return true
            }

            override fun handleFinally(state: State): Boolean {
                val marker = finallyStack.lastOrNull() ?: return false
                val markerIndex = insns.indexOf(marker)
                check(markerIndex != -1) { "Could not find marker for finally" }
                ip = markerIndex
                return true
            }
        }
    }

    companion object {
        private val metatable = initChunk()

        fun load(source: CodeSource): Chunk {
            val lexer = Lexer(source)
            val parser = Parser(lexer.lex(), source)
            val compiler = Compiler()
            return compiler.compileCode(source.name, parser.parse())
        }
    }
}
