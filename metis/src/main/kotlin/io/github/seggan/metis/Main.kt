package io.github.seggan.metis

import io.github.seggan.metis.errors.MetisException
import io.github.seggan.metis.errors.report
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.parsing.Parser
import kotlin.io.path.Path
import kotlin.io.path.readText

internal fun main(args: Array<String>) {
    val code = Path(args[0]).readText()
    try {
        val toks = Lexer(code).lex()
        println(toks)
        val ast = Parser(toks).parse()
        println(ast)
    } catch (e: MetisException) {
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