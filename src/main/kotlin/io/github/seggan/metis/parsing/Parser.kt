package io.github.seggan.metis.parsing

import io.github.seggan.metis.BinOp
import io.github.seggan.metis.UnOp
import io.github.seggan.metis.Visibility
import io.github.seggan.metis.parsing.Token.Type.*
import io.github.seggan.metis.runtime.Value
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
        val returnSpan = Span(0, tokens.lastOrNull()?.span?.end ?: 0)
        return statements + AstNode.Return(AstNode.Literal(Value.Null, returnSpan), returnSpan)
    }

    private fun parseBlock(): AstNode.Block {
        val result = mutableListOf<AstNode.Statement>()
        val startSpan = previous.span
        while (tryConsume(END) == null) {
            result.add(parseStatement())
        }
        return AstNode.Block(result, startSpan + previous.span)
    }

    private fun parseStatement(): AstNode.Statement {
        tryParse(::parseVarDecl)?.let { return it }
        tryParse(::parseVarAssign)?.let { return it }
        tryParse(::parseExpression)?.let { return it }
        if (tryConsume(FN) != null) {
            val startSpan = previous.span
            val target = parseAssignTarget()
            val fn = parseFunctionDef(startSpan)
            if (target is AstNode.Var) {
                return AstNode.VarDecl(Visibility.GLOBAL, target.name, fn, fn.span)
            }
            return AstNode.VarAssign(target, fn, fn.span)
        }
        if (tryConsume(DO) != null) return parseBlock()
        if (tryConsume(RETURN) != null) {
            val startSpan = previous.span
            val expr = tryParse(::parseExpression) ?: AstNode.Literal(Value.Null, startSpan)
            return AstNode.Return(expr, startSpan + expr.span)
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

    private fun parsePostfix(allowCalls: Boolean = true): AstNode.Expression {
        var expr = parsePrimary()
        val allowed = arrayOf(OPEN_BRACKET, DOT, COLON) + if (allowCalls) arrayOf(OPEN_PAREN) else arrayOf()
        while (true) {
            val op = tryConsume(*allowed) ?: break
            expr = when (op.type) {
                OPEN_PAREN -> AstNode.Call(expr, parseArgList(::parseExpression), op.span + previous.span)

                OPEN_BRACKET -> {
                    val index = parseExpression()
                    consume(CLOSE_BRACKET)
                    AstNode.Index(expr, index, op.span + previous.span)
                }

                DOT -> {
                    val name = parseId()
                    AstNode.Index(
                        expr,
                        AstNode.Literal(Value.String(name.text), name.span),
                        op.span + previous.span
                    )
                }

                COLON -> {
                    val name = parseId().text
                    consume(OPEN_PAREN)
                    AstNode.ColonCall(
                        expr,
                        name,
                        parseArgList(::parseExpression),
                        op.span + previous.span
                    )
                }

                else -> throw AssertionError()
            }
        }
        return expr
    }

    private inline fun <T> parseArgList(subParser: () -> T): List<T> {
        val args = mutableListOf<T>()
        while (true) {
            if (tryConsume(CLOSE_PAREN) != null) break
            args.add(subParser())
            if (tryConsume(CLOSE_PAREN) != null) break
            consume(COMMA)
        }
        return args
    }

    private fun parsePrimary(): AstNode.Expression {
        val token = consume(STRING, BYTES, NUMBER, OPEN_PAREN, IDENTIFIER, FN)
        return when (token.type) {
            STRING -> AstNode.Literal(Value.String(token.text.substring(1, token.text.length - 1).intern()), token.span)
            BYTES -> AstNode.Literal(
                Value.Bytes(token.text.substring(1, token.text.length - 1).encodeToByteArray()),
                token.span
            )

            NUMBER -> AstNode.Literal(Value.Number.from(token.text.toDouble()), token.span)
            OPEN_PAREN -> parseExpression().also { consume(CLOSE_PAREN) }
            IDENTIFIER -> AstNode.Var(token.text, token.span)
            FN -> parseFunctionDef(token.span)
            else -> throw AssertionError()
        }
    }

    private fun parseFunctionDef(startSpan: Span): AstNode.FunctionDef {
        consume(OPEN_PAREN)
        val args = parseArgList { parseId().text }
        var block = if (tryConsume(EQUALS) != null) {
            val expr = parseExpression()
            AstNode.Block(listOf(AstNode.Return(expr, expr.span)), expr.span)
        } else parseBlock()
        if (block.lastOrNull() !is AstNode.Return) {
            val nodes = block.toMutableList()
            nodes.add(AstNode.Return(AstNode.Literal(Value.Null, block.span), block.span))
            block = AstNode.Block(nodes, block.span)
        }
        return AstNode.FunctionDef(args, block, startSpan + block.span)
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
        val start = consume(GLOBAL, LOCAL)
        val visibility = if (start.type == GLOBAL) Visibility.GLOBAL else Visibility.LOCAL
        val name = parseId().text
        consume(EQUALS)
        val value = tryParse(::parseExpression)
        val endSpan = value?.span ?: current.span
        return AstNode.VarDecl(
            visibility,
            name,
            value ?: AstNode.Literal(Value.Null, endSpan),
            start.span + endSpan
        )
    }

    private fun parseVarAssign(): AstNode.VarAssign {
        val name = parseAssignTarget()
        consume(EQUALS)
        val value = parseExpression()
        return AstNode.VarAssign(name, value, name.span + value.span)
    }

    private fun parseAssignTarget(): AstNode.AssignTarget {
        val target = parsePostfix(false)
        if (target is AstNode.AssignTarget) return target
        throw ParseException("Invalid variable/function assignment target", target.span)
    }

    private fun parseId(): Token {
        return consume(IDENTIFIER) // can add soft keywords
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