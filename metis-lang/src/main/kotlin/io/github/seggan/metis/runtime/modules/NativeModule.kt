package io.github.seggan.metis.runtime.modules

import io.github.seggan.metis.runtime.value.TableValue

interface NativeModule {
    val name: String
    fun init(table: TableValue)
}