@file:JvmName("Stacks")

package io.github.seggan.metis.util

fun <E> ArrayDeque<E>.push(value: E) = this.addFirst(value)
fun <E> ArrayDeque<E>.pop() = this.removeFirst()
fun <E> ArrayDeque<E>.peek() = this.first()