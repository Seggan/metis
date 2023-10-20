package io.github.seggan.metis.app

import io.github.seggan.metis.parsing.Token
import io.github.seggan.metis.parsing.Token.Type.*
import kotlinx.html.span
import kotlinx.html.stream.createHTML

fun highlight(tokens: List<Token>): String = createHTML(prettyPrint = false).span {
    for (token in tokens) {
        val cls = when (token.type) {
            STRING -> "string"
            NUMBER -> "number"
            BYTES -> "bytes"
            COMMENT -> "comment"

            IF, ELSE, ELIF, WHILE, FOR, IN, NOT_IN, IS, IS_NOT, RETURN, AND, OR, NOT, BREAK, CONTINUE,
            FN, GLOBAL, LET, DO, END, ERROR, EXCEPT, FINALLY, RAISE
            -> "keyword"

            PLUS, MINUS, STAR, SLASH, PERCENT, RANGE, ELVIS, QUESTION_MARK, EQUALS, DOUBLE_EQUALS, NOT_EQUALS,
            GREATER_THAN, LESS_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL
            -> "operator"

            DOT, COLON, COMMA, SEMICOLON -> "punctuation"

            OPEN_PAREN, CLOSE_PAREN, OPEN_BRACE, CLOSE_BRACE, OPEN_BRACKET, CLOSE_BRACKET -> "bracket"

            IDENTIFIER -> if (token.text in specialIdentifier) "keyword-literal" else "identifier"

            WHITESPACE, EOF -> null
        }
        if (cls != null) {
            span(classes = "highlight $cls") {
                +token.text
            }
        } else {
            +token.text
        }
    }
}

private val specialIdentifier = setOf("true", "false", "null")