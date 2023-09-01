package io.github.seggan.metis.parsing

import io.github.seggan.metis.BinOp
import io.github.seggan.metis.UnOp
import io.github.seggan.metis.Visibility
import io.github.seggan.metis.runtime.Value

sealed interface AstNode {

    val span: Span

    data class Program(val statements: List<Statement>, override val span: Span) : AstNode
    data class Block(val statements: List<Statement>, override val span: Span) : AstNode

    sealed interface Statement : AstNode
    data class VarAssign(val target: Expression, val value: Expression, override val span: Span) : Statement
    data class VarDecl(
        val visibility: Visibility,
        val name: String,
        val value: Expression,
        override val span: Span
    ) : Statement
    data class Return(val value: Expression, override val span: Span) : Statement
    data class If(
        val condition: Expression,
        val body: Block,
        val elseBody: Block?,
        override val span: Span
    ) : Statement
    data class While(val condition: Expression, val body: Block, override val span: Span) : Statement
    data class For(val name: String, val range: Expression, val body: Block, override val span: Span) : Statement
    data class Do(val body: Block, override val span: Span) : Statement
    data class Break(override val span: Span) : Statement
    data class Continue(override val span: Span) : Statement

    sealed interface Expression : Statement
    data class Var(val name: String, override val span: Span) : Expression
    data class Call(val expr: Expression, val args: List<Expression>, override val span: Span) : Expression
    data class Index(val expr: Expression, val index: Expression, override val span: Span) : Expression
    data class BinaryOp(
        val left: Expression,
        val op: BinOp,
        val right: Expression
    ) : Expression {
        override val span = left.span + right.span
    }
    data class UnaryOp(val op: UnOp, val expr: Expression, override val span: Span) : Expression
    data class Literal(val value: Value, override val span: Span) : Expression
}