package io.github.seggan.metis.compilation

import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.parsing.SyntaxException
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

class MetisCompiler private constructor(private val name: String) {

    private val locals = ArrayDeque<Local>()
    private var scope = 0
    private val loops = ArrayDeque<Loop>()

    private fun compileCode(code: AstNode.Block): Chunk {
        val insns = compileBlock(code)

        // backpatch jumps
        val rawInsns = insns.unzip().first
        val backpatched = insns.map { (insn, span) ->
            when (insn) {
                is Insn.RawJump -> insn.backpatch(rawInsns)
                else -> insn
            } to span
        }

        return Chunk(name, backpatched)
    }

    private fun compileBlock(block: AstNode.Block): List<FullInsn> {
        scope++
        val insns = block.flatMap(::compileStatement)
        val scopeExit = exitScope(block.span) { it == scope }
        scope--
        return insns + scopeExit
    }

    private inline fun exitScope(span: Span, cond: (Int) -> Boolean) = buildInsns(span) {
        val localIt = locals.iterator()
        while (localIt.hasNext()) {
            val local = localIt.next()
            if (cond(local.scope)) {
                +Insn.Pop
                localIt.remove()
            }
        }
    }

    private fun compileStatement(statement: AstNode.Statement): List<FullInsn> {
        return when (statement) {
            is AstNode.Expression -> compileExpression(statement)
            is AstNode.Block -> compileBlock(statement)
            is AstNode.Break -> buildInsns(statement.span) {
                if (loops.isEmpty()) {
                    throw SyntaxException("Break statement outside of loop", statement.span)
                }
                val loop = loops.peek()
                +exitScope(statement.span) { it > loop.scope }
                +Insn.RawDirectJump(loop.end)
            }

            is AstNode.Continue -> buildInsns(statement.span) {
                if (loops.isEmpty()) {
                    throw SyntaxException("Continue statement outside of loop", statement.span)
                }
                val loop = loops.peek()
                +exitScope(statement.span) { it > loop.scope }
                +Insn.RawDirectJump(loop.start)
            }

            is AstNode.DoExcept -> TODO()
            is AstNode.For -> TODO()
            is AstNode.If -> compileIf(statement)
            is AstNode.Import -> TODO()
            is AstNode.Raise -> TODO()
            is AstNode.Return -> buildInsns(statement.span) {
                +compileExpression(statement.value)
                +Insn.Save
                +exitScope(statement.span) { true }
                +Insn.Return
            }

            is AstNode.VarAssign -> compileAssignment(statement)
            is AstNode.VarDecl -> compileDeclaration(statement)
            is AstNode.While -> compileWhile(statement)
        }
    }

    private fun compileDeclaration(declaration: AstNode.VarDecl) = buildInsns(declaration.span) {
        if (getLocal(declaration.name) != null) {
            throw SyntaxException("Variable '${declaration.name}' already declared", declaration.span)
        }
        +compileExpression(declaration.value)
        if (declaration.visibility == Visibility.GLOBAL) {
            +Insn.SetGlobal(declaration.name, true)
        } else {
            locals.push(Local(declaration.name, locals.size, scope))
        }
    }

    private fun compileAssignment(assignment: AstNode.VarAssign) = buildInsns(assignment.span) {
        when (val target = assignment.target) {
            is AstNode.Var -> {
                +compileExpression(assignment.value)
                val local = getLocal(target.name)
                if (local == null) {
                    +Insn.SetGlobal(target.name, false)
                } else {
                    +Insn.SetLocal(local.index)
                }
            }

            is AstNode.Index -> {
                chooseIndexOp(target.index, compileExpression(target.target), IndexOperation.SET)
            }
        }
    }

    private fun compileIf(node: AstNode.If) = buildInsns(node.span) {
        val end = Insn.Label()
        +compileExpression(node.condition)
        +Insn.RawJumpIf(end, condition = false)
        +compileBlock(node.body)
        if (node.elseBody != null) {
            val elseEnd = Insn.Label()
            +Insn.RawDirectJump(elseEnd)
            +end
            +compileBlock(node.elseBody)
            +elseEnd
        } else {
            +end
        }
    }

    private fun compileWhile(node: AstNode.While) = buildInsns(node.span) {
        val start = Insn.Label()
        val end = Insn.Label()
        loops.push(Loop(start, end, scope))
        +start
        +compileExpression(node.condition)
        +Insn.RawJumpIf(end, condition = false)
        +compileBlock(node.body)
        +Insn.RawDirectJump(start)
        +end
        loops.pop()
    }

    private fun compileExpression(expression: AstNode.Expression): List<FullInsn> {
        return when (expression) {
            is AstNode.BinaryOp -> buildInsns(expression.span) {
                expression.op.generateCode(
                    this,
                    compileExpression(expression.left),
                    compileExpression(expression.right)
                )
            }

            is AstNode.Index -> buildInsns(expression.span) {
                chooseIndexOp(expression.index, compileExpression(expression.target), IndexOperation.GET)
            }

            is AstNode.Var -> buildInsns(expression.span) {
                val local = getLocal(expression.name)
                if (local == null) {
                    +Insn.GetGlobal(expression.name)
                } else {
                    +Insn.GetLocal(local.index)
                }
            }

            is AstNode.Call -> TODO()
            is AstNode.CombinedCall -> TODO()
            is AstNode.ErrorLiteral -> TODO()
            is AstNode.FunctionLiteral -> TODO()
            is AstNode.ListLiteral -> TODO()
            is AstNode.Literal -> listOf(Insn.Push(expression.value) to expression.span)
            is AstNode.TableLiteral -> TODO()
            is AstNode.TernaryOp -> TODO()
            is AstNode.UnaryOp -> TODO()
        }
    }

    private fun getLocal(name: String): Local? {
        return locals.firstOrNull { it.name == name }
    }

    private fun InsnsBuilder.chooseIndexOp(expr: AstNode.Expression, value: List<FullInsn>, op: IndexOperation) {
        if (expr is AstNode.Literal) {
            +value
            if (op == IndexOperation.SET) {
                +Insn.SetIndexDirect(expr.value)
            } else {
                +Insn.GetIndexDirect(expr.value)
            }
        } else {
            +compileExpression(expr)
            +value
            if (op == IndexOperation.SET) {
                +Insn.SetIndex
            } else {
                +Insn.GetIndex
            }
        }
    }

    companion object {
        fun compile(name: String, code: AstNode.Block): Chunk {
            return MetisCompiler(name).compileCode(code)
        }
    }
}

private enum class IndexOperation {
    GET, SET
}

private data class Loop(val start: Insn.Label, val end: Insn.Label, val scope: Int)

private data class Local(val name: String, val index: Int, val scope: Int)