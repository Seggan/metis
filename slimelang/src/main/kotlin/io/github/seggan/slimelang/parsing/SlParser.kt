package io.github.seggan.slimelang.parsing

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import io.github.seggan.slimelang.Visibility
import io.github.seggan.slimelang.runtime.Value

@Suppress("MemberVisibilityCanBePrivate")
class SlParser : Grammar<AstNode.Program>() {

    @Suppress("unused")
    val ws by regexToken("""\s+""", ignore = true)

    @Suppress("unused")
    val lineComment by regexToken("""//.*\n""", ignore = true)

    @Suppress("unused")
    val blockComment by regexToken("""/\*(.|\n)*?\*/""", ignore = true)

    val `fun` by keyword("fun")
    val `return` by keyword("return")
    val `if` by keyword("if")
    val `else` by keyword("else")
    val `while` by keyword("while")
    val `for` by keyword("for")
    val `do` by keyword("do")
    val end by keyword("end")

    val local by keyword("local")
    val global by keyword("global")
    val visibilityModifier by local or global use {
        (if (text == "local") Visibility.LOCAL else Visibility.GLOBAL) to span
    }

    val openParen by literalToken("(")
    val closeParen by literalToken(")")
    val openBrace by literalToken("{")
    val closeBrace by literalToken("}")
    val openBracket by literalToken("[")
    val closeBracket by literalToken("]")

    val comma by literalToken(",")
    val dot by literalToken(".")
    val plus by literalToken("+")
    val minus by literalToken("-")
    val times by literalToken("*")
    val div by literalToken("/")
    val mod by literalToken("%")
    val pow by literalToken("^")
    val eq by literalToken("=")
    val doubleEq by literalToken("==")
    val notEq by literalToken("!=")
    val less by literalToken("<")
    val lessEq by literalToken("<=")
    val greater by literalToken(">")
    val greaterEq by literalToken(">=")

    val and by keyword("and")
    val or by keyword("or")
    val not by keyword("not")

    val id by regexToken("""[a-zA-Z_][a-zA-Z0-9_]*""")
    val number by regexToken("""\d+(\.\d+)?""")
    val string by regexToken(""""([^\\"]|\\.)*"""")

    val primary by (id use { AstNode.Var(text, span) }) or
            (number use { AstNode.Literal(Value.Number(text.toDouble()), span) }) or
            (string use {
                AstNode.Literal(
                    Value.String(
                        text.substring(1, text.length - 1)
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                            .replace("\\t", "\t")
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                    ),
                    span
                )
            }) or
            (-openParen * parser(::expr) * -closeParen)

    private val argList by -openParen * separatedTerms(parser(::expr), comma) * -closeParen map { (o, exprs, c) ->
        PostfixOp.CALL to exprs to o.span + c.span
    }
    private val index by openBracket * parser(::expr) * closeBracket map { (o, expr, c) ->
        PostfixOp.INDEX to listOf(expr) to o.span + c.span
    }
    private val access by -dot * id use {
        PostfixOp.ACCESS to listOf(AstNode.Literal(Value.String(text), span)) to span
    }
    val postfix by primary * zeroOrMore(argList or index or access) map { (primary, postfixes) ->
        postfixes.fold(primary) { acc, (postfix, span) ->
            @Suppress("UNCHECKED_CAST") // For some reason the compiler doesn't like this
            val exprs = postfix.second as List<AstNode.Expression>
            when (postfix.first) {
                PostfixOp.CALL -> AstNode.Call(acc, exprs, primary.span + span)
                PostfixOp.INDEX -> AstNode.Index(acc, exprs.first(), primary.span + span)
                PostfixOp.ACCESS -> AstNode.Index(acc, exprs.first(), primary.span + span)
            }
        }
    }

    val exp by rightAssociative(postfix, pow) { l, _, r -> AstNode.BinaryOp(l, BinOp.POW, r) }
    val factor by optional(
        (not use { UnOp.NOT to span }) or (minus use { UnOp.NEG to span })
    ) * exp map { (sign, exp) ->
        sign?.let { (sign, span) -> AstNode.UnaryOp(sign, exp, span + exp.span) } ?: exp
    }
    val term by leftAssociative(
        factor,
        (times use { BinOp.TIMES }) or
                (div use { BinOp.DIV }) or
                (mod use { BinOp.MOD }),
        AstNode::BinaryOp
    )
    val sum by leftAssociative(
        term,
        (plus use { BinOp.PLUS }) or (minus use { BinOp.MINUS }),
        AstNode::BinaryOp
    )
    val comparison by leftAssociative(
        sum,
        (less use { BinOp.LESS }) or
                (lessEq use { BinOp.LESS_EQ }) or
                (greater use { BinOp.GREATER }) or
                (greaterEq use { BinOp.GREATER_EQ }),
        AstNode::BinaryOp
    )
    val equality by leftAssociative(
        comparison,
        (doubleEq use { BinOp.EQ }) or (notEq use { BinOp.NOT_EQ }),
        AstNode::BinaryOp
    )
    val andExpr by leftAssociative(equality, and) { l, _, r -> AstNode.BinaryOp(l, BinOp.AND, r) }
    val orExpr by leftAssociative(andExpr, or) { l, _, r -> AstNode.BinaryOp(l, BinOp.OR, r) }
    val expr: Parser<AstNode.Expression> by orExpr

    val varDecl by visibilityModifier * id * optional(-eq * expr) map { (vis, id, expr) ->
        if (expr != null) {
            AstNode.VarDecl(vis.first, id.text, expr, vis.second + expr.span)
        } else {
            val span = vis.second + id.span
            AstNode.VarDecl(vis.first, id.text, AstNode.Literal(Value.Null, span), span)
        }
    }
    val varAssign by id * -eq * expr map { (id, expr) -> AstNode.VarAssign(id.text, expr, id.span + expr.span) }

    val statement: Parser<AstNode.Statement> by varDecl or varAssign or expr
    val statements by oneOrMore(statement)

    override val rootParser by optional(statements) map {
        val statements = it.orEmpty()
        AstNode.Program(
            statements,
            statements.fold(Span(Int.MAX_VALUE, 0)) { acc, stmt -> acc + stmt.span }
        )
    }
}

private fun keyword(name: String) = regexToken("""\b$name\b""")

private enum class PostfixOp {
    CALL, INDEX, ACCESS
}