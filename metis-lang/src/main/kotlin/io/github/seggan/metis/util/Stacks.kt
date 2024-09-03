@file:JvmName("Stacks")

package io.github.seggan.metis.util

import io.github.seggan.metis.runtime.value.Value

typealias Stack = ArrayDeque<Value>

fun <E> ArrayDeque<E>.push(value: E) = this.addLast(value)
fun <E> ArrayDeque<E>.pop() = this.removeLast()
fun <E> ArrayDeque<E>.peek() = this.last()
fun <E> ArrayDeque<E>.getFromTop(index: Int): E = this[this.size - index - 1]