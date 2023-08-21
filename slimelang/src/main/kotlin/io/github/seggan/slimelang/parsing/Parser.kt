package io.github.seggan.slimelang.parsing

import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.lexer.regexToken

class Parser : Grammar<Unit>() {
    override val rootParser = TODO()

    val id by regexToken("""[a-zA-Z_][a-zA-Z0-9_]*""")
}