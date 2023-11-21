package io.github.seggan.metis.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.seggan.metis.debug.Debugger
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.parsing.Lexer
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.util.MetisException
import org.unbescape.html.HtmlEscape
import kotlin.io.path.absolutePathString

fun main(args: Array<String>) = Main.main(args)

private object Main : CliktCommand(name = "metis") {

    val source by mutuallyExclusiveOptions(
        option("-c", "--code", help = "The code to run").convert {
            CodeSource.constant("<argument>", it)
        },
        option("-f", "--file", help = "The file to run").path(mustExist = true, canBeDir = false).convert {
            System.setProperty("user.dir", it.parent.absolutePathString())
            CodeSource.fromPath(it)
        },
        option("-i", "--stdin", help = "Read code from stdin").flag(default = false).convert {
            if (it) {
                CodeSource.constant("<stdin>", generateSequence(::readlnOrNull).joinToString("\n"))
            } else {
                CodeSource("INVALID") { throw AssertionError() }
            }
        },
    ).single().required()
    val debug by option("-d", "--debug", help = "Enable debug mode").flag(default = false)
    val printChunk by option("-p", "--print-chunk", help = "Print the chunk").flag(default = false)
    val syntaxHighlight by option(
        "-s",
        "--syntax-highlight",
        help = "Generate syntax highlighted HTML from the source"
    ).flag(default = false)

    override fun run() {
        try {
            if (syntaxHighlight) {
                println(highlight(Lexer.lex(source.mapText(HtmlEscape::unescapeHtml))))
            } else {
                val chunk = Chunk.load(source)
                if (printChunk) {
                    println(chunk)
                }
                val state = State()
                state.loadChunk(chunk)
                state.call(0)
                if (debug) {
                    Debugger(state, source.name).debug()
                } else {
                    state.runTillComplete()
                }
            }
        } catch (e: MetisException) {
            System.err.println(e.report(source.name))
            if (e.cause != null) {
                throw e.cause!!
            }
            if (debug) {
                e.printStackTrace()
            }
        }
    }
}