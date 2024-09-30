package io.github.seggan.metis.debug

import io.github.seggan.metis.runtime.State
import io.github.seggan.metis.runtime.chunk.StepResult
import io.github.seggan.metis.util.MetisException

class Debugger(private val state: State, private val sourceName: String) {

    private val commands = listOf(
        DebugCommand("step", "s") { stepOnce() },
        DebugCommand("verboseStep", "vs", "v") {
            stepOnce()
            print("Previous instruction: ")
            println(debugInfo?.insn ?: error("No debug info yet available"))
            println("\nCall stack: ")
            printStacktrace(this)
        },
        DebugCommand("next", "n") { next() },
        DebugCommand("verboseNext", "vn") {
            next()
            print("Previous instruction: ")
            println(dbg.insn)
            println("\nCall stack: ")
            printStacktrace(this)
        },
        DebugCommand("continue", "c") {
            @Suppress("ControlFlowWithEmptyBody")
            while (stepOnce() == StepResult.Continue) {
            }
        },
        DebugCommand("breakpoint", "break", "b") {
            val location = it.firstOrNull() ?: error("No location specified")
            val match = locationRegex.matchEntire(location) ?: error("Invalid location")
            val name = match.groups["name"]?.value
            val line = match.groups["line"]?.value?.toInt() ?: error("Invalid line")
            state.breakpoints.add(Breakpoint(line, name))
        },
        DebugCommand("breakpoints", "breaks", "bs") {
            for ((i, breakpoint) in state.breakpoints.withIndex()) {
                println("$i: $breakpoint")
            }
        },
        DebugCommand("delete", "del", "d") {
            val index = it.firstOrNull()?.toIntOrNull() ?: error("No breakpoint specified")
            state.breakpoints.removeAt(index)
        },
        DebugCommand("into", "i") {
            val startSize = state.callStack.size
            while (state.callStack.size <= startSize) {
                if (stepOnce() !is StepResult.Continue) break
            }
        },
        DebugCommand("out", "o") {
            val startSize = state.callStack.size
            while (state.callStack.size >= startSize) {
                if (stepOnce() !is StepResult.Continue) break
            }
        },
        DebugCommand("backtrace", "bt") {
            printStacktrace(this)
        },
        DebugCommand("stack", "st") {
            val bottoms = callStack.map { it.stackBottom }
            for ((i, value) in stack.withIndex()) {
                if (stack.size - i in bottoms) {
                    println("---")
                }
                println(value)
            }
        },
        DebugCommand("instruction", "insn", "is") {
            println(dbg.insn)
        },
    )

    fun debug() {
        state.debugMode = true
        while (true) {
            print("> ")
            val line = readlnOrNull() ?: break
            val args = line.split(" ")
            val cmd = args.firstOrNull() ?: continue
            val command = commands.firstOrNull { cmd.trim() in it.names }
            if (command == null) {
                println("Unknown command: $cmd")
                continue
            }
            try {
                command.action(state, args.drop(1))
            } catch (e: MetisException) {
                System.err.println(e.report(sourceName))
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        state.debugMode = false
    }

    private fun printStacktrace(state: State) {
        println(state.debugInfo?.span?.fancyToString() ?: "<unknown>")
        val it = state.callStack.iterator()
        while (it.hasNext()) {
            val span = it.next().span
            if (!it.hasNext()) break
            if (span != null) {
                println(span.fancyToString())
            } else {
                println("<unknown>")
            }
        }
    }
}

private val locationRegex = """(?:(?<name>[a-zA-Z0-9_.]+):)?(?<line>[0-9]+)""".toRegex()

private val State.dbg: DebugInfo get() = debugInfo ?: error("No debug info yet available")

private fun State.next() {
    val currentSpan = dbg.span
    var nextSpan = currentSpan
    while (currentSpan.line == nextSpan.line || currentSpan.source != nextSpan.source) {
        stepOnce()
        nextSpan = dbg.span
    }
}