package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.nio.file.InvalidPathException
import kotlin.io.path.exists

object ModuleLoader : OneShotFunction(Arity.ONE) {

    final override fun execute(state: State, nargs: Int) {
        val name = state.stack.pop().stringValue()
        val path = try {
            state.fileSystem.getPath(name)
        } catch (e: InvalidPathException) {
            throw MetisRuntimeException("ValueError", "Invalid path: $name")
        }
        val result = if (path.exists()) {
            Chunk.load(CodeSource.from(path))
        } else {
            null
        }
        if (result == null) {
            state.stack.push(Value.Null)
        } else {
            state.stack.push(result.Instance(state))
        }
    }
}