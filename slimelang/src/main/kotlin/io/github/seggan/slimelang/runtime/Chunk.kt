package io.github.seggan.slimelang.runtime

import io.github.seggan.slimelang.parsing.Span

class Chunk(
    val name: String,
    val insns: List<Insn>,
    override val arity: Int,
    val spans: List<Span>
) : CallableValue {

    override var metatable: Value.Table? = null

    override fun call(): CallableValue.Executor = ChunkExecutor()

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

    private inner class ChunkExecutor : CallableValue.Executor {
        private var ip = 0

        override fun step(state: State): Boolean {
            if (ip >= insns.size) return true
            try {
                when (val insn = insns[ip++]) {
                    is Insn.BinaryOp -> TODO()
                    is Insn.GetGlobals -> state.stack.push(state.globals)
                    is Insn.GetLocal -> TODO()
                    is Insn.Index -> state.index()
                    is Insn.IndexImm -> state.indexImm(insn.key)
                    is Insn.Pop -> state.stack.pop()
                    is Insn.Push -> state.stack.push(insn.value)
                    is Insn.Set -> state.set()
                    is Insn.SetImm -> state.setImm(insn.key, insn.allowNew)
                    is Insn.UnaryOp -> TODO()
                }
            } catch (e: SlRuntimeException) {
                e.span = spans[ip - 1]
                throw e
            }
            return false
        }
    }
}
