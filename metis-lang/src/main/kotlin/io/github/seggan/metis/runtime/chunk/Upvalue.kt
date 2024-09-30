package io.github.seggan.metis.runtime.chunk

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.util.pop
import java.util.UUID

data class Upvalue(val name: String, val index: Int, val function: UUID) {

    fun newInstance(state: State): Instance {
        val existing = state.openUpvalues.find { it.template === this }
        if (existing != null) return existing
        val instance = Instance(state, null)
        state.openUpvalues.addFirst(instance)
        return instance
    }

    inner class Instance internal constructor(private val owner: State, private var value: Value?) {

        private val frame = owner.callStack.first { it.id == function }

        val template = this@Upvalue

        val closed: Boolean get() = value != null

        fun get(): Value {
            return value ?: owner.stack[frame.getLocalIndex(index)]
        }

        fun set(set: Value) {
            if (closed) {
                value = set
            } else {
                owner.stack[frame.getLocalIndex(index)] = set
            }
        }

        fun close() {
            require(value == null) { "Upvalue already closed" }
            value = owner.stack.pop()
            owner.openUpvalues.remove(this)
        }
    }
}