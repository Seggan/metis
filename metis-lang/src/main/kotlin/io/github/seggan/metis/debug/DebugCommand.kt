package io.github.seggan.metis.debug

import io.github.seggan.metis.runtime.State

internal class DebugCommand(vararg names: String, val action: State.(List<String>) -> Unit) {
    val names = names.toList()
}
