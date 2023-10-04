package io.github.seggan.metis

import io.github.seggan.metis.compilation.Compiler
import io.github.seggan.metis.debug.Debugger
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.parsing.Parser
import io.github.seggan.metis.runtime.State
import kotlin.io.path.Path

internal fun main(args: Array<String>) {
    val source = CodeSource.from(Path(args.firstOrNull() ?: error("No file specified")))
    try {
        val state = State()
        val toks = Lexer(source).lex()
        val ast = Parser(toks, source).parse()
        val compiler = Compiler()
        val chunk = compiler.compileCode(source.name, ast)
        println(chunk)
        state.loadChunk(chunk)
        state.call(0)
        if (args.contains("--debug")) {
            Debugger(state, source.name).debug()
        } else {
            state.runTillComplete()
        }
    } catch (e: MetisException) {
        System.err.println(e.report(source.name))
        if (args.contains("--debug")) {
            e.printStackTrace()
        }
    }
}