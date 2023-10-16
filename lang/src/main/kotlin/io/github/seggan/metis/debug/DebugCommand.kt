package io.github.seggan.metis.debug

import io.github.seggan.metis.runtime.State

internal data class DebugCommand(val names: List<String>, val action: State.(List<String>) -> Unit) {
    constructor(name: String, action: State.(List<String>) -> Unit) : this(listOf(name), action)
    constructor(vararg names: String, action: State.(List<String>) -> Unit) : this(names.toList(), action)
}
