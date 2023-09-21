package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.pop
import io.github.seggan.metis.runtime.push

data class Upvalue(
    val name: String,
    private val index: Int,
    private val callDepth: Int
) {

    fun newInstance(state: State): Instance {
        for (upvalue in state.openUpvalues) {
            if (upvalue.isInstanceOf(this)) {
                return upvalue
            }
        }
        val instance = Instance(null)
        state.openUpvalues.addFirst(instance)
        return instance
    }

    inner class Instance internal constructor(private var value: Value?) {

        fun get(state: State) {
            state.stack.push(value ?: state.stack[state.callStack[callDepth].stackBottom + index])
        }

        fun set(state: State) {
            val toSet = state.stack.pop()
            if (value != null) {
                value = toSet
            } else {
                state.stack[state.callStack[callDepth].stackBottom + index] = toSet
            }
        }

        fun close(state: State) {
            value = state.stack.pop()
            state.openUpvalues.remove(this)
        }

        fun isInstanceOf(upvalue: Upvalue) = upvalue === this@Upvalue
    }
}