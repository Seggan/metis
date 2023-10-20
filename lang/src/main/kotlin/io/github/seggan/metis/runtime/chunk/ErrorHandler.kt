package io.github.seggan.metis.runtime.chunk

/**
 * An error handler.
 *
 * @property errorName The name of the error.
 * @property label The label to jump to.
 */
data class ErrorHandler(val errorName: String, val label: Insn.Label)