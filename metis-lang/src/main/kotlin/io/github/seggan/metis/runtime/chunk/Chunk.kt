package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.compilation.FullInsn
import io.github.seggan.metis.compilation.MetisCompiler
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.parsing.MetisLexer
import io.github.seggan.metis.parsing.MetisParser
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.value.*
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.io.Serial

class Chunk(
    private val name: String,
    override val arity: CallableValue.Arity,
    private val insns: List<FullInsn>
) : CallableValue {

    @Suppress("serial")
    override fun call(): CallableValue.Executor = object : CallableValue.Executor {

        private var ip = 0

        private lateinit var saved: Value

        override fun step(state: State): StepResult {
            val (insn, span) = insns[ip]
            when (insn) {
                is Insn.Push -> state.stack.push(insn.value)
                is Insn.Pop -> state.stack.pop()
                is Insn.CopyUnder -> state.stack.push(state.stack[insn.index])
                is Insn.GetLocal -> state.stack.push(state.stack[insn.index - state.stackBottom])
                is Insn.SetLocal -> state.stack[insn.index - state.stackBottom] = state.stack.pop()
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

                is Insn.GetIndexDirect -> {
                    TODO()
                }

                is Insn.GetIndex -> {
                    TODO()
                }

                is Insn.SetIndexDirect -> {
                    TODO()
                }

                is Insn.SetIndex -> {
                    TODO()
                }

                is Insn.Call -> state.call(insn.nargs, insn.selfProvided)
                is Insn.MetaCall -> TODO()

                is Insn.DirectJump -> ip = insn.target
                is Insn.JumpIf -> {
                    val value = if (insn.consume) state.stack.pop() else state.stack.peek()
                    if (value.convertTo<BooleanValue>().value == insn.condition) {
                        ip = insn.target
                    }
                }

                is Insn.Label -> {
                    // Congratulations, you found this comment!
                }

                is Insn.Save -> saved = state.stack.pop()
                is Insn.Return -> {
                    state.stack.push(saved)
                    return StepResult.Finished
                }

                is Insn.Is -> state.stack.push(BooleanValue.of(state.stack.pop() === state.stack.pop()))
                is Insn.Not -> state.not()

                is Insn.IllegalInsn -> throw IllegalStateException("Illegal instruction: $insn at $span")
            }
            ip++
            return StepResult.Continue
        }
    }

    override fun toString(): String {
        return "Chunk(name='$name', insns=$insns)"
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