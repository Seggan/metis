@file:JvmName("Stacks")

package io.github.seggan.metis.util

import io.github.seggan.metis.runtime.value.Value

typealias Stack = ArrayDeque<Value>

fun <E> ArrayDeque<E>.push(value: E) = this.addFirst(value)
fun <E> ArrayDeque<E>.pop() = this.removeFirst()
fun <E> ArrayDeque<E>.peek() = this.first()