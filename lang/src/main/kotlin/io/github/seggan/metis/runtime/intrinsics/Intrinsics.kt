package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.runtime.*
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.util.MutableLazy
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.io.IOException
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.collections.set
import kotlin.io.path.*

/**
 * An object which contains all the global intrinsic functions.
 */
object Intrinsics {

    private val _intrinsics = mutableMapOf<String, CallableValue>()

    /**
     * All registered intrinsic functions.
     */
    val intrinsics: Map<String, CallableValue> get() = _intrinsics

    fun register(name: String, value: CallableValue) {
        _intrinsics[name] = value
    }

    internal fun registerDefault() {
        _intrinsics["load_chunk"] = twoArgFunction { name, chunk ->
            val source = CodeSource.constant(name.stringValue(), chunk.stringValue())
            Chunk.load(source).Instance(this)
        }
        _intrinsics["type"] = oneArgFunction { value ->
            typeToName(value::class).metisValue()
        }
        _intrinsics["globals"] = zeroArgFunction { globals }
        _intrinsics["yield"] = object : CallableValue {
            override var metatable: Value.Table? by MutableLazy {
                Value.Table(mutableMapOf("__str__".metisValue() to oneArgFunction { "OneShot".metisValue() }))
            }
            override val arity = Arity.ONE
            override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
                private var yielded = false
                override fun step(state: State): StepResult {
                    return if (yielded) {
                        state.stack.push(state.yieldComm)
                        StepResult.FINISHED
                    } else {
                        yielded = true
                        state.yieldComm = state.stack.pop()
                        StepResult.YIELDED
                    }
                }
            }
        }
    }
}


/**
 * A function that is guaranteed to finish in one step. Optimization for this is implemented.
 *
 * @param arity The arity of the function.
 */
abstract class OneShotFunction(override val arity: Arity) : CallableValue {

    override var metatable: Value.Table? by MutableLazy {
        Value.Table(mutableMapOf("__str__".metisValue() to oneArgFunction { "OneShot".metisValue() }))
    }

    final override fun call(nargs: Int): CallableValue.Executor = object : CallableValue.Executor {
        override fun step(state: State): StepResult {
            execute(state, nargs)
            return StepResult.FINISHED
        }
    }

    abstract fun execute(state: State, nargs: Int)
}

/**
 * Creates a [OneShotFunction] with zero arguments.
 *
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun zeroArgFunction(crossinline fn: State.() -> Value): OneShotFunction = object : OneShotFunction(Arity.ZERO) {
    override fun execute(state: State, nargs: Int) {
        state.stack.push(state.fn())
    }
}

/**
 * Creates a [OneShotFunction] with one argument.
 *
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun oneArgFunction(crossinline fn: State.(Value) -> Value): OneShotFunction =
    object : OneShotFunction(Arity.ONE) {
        override fun execute(state: State, nargs: Int) {
            state.stack.push(state.fn(state.stack.pop()))
        }
    }

/**
 * Creates a [OneShotFunction] with two arguments.
 *
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun twoArgFunction(crossinline fn: State.(Value, Value) -> Value): OneShotFunction =
    object : OneShotFunction(Arity.TWO) {
        override fun execute(state: State, nargs: Int) {
            val b = state.stack.pop()
            val a = state.stack.pop()
            state.stack.push(state.fn(a, b))
        }
    }

/**
 * Creates a [OneShotFunction] with three arguments.
 *
 * @param fn The function to execute.
 * @return The created function.
 * @see OneShotFunction
 */
inline fun threeArgFunction(crossinline fn: State.(Value, Value, Value) -> Value): OneShotFunction =
    object : OneShotFunction(Arity.THREE) {
        override fun execute(state: State, nargs: Int) {
            val c = state.stack.pop()
            val b = state.stack.pop()
            val a = state.stack.pop()
            state.stack.push(state.fn(a, b, c))
        }
    }

internal fun initPathLib() = buildTable { lib ->

    fun pathFunction(fn: (Path) -> Value) = oneArgFunction { self ->
        try {
            var path = fileSystem.getPath(self.stringValue())
            if (!path.isAbsolute) {
                path = cwd.resolve(path)
            }
            fn(path)
        } catch (e: InvalidPathException) {
            Value.Null
        } catch (e: IOException) {
            throw MetisRuntimeException("IoError", e.message ?: "Unknown IO error", cause = e)
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
    lib["open_write"] = pathFunction {
        try {
            wrapOutStream(it.outputStream())
        } catch (e: FileSystemAlreadyExistsException) {
            throw MetisRuntimeException(
                "IoError",
                "File already exists: ${it.absolutePathString()}",
                Value.Table(mutableMapOf("path".metisValue() to it.absolutePathString().metisValue()))
            )
        }
    }
    lib["open_read"] = pathFunction {
        try {
            wrapInStream(it.inputStream())
        } catch (e: NoSuchFileException) {
            throw MetisRuntimeException(
                "IoError",
                "File not found: ${it.absolutePathString()}",
                Value.Table(mutableMapOf("path".metisValue() to it.absolutePathString().metisValue()))
            )
        }
    }
}