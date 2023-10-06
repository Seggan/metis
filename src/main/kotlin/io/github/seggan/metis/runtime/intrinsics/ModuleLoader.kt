package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.MetisRuntimeException
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.runtime.Arity
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.stringValue
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.InvalidPathException
import kotlin.io.path.exists

abstract class ModuleLoader : OneShotFunction(Arity.ONE) {
    final override fun execute(state: State, nargs: Int) {
        val name = state.stack.pop().stringValue()
        val result = load(name)
        if (result == null) {
            state.stack.push(Value.Null)
        } else {
            state.stack.push(result.Instance(state))
        }
    }

    abstract fun load(name: String): Chunk?
}

object PathLoader : ModuleLoader() {

    var fileSystem: FileSystem = FileSystems.getDefault()

    override fun load(name: String): Chunk? {
        val path = try {
            fileSystem.getPath(name)
        } catch (e: InvalidPathException) {
            throw MetisRuntimeException("Invalid path: $name")
        }
        return if (path.exists()) {
            Chunk.load(CodeSource.from(path))
        } else {
            null
        }
    }
}