package io.github.seggan.slimelang

import io.github.seggan.slimelang.errors.SlException
import io.github.seggan.slimelang.errors.report
import io.github.seggan.slimelang.parsing.Lexer
import kotlin.io.path.Path
import kotlin.io.path.readText

internal fun main(args: Array<String>) {
    val code = Path(args[0]).readText()
    val lexer = Lexer(code)
    try {
        println(lexer.lex())
    } catch (e: SlException) {
        println(e.report(code, args[0]))
    }
    /*
    val compiler = Compiler()
    val chunk = compiler.compileCode("<string>", ast)
    println(chunk)
    val state = State()
    state.loadChunk(chunk)
    while (!state.step()) {
    }
    println(state.stack)
    println(state.globals)

     */
}