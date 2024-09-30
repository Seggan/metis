package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.runtime.value.CallableValue
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.util.push
import java.io.Serial
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume

abstract class SuspendingExecutor : CallableValue.Executor, Continuation<Value?> {

    override val context = EmptyCoroutineContext

    private var currentState = ExecutionState.NOT_STARTED
    private lateinit var continuation: Continuation<Unit>
    private var stepResult: StepResult = StepResult.Continue

    private var returnValue: Value? = null

    final override fun step(state: State): StepResult {
        return when (currentState) {
            ExecutionState.NOT_STARTED -> {
                currentState = ExecutionState.RUNNING
                val coro = suspend { NativeScopeImpl(state).execute() }
                continuation = coro.createCoroutineUnintercepted(this)
                continuation.resume(Unit)
                StepResult.Continue
            }

            ExecutionState.RUNNING -> {
                continuation.resume(Unit)
                if (stepResult == StepResult.Finished) StepResult.Continue else stepResult
            }

            ExecutionState.DONE -> {
                if (returnValue != null) {
                    state.stack.push(returnValue!!)
                    returnValue = null
                }
                StepResult.Finished
            }
        }
    }

    override fun resumeWith(result: Result<Value?>) {
        returnValue = result.getOrThrow() ?: Value.Null
        currentState = ExecutionState.DONE
    }

    abstract suspend fun NativeScope.execute(): Value?

    private inner class NativeScopeImpl(override val state: State) : NativeScope {
        override suspend fun continueStep(result: StepResult) {
            stepResult = result
            return suspendCoroutineUninterceptedOrReturn {
                continuation = it
                COROUTINE_SUSPENDED
            }
        }
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = 8946074591453746488L
    }
}

private enum class ExecutionState {
    NOT_STARTED,
    RUNNING,
    DONE
}