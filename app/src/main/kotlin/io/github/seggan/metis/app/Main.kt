package io.github.seggan.metis.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.seggan.metis.debug.Debugger
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.util.MetisException
import kotlin.io.path.absolutePathString

fun main(args: Array<String>) = Main.main(args)

private object Main : CliktCommand() {

    val file by argument("file", "The file to run").path(mustExist = true, canBeDir = false)
    val debug by option("-d", "--debug", help = "Enable debug mode").flag(default = false)

    override fun run() {
        val source = CodeSource.fromPath(file)
        val oldCwd = System.getProperty("user.dir")
        System.setProperty("user.dir", file.parent.absolutePathString())
        try {
            val chunk = Chunk.load(source)
            val state = State()
            state.loadChunk(chunk)
            state.call(0)
            if (debug) {
                Debugger(state, source.name).debug()
            } else {
                state.runTillComplete()
            }
        } catch (e: MetisException) {
            System.err.println(e.report(source.name))
            if (debug) {
                e.printStackTrace()
            }
        } finally {
            System.setProperty("user.dir", oldCwd)
        }
    }
}