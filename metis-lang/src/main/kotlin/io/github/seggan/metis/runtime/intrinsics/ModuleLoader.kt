package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import kotlin.io.path.exists

/**
 * A class that loads a module.
 */
fun interface ModuleLoader {

    /**
     * Loads a module
     *
     * @param state The state to load the module into
     * @param module The name of the module to load
     * @return The loaded module, or `null` if it could not be loaded
     */
    fun load(state: State, module: String): CallableValue?
}

/**
 * A loader that loads from the Metis JAR's resources
 */
object ResourceLoader : ModuleLoader {
    override fun load(state: State, module: String): CallableValue? {
        return ResourceLoader::class.java.getResource("/std/$module.metis")?.let { resource ->
            val source = CodeSource(module) { resource.readText() }
            val chunk = Chunk.load(source)
            chunk.Instance(state)
        }
    }
}

/**
 * A loader that loads [NativeLibrary]s
 *
 * @param libs The list of libraries to load from
 */
class NativeLoader(private val libs: List<NativeLibrary>) : ModuleLoader {
    override fun load(state: State, module: String): CallableValue? {
        return libs.firstOrNull { it.name == module }
    }
}

/**
 * A loader that loads from the filesystem
 */
object FileLoader : ModuleLoader {
    override fun load(state: State, module: String): CallableValue? {
        val searchPaths = state.globals.lookUpHierarchy("package", "path")!!.listValue().map {
            state.currentDir.resolve(state.fileSystem.getPath(it.stringValue()))
        }
        for (searchPath in searchPaths) {
            val path = searchPath.resolve("$module.metis")
            if (path.exists()) {
                val source = CodeSource.fromPath(path)
                val chunk = Chunk.load(source)
                return chunk.Instance(state)
            }
        }

        return null
    }
}