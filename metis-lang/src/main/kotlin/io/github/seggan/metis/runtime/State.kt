package io.github.seggan.metis.runtime

import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.debug.Breakpoint
import io.github.seggan.metis.debug.DebugInfo
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.modules.DefaultModuleManager
import io.github.seggan.metis.runtime.modules.ModuleManager
import io.github.seggan.metis.runtime.modules.impl.StdioModule
import io.github.seggan.metis.runtime.value.*
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

class State {

    val stack = ArrayDeque<Value>()
    internal val callStack = ArrayDeque<CallFrame>()

    val globals = TableValue()

    var debugInfo: DebugInfo? = null
    var debugMode = false
    val breakpoints = mutableListOf<Breakpoint>()

    var moduleManager: ModuleManager = DefaultModuleManager(this)

    init {
        globals["true"] = BooleanValue.TRUE
        globals["false"] = BooleanValue.FALSE
        globals["null"] = Value.Null
    }

    fun loadCoreGlobals() {
        globals["boolean"] = BooleanValue.metatable
        globals["bytes"] = BytesValue.metatable
        globals["list"] = ListValue.metatable
        globals["error"] = MetisRuntimeException.metatable
        globals["int"] = NumberValue.Int.metatable
        globals["float"] = NumberValue.Float.metatable
        globals["string"] = StringValue.metatable
        globals["table"] = TableValue.metatable

        val pkg = TableValue()
        pkg["loaders"] = ListValue().also(moduleManager::addDefaultLoaders)
        pkg["loaded"] = TableValue()
        pkg["path"] = mutableListOf("./".metis(), "/usr/lib/metis/".metis()).metis()
        globals["package"] = pkg
    }

    fun loadStandardLibrary() {
        moduleManager.nativeLoader.addNativeModule(StdioModule)
    }

    fun stepOnce(): StepResult {
        if (callStack.isEmpty()) return StepResult.Finished
        val result = callStack.peek().executor.step(this)
        if (result is StepResult.Finished) {
            callStack.pop()
            return if (callStack.isEmpty()) StepResult.Finished else StepResult.Continue
        }
        return result
    }

    fun runTillComplete() {
        while (stepOnce() != StepResult.Finished) {
            // side effects go brr
        }
    }

    fun getLocal(index: Int) {
        stack.push(stack[index - callStack.peek().stackBottom])
    }

    fun setLocal(index: Int) {
        stack[index - callStack.peek().stackBottom] = stack.pop()
    }

    fun getIndex() {
        checkStack(2)
        if (stack.peek() == metatableString) {
            stack.pop()
            stack[0] = stack[0].metatable ?: Value.Null
        } else {
            metaCall(1, Metamethod.GET)
        }
    }

    fun setIndex() {
        checkStack(3)
        if (stack[1] == metatableString) {
            val metatable = stack.pop()
            stack.pop()
            val value = stack.pop()
            value.metatable = metatable.into<TableValue>()
        } else {
            metaCall(2, Metamethod.SET)
        }
    }

    fun call(nargs: Int, selfProvided: Boolean = false, span: Span? = null) {
        checkStack(nargs + 1)
        val callable = stack.peek()
        if (callable is CallableValue) {
            stack.pop()
            callValue(callable, nargs, selfProvided, span)
        } else {
            metaCall(nargs, Metamethod.CALL, span)
        }
    }

    fun metaCall(nargs: Int, metamethod: String, span: Span? = null) {
        checkStack(nargs)
        val value = stack.peek()
        val metatable = value.metatable ?: throw MetisKeyError(
            value,
            "metatable".metis(),
            "Could not find metatable for value"
        )
        val method = metatable.metaGet(metamethod.metis()) ?: throw MetisKeyError(
            value,
            metamethod.metis(),
            "Could not find metamethod '$metamethod' in metatable"
        )
        callValue(method.into(), nargs + 1, true, span)
    }

    private fun callValue(value: CallableValue, nargs: Int, selfProvided: Boolean, span: Span?) {
        val arity = value.arity
        var numArgs = nargs
        if (selfProvided && !arity.needsSelf) {
            stack.removeAt(nargs)
            numArgs--
        } else if (!selfProvided && arity.needsSelf) {
            stack.add(nargs, Value.Null)
            numArgs++
        }
        while (numArgs > arity.nargs) {
            stack.pop()
            numArgs--
        }
        while (numArgs < arity.nargs) {
            stack.push(Value.Null)
            numArgs++
        }
        val executor = value.call()
        callStack.push(CallFrame(executor, stack.size - numArgs, span))
    }

    fun not() {
        checkStack(1)
        stack[0] = stack[0].into<BooleanValue>().value.not().metis()
    }

    fun checkStack(n: Int) {
        if (stack.size < n) {
            throw MetisInternalError("Stack underflow: expected $n values, got ${stack.size}")
        }
    }
}

internal data class CallFrame(
    val executor: CallableValue.Executor,
    val stackBottom: Int,
    val span: Span?
)

private val metatableString = "metatable".metis()