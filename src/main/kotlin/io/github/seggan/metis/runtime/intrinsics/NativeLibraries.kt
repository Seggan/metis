package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.buildTable
import io.github.seggan.metis.runtime.metisValue
import io.github.seggan.metis.runtime.stringValue
import java.nio.file.Path
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.io.path.*

internal fun initPathLib() = buildTable { lib ->

    fun pathFunction(fn: (Path) -> Value) = oneArgFunction { self ->
        fn(fileSystem.getPath(self.stringValue()))
    }

    lib["separator"] = Value.String(System.getProperty("file.separator"))
    lib["normalize"] = pathFunction { it.normalize().toString().metisValue() }
    lib["absolute"] = pathFunction { it.toAbsolutePath().toString().metisValue() }
    lib["resolve"] = twoArgFunction { self, other ->
        fileSystem.getPath(self.stringValue()).resolve(other.stringValue()).toString().metisValue()
    }
    lib["parent"] = pathFunction { it.parent.toString().metisValue() }
    lib["name"] = pathFunction { it.fileName.toString().metisValue() }
    lib["root"] = pathFunction { it.root.toString().metisValue() }
    lib["is_absolute"] = pathFunction { it.isAbsolute.metisValue() }
    lib["list"] = pathFunction { path ->
        val list = Value.List()
        path.toFile().listFiles()?.forEach {
            list.add(it.absolutePath.metisValue())
        }
        list
    }
    lib["exists"] = pathFunction { it.exists().metisValue() }
    lib["is_file"] = pathFunction { it.isRegularFile().metisValue() }
    lib["is_dir"] = pathFunction { it.isDirectory().metisValue() }
    lib["is_symlink"] = pathFunction { it.isSymbolicLink().metisValue() }
    lib["is_hidden"] = pathFunction { it.isHidden().metisValue() }
    lib["open_write"] = pathFunction { wrapOutStream(it.outputStream()) }
    lib["open_read"] = pathFunction { wrapInStream(it.inputStream()) }
}