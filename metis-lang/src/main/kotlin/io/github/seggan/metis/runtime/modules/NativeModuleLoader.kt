package io.github.seggan.metis.runtime.modules

import io.github.seggan.metis.runtime.intrinsics.NativeScope
import io.github.seggan.metis.runtime.value.TableValue
import io.github.seggan.metis.runtime.value.Value
import java.io.Serial

class NativeModuleLoader private constructor(
    private val nativeModules: MutableMap<String, NativeModule>
) : ModuleLoader() {

    constructor() : this(mutableMapOf())

    constructor(other: NativeModuleLoader) : this(other.nativeModules.toMutableMap())

    fun addNativeModule(module: NativeModule) {
        nativeModules[module.name] = module
    }

    override suspend fun NativeScope.getModule(name: String): Value? {
        val module = nativeModules[name] ?: return null
        val table = TableValue()
        module.init(table)
        return table
    }

    companion object {
        @Serial
        private const val serialVersionUID: Long = 6878291186320925182L
    }
}