package io.github.seggan.metis.parsing

import org.intellij.lang.annotations.Language
import java.util.*

class Lexer(private val code: String) {

    private val matchers = mutableListOf<TokenMatcher>()

    private fun text(text: String, type: Token.Type) {
        matchers.add(TokenMatcher.Text(text, type))
    }

    private fun regex(@Language("RegExp") regex: String, type: Token.Type) {
        matchers.add(TokenMatcher.Regex(regex, type))
    }

    private fun keyword(keyword: String, type: Token.Type) {
        matchers.add(TokenMatcher.Keyword(keyword, type))
    }

    init {
        text("(", Token.Type.OPEN_PAREN)
        text(")", Token.Type.CLOSE_PAREN)
        text("{", Token.Type.OPEN_BRACE)
        text("}", Token.Type.CLOSE_BRACE)
        text("[", Token.Type.OPEN_BRACKET)
        text("]", Token.Type.CLOSE_BRACKET)
        text(",", Token.Type.COMMA)
        text(";", Token.Type.SEMICOLON)
        text(".", Token.Type.DOT)
        text(":", Token.Type.COLON)
        text("==", Token.Type.DOUBLE_EQUALS)
        text("!=", Token.Type.NOT_EQUALS)
        text("=", Token.Type.EQUALS)
        text(">", Token.Type.GREATER_THAN)
        text("<", Token.Type.LESS_THAN)
        text(">=", Token.Type.GREATER_THAN_OR_EQUAL)
        text("<=", Token.Type.LESS_THAN_OR_EQUAL)
        text("+", Token.Type.PLUS)
        text("-", Token.Type.MINUS)
        text("*", Token.Type.STAR)
        text("/", Token.Type.SLASH)
        text("%", Token.Type.PERCENT)
        keyword("if", Token.Type.IF)
        keyword("else", Token.Type.ELSE)
        keyword("elif", Token.Type.ELIF)
        keyword("while", Token.Type.WHILE)
        keyword("for", Token.Type.FOR)
        keyword("in", Token.Type.IN)
        keyword("break", Token.Type.BREAK)
        keyword("continue", Token.Type.CONTINUE)
        keyword("return", Token.Type.RETURN)
        keyword("and", Token.Type.AND)
        keyword("or", Token.Type.OR)
        keyword("not", Token.Type.NOT)
        keyword("fn", Token.Type.FN)
        keyword("global", Token.Type.GLOBAL)
        keyword("let", Token.Type.LET)
        keyword("do", Token.Type.DO)
        keyword("end", Token.Type.END)
        regex("//.*\\n", Token.Type.COMMENT)
        regex("/\\*.*?\\*/", Token.Type.COMMENT)
        regex("\\s+", Token.Type.WHITESPACE)
        regex("[0-9]+(\\.[0-9]+)?(e[0-9]+(\\.[0-9]+)?)?", Token.Type.NUMBER)
        regex("[a-zA-Z_][a-zA-Z0-9_]*", Token.Type.IDENTIFIER)
        matchers.add(TokenMatcher.StringyLiteral('"', Token.Type.STRING))
        matchers.add(TokenMatcher.StringyLiteral('\'', Token.Type.BYTES))
    }

    private var pos = 0

    fun lex(): List<Token> {
        val tokens = mutableListOf<Token>()
        val code = StringBuilder(this.code)
        while (code.isNotEmpty()) {
            val matched = mutableListOf<Pair<TokenMatcher.Match, Token>>()
            for (matcher in matchers) {
                val match = matcher.parse(code, pos)
                if (match != null) {
                    matched.add(match to Token(matcher.type, match.text, Span(pos, pos + match.length)))
                }
            }
            val bestMatch = matched.maxByOrNull { it.first } ?: throw ParseException(
                "Unexpected character '${code[0]}'",
                Span(pos, pos + 1)
            )
            tokens.add(bestMatch.second)
            val length = bestMatch.first.length
            pos += length
            code.delete(0, length)
        }
        return tokens
    }
}

private sealed interface TokenMatcher {

    val type: Token.Type

    fun parse(nextText: CharSequence, pos: Int): Match?

    class Text(private val text: String, override val type: Token.Type) : TokenMatcher {
        override fun parse(nextText: CharSequence, pos: Int): Match? {
            if (nextText.startsWith(text)) {
                return text.toMatch()
            }
            return null
        }
    }

    class Regex(@Language("RegExp") regex: String, override val type: Token.Type) : TokenMatcher {

        private val regex = "^$regex".toRegex()

        override fun parse(nextText: CharSequence, pos: Int): Match? {
            val match = regex.find(nextText)
            if (match != null) {
                return match.value.toMatch()
            }
            return null
        }
    }

    class Keyword(private val keyword: String, override val type: Token.Type) : TokenMatcher {
        override fun parse(nextText: CharSequence, pos: Int): Match? {
            if (nextText.startsWith(keyword) && !nextText.getOrElse(keyword.length) { '\u0000' }.isLetterOrDigit()) {
                return keyword.toMatch()
            }
            return null
        }
    }

    class StringyLiteral(private val delim: Char, override val type: Token.Type) : TokenMatcher {
        override fun parse(nextText: CharSequence, pos: Int): Match? {
            if (nextText.startsWith(delim)) {
                var escaped = false
                var i = 1
                while (i < nextText.length) {
                    val char = nextText[i]
                    if (char == delim && !escaped) {
                        return Match(
                            nextText.substring(0, i + 1)
                                .replace("\\n", "\n")
                                .replace("\\r", "\r")
                                .replace("\\t", "\t")
                                .replace("\\\\", "\\")
                                .replace("\\$delim", delim.toString()),
                            i + 1
                        )
                    }
                    escaped = char == '\\' && !escaped
                    i++
                }
            }
            return null
        }
    }

    data class Match(val text: String, val length: Int) : Comparable<Match> {
        override fun compareTo(other: Match): Int {
            return length.compareTo(other.length)
        }
    }
}

private fun String.toMatch(): TokenMatcher.Match {
    return TokenMatcher.Match(this, length)
}

data class Token(val type: Type, val text: String, val span: Span) {
    enum class Type {
        IDENTIFIER,
        STRING,
        BYTES,
        NUMBER,
        OPEN_PAREN,
        CLOSE_PAREN,
        OPEN_BRACE,
        CLOSE_BRACE,
        OPEN_BRACKET,
        CLOSE_BRACKET,
        SEMICOLON,
        EQUALS,
        DOUBLE_EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        LESS_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN_OR_EQUAL,
        DOT,
        COLON,
        COMMA,
        COMMENT,
        WHITESPACE,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,
        IF,
        ELSE,
        ELIF,
        WHILE,
        FOR,
        IN,
        RETURN,
        AND,
        OR,
        NOT,
        BREAK,
        CONTINUE,
        FN,
        GLOBAL,
        LET,
        DO,
        END;

        operator fun plus(other: Type): EnumSet<Type> = EnumSet.of(this, other)
    }
}

operator fun EnumSet<Token.Type>.plus(other: Token.Type): EnumSet<Token.Type> =
    EnumSet.copyOf(this).apply { add(other) }