package io.github.seggan.metis.runtime

import io.github.seggan.metis.MetisException
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
import io.github.seggan.metis.runtime.intrinsics.Intrinsics
import io.github.seggan.metis.runtime.intrinsics.OneShotFunction
import io.github.seggan.metis.runtime.intrinsics.initPathLib
import io.github.seggan.metis.runtime.intrinsics.wrapOutStream
import io.github.seggan.metis.util.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems

class State(val isChildState: Boolean = false) {

    val globals = Value.Table()

    val stack = Stack()

    internal val callStack = ArrayDeque<CallFrame>()

    var stdout: OutputStream = System.out
    var stderr: OutputStream = System.err
    var stdin: InputStream = System.`in`

    var fileSystem: FileSystem = FileSystems.getDefault()

    internal val openUpvalues = ArrayDeque<Upvalue.Instance>()

    val localsOffset: Int
        get() = callStack.peek().stackBottom

    var debugMode = false
    var debugInfo: DebugInfo? = null
    val breakpoints = mutableListOf<Breakpoint>()

    companion object {
        init {
            Intrinsics.registerDefault()
        }

        val coreScripts = mutableListOf("string", "table", "list")
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

        globals["io"] = io

        globals["string"] = Value.String.metatable
        globals["number"] = Value.Number.metatable
        globals["table"] = Value.Table.metatable
        globals["list"] = Value.List.metatable

        val pkg = Value.Table()
        pkg["loaded"] = Value.List()
        pkg["path"] = Value.List()
        pkg["loaders"] = Value.List()
        globals["package"] = pkg

        globals["path"] = initPathLib()

        runCode(CodeSource("core") { State::class.java.classLoader.getResource("core.metis")!!.readText() })
        for (script in coreScripts) {
            runCode(CodeSource(script) { State::class.java.classLoader.getResource("./$it.metis")!!.readText() })
        }
    }

    fun loadChunk(chunk: Chunk) {
        stack.push(chunk.Instance(this))
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
        } catch (e: MetisException) {
            for (i in callStack.lastIndex downTo 0) {
                val span = callStack[i].span
                if (span != null) {
                    e.addStackFrame(span)
                }
            }
            throw e
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
            val getter = stack.getFromTop(1).lookUp("__index__".metisValue())
            if (getter is CallableValue) {
                callValue(getter, 2)
            } else {
                throw MetisRuntimeException("IndexError", "Cannot index a non indexable value")
            }
        }
    }

    fun set() {
        if (stack.getFromTop(1) == metatableString) {
            val toSet = stack.pop().convertTo<Value.Table>()
            stack.pop()
            stack.pop().metatable = toSet
        } else {
            val setter = stack.getFromTop(2).lookUp(Value.String("__set__"))
            if (setter is CallableValue) {
                callValue(setter, 3)
                stack.pop()
            } else {
                throw MetisRuntimeException("IndexError", "Cannot set index on a non indexable value")
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
                throw MetisRuntimeException("TypeError", "Cannot call non-callable")
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

    fun not() {
        stack.push(Value.Boolean.of(!stack.pop().convertTo<Value.Boolean>().value))
    }
}

internal data class CallFrame(val executing: CallableValue.Executor, val stackBottom: Int, val span: Span?)

private val metatableString = Value.String("metatable")