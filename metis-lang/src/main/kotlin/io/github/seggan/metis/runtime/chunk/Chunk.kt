package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.compilation.Compiler
import io.github.seggan.metis.debug.DebugInfo
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.parsing.Parser
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.util.*
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.set

/**
 * A chunk is a compiled piece of code.
 *
 * @property name The name of the chunk.
 * @property insns The instructions of the chunk.
 * @property arity The arity of the chunk.
 * @property spans The spans of the chunk. Must be the same size as [insns].
 */
class Chunk(
    val name: String,
    val insns: List<Insn>,
    val arity: Arity,
    private val upvalues: List<Upvalue>,
    private val id: UUID,
    val spans: List<Span>
) {

    /**
     * Provides a dissasembly of the chunk.
     */
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

    /**
     * An instance of the chunk.
     *
     * @property state The state of the instance.
     */
    inner class Instance(state: State) : CallableValue {

        override var metatable: Value.Table? = Companion.metatable

        override val arity = this@Chunk.arity

        val id = this@Chunk.id

        /**
         * The upvalue instances of the chunk instance.
         */
        val upvalues = this@Chunk.upvalues.map { it.newInstance(state) }

        override fun call(nargs: Int): CallableValue.Executor = ChunkExecutor()

        override fun toString() = name

        /**
         * Provides a dissasembly of the chunk.
         */
        fun dissasemble() = this@Chunk.toString()

        private inner class ChunkExecutor : CallableValue.Executor {

            private var ip = 0

            private var toBeUsed: Value? = null

            private var justHitBreakpoint = false

            private val errorHandlerStack = ArrayDeque<ErrorHandler>()
            private val finallyStack = ArrayDeque<Insn.Label>()
            private var importing: Set<Value>? = null
            private var loaded: Value? = null

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
                        is Insn.UpdateGlobal -> {
                            val name = insn.name.metisValue()
                            val value = state.stack.pop()
                            if (name in state.globals) {
                                state.globals[name] = value
                            }
                        }
                        is Insn.GetLocal -> state.stack.push(state.stack[state.localsOffset + insn.index])
                        is Insn.SetLocal -> state.stack[state.localsOffset + insn.index] = state.stack.peek()
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
                        is Insn.Call -> state.call(insn.nargs, insn.selfProvided, spans[ip - 1])
                        is Insn.MetaCall -> state.metaCall(insn.nargs, insn.metamethod, spans[ip - 1])
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
                        is Insn.Is -> state.`is`()

                        is Insn.Import -> {
                            val found = state.globals.lookUpHierarchy("package", "loaded", insn.name)
                            if (found == null) {
                                importing = state.globals.keys.toSet()
                                var foundLoader = false
                                for (loader in state.loaders) {
                                    val result = loader.load(state, insn.name)
                                    if (result != null) {
                                        state.stack.push(result)
                                        state.call(0, false, spans[ip - 1])
                                        foundLoader = true
                                        break
                                    }
                                }
                                if (!foundLoader) {
                                    throw MetisRuntimeException(
                                        "ImportError",
                                        "Could not find module '${insn.name}'",
                                        buildTable { it["module"] = insn.name.metisValue() }
                                    )
                                }
                            } else {
                                loaded = found
                            }
                        }

                        is Insn.PostImport -> {
                            val module = if (loaded == null) {
                                state.stack.pop()
                                val saved = importing!!
                                val extra = Value.Table()
                                val it = state.globals.entries.iterator()
                                while (it.hasNext()) {
                                    val next = it.next()
                                    if (next.key !in saved) {
                                        extra[next.key] = next.value
                                        it.remove()
                                    }
                                }
                                val loaded =
                                    state.globals.lookUpHierarchy("package", "loaded")!!.tableValue()
                                loaded[insn.name] = extra
                                extra
                            } else {
                                loaded!!
                            }
                            loaded = null
                            if (insn.global) {
                                state.globals[insn.name] = module
                            } else {
                                state.stack.push(module)
                            }
                        }

                        is Insn.Label -> {}
                        is Insn.IllegalInsn -> error("Illegal instruction $insn at $ip")
                    }
                } catch (e: MetisException) {
                    e.addStackFrame(spans[ip - 1])
                    throw e
                } catch (e: RuntimeException) {
                    val err = MetisRuntimeException("InternalError", e.message ?: "Unknown error", cause = e)
                    err.addStackFrame(spans[ip - 1])
                    throw err
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
        private val metatable = buildTable { table ->
            table["__str__"] = oneArgFunction(true) { self ->
                self.convertTo<Instance>().toString().metisValue()
            }
            table["disassemble"] = oneArgFunction(true) { self ->
                self.convertTo<Instance>().dissasemble().metisValue()
            }
        }

        /**
         * Loads a chunk from a [CodeSource], performing lexing, parsing and compilation.
         *
         * @param source The source to load from.
         * @return The loaded chunk.
         */
        fun load(source: CodeSource): Chunk {
            val parser = Parser(Lexer.lex(source), source)
            val compiler = Compiler()
            return compiler.compileCode(source.name, parser.parse())
        }
    }
}
