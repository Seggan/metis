package io.github.seggan.metis.app

import io.github.seggan.metis.parsing.Token
import io.github.seggan.metis.parsing.Token.Type.*
import kotlinx.html.classes
import kotlinx.html.code
import kotlinx.html.span
import kotlinx.html.stream.createHTML

fun highlight(tokens: List<Token>): String = createHTML(prettyPrint = false).code {
    for (token in tokens) {
        val cls = when (token.type) {
            STRING -> "string"
            NUMBER -> "number"
            BYTES -> "bytes"
            COMMENT -> "comment"
            IF, ELSE, ELIF, WHILE, FOR, IN, NOT_IN, IS, IS_NOT, RETURN, AND, OR, NOT, BREAK, CONTINUE,
            FN, GLOBAL, LET, DO, END, ERROR, EXCEPT, FINALLY, RAISE
            -> "keyword"

            else -> null
        }
        span {
            if (cls != null) {
                classes = setOf("highlight", cls)
            }
            +token.text
        }
    }
}