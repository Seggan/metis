package io.github.seggan.metis.runtime.modules

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.value.*

class DefaultModuleManager(private val state: State) : ModuleManager() {

    override val loaders: MutableList<ModuleLoader>
        get() = state.globals.getHierarchy("package", "loaders")
            .nullToValue()
            .listValue
            .leaveInstancesOf<ModuleLoader>()

    override val loaded: MutableMap<Value, Value>
        get() = state.globals.getHierarchy("package", "loaded")
            .nullToValue()
            .tableValue
}

private inline fun <reified T> MutableList<*>.leaveInstancesOf(): MutableList<T> {
    val it = iterator()
    while (it.hasNext()) {
        if (it.next() !is T) {
            it.remove()
        }
    }
    @Suppress("UNCHECKED_CAST")
    return this as MutableList<T>
}