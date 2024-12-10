package io.github.seggan.metis.runtime.intrinsics

import io.github.seggan.metis.runtime.value.NativeValue
import io.github.seggan.metis.runtime.value.TableValue
import io.github.seggan.metis.runtime.value.Value
import io.github.seggan.metis.runtime.value.asNativeObj
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class NativeValueTemplate<T : Any> {

    protected abstract val clazz: Class<T>

    private val table = TableValue()

    fun constructNativeValue(obj: T): NativeValue {
        return NativeValue(obj, table)
    }

    protected fun metaProperty(value: Value) =
        PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Value>> { _, property ->
            table[property.name] = value
            ReadOnlyProperty { _, _ -> value }
        }

    protected inline fun selfOneArgFunction(crossinline block: NativeScope.(T) -> Value?) =
        metaProperty(oneArgFunction(true) { self -> block(self.asNativeObj(clazz)) })

    protected inline fun selfTwoArgFunction(crossinline block: NativeScope.(T, Value) -> Value?) =
        metaProperty(twoArgFunction(true) { self, a -> block(self.asNativeObj(clazz), a) })

    protected inline fun selfThreeArgFunction(crossinline block: NativeScope.(T, Value, Value) -> Value?) =
        metaProperty(threeArgFunction(true) { self, a, b -> block(self.asNativeObj(clazz), a, b) })
}