package io.github.seggan.metis.runtime.value.intrinsics

import io.github.seggan.metis.runtime.value.MetisNull

@Suppress("unused")
object IntrinsicFunctions {
    val println = oneShotFunction { value ->
        println(value)
        MetisNull
    }
}