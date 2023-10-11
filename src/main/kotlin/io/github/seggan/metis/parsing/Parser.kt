package io.github.seggan.metis.parsing

import io.github.seggan.metis.compilation.BinOp
import io.github.seggan.metis.compilation.UnOp
import io.github.seggan.metis.compilation.Visibility
import io.github.seggan.metis.parsing.Token.Type.*
import io.github.seggan.metis.runtime.Value
import java.util.*

class Parser(tokens: List<Token>, private val source: CodeSource) {

    private val tokens = tokens.filter { it.type !in SKIPPED_TOKENS }

    private var index = 0

    private val previous: Token
        get() = tokens[index - 1]

    private val next: Token
        get() = tokens[index]

    fun parse(): AstNode.Block {
        val statements = mutableListOf<AstNode.Statement>()
        while (tryConsume(EOF) == null) {
            skip(SEMICOLON)
            statements.add(parseStatement())
            skip(SEMICOLON)
        }
        val returnSpan = Span(0, tokens.lastOrNull()?.span?.end ?: 0, source)
        return AstNode.Block(
            statements + AstNode.Return(AstNode.Literal(Value.Null, returnSpan), returnSpan),
            returnSpan
        )
    }

    private fun parseBlock(vararg validEnders: Token.Type): AstNode.Block {
        val result = mutableListOf<AstNode.Statement>()
        val startSpan = previous.span
        while (tryConsume(*validEnders) == null) {
            result.add(parseStatement())
            if (tryConsume(EOF) != null) {
                throw SyntaxException("Unterminated block", index, startSpan)
            }
        }
        return AstNode.Block(result, startSpan + previous.span)
    }

    private fun parseStatement(): AstNode.Statement = oneOf(
        ::parseFunctionDecl,
        ::parseVarDecl,
        ::parseVarAssign,
        ::parseExpression,
        ::parseWhile,
        ::parseFor,
        { consume(IF); parseIf() },
        { consume(DO); parseBlock(END) },
        ::parseReturn,
        ::parseRaise,
        ::parseDoExcept
    )

    private fun parseReturn(): AstNode.Return {
        val startSpan = consume(RETURN).span
        val expr = tryParse(::parseExpression) ?: AstNode.Literal(Value.Null, startSpan)
        return AstNode.Return(expr, startSpan + expr.span)
    }

    private fun parseRaise(): AstNode.Raise {
        val startSpan = consume(RAISE).span
        val expr = parseExpression()
        return AstNode.Raise(expr, startSpan + expr.span)
    }

    private fun parseExpression() = parseOr()
    private fun parseOr() = parseBinOp(::parseAnd, mapOf(OR to BinOp.OR))
    private fun parseAnd() = parseBinOp(::parseEquality, mapOf(AND to BinOp.AND))
    private fun parseEquality() = parseBinOp(
        ::parseComparison,
        mapOf(DOUBLE_EQUALS to BinOp.EQ, NOT_EQUALS to BinOp.NOT_EQ)
    )

    private fun parseComparison() = parseBinOp(
        ::parseIn, mapOf(
            LESS_THAN to BinOp.LESS,
            LESS_THAN_OR_EQUAL to BinOp.LESS_EQ,
            GREATER_THAN to BinOp.GREATER,
            GREATER_THAN_OR_EQUAL to BinOp.GREATER_EQ
        )
    )

    private fun parseIn() = parseBinOp(::parseRange, mapOf(IN to BinOp.IN))
    private fun parseRange() = parseBinOp(::parseAddition, mapOf(RANGE to BinOp.RANGE))
    private fun parseAddition() = parseBinOp(::parseMultiplication, mapOf(PLUS to BinOp.PLUS, MINUS to BinOp.MINUS))
    private fun parseMultiplication() =
        parseBinOp(::parseUnary, mapOf(STAR to BinOp.TIMES, SLASH to BinOp.DIV, PERCENT to BinOp.MOD))

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
                OPEN_PAREN -> AstNode.Call(
                    expr,
                    parseArgList(CLOSE_PAREN, ::parseExpression),
                    op.span + previous.span
                )

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
                        parseArgList(CLOSE_PAREN, ::parseExpression),
                        op.span + previous.span
                    )
                }

                else -> throw AssertionError()
            }
        }
        return expr
    }

    private inline fun <T> parseArgList(closer: Token.Type, subParser: () -> T): List<T> {
        val args = mutableListOf<T>()
        while (true) {
            if (tryConsume(closer) != null) break
            args.add(subParser())
            if (tryConsume(closer) != null) break
            consume(COMMA)
        }
        return args
    }

    private fun parsePrimary(): AstNode.Expression = oneOf(
        ::parseString,
        ::parseBytes,
        ::parseNumber,
        { consume(OPEN_PAREN); parseExpression().also { consume(CLOSE_PAREN) } },
        { AstNode.Var(parseId().text, previous.span) },
        { parseFunctionDef(consume(FN).span) },
        ::parseList,
        ::parseTable,
        ::parseError
    )

    private fun parseString(): AstNode.Literal {
        val token = consume(STRING)
        return AstNode.Literal(Value.String(token.text.substring(1, token.text.length - 1).intern()), token.span)
    }

    private fun parseBytes(): AstNode.Literal {
        val token = consume(BYTES)
        return AstNode.Literal(
            Value.Bytes(token.text.substring(1, token.text.length - 1).encodeToByteArray()),
            token.span
        )
    }

    private fun parseNumber(): AstNode.Literal {
        val token = consume(NUMBER)
        return AstNode.Literal(Value.Number.of(token.text.toDouble()), token.span)
    }

    private fun parseList(): AstNode.ListLiteral {
        val token = consume(OPEN_BRACKET)
        val list = parseArgList(CLOSE_BRACKET, ::parseExpression)
        return AstNode.ListLiteral(list, token.span + previous.span)
    }

    private fun parseTable(): AstNode.TableLiteral {
        val token = consume(OPEN_BRACE)
        val table = parseArgList(CLOSE_BRACE) {
            val key = parseExpression()
            consume(EQUALS)
            val value = parseExpression()
            key to value
        }
        return AstNode.TableLiteral(table, token.span + previous.span)
    }

    private fun parseError(): AstNode.ErrorLiteral {
        val startSpan = consume(ERROR).span
        val type = parseId().text
        consume(OPEN_PAREN)
        val message = parseExpression()
        consume(CLOSE_PAREN)
        val companionData = if (tryConsume(COLON) != null) {
            parseTable()
        } else null
        return AstNode.ErrorLiteral(type, message, companionData, startSpan + previous.span)
    }

    private fun parseFunctionDef(startSpan: Span, name: String? = null): AstNode.FunctionLiteral {
        consume(OPEN_PAREN)
        val args = parseArgList(CLOSE_PAREN) { parseId().text }
        var block = if (tryConsume(EQUALS) != null) {
            val expr = parseExpression()
            AstNode.Block(listOf(AstNode.Return(expr, expr.span)), expr.span)
        } else parseBlock(END)
        if (block.lastOrNull() !is AstNode.Return) {
            val nodes = block.toMutableList()
            nodes.add(AstNode.Return(AstNode.Literal(Value.Null, block.span), block.span))
            block = AstNode.Block(nodes, block.span)
        }
        return AstNode.FunctionLiteral(args, block, name, startSpan + block.span)
    }

    private fun parseFunctionDecl(): AstNode.Statement {
        val global = tryConsume(GLOBAL)
        consume(FN)
        val startSpan = global?.span ?: previous.span
        val target = parseAssignTarget()
        val fn = parseFunctionDef(startSpan, if (target is AstNode.Var) target.name else null)
        return if (target is AstNode.Var) {
            AstNode.VarDecl(
                if (global != null) Visibility.GLOBAL else Visibility.LOCAL,
                target.name,
                fn,
                fn.span
            )
        } else {
            AstNode.VarAssign(target, fn, fn.span)
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
        val start = consume(LET)
        val visibility = if (tryConsume(GLOBAL) != null) Visibility.GLOBAL else Visibility.LOCAL
        val name = parseId().text
        consume(EQUALS)
        val value = tryParse(::parseExpression)
        return AstNode.VarDecl(
            visibility,
            name,
            value ?: AstNode.Literal(Value.Null, start.span + previous.span),
            start.span + previous.span
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
        throw SyntaxException("Invalid variable/function assignment target", index, target.span)
    }

    private fun parseIf(): AstNode.If {
        val startSpan = previous.span
        val condition = parseExpression()
        val then = parseBlock(ELSE, ELIF, END)
        return when (previous.type) {
            ELSE -> {
                val elseBlock = parseBlock(END)
                AstNode.If(condition, then, elseBlock, startSpan + elseBlock.span)
            }

            ELIF -> {
                val elseIf = parseIf()
                AstNode.If(
                    condition,
                    then,
                    AstNode.Block(listOf(elseIf), elseIf.span),
                    startSpan + elseIf.span
                )
            }

            END -> AstNode.If(condition, then, null, startSpan + then.span)
            else -> throw AssertionError()
        }
    }

    private fun parseWhile(): AstNode.While {
        val startSpan = consume(WHILE).span
        val condition = parseExpression()
        val body = parseBlock(END)
        return AstNode.While(condition, body, startSpan + body.span)
    }

    private fun parseFor(): AstNode.For {
        val startSpan = consume(FOR).span
        val name = parseId().text
        consume(IN)
        val expr = parseExpression()
        val body = parseBlock(END)
        return AstNode.For(name, expr, body, startSpan + body.span)
    }

    private fun parseDoExcept(): AstNode.DoExcept {
        val startSpan = consume(DO).span
        val body = parseBlock(EXCEPT, FINALLY)
        val excepts = mutableListOf<AstNode.Except>()
        while (previous.type == EXCEPT) {
            var name = parseId().text
            val variable: String?
            if (tryConsume(EQUALS) != null) {
                variable = name
                name = parseId().text
            } else {
                variable = null
            }
            val exceptBody = parseBlock(EXCEPT, FINALLY, END)
            excepts.add(AstNode.Except(name, variable, exceptBody))
        }
        val finally = if (previous.type == FINALLY) parseBlock(END) else null
        return AstNode.DoExcept(body, excepts, finally, startSpan + previous.span)
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
        return tryConsume(*types) ?: throw UnexpectedTokenException(next, types.toList(), index, next.span)
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
        } catch (e: SyntaxException) {
            this.index = index
            null
        }
    }

    private fun <T : AstNode> oneOf(vararg parsers: () -> T): T {
        val errors = mutableListOf<SyntaxException>()
        val index = this.index
        for (parser in parsers) {
            try {
                return parser()
            } catch (e: SyntaxException) {
                errors.add(e)
                this.index = index
            }
        }
        var err = errors.maxBy { it.consumed }
        if (err is UnexpectedTokenException) {
            val allErrs = errors.filterIsInstance<UnexpectedTokenException>()
            val expected = allErrs.flatMap { it.expected }.distinct()
            err = UnexpectedTokenException(err.token, expected, err.consumed, err.backtrace.first())
        }
        throw err
    }
}

private val SKIPPED_TOKENS = EnumSet.of(WHITESPACE, COMMENT)