package io.github.seggan.metis.compilation

typealias Register = Int

interface RegisterHolder {

    fun nextFreeRegister(): Register

    fun freeRegisters(registers: List<Register>)

    fun freeRegisters(vararg registers: Register) {
        freeRegisters(registers.toList())
    }
}