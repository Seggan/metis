package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.compilation.FullInsn
import io.github.seggan.metis.compilation.MetisCompiler
import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.debug.DebugInfo
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.parsing.MetisLexer
import io.github.seggan.metis.parsing.MetisParser
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.value.*
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.io.Serial

class Chunk(
    private val name: String,
    override val arity: CallableValue.Arity,
    insns: List<FullInsn>
) : CallableValue {

    override var metatable: TableValue? = mapOf(Metamethod.TO_STRING to name.metis()).metis()

    private val insns: List<Insn>
    private val spans: List<Span>

    init {
        val unzipped = insns.unzip()
        this.insns = unzipped.first
        this.spans = unzipped.second
    }

    @Suppress("serial")
    override fun call(): CallableValue.Executor = object : CallableValue.Executor {

        private var ip = 0

        private lateinit var saved: Value

        private var justHitBreakpoint = false

        override fun step(state: State): StepResult {
            if (ip >= insns.size) return StepResult.Finished
            val insn = insns[ip]
            if (state.debugMode) {
                val span = spans[ip]
                if (!justHitBreakpoint) {
                    if (state.breakpoints.any { it.isInSpan(span) }) {
                        println("Hit breakpoint at ${span.source.name}:${span.line}")
                        println(span.fancyToString())
                        justHitBreakpoint = true
                        return StepResult.Breakpoint
                    }
                } else {
                    justHitBreakpoint = false
                }
                state.debugInfo = DebugInfo(span, insn)
            }

            try {
                return stepImpl(state, insn).also { ip++ }
            } catch (e: MetisRuntimeException) {
                e.addStackFrame(spans[ip])
                throw e
            } catch (e: RuntimeException) {
                val err = MetisRuntimeException("InternalError", e.message ?: "Unknown error", cause = e)
                err.addStackFrame(spans[ip])
                throw err
            }
        }

        private fun stepImpl(state: State, insn: Insn): StepResult {
            when (insn) {
                is Insn.Push -> state.stack.push(insn.value)
                is Insn.Pop -> state.stack.pop()
                is Insn.CopyUnder -> state.stack.push(state.stack[insn.index])
                is Insn.GetLocal -> state.getLocal(insn.index)
                is Insn.SetLocal -> state.setLocal(insn.index)

                is Insn.GetGlobal -> state.stack.push(
                    state.globals[insn.name] ?: throw MetisRuntimeException(
                        "MissingKeyError",
                        "Undefined variable: ${insn.name}"
                    )
                )

                is Insn.SetGlobal -> {
                    if (!insn.define && insn.name !in state.globals) {
                        throw MetisRuntimeException(
                            "MissingKeyError",
                            "Undefined variable: ${insn.name}"
                        )
                    }
                    state.globals[insn.name] = state.stack.pop()
                }

                is Insn.WrapList -> {
                    val list = ArrayDeque<Value>(insn.size)
                    repeat(insn.size) {
                        list.addFirst(state.stack.pop())
                    }
                    state.stack.push(list.metis())
                }

                is Insn.WrapTable -> {
                    val table = HashMap<Value, Value>(insn.size)
                    repeat(insn.size) {
                        val value = state.stack.pop()
                        val key = state.stack.pop()
                        table[key] = value
                    }
                    state.stack.push(table.metis())
                }

                is Insn.BuildError -> {
                    val companionData = state.stack.pop().tableValue
                    val message = state.stack.pop().stringValue
                    state.stack.push(
                        MetisRuntimeException(
                            insn.type,
                            message,
                            companionData
                        )
                    )
                }

                is Insn.GetIndex -> state.getIndex()
                is Insn.SetIndex -> state.setIndex()

                is Insn.Call -> state.call(insn.nargs, insn.selfProvided, spans[ip])
                is Insn.MetaCall -> state.metaCall(insn.nargs, insn.meta, spans[ip])

                is Insn.DirectJump -> ip = insn.target
                is Insn.JumpIf -> {
                    val value = if (insn.consume) state.stack.pop() else state.stack.peek()
                    if (value.into<BooleanValue>().value == insn.condition) {
                        ip = insn.target
                    }
                }

                is Insn.Label -> {
                    // Congratulations, you found this comment!
                }

                is Insn.Save -> saved = state.stack.pop()
                is Insn.Return -> {
                    state.stack.push(saved)
                    ip = insns.size
                    return StepResult.Finished
                }

                is Insn.Is -> state.stack.push(BooleanValue.of(state.stack.pop() === state.stack.pop()))
                is Insn.Not -> state.not()

                is Insn.IllegalInsn -> throw IllegalStateException("Illegal instruction: $insn at ${spans[ip]}")
            }
            return StepResult.Continue
        }
    }

    override fun toString(): String {
        val lines = insns.withIndex().map { (i, insn) -> "$i: $insn" }
        val padLength = lines.maxOf { it.length } + 1
        return buildString {
            append("=== ")
            append(name)
            appendLine(" ===")
            for ((i, line) in lines.withIndex()) {
                append(line.padEnd(padLength))
                append(" (")
                val span = spans[i]
                append(span.start)
                append("..")
                append(span.end)
                append("; ")
                append(span)
                append(')')
                appendLine()
            }
        }
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -6204284966809780651L

        fun load(source: CodeSource): Chunk {
            val lexed = MetisLexer.lex(source)
            val parsed = MetisParser.parse(lexed, source)
            return MetisCompiler.compile(source.name, parsed)
        }
    }
}