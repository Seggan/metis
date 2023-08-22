package io.github.seggan.slimelang

import com.github.h0tk3y.betterParse.parser.parseToEnd
import io.github.seggan.slimelang.parsing.SlParser

internal fun main(args: Array<String>) {
    val code = """
        local a = 1
    """.trimIndent()
    val parser = SlParser()
    val tokens = parser.tokenizer.tokenize(code)
    println(tokens.toList())
    val ast = parser.parseToEnd(tokens)
    println(ast)
}