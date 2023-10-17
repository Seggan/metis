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
import io.github.seggan.metis.util.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems

class State(val isChildState: Boolean = false) {

    val globals = Value.Table()
    private val nativeLibraries = mutableListOf<NativeLibrary>()

    val stack = Stack()
    internal val callStack = ArrayDeque<CallFrame>()

    var stdout: OutputStream = System.out
    var stderr: OutputStream = System.err
    var stdin: InputStream = System.`in`

    var fileSystem: FileSystem = FileSystems.getDefault()
    var cwd = fileSystem.getPath(System.getProperty("user.dir")).toAbsolutePath()

    internal val openUpvalues = ArrayDeque<Upvalue.Instance>()

    private var throwingException: MetisRuntimeException? = null

    val localsOffset: Int
        get() = callStack.peek().stackBottom

    var debugMode = false
    var debugInfo: DebugInfo? = null
    val breakpoints = mutableListOf<Breakpoint>()

    var recursionLimit = 2048

    companion object {
        init {
            Intrinsics.registerDefault()
        }

        private val coreScripts = listOf("collection", "list", "string", "table", "number", "range", "io", "package")
    }

    init {
        globals["true"] = Value.Boolean.TRUE
        globals["false"] = Value.Boolean.FALSE
        globals["null"] = Value.Null

        for ((name, value) in Intrinsics.intrinsics) {
            globals[name] = value
        }

        val io = Value.Table()
        io["stdout"] = wrapOutStream(stdout)
        io["stderr"] = wrapOutStream(stderr)
        io["stdin"] = wrapInStream(stdin)

        io["in_stream"] = inStreamMetatable
        io["out_stream"] = outStreamMetatable
        globals["io"] = io

        globals["string"] = Value.String.metatable
        globals["number"] = Value.Number.metatable
        globals["table"] = Value.Table.metatable
        globals["list"] = Value.List.metatable
        globals["bytes"] = Value.Bytes.metatable

        val pkg = Value.Table()
        pkg["loaders"] = Value.List(mutableListOf(ResourceLoader, NativeLoader(nativeLibraries)))
        globals["package"] = pkg

        globals["path"] = initPathLib()

        runCode(CodeSource("core") { State::class.java.classLoader.getResource("core.metis")!!.readText() })
        for (script in coreScripts) {
            runCode(CodeSource(script) { State::class.java.classLoader.getResource("./core/$it.metis")!!.readText() })
        }

        addNativeLibrary(OsLib)
        addNativeLibrary(RegexLib)
    }

    fun loadChunk(chunk: Chunk) {
        stack.push(chunk.Instance(this))
    }

    fun addNativeLibrary(lib: NativeLibrary) {
        nativeLibraries.add(lib)
    }

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
                    if (throwingException != null) {
                        err = throwingException!!
                        throwingException = null
                    } else {
                        stepResult = StepResult.CONTINUE
                        caught = true
                        break
                    }
                }
                val (executor, bottom, span) = callStack.peek()
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
                            it.template.callDepth == callStack.size && it.template.index == i
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

    @Suppress("ControlFlowWithEmptyBody")
    fun runTillComplete() {
        while (step() != StepResult.FINISHED) {
        }
    }

    fun runCode(source: CodeSource) {
        val lexer = Lexer(source)
        val parser = Parser(lexer.lex(), source)
        val compiler = Compiler()
        loadChunk(compiler.compileCode(source.name, parser.parse()))
        call(0)
        runTillComplete()
        stack.pop()
    }

    fun index() {
        if (stack.peek() == metatableString) {
            stack.pop()
            stack.push(stack.pop().metatable.orNull())
        } else {
            val getter = stack.getFromTop(1).metatable?.lookUp(indexString)
            if (getter is CallableValue) {
                callValue(getter, 2)
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

    fun set() {
        if (stack.getFromTop(1) == metatableString) {
            val toSet = stack.pop().convertTo<Value.Table>()
            stack.pop()
            stack.pop().metatable = toSet
        } else {
            val setter = stack.getFromTop(2).metatable?.lookUp(setString)
            if (setter is CallableValue) {
                callValue(setter, 3)
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

    fun call(nargs: Int, span: Span? = null) {
        val callable = stack.pop()
        if (callable is CallableValue) {
            callValue(callable, nargs, span)
        } else {
            val possiblyCallable = callable.lookUp("__call__".metisValue())
            if (possiblyCallable is CallableValue) {
                callValue(possiblyCallable, nargs, span)
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

    private fun callValue(value: CallableValue, nargs: Int, span: Span? = null) {
        val stackBottom = stack.size - nargs
        val (reqArgs, isVarargs) = value.arity
        var argc = nargs
        while (argc < reqArgs) {
            stack.push(Value.Null)
            argc++
        }
        if (!isVarargs) {
            while (argc > reqArgs) {
                stack.pop()
                argc--
            }
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
            callStack.push(CallFrame(executor, stackBottom, span))
        }
    }

    fun wrapToList(values: Int) {
        val list = ArrayDeque<Value>(values)
        repeat(values) {
            list.addFirst(stack.pop())
        }
        stack.push(Value.List(list))
    }

    fun wrapToTable(values: Int) {
        val table = Value.Table()
        repeat(values) {
            val value = stack.pop()
            val key = stack.pop()
            table[key] = value
        }
        stack.push(table)
    }

    fun newError(type: String) {
        val companionData = stack.pop().convertTo<Value.Table>()
        val message = stack.pop().convertTo<Value.String>().value
        stack.push(MetisRuntimeException(type, message, companionData))
    }

    fun not() = stack.push((!stack.pop().convertTo<Value.Boolean>().value).metisValue())

    fun `is`() = stack.push(Value.Boolean.of(stack.pop() === stack.pop()))

    fun stringify(value: Value): String {
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
    }
}

internal data class CallFrame(val executing: CallableValue.Executor, val stackBottom: Int, val span: Span?)

private val metatableString = Value.String("metatable")
private val indexString = Value.String("__index__")
private val setString = Value.String("__set__")