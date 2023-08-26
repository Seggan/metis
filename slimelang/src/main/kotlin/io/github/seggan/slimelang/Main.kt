package io.github.seggan.slimelang

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.github.seggan.slimelang.compilation.Compiler
import io.github.seggan.slimelang.parsing.SlParser
import io.github.seggan.slimelang.runtime.State
import kotlin.io.path.Path
import kotlin.io.path.readText

internal fun main(args: Array<String>) {
    val code = Path(args[0]).readText()
    val parser = SlParser()
    val ast = parser.parseToEnd(code)
    println(ast)
    val compiler = Compiler()
    val chunk = compiler.compileCode("<string>", ast)
    println(chunk)
    val state = State()
    state.loadChunk(chunk)
    while (!state.step()) {
    }
    println(state.stack)
    println(state.globals)
}