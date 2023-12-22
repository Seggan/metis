package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.util.peek
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
        val instance = Instance(state, null)
        state.openUpvalues.addFirst(instance)
        return instance
    }

    /**
     * An instance of the upvalue.
     *
     * @see Upvalue.newInstance
     */
    inner class Instance internal constructor(
        private val owningState: State,
        private var value: Value?
    ) {

        val template: Upvalue = this@Upvalue

        val isClosed: Boolean
            get() = value != null

        private val stackBottom = owningState.callStack.first { it.id == function }.stackBottom

        fun get(state: State) {
            val toPush = if (value == null) {
                var realState = state
                while (realState !== owningState) {
                    realState = realState.parentState ?: error("Upvalue not found in state chain")
                }
                realState.stack[stackBottom + index]
            } else {
                value!!
            }
            state.stack.push(toPush)
        }

        fun set(state: State) {
            val toSet = state.stack.peek()
            if (value != null) {
                value = toSet
            } else {
                var realState = state
                while (realState !== owningState) {
                    realState = realState.parentState ?: error("Upvalue not found in state chain")
                }
                realState.stack[stackBottom + index] = toSet
            }
        }

        fun close(state: State) {
            require(value == null)
            require(state === owningState)
            value = state.stack.pop()
            state.openUpvalues.remove(this)
        }
    }
}