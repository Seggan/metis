package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.nio.file.Path
import kotlin.io.path.*

internal fun initPathLib() = buildTable { lib ->

    fun pathFunction(fn: (Path) -> Value): OneShotFunction = object : OneShotFunction(Arity.ONE) {
        override fun execute(state: State, nargs: Int) {
            val path = state.stack.pop().stringValue()
            state.stack.push(fn(state.fileSystem.getPath(path)))
        }
    }

    lib["separator"] = Value.String(System.getProperty("file.separator"))
    lib["normalize"] = pathFunction { it.normalize().toString().metisValue() }
    lib["absolute"] = pathFunction { it.toAbsolutePath().toString().metisValue() }
    lib["parent"] = pathFunction { it.parent.toString().metisValue() }
    lib["name"] = pathFunction { it.fileName.toString().metisValue() }
    lib["root"] = pathFunction { it.root.toString().metisValue() }
    lib["is_absolute"] = pathFunction { Value.Boolean.of(it.isAbsolute) }
    lib["list"] = pathFunction { path ->
        val list = Value.List()
        path.toFile().listFiles()?.forEach {
            list.add(Value.String(it.absolutePath))
        }
        list
    }
    lib["exists"] = pathFunction { Value.Boolean.of(it.exists()) }
    lib["is_file"] = pathFunction { Value.Boolean.of(it.isRegularFile()) }
    lib["is_dir"] = pathFunction { Value.Boolean.of(it.isDirectory()) }
    lib["is_symlink"] = pathFunction { Value.Boolean.of(it.isSymbolicLink()) }
    lib["is_hidden"] = pathFunction { Value.Boolean.of(it.isHidden()) }
    lib["open_write"] = pathFunction { path ->
        val stream = path.toFile().outputStream()
        wrapOutStream(stream)
    }
}