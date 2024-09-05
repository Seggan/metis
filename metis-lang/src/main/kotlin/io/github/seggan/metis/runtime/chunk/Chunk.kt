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
    override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {

        private var ip = 0

        private lateinit var saved: Value

        override fun State.step(): StepResult {
            val (insn, span) = insns[ip]
            when (insn) {
                is Insn.Push -> stack.push(insn.value)
                is Insn.Pop -> stack.pop()
                is Insn.CopyUnder -> stack.push(stack[insn.index])
                is Insn.GetLocal -> stack.push(stack[insn.index - stackBottom])
                is Insn.SetLocal -> stack[insn.index - stackBottom] = stack.pop()
                is Insn.GetGlobal -> stack.push(
                    globals[insn.name] ?: throw MetisRuntimeException(
                        "MissingKeyError",
                        "Undefined variable: ${insn.name}"
                    )
                )

                is Insn.SetGlobal -> {
                    if (!insn.define && insn.name !in globals) {
                        throw MetisRuntimeException(
                            "MissingKeyError",
                            "Undefined variable: ${insn.name}"
                        )
                    }
                    globals[insn.name] = stack.pop()
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

                is Insn.Call -> call(insn.nargs, insn.selfProvided)
                is Insn.MetaCall -> TODO()

                is Insn.DirectJump -> ip = insn.target
                is Insn.JumpIf -> {
                    val value = if (insn.consume) stack.pop() else stack.peek()
                    if (value.convertTo<BooleanValue>().value == insn.condition) {
                        ip = insn.target
                    }
                }

                is Insn.Label -> { /* nop */
                }

                is Insn.Save -> saved = stack.pop()
                is Insn.Return -> {
                    stack.push(saved)
                    return StepResult.Finished
                }

                is Insn.Is -> stack.push(BooleanValue.of(stack.pop() === stack.pop()))
                is Insn.Not -> not()

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