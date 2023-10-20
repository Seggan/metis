package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

/**
 * An upvalue. Used in the implementation of closures.
 *
 * @property name The name of the upvalue.
 * @property index The index of the upvalue's local variable.
 * @property callDepth The call depth of the upvalue's function.
 */
data class Upvalue(
    val name: String,
    val index: Int,
    val callDepth: Int
) {

    /**
     * Creates a new instance of the upvalue.
     *
     * @param state The state to create the instance in.
     * @return The new instance.
     */
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

    /**
     * An instance of the upvalue.
     *
     * @see Upvalue.newInstance
     */
    inner class Instance internal constructor(internal var value: Value?) {

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