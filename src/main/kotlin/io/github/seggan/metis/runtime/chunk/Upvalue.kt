package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

data class Upvalue(
    val name: String,
    val index: Int,
    val callDepth: Int
) {

    fun newInstance(state: State): Instance {
        for (upvalue in state.openUpvalues) {
            if (upvalue.template === this) {
                return upvalue
            }
        }
        val instance = Instance(null)
        state.openUpvalues.addFirst(instance)
        return instance
    }

    inner class Instance internal constructor(private var value: Value?) {

        val template: Upvalue
            get() = this@Upvalue

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
    }
}