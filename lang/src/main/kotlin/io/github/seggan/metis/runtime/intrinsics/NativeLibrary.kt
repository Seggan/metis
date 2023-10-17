package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.util.push
import java.util.regex.PatternSyntaxException
import kotlin.collections.set
import kotlin.io.path.absolutePathString

abstract class NativeLibrary(val name: String) : OneShotFunction(Arity.ZERO) {
    final override fun execute(state: State, nargs: Int) {
        val table = buildTable(::init)
        state.globals[name] = table
        state.stack.push(table)
    }

    abstract fun init(lib: MutableMap<String, Value>)
}

object RegexLib : NativeLibrary("regex") {
    override fun init(lib: MutableMap<String, Value>) {
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
}

object OsLib : NativeLibrary("os") {
    override fun init(lib: MutableMap<String, Value>) {
        lib["get_env"] = oneArgFunction { self ->
            System.getenv(self.stringValue())?.metisValue() ?: Value.Null
        }
        lib["set_env"] = twoArgFunction { self, other ->
            System.setProperty(self.stringValue(), other.stringValue())
            Value.Null
        }
        lib["get_cwd"] = zeroArgFunction { cwd.absolutePathString().metisValue() }
        lib["set_cwd"] = oneArgFunction { self ->
            val path = fileSystem.getPath(self.stringValue())
            if (!path.isAbsolute) {
                throw MetisRuntimeException(
                    "IoError",
                    "Cannot set cwd to relative path: ${path.absolutePathString()}"
                )
            }
            cwd = path
            Value.Null
        }
    }
}