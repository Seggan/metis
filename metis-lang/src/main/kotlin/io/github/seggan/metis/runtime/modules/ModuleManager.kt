package io.github.seggan.metis.runtime.modules

import io.github.seggan.metis.runtime.value.ListValue
import io.github.seggan.metis.runtime.value.Value

abstract class ModuleManager {

    val nativeLoader = NativeModuleLoader()

    abstract val loaders: MutableList<ModuleLoader>

    abstract val loaded: MutableMap<Value, Value>

    open fun addDefaultLoaders(loaders: ListValue) {
        loaders.add(nativeLoader)
    }
}