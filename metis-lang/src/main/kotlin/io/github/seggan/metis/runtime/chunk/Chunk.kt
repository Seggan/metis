package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.compilation.Compiler
import io.github.seggan.metis.compilation.Register
import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.parsing.Parser
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.value.*
import java.util.*

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
    val registers: Int,
    val arity: Int,
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

    companion object {

        /**
         * Loads a chunk from a [CodeSource], performing lexing, parsing and compilation.
         *
         * @param source The source to load from.
         * @return The loaded chunk.
         */
        fun load(source: CodeSource): Chunk {
            val parser = Parser(Lexer.lex(source), source)
            return Compiler.compile(source.name, parser.parse())
        }

        val metatable = MetisTable()
    }
}

class ChunkInstance(val chunk: Chunk) : CallableValue {

    override val arity = chunk.arity

    override var metatable = Chunk.metatable
    override fun call(args: List<Value>): CallableValue.Executor = ChunkExecutor(this, args)
}

private class ChunkExecutor(val chunk: ChunkInstance, args: List<Value>) : CallableValue.Executor {

    val insns = chunk.chunk.insns
    var ip = 0

    var returnRegister = Register(-1)

    val registers = Array<Value?>(chunk.chunk.registers) { null }

    init {
        for ((i, value) in args.withIndex()) {
            registers[i] = value
        }
    }

    override fun step(state: State): StepResult {
        if (ip !in insns.indices) throw AssertionError("No Return insn was reached before running out of insns")
        if (state.returnValue != null) {
            registers[returnRegister] = state.returnValue
            state.returnValue = null
        }
        try {
            when (val insn = insns[ip]) {
                is Insn.Clear -> {
                    for (register in insn.registers) {
                        registers[register] = null
                    }
                }

                is Insn.Move -> {
                    registers[insn.dest] = registers[insn.src]
                }

                is Insn.SetValue -> {
                    registers[insn.dest] = insn.value
                }

                is Insn.Return -> {
                    return StepResult.Finished(registers[insn.register]!!)
                }

                is Insn.GetGlobal -> {
                    val global = state.globals.lookUp(insn.name.metisValue())
                    if (global == null) {
                        throw MetisGlobalError("Global $global could not be found")
                    }
                    registers[insn.dest] = global
                }

                is Insn.SetGlobal -> {
                    val value = registers[insn.value]!!
                    state.globals[insn.name] = value
                }

                is Insn.UpdateGlobal -> {
                    val global = state.globals.lookUp(insn.name.metisValue())
                    if (global == null) {
                        throw MetisGlobalError("Global $global could not be found")
                    }
                    state.globals[insn.name] = registers[insn.value]!!
                }

                is Insn.Call -> {
                    returnRegister = insn.dest
                    val target = registers[insn.target]!!
                    val args = insn.args.mapTo(mutableListOf()) { registers[it]!! }
                    val callable = if (target !is CallableValue) {
                        val metamethod = target.metatable.lookUp(Metamethod.CALL.metisValue())
                        if (metamethod !is CallableValue) {
                            throw MetisValueError("$target is neither a callable nor has a valid ${Metamethod.CALL} metamethod")
                        }
                        args.addFirst(target)
                        metamethod
                    } else {
                        target
                    }
                    state.call(callable, args)
                }

                is Insn.MetaCall -> {
                    returnRegister = insn.dest
                    val target = registers[insn.target]!!
                    val metamethod = target.metatable.lookUp(insn.method.metisValue())
                    if (metamethod !is CallableValue) {
                        throw MetisIndexError("Could not find metamethod ${insn.method} on $target")
                    }
                    val args = buildList {
                        add(target)
                        for (register in insn.args) {
                            add(registers[register]!!)
                        }
                    }
                    state.call(metamethod, args)
                }

                is Insn.ConstructError -> TODO()
                is Insn.ConstructList -> TODO()
                is Insn.ConstructTable -> TODO()

                is Insn.Is -> {
                    registers[insn.dest] = (registers[insn.value1]!! === registers[insn.value2]!!).metisValue()
                }

                is Insn.Not -> {
                    registers[insn.dest] = (!(registers[insn.value]!!.booleanValue())).metisValue()
                }

                is Insn.Jump -> {
                    ip += insn.offset
                }

                is Insn.JumpIf -> {
                    if (registers[insn.register]!!.booleanValue() == insn.condition) {
                        ip += insn.offset
                    }
                }

                is Insn.Label -> {}
                is Insn.IllegalInsn -> throw AssertionError("Should not happen")
            }
        } catch (e: MetisRuntimeException) {
            e.addStackFrame(chunk.chunk.spans[ip])
            throw e
        } catch (e: Exception) {
            val metisError = MetisInternalError(e)
            metisError.addStackFrame(chunk.chunk.spans[ip])
            throw metisError
        }
        ip++
        return StepResult.Continue
    }
}

private operator fun Array<Value?>.get(register: Register) = this[register.index]
private operator fun Array<Value?>.set(register: Register, value: Value?) {
    this[register.index] = value
}
