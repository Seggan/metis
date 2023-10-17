package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.runtime.Arity
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.stringValue
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

object ResourceLoader : OneShotFunction(Arity.ONE) {
    override fun execute(state: State, nargs: Int) {
        val path = state.stack.pop().stringValue()
        val resource = ResourceLoader::class.java.getResource("/std/$path.metis")
        if (resource != null) {
            val source = CodeSource(path) { resource.readText() }
            val chunk = Chunk.load(source)
            state.stack.push(chunk.Instance(state))
        } else {
            state.stack.push(Value.Null)
        }
    }
}