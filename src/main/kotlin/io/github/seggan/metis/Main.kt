package io.github.seggan.metis

import io.github.seggan.metis.compilation.Compiler
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.parsing.Parser
import io.github.seggan.metis.runtime.State
import kotlin.io.path.Path
import kotlin.io.path.readText

internal fun main(args: Array<String>) {
    val code = Path(args[0]).readText()
    try {
        val toks = Lexer(code).lex()
        println(toks)
        val ast = Parser(toks).parse()
        println(ast)
        val compiler = Compiler(args[0], code)
        val chunk = compiler.compileCode(args[0], ast)
        println(chunk)
        val state = State()
        //state.debugMode = true
        state.loadChunk(chunk)
        state.call(0)
        state.runTillComplete()
    } catch (e: MetisException) {
        System.err.println(e.report(code, args[0]))
    }
}