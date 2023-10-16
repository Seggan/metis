package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.regex.PatternSyntaxException
import kotlin.collections.set
import kotlin.io.path.*

internal fun initPathLib() = buildTable { lib ->

    fun pathFunction(fn: (Path) -> Value) = oneArgFunction { self ->
        try {
            fn(fileSystem.getPath(self.stringValue()))
        } catch (e: InvalidPathException) {
            Value.Null
        } catch (e: IOException) {
            throw MetisRuntimeException("IoError", e.message ?: "Unknown IO error")
        }
    }

    lib["separator"] = Value.String(System.getProperty("file.separator"))
    lib["normalize"] = pathFunction { it.normalize().toString().metisValue() }
    lib["absolute"] = pathFunction { it.toAbsolutePath().toString().metisValue() }
    lib["resolve"] = twoArgFunction { self, other ->
        fileSystem.getPath(self.stringValue()).resolve(other.stringValue()).toString().metisValue()
    }
    lib["parent"] = pathFunction { it.parent.toString().metisValue() }
    lib["base_name"] = pathFunction { it.fileName.toString().metisValue() }
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

internal fun initRegexLib() = buildTable { lib ->
    lib["compile"] = oneArgFunction { self ->
        val pattern = self.stringValue()
        try {
            Value.Native(Regex(pattern))
        } catch (e: PatternSyntaxException) {
            throw MetisRuntimeException(
                "RegexError",
                "Failed to compile regex pattern: $pattern",
                buildTable { it["pattern"] = pattern.metisValue() }
            )
        }
    }
    lib["escape"] = oneArgFunction { self ->
        val regex = self.stringValue()
        Value.String(Regex.escape(regex))
    }

    lib["match"] = twoArgFunction { self, other ->
        val regex = self.asObj<Regex>()
        val input = other.stringValue()
        val match = regex.find(input)
        if (match != null) {
            val groups = Value.Table()
            match.groups.forEachIndexed { index, group ->
                groups[index.metisValue()] = group?.value?.metisValue() ?: Value.Null
            }
            groups
        } else {
            Value.Null
        }
    }
    lib["replace"] = threeArgFunction { self, other, replacement ->
        val regex = self.asObj<Regex>()
        val input = other.stringValue()
        val repl = replacement.stringValue()
        regex.replace(input, repl).metisValue()
    }
    lib["split"] = twoArgFunction { self, other ->
        val regex = self.asObj<Regex>()
        val input = other.stringValue()
        val list = Value.List()
        regex.split(input).forEach {
            list.add(it.metisValue())
        }
        list
    }
}

internal fun initOsLib() = buildTable { lib ->
    lib["get_env"] = oneArgFunction { self ->
        System.getenv(self.stringValue())?.metisValue() ?: Value.Null
    }
    lib["set_env"] = twoArgFunction { self, other ->
        System.setProperty(self.stringValue(), other.stringValue())
        Value.Null
    }
    lib["cwd"] = System.getProperty("user.dir").metisValue()
}