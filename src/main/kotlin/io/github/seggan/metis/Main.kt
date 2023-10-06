package io.github.seggan.metis

import io.github.seggan.metis.debug.Debugger
import io.github.seggan.metis.parsing.CodeSource
import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.Chunk
import kotlin.io.path.Path

internal fun main(args: Array<String>) {
    val source = CodeSource.from(Path(args.firstOrNull() ?: error("No file specified")))
    try {
        val state = State()
        val chunk = Chunk.load(source)
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