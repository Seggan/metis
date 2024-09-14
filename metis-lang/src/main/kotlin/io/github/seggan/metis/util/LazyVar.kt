package io.github.seggan.metis.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class LazyVar<T>(private val init: () -> T) : ReadWriteProperty<Any, T> {

    private var value: T? = null
    private var set = false

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (!set) {
            value = init()
            set = true
        }
        return value!!
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        this.value = value
        set = true
    }
}