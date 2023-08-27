package io.github.seggan.slimelang.compilation

import io.github.seggan.slimelang.Visibility
import io.github.seggan.slimelang.parsing.AstNode
import io.github.seggan.slimelang.parsing.Span
import io.github.seggan.slimelang.runtime.Chunk
import io.github.seggan.slimelang.runtime.Insn
import io.github.seggan.slimelang.runtime.Value

class Compiler {

    private val localStack = ArrayDeque<List<String>>()

    fun compileCode(name: String, code: AstNode.Program): Chunk {
        val statements = code.statements
        val (insns, spans) = compileStatements(statements).unzip()
        return Chunk(name, insns, 0, spans)
    }

    private fun compileStatements(statements: List<AstNode.Statement>): List<FullInsn> {
        return statements.flatMap(::compileStatement)
    }

    private fun compileStatement(statement: AstNode.Statement): List<FullInsn> {
        return when (statement) {
            is AstNode.Expression -> compileExpression(statement) + (Insn.Pop to statement.span)
            is AstNode.VarDecl -> compileVarDecl(statement)
            is AstNode.Break -> TODO()
            is AstNode.Continue -> TODO()
            is AstNode.Do -> TODO()
            is AstNode.For -> TODO()
            is AstNode.If -> TODO()
            is AstNode.Return -> TODO()
            is AstNode.VarAssign -> TODO()
            is AstNode.While -> TODO()
        }
    }

    private fun compileExpression(expression: AstNode.Expression): List<FullInsn> {
        return when (expression) {
            is AstNode.BinaryOp -> buildList {
                addAll(compileExpression(expression.left))
                addAll(compileExpression(expression.right))
                add(Insn.BinaryOp(expression.op) to expression.span)
            }

            is AstNode.Call -> TODO()
            is AstNode.Index -> buildList {
                addAll(compileExpression(expression.expr))
                val index = expression.index
                if (index is AstNode.Literal) {
                    val value = index.value
                    if (value is Value.Number && (value.value % 1.0) == 0.0) {
                        add(Insn.ListIndexImm(value.value.toInt()) to index.span)
                        return@buildList
                    } else if (value is Value.String) {
                        add(Insn.IndexImm(value.value) to index.span)
                        return@buildList
                    }
                }
                addAll(compileExpression(index))
                add(Insn.Index to expression.span)
            }

            is AstNode.Literal -> listOf(Insn.Push(expression.value) to expression.span)
            is AstNode.UnaryOp -> listOf(Insn.UnaryOp(expression.op) to expression.span)
            is AstNode.Var -> {
                val name = expression.name
                for (scope in localStack) {
                    if (name in scope) {
                        return listOf(Insn.GetLocal(name) to expression.span)
                    }
                }
                listOf(
                    Insn.GetGlobals to expression.span,
                    Insn.IndexImm(expression.name) to expression.span
                )
            }
        }
    }

    private fun compileVarDecl(decl: AstNode.VarDecl): List<FullInsn> {
        if (decl.visibility == Visibility.GLOBAL) {
            return buildList {
                add(Insn.GetGlobals to decl.span)
                addAll(compileExpression(decl.value))
                add(Insn.SetImm(decl.name) to decl.span)
            }
        } else {
            TODO()
        }
    }
}

private typealias FullInsn = Pair<Insn, Span>