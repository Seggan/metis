package io.github.seggan.metis.runtime.modules.impl

import io.github.seggan.metis.runtime.intrinsics.oneArgFunction
import io.github.seggan.metis.runtime.modules.NativeModule
import io.github.seggan.metis.runtime.value.TableValue

object StdioModule : NativeModule {
    override val name = "stdio"

    override fun init(table: TableValue) {
        table["print"] = oneArgFunction { s ->
            println(s.metisToString())
            null
        }
    }
}