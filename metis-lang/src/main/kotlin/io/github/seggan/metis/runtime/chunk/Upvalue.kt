package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.util.*

/**
 * An upvalue. Used in the implementation of closures.
 *
 * @property name The name of the upvalue.
 * @property index The index of the upvalue's local variable.
 * @property function The [UUID] of the function that owns the upvalue.
 */
data class Upvalue(
    val name: String,
    val index: Int,
    val function: UUID
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
        val bottom = state.callStack.first { it.id == function }.stackBottom
        val instance = Instance(null, bottom)
        state.openUpvalues.addFirst(instance)
        return instance
    }

    /**
     * An instance of the upvalue.
     *
     * @see Upvalue.newInstance
     */
    inner class Instance internal constructor(internal var value: Value?, private val stackBottom: Int) {

        val template: Upvalue = this@Upvalue

        fun get(state: State) {
            state.stack.push(value ?: state.stack[stackBottom + index])
        }

        fun set(state: State) {
            val toSet = state.stack.pop()
            if (value != null) {
                value = toSet
            } else {
                state.stack[stackBottom + index] = toSet
            }
        }

        fun close(state: State) {
            value = state.stack.pop()
            state.openUpvalues.remove(this)
        }
    }
}