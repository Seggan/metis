package io.github.seggan.slimelang.parsing

import org.intellij.lang.annotations.Language

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
        regex("[a-zA-Z_][a-zA-Z0-9_]*", Token.Type.IDENTIFIER)
        regex("[0-9]+(\\.[0-9]+)?(e[0-9]+(\\.[0-9]+)?)?", Token.Type.NUMBER)
        matchers.add(TokenMatcher.StringLiteral())
        keyword("if", Token.Type.IF)
        keyword("else", Token.Type.ELSE)
        keyword("elif", Token.Type.ELIF)
        keyword("while", Token.Type.WHILE)
        keyword("for", Token.Type.FOR)
        keyword("in", Token.Type.IN)
        keyword("break", Token.Type.BREAK)
        keyword("continue", Token.Type.CONTINUE)
        keyword("return", Token.Type.RETURN)
        keyword("fun", Token.Type.FUN)
        keyword("global", Token.Type.GLOBAL)
        keyword("local", Token.Type.LOCAL)
        keyword("end", Token.Type.END)
        regex("//.*\\n", Token.Type.COMMENT)
        regex("/\\*.*?\\*/", Token.Type.COMMENT)
        regex("\\s+", Token.Type.WHITESPACE)
    }

    private var pos = 0

    fun lex(): List<Token> {
        val tokens = mutableListOf<Token>()
        val code = StringBuilder(this.code)
        while (code.isNotEmpty()) {
            var matched = false
            for (matcher in matchers) {
                val match = matcher.parse(code, pos)
                if (match != null) {
                    if (matcher.type != Token.Type.WHITESPACE) {
                        tokens.add(Token(matcher.type, match, Span(pos, pos + match.length)))
                    }
                    pos += match.length
                    code.delete(0, match.length)
                    matched = true
                    break
                }
            }
            if (!matched) throw ParseException("Unknown token", Span(pos, pos))
        }
        return tokens
    }
}

private sealed interface TokenMatcher {

    val type: Token.Type

    fun parse(nextText: CharSequence, pos: Int): String?

    class Text(private val text: String, override val type: Token.Type) : TokenMatcher {
        override fun parse(nextText: CharSequence, pos: Int): String? {
            if (nextText.startsWith(text)) {
                return text
            }
            return null
        }
    }

    class Regex(@Language("RegExp") regex: String, override val type: Token.Type) : TokenMatcher {

        private val regex = "^$regex".toRegex()

        override fun parse(nextText: CharSequence, pos: Int): String? {
            val match = regex.find(nextText)
            if (match != null) {
                return match.value
            }
            return null
        }
    }

    class Keyword(private val keyword: String, override val type: Token.Type) : TokenMatcher {
        override fun parse(nextText: CharSequence, pos: Int): String? {
            if (nextText.startsWith(keyword) && !nextText.getOrElse(keyword.length) { '\u0000' }.isLetterOrDigit()) {
                return keyword
            }
            return null
        }
    }

    class StringLiteral : TokenMatcher {

        override val type = Token.Type.STRING

        override fun parse(nextText: CharSequence, pos: Int): String? {
            if (nextText.startsWith('"')) {
                var escaped = false
                var i = 1
                while (i < nextText.length) {
                    val char = nextText[i]
                    if (char == '"' && !escaped) {
                        return nextText.substring(0, i + 1)
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                            .replace("\\t", "\t")
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                    }
                    escaped = char == '\\' && !escaped
                    i++
                }
            }
            return null
        }
    }
}

data class Token(val type: Type, val text: String, val span: Span) {
    enum class Type {
        IDENTIFIER,
        STRING,
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
        BREAK,
        CONTINUE,
        FUN,
        GLOBAL,
        LOCAL,
        END,
    }
}