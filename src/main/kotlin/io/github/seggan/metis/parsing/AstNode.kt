package io.github.seggan.metis.parsing

import io.github.seggan.metis.compilation.BinOp
import io.github.seggan.metis.compilation.UnOp
import io.github.seggan.metis.compilation.Visibility
import io.github.seggan.metis.runtime.Value

sealed interface AstNode {

    val span: Span

    sealed interface Statement : AstNode
    data class Block(val statements: List<Statement>, override val span: Span) : Statement,
        List<Statement> by statements

    data class DoExcept(
        val body: Block,
        val excepts: List<Except>,
        val finally: Block?,
        override val span: Span
    ) : Statement

    data class Except(val name: String, val variable: String?, val body: Block)
    data class VarAssign(val target: AssignTarget, val value: Expression, override val span: Span) : Statement
    data class VarDecl(
        val visibility: Visibility,
        val name: String,
        val value: Expression,
        override val span: Span
    ) : Statement

    data class Return(val value: Expression, override val span: Span) : Statement
    data class Raise(val value: Expression, override val span: Span) : Statement
    data class If(
        val condition: Expression,
        val body: Block,
        val elseBody: Block?,
        override val span: Span
    ) : Statement

    data class While(val condition: Expression, val body: Block, override val span: Span) : Statement
    data class For(val name: String, val iterable: Expression, val body: Block, override val span: Span) : Statement
    data class Break(override val span: Span) : Statement
    data class Continue(override val span: Span) : Statement

    sealed interface Expression : Statement
    sealed interface AssignTarget : Expression
    data class Var(val name: String, override val span: Span) : Expression, AssignTarget
    data class Call(val expr: Expression, val args: List<Expression>, override val span: Span) : Expression
    data class Index(val target: Expression, val index: Expression, override val span: Span) : Expression, AssignTarget
    data class ColonCall(
        val expr: Expression,
        val name: String,
        val args: List<Expression>,
        override val span: Span
    ) : Expression

    data class UnaryOp(val op: UnOp, val expr: Expression, override val span: Span) : Expression
    data class BinaryOp(
        val left: Expression,
        val op: BinOp,
        val right: Expression
    ) : Expression {
        override val span = left.span + right.span
    }

    data class TernaryOp(
        val condition: Expression,
        val trueExpr: Expression,
        val falseExpr: Expression
    ) : Expression {
        override val span = condition.span + trueExpr.span + falseExpr.span
    }

    data class Literal(val value: Value, override val span: Span) : Expression
    data class ListLiteral(val values: List<Expression>, override val span: Span) : Expression
    data class TableLiteral(val values: List<Pair<Expression, Expression>>, override val span: Span) : Expression
    data class FunctionLiteral(val args: List<String>, val body: Block, val name: String?, override val span: Span) :
        Expression

    data class ErrorLiteral(
        val type: String,
        val message: Expression,
        val companionData: TableLiteral?,
        override val span: Span
    ) : Expression
}