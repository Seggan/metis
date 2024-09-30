package io.github.seggan.metis.runtime.modules

import io.github.seggan.metis.runtime.intrinsics.NativeScope
import io.github.seggan.metis.runtime.intrinsics.SuspendingExecutor
import io.github.seggan.metis.runtime.value.CallableValue
import io.github.seggan.metis.runtime.value.TableValue
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.runtime.value.stringValue
import io.github.seggan.metis.util.pop
import java.io.Serial

abstract class ModuleLoader : CallableValue {

    override var metatable: TableValue? = TableValue()

    final override val arity = CallableValue.Arity(1, false)

    @Suppress("serial")
    final override fun call() = object : SuspendingExecutor() {
        override suspend fun NativeScope.execute(): Value? {
            return getModule(state.stack.pop().stringValue)
        }
    }

    abstract suspend fun NativeScope.getModule(name: String): Value?

    companion object {
        @Serial
        private const val serialVersionUID: Long = -3561508049893395859L
    }
}