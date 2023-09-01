package io.github.seggan.slimelang.parsing

import io.github.seggan.slimelang.BinOp
import io.github.seggan.slimelang.UnOp
import io.github.seggan.slimelang.Visibility
import io.github.seggan.slimelang.parsing.Token.Type.*
import io.github.seggan.slimelang.runtime.Value
import java.util.*

class Parser(tokens: List<Token>) {

    private val tokens = tokens.filter { it.type !in SKIPPED_TOKENS }

    private var index = 0

    private val previous: Token
        get() = tokens[index - 1]

    private val current: Token
        get() = tokens[index]

    private val next: Token
        get() = tokens[index + 1]

    fun parse(): List<AstNode.Statement> {
        val statements = mutableListOf<AstNode.Statement>()
        while (index < tokens.size) {
            skip(SEMICOLON)
            statements.add(parseStatement())
            skip(SEMICOLON)
        }
        return statements
    }

    private fun parseStatement(): AstNode.Statement {
        if (tryConsume(GLOBAL, LOCAL) != null) return parseVarDecl()
        val expr = tryParse(::parseExpression)
        if (expr != null) {
            return expr
        }
        throw ParseException("Unknown statement", current.span)
    }

    private fun parseExpression() = parseOr()
    private fun parseOr() = parseBinOp(::parseAnd, mapOf(OR to BinOp.OR))
    private fun parseAnd() = parseBinOp(::parseEquality, mapOf(AND to BinOp.AND))
    private fun parseEquality() = parseBinOp(
        ::parseComparison,
        mapOf(DOUBLE_EQUALS to BinOp.EQ, NOT_EQUALS to BinOp.NOT_EQ)
    )

    private fun parseComparison() = parseBinOp(
        ::parseAddition, mapOf(
            LESS_THAN to BinOp.LESS,
            LESS_THAN_OR_EQUAL to BinOp.LESS_EQ,
            GREATER_THAN to BinOp.GREATER,
            GREATER_THAN_OR_EQUAL to BinOp.GREATER_EQ
        )
    )

    private fun parseAddition() = parseBinOp(::parseMultiplication, mapOf(PLUS to BinOp.PLUS, MINUS to BinOp.MINUS))
    private fun parseMultiplication() = parseBinOp(::parseUnary, mapOf(STAR to BinOp.TIMES, SLASH to BinOp.DIV))
    private fun parseUnary(): AstNode.Expression {
        val op = tryConsume(NOT, MINUS)
        if (op != null) {
            val expr = parseUnary()
            return AstNode.UnaryOp(if (op.type == NOT) UnOp.NOT else UnOp.NEG, expr, op.span + expr.span)
        }
        return parsePostfix()
    }

    private fun parsePostfix(): AstNode.Expression {
        var expr = parsePrimary()
        while (true) {
            val op = tryConsume(OPEN_PAREN, OPEN_BRACKET, DOT) ?: break
            expr = when (op.type) {
                OPEN_PAREN -> {
                    val args = mutableListOf<AstNode.Expression>()
                    while (tryConsume(CLOSE_PAREN) == null) {
                        args.add(parseExpression())
                        consume(COMMA)
                    }
                    AstNode.Call(expr, args, op.span + previous.span)
                }

                OPEN_BRACKET -> {
                    val index = parseExpression()
                    consume(CLOSE_BRACKET)
                    AstNode.Index(expr, index, op.span + previous.span)
                }

                DOT -> {
                    val name = consume(IDENTIFIER)
                    AstNode.Index(
                        expr,
                        AstNode.Literal(Value.String(name.text), name.span),
                        op.span + previous.span
                    )
                }

                else -> throw AssertionError()
            }
        }
        return expr
    }

    private fun parsePrimary(): AstNode.Expression {
        val token = consume(STRING, NUMBER, OPEN_PAREN, IDENTIFIER)
        return when (token.type) {
            STRING -> AstNode.Literal(Value.String(token.text), token.span)
            NUMBER -> AstNode.Literal(Value.Number(token.text.toDouble()), token.span)
            OPEN_PAREN -> parseExpression().also { consume(CLOSE_PAREN) }
            IDENTIFIER -> AstNode.Var(token.text, token.span)
            else -> throw AssertionError()
        }
    }

    private inline fun parseBinOp(next: () -> AstNode.Expression, types: Map<Token.Type, BinOp>): AstNode.Expression {
        var expr = next()
        val keys = types.keys.toTypedArray()
        while (tryConsume(*keys) != null) {
            expr = AstNode.BinaryOp(
                expr,
                types[previous.type] ?: throw AssertionError(),
                next()
            )
        }
        return expr
    }

    private fun parseVarDecl(): AstNode.VarDecl {
        val startSpan = previous.span
        val visibility = if (previous.type == GLOBAL) Visibility.GLOBAL else Visibility.LOCAL
        val name = consume(IDENTIFIER).text
        consume(EQUALS)
        val value = tryParse(::parseExpression)
        val endSpan = value?.span ?: current.span
        return AstNode.VarDecl(
            visibility,
            name,
            value ?: AstNode.Literal(Value.Null, endSpan),
            startSpan + endSpan
        )
    }

    private fun tryConsume(vararg types: Token.Type): Token? {
        if (index >= tokens.size) return null
        val token = tokens[index]
        if (token.type in types) {
            index++
            return token
        }
        return null
    }

    private fun consume(vararg types: Token.Type): Token {
        return tryConsume(*types) ?: throw ParseException(
            "Expected ${types.joinToString(" or ")}, got ${current.type}",
            current.span
        )
    }

    @Suppress("ControlFlowWithEmptyBody")
    private fun skip(vararg types: Token.Type) {
        while (tryConsume(*types) != null) {
        }
    }

    private inline fun <T : AstNode> tryParse(parser: () -> T): T? {
        val index = this.index
        return try {
            parser()
        } catch (e: ParseException) {
            this.index = index
            null
        }
    }
}

private val SKIPPED_TOKENS = EnumSet.of(WHITESPACE, COMMENT)