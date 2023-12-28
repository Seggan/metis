package io.github.seggan.metis.runtime

import io.github.seggan.metis.compilation.Compiler
import io.github.seggan.metis.debug.Breakpoint
import io.github.seggan.metis.debug.DebugInfo
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.parsing.Parser
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.chunk.Upvalue
import io.github.seggan.metis.runtime.intrinsics.*
import io.github.seggan.metis.util.Stack
import io.github.seggan.metis.util.getFromTop
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * Represents the state of a Metis virtual machine. This is the class that is used to run Metis code.
 */
class State(val parentState: State? = null) {

    /**
     * The global variables of the state.
     */
    val globals = Value.Table()

    private val nativeLibraries = mutableListOf<NativeLibrary>()
    val loaders = mutableListOf<ModuleLoader>()

    /**
     * The stack of the state.
     */
    val stack = Stack()
    internal val callStack = ArrayDeque<CallFrame>()

    internal var yieldComm: Value = Value.Null

    /**
     * The standard output stream of the state.
     */
    var stdout: OutputStream = parentState?.stdout ?: System.out

    /**
     * The standard error stream of the state.
     */
    var stderr: OutputStream = parentState?.stderr ?: System.err

    /**
     * The standard input stream of the state.
     */
    var stdin: InputStream = parentState?.stdin ?: System.`in`

    /**
     * The [FileSystem] used by the state.
     */
    var fileSystem: FileSystem = parentState?.fileSystem ?: FileSystems.getDefault()

    /**
     * The current working directory of the state.
     */
    var currentDir: String = parentState?.currentDir ?: System.getProperty("user.dir")

    internal val openUpvalues = ArrayDeque<Upvalue.Instance>()

    private var throwingException: MetisRuntimeException? = null

    /**
     * The offset of the local variables of the current function in the stack.
     */
    val localsOffset: Int
        get() = callStack.peek().stackBottom

    /**
     * True if the state is in debug mode.
     */
    var debugMode = false

    /**
     * The debug information of the state. Only set if [debugMode] is true.
     */
    var debugInfo: DebugInfo? = null

    /**
     * The breakpoints of the state. Only used if [debugMode] is true.
     */
    val breakpoints = mutableListOf<Breakpoint>()

    /**
     * The recursion limit of the state.
     */
    var recursionLimit = 2048

    companion object {
        init {
            Intrinsics.registerDefault()
        }

        private val coreScripts = listOf(
            "collection",
            "list",
            "string",
            "table",
            "number",
            "range",
            "io",
            "coroutine"
        )
    }

    init {
        if (parentState != null) {
            globals.putAll(parentState.globals)
            nativeLibraries.addAll(parentState.nativeLibraries)
            loaders.addAll(parentState.loaders)
        } else {
            globals["true"] = Value.Boolean.TRUE
            globals["false"] = Value.Boolean.FALSE
            globals["null"] = Value.Null

            for ((name, value) in Intrinsics.intrinsics) {
                globals[name] = value
            }

            val io = Value.Table()
            io["stdout"] = zeroArgFunction { Value.Native(stdout, NativeObjects.OUTPUT_STREAM) }
            io["stderr"] = zeroArgFunction { Value.Native(stderr, NativeObjects.OUTPUT_STREAM) }
            io["stdin"] = zeroArgFunction { Value.Native(stdin, NativeObjects.INPUT_STREAM) }

            io["inStream"] = NativeObjects.INPUT_STREAM
            io["outStream"] = NativeObjects.OUTPUT_STREAM
            globals["io"] = io

            globals["string"] = Value.String.metatable
            globals["number"] = Value.Number.metatable
            globals["table"] = Value.Table.metatable
            globals["list"] = Value.List.metatable
            globals["bytes"] = Value.Bytes.metatable
            globals["coroutine"] = Coroutine.metatable

            val pkg = Value.Table()
            pkg["path"] = Value.List(mutableListOf("./".metisValue(), "/usr/lib/metis/".metisValue()))
            pkg["loaded"] = Value.Table()
            globals["package"] = pkg

            runCode(CodeSource("core") { State::class.java.classLoader.getResource("core.metis")!!.readText() })
            for (script in coreScripts) {
                runCode(CodeSource(script) {
                    State::class.java.getResource("/core/$it.metis")!!.readText()
                })
            }

            addNativeLibrary(OsLib)
            addNativeLibrary(RegexLib)
            addNativeLibrary(PathLib)
            addNativeLibrary(MathLib)
            addNativeLibrary(RandomLib)

            loaders.add(ResourceLoader)
            loaders.add(NativeLoader(nativeLibraries))
            loaders.add(FileLoader)
        }
    }

    /**
     * Loads a chunk into the state but does not call it.
     *
     * @param chunk The chunk to load.
     */
    fun loadChunk(chunk: Chunk) {
        stack.push(chunk.Instance(this))
    }

    /**
     * Adds a [NativeLibrary] to the state.
     *
     * @param lib The library to add.
     */
    fun addNativeLibrary(lib: NativeLibrary) {
        nativeLibraries.add(lib)
    }

    /**
     * Steps the state.
     *
     * @return The result of the step.
     */
    fun step(): StepResult {
        if (callStack.isEmpty()) return StepResult.FINISHED
        lateinit var stepResult: StepResult
        try {
            stepResult = callStack.peek().executing.step(this)
            if (stepResult == StepResult.FINISHED) {
                callStack.removeLast()
                stepResult = StepResult.CONTINUE
            }
        } catch (e: MetisRuntimeException) {
            var err = e
            var caught = false
            while (callStack.isNotEmpty()) {
                if (e is MetisRuntimeException.Finally) {
                    // TODO: handle this absolute mess
                    if (throwingException != null) {
                        err = throwingException!!
                        throwingException = null
                    } else {
                        stepResult = StepResult.CONTINUE
                        caught = true
                        break
                    }
                }
                val (executor, bottom, id, span) = callStack.peek()
                if (span != null) {
                    err.addStackFrame(span)
                }

                if (executor.handleError(this, e)) {
                    throwingException = null
                    stepResult = StepResult.CONTINUE
                    caught = true
                    break
                } else {
                    if (executor.handleFinally(this)) {
                        throwingException = err
                        stepResult = StepResult.CONTINUE
                        caught = true
                        break
                    }
                    for (i in stack.lastIndex downTo bottom) {
                        val upvalue = openUpvalues.firstOrNull {
                            it.template.function == id && it.template.index == i
                        }
                        if (upvalue != null) {
                            upvalue.close(this)
                        } else {
                            stack.pop()
                        }
                    }
                    callStack.pop()
                }
            }
            if (!caught) throw err
        }
        return stepResult
    }

    /**
     * Runs the state till it is finished.
     *
     * @see step
     */
    @Suppress("ControlFlowWithEmptyBody")
    fun runTillComplete() {
        while (step() != StepResult.FINISHED) {
        }
    }


    /**
     * Loads the given [CodeSource] and runs it.
     *
     * @param source The source to load.
     */
    fun runCode(source: CodeSource) {
        val parser = Parser(Lexer.lex(source), source)
        val compiler = Compiler()
        loadChunk(compiler.compileCode(source.name, parser.parse()))
        call(0)
        runTillComplete()
        stack.pop()
    }

    /**
     * Performs an index operation on the stack.
     *
     * The top of the stack must be like so:
     * ```
     * index
     * value
     * ```
     */
    fun index() {
        if (stack.peek() == metatableString) {
            stack.pop()
            stack.push(stack.pop().metatable.orNull())
        } else {
            val getter = stack.getFromTop(1).metatable?.lookUp(indexString)
            if (getter is CallableValue) {
                callValue(getter, 2, true)
            } else {
                val index = stack.pop()
                val value = stack.pop()
                throw MetisRuntimeException(
                    "IndexError",
                    "Cannot index a non indexable value of type ${typeToName(value::class)} with index ${stringify(index)}",
                    buildTable { table ->
                        table["index"] = index
                        table["value"] = value
                    }
                )
            }
        }
    }

    /**
     * Performs a set operation on the stack.
     *
     * The top of the stack must be like so:
     * ```
     * value to set
     * index
     * value
     * ```
     */
    fun set() {
        if (stack.getFromTop(1) == metatableString) {
            val toSet = stack.pop().convertTo<Value.Table>()
            stack.pop()
            stack.pop().metatable = toSet
        } else {
            val setter = stack.getFromTop(2).metatable?.lookUp(setString)
            if (setter is CallableValue) {
                callValue(setter, 3, true)
                stack.pop()
            } else {
                val index = stack.pop()
                val value = stack.pop()
                val toSet = stack.pop()
                throw MetisRuntimeException(
                    "IndexError",
                    "Cannot set on a non indexable value: ${stringify(toSet)} (index = ${stringify(index)})",
                    buildTable { table ->
                        table["index"] = index
                        table["value"] = value
                        table["toSet"] = toSet
                    }
                )
            }
        }
    }

    /**
     * Calls the top of the stack with [nargs] arguments.
     *
     * @param nargs The number of arguments to pass to the callable.
     * @param span The span of the call. May be null if unknown.
     */
    fun call(nargs: Int, selfProvided: Boolean = false, span: Span? = null) {
        val callable = stack.pop()
        if (callable is CallableValue) {
            callValue(callable, nargs, selfProvided, span)
        } else {
            val possiblyCallable = callable.lookUp("__call__".metisValue())
            if (possiblyCallable is CallableValue) {
                callValue(possiblyCallable, nargs, selfProvided, span)
            } else {
                throw MetisRuntimeException(
                    "TypeError",
                    "Cannot call non-callable: ${stringify(callable)}",
                    buildTable { table ->
                        table["callable"] = callable
                    }
                )
            }
        }
    }

    /**
     * Performs a metamethod call on the value [nargs] below the top of the stack.
     *
     * @param nargs The number of arguments to pass to the metamethod.
     * @param metamethod The name of the metamethod to call.
     * @param span The span of the call. May be null if unknown.
     */
    fun metaCall(nargs: Int, metamethod: String, span: Span? = null) {
        val value = stack.getFromTop(nargs)
        val meta = value.metatable?.lookUp(metamethod.metisValue())
        if (meta is CallableValue) {
            callValue(meta, nargs + 1, true, span)
        } else {
            throw MetisRuntimeException(
                "TypeError",
                "Cannot call metamethod $metamethod on value of type ${typeToName(value::class)}",
                buildTable { table ->
                    table["value"] = value
                }
            )
        }
    }

    private fun callValue(value: CallableValue, nargs: Int, selfProvided: Boolean, span: Span? = null) {
        val stackBottom = stack.size - nargs
        val (reqArgs, requiresSelf) = value.arity
        var argc = nargs
        if (selfProvided && !requiresSelf) {
            // this is so cursed I love it
            stack.removeAt(stack.size - 1 - --argc)
        }
        while (argc < reqArgs) {
            stack.push(Value.Null)
            argc++
        }
        while (argc > reqArgs) {
            stack.pop()
            argc--
        }
        val executor = value.call(argc)
        if (value is OneShotFunction) {
            executor.step(this)
        } else {
            if (callStack.size >= recursionLimit) {
                throw MetisRuntimeException(
                    "RecursionError",
                    "Recursion limit exceeded",
                    buildTable { table ->
                        table["limit"] = recursionLimit.metisValue()
                    }
                )
            }
            callStack.push(
                CallFrame(
                    executor,
                    stackBottom,
                    (value as? Chunk.Instance)?.id,
                    span
                )
            )
        }
    }

    /**
     * Wraps the top [values] values of the stack into a [Value.List].
     *
     * @param values The number of values to wrap.
     */
    fun wrapToList(values: Int) {
        val list = ArrayDeque<Value>(values)
        repeat(values) {
            list.addFirst(stack.pop())
        }
        stack.push(Value.List(list))
    }

    /**
     * Wraps the top [values] values of the stack into a [Value.Table].
     *
     * @param values The number of values to wrap.
     */
    fun wrapToTable(values: Int) {
        val table = Value.Table()
        repeat(values) {
            val value = stack.pop()
            val key = stack.pop()
            table[key] = value
        }
        stack.push(table)
    }

    /**
     * Creates a new [MetisRuntimeException] and pushes it to the stack.
     *
     * @param type The type of the exception.
     */
    fun newError(type: String) {
        val companionData = stack.pop().convertTo<Value.Table>()
        val message = stack.pop().convertTo<Value.String>().value
        stack.push(MetisRuntimeException(type, message, companionData))
    }

    /**
     * Performs a `not` operation on the top of the stack.
     */
    fun not() = stack.push((!stack.pop().convertTo<Value.Boolean>().value).metisValue())

    /**
     * Performs a `is` operation on the top of the stack.
     *
     * The top of the stack must be like so:
     * ```
     * value2
     * value1
     * ```
     */
    fun `is`() = stack.push(Value.Boolean.of(stack.pop() === stack.pop()))

    /**
     * Stringifies the given [value] according to its `__str__` metamethod.
     *
     * @param value The value to stringify.
     * @return The stringified value.
     */
    fun stringify(value: Value): String {
        /*
        stack.push(value)
        stack.push(value)
        stack.push("__str__".metisValue())
        index()
        val callStackSize = callStack.size
        call(1)
        while (callStack.size > callStackSize) {
            if (step() == StepResult.FINISHED) break
        }
        return stack.pop().stringValue()

         */
        return value.toString()
    }
}

internal data class CallFrame(
    val executing: CallableValue.Executor,
    val stackBottom: Int,
    val id: UUID?,
    val span: Span?
)

private val metatableString = Value.String("metatable")
private val indexString = Value.String("__index__")
private val setString = Value.String("__set__")