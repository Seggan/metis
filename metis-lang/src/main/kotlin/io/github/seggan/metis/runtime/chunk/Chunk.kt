package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.value.CallableValue
import java.io.Serial

class Chunk(private val name: String, private val insns: List<Insn>) : CallableValue {
    override fun call(nargs: Int): CallableValue.Executor {
        TODO("Not yet implemented")
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = -6204284966809780651L
    }
}