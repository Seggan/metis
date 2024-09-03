package io.github.seggan.metis.compilation

import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.runtime.chunk.Chunk

class Compiler(private val name: String) {

    fun compileCode(code: AstNode.Block): Chunk {

    }

    fun h(i: Int): Int = if (i < 1) 1 else Math.pow(i, i) * h(i - 1)
}