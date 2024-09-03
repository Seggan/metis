package io.github.seggan.metis.parsing

import org.intellij.lang.annotations.Language

/**
 * The Metis lexer. Converts source code into a list of [Token]s.
 */
object Lexer {

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
        regex("#.*(?:\\n|$)", Token.Type.COMMENT)
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
        text("+=", Token.Type.PLUS_EQUALS)
        text("-", Token.Type.MINUS)
        text("-=", Token.Type.MINUS_EQUALS)
        text("*", Token.Type.STAR)
        text("*=", Token.Type.STAR_EQUALS)
        text("**", Token.Type.DOUBLE_STAR)
        text("**=", Token.Type.DOUBLE_STAR_EQUALS)
        text("/", Token.Type.SLASH)
        text("/=", Token.Type.SLASH_EQUALS)
        text("//", Token.Type.DOUBLE_SLASH)
        text("//=", Token.Type.DOUBLE_SLASH_EQUALS)
        text("%", Token.Type.PERCENT)
        text("%=", Token.Type.PERCENT_EQUALS)
        text("&", Token.Type.AMPERSAND)
        text("&=", Token.Type.AMP_EQUALS)
        text("|", Token.Type.PIPE)
        text("|=", Token.Type.PIPE_EQUALS)
        text("^", Token.Type.CARET)
        text("^=", Token.Type.CARET_EQUALS)
        text("<<", Token.Type.SHL)
        text("<<=", Token.Type.SHL_EQUALS)
        text(">>", Token.Type.SHR)
        text(">>=", Token.Type.SHR_EQUALS)
        text(">>>", Token.Type.SHRU)
        text(">>>=", Token.Type.SHRU_EQUALS)
        text("~", Token.Type.TILDE)
        text("..<", Token.Type.RANGE)
        text("..=", Token.Type.INCLUSIVE_RANGE)
        text("?:", Token.Type.ELVIS)
        text("?:=", Token.Type.ELVIS_EQUALS)
        text("?", Token.Type.QUESTION_MARK)
        keyword("if", Token.Type.IF)
        keyword("else", Token.Type.ELSE)
        keyword("elif", Token.Type.ELIF)
        keyword("while", Token.Type.WHILE)
        keyword("for", Token.Type.FOR)
        keyword("in", Token.Type.IN)
        regex("""\bnot\s+in\b""", Token.Type.NOT_IN)
        keyword("is", Token.Type.IS)
        regex("""\bis\s+not\b""", Token.Type.IS_NOT)
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
        keyword("error", Token.Type.ERROR)
        keyword("except", Token.Type.EXCEPT)
        keyword("finally", Token.Type.FINALLY)
        keyword("raise", Token.Type.RAISE)
        keyword("import", Token.Type.IMPORT)
        regex("\\s+", Token.Type.WHITESPACE)
        regex("[0-9]+", Token.Type.INTEGER)
        regex("[0-9]+(\\.[0-9]+)?(e[0-9]+(\\.[0-9]+)?)?", Token.Type.FLOAT)
        regex("[a-zA-Z_][a-zA-Z0-9_]*", Token.Type.IDENTIFIER)
        matchers.add(TokenMatcher.StringyLiteral('"', Token.Type.STRING))
        matchers.add(TokenMatcher.StringyLiteral('\'', Token.Type.BYTES))
    }

    /**
     * Lexes the source code.
     *
     * @param source The [CodeSource] to lex.
     * @return The list of [Token]s.
     */
    fun lex(source: CodeSource): List<Token> {
        val tokens = mutableListOf<Token>()
        val code = StringBuilder(source.text)
        var pos = 0
        while (code.isNotEmpty()) {
            val matched = mutableListOf<Pair<TokenMatcher.Match, Token>>()
            for (matcher in matchers) {
                val match = matcher.parse(code, pos)
                if (match != null) {
                    matched.add(
                        match to Token(
                            matcher.type, match.text, Span(
                                pos,
                                pos + match.length,
                                source
                            )
                        )
                    )
                }
            }
            val bestMatch = matched.maxByOrNull { it.first } ?: throw SyntaxException(
                "Unexpected character '${code[0]}'",
                pos,
                Span(pos, pos + 1, source)
            )
            tokens.add(bestMatch.second)
            val length = bestMatch.first.length
            pos += length
            code.delete(0, length)
        }
        return tokens + Token(Token.Type.EOF, "", Span(pos, pos, source))
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
                        return Match(nextText.substring(0, i + 1), i + 1)
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