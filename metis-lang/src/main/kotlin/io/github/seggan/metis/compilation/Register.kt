package io.github.seggan.metis.compilation

data class Register(val index: Int, var name: String? = null) {
    override fun toString() = if (name != null) "$name:$index" else index.toString()
    override fun equals(other: Any?) = other is Register && index == other.index
    override fun hashCode() = index.hashCode()
}

interface RegisterHolder {

    fun nextFreeRegister(): Register

    fun freeRegisters(registers: List<Register>)

    fun freeRegisters(vararg registers: Register) {
        freeRegisters(registers.toList())
    }
}