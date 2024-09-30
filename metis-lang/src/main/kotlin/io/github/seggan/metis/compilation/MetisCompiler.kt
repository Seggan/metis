package io.github.seggan.metis.compilation

import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.compilation.op.UnOp
import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.parsing.SyntaxException
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.chunk.Upvalue
import io.github.seggan.metis.runtime.value.CallableValue
import io.github.seggan.metis.runtime.value.TableValue
import io.github.seggan.metis.util.peek
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.util.UUID

class MetisCompiler private constructor(
    private val args: List<String>,
    private val enclosingCompiler: MetisCompiler?
) {

    private val id = UUID.randomUUID()

    private val locals = ArrayDeque<Local>()
    private var scope = 0
    private val upvalues = mutableListOf<Upvalue>()
    private val loops = ArrayDeque<Loop>()

    private var name: String? = null

    private fun compileCode(name: String, code: AstNode.Block): Chunk {
        for (arg in args) {
            locals.push(Local(arg, locals.size, scope))
        }

        val insns = compileBlock(code)

        // backpatch jumps
        val rawInsns = insns.unzip().first
        val backpatched = insns.map { (insn, span) ->
            when (insn) {
                is Insn.RawJump -> insn.backpatch(rawInsns)
                else -> insn
            } to span
        }

        return Chunk(
            name,
            CallableValue.Arity(args.size, args.firstOrNull() == "self"),
            backpatched,
            upvalues,
            id
        )
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
                val capturer = local.capturer
                if (capturer != null) {
                    +Insn.CloseUpvalue(capturer)
                } else {
                    +Insn.Pop
                }
                localIt.remove()
            }
        }
    }

    private fun compileStatement(statement: AstNode.Statement): List<FullInsn> {
        return when (statement) {
            is AstNode.Expression -> buildInsns(statement) {
                +compileExpression(statement)
                +Insn.Pop
            }

            is AstNode.Block -> compileBlock(statement)
            is AstNode.Break -> buildInsns(statement) {
                if (loops.isEmpty()) {
                    throw SyntaxException("Break statement outside of loop", statement.span)
                }
                val loop = loops.peek()
                +exitScope(statement.span) { it > loop.scope }
                +Insn.RawDirectJump(loop.end)
            }

            is AstNode.Continue -> buildInsns(statement) {
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
            is AstNode.Import -> buildInsns(statement) {
                val name = statement.name
                +Insn.Import(name)
                if (statement.global) {
                    +Insn.SetGlobal(name, true)
                } else {
                    locals.push(Local(name, locals.size, scope))
                }
            }

            is AstNode.Raise -> TODO()
            is AstNode.Return -> buildInsns(statement) {
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

    private fun compileDeclaration(declaration: AstNode.VarDecl) = buildInsns(declaration) {
        if (getLocal(declaration.name) != null) {
            throw SyntaxException("Variable '${declaration.name}' already declared", declaration.span)
        }
        if (declaration.value is AstNode.FunctionLiteral) {
            name = declaration.name
        }
        +compileExpression(declaration.value)
        name = null
        if (declaration.visibility == Visibility.GLOBAL) {
            +Insn.SetGlobal(declaration.name, true)
        } else {
            locals.push(Local(declaration.name, locals.size, scope))
        }
    }

    private fun compileAssignment(assignment: AstNode.VarAssign) = buildInsns(assignment) {
        when (val target = assignment.target) {
            is AstNode.Var -> {
                val name = target.name
                +compileExpression(assignment.value)
                val local = getLocal(name)
                if (local != null) {
                    +Insn.SetLocal(local.index)
                } else {
                    val upvalue = getUpvalue(name)
                    if (upvalue != null) {
                        val index = upvalues.indexOf(upvalue)
                        +Insn.SetUpvalue(index)
                    } else {
                        +Insn.SetGlobal(name, false)
                    }
                }
            }

            is AstNode.Index -> {
                +compileExpression(target.target)
                +compileExpression(target.index)
                +compileExpression(assignment.value)
                +Insn.SetIndex
            }
        }
    }

    private fun compileIf(node: AstNode.If) = buildInsns(node) {
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

    private fun compileWhile(node: AstNode.While) = buildInsns(node) {
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
            is AstNode.BinaryOp -> buildInsns(expression) {
                expression.op.generateCode(
                    this,
                    compileExpression(expression.left),
                    compileExpression(expression.right)
                )
            }

            is AstNode.Index -> buildInsns(expression) {
                +compileExpression(expression.target)
                +compileExpression(expression.index)
                +Insn.GetIndex
            }

            is AstNode.Var -> {
                getLocal(expression.name)?.let { local ->
                    return listOf(Insn.GetLocal(local.index) to expression.span)
                }
                getUpvalue(expression.name)?.let { upvalue ->
                    return listOf(Insn.GetUpvalue(upvalues.indexOf(upvalue)) to expression.span)
                }
                listOf(Insn.GetGlobal(expression.name) to expression.span)
            }

            is AstNode.Call -> buildInsns(expression) {
                for (arg in expression.args) {
                    +compileExpression(arg)
                }
                +compileExpression(expression.expr)
                +Insn.Call(expression.args.size, false)
            }

            is AstNode.CombinedCall -> buildInsns(expression) {
                +compileExpression(expression.expr)
                for (arg in expression.args) {
                    +compileExpression(arg)
                }
                +Insn.CopyUnder(expression.args.size)
                +Insn.Push(expression.name)
                +Insn.GetIndex
                +Insn.Call(expression.args.size + 1, true)
            }

            is AstNode.ErrorLiteral -> buildInsns(expression) {
                +compileExpression(expression.message)
                +Insn.MetaCall(0, Metamethod.TO_STRING)
                if (expression.companionData != null) {
                    +compileExpression(expression.companionData)
                } else {
                    +Insn.Push(TableValue())
                }
                +Insn.PushError(expression.type)
            }

            is AstNode.FunctionLiteral -> {
                val compiler = MetisCompiler(expression.args, this)
                val chunk = compiler.compileCode(name ?: "<function>", expression.body)
                listOf(Insn.PushClosure(chunk) to expression.span)
            }

            is AstNode.ListLiteral -> buildInsns(expression) {
                for (value in expression.values) {
                    +compileExpression(value)
                }
                +Insn.PushList(expression.values.size)
            }

            is AstNode.Literal -> listOf(Insn.Push(expression.value) to expression.span)
            is AstNode.TableLiteral -> buildInsns(expression) {
                for ((key, value) in expression.values) {
                    +compileExpression(key)
                    +compileExpression(value)
                }
                +Insn.PushTable(expression.values.size)
            }

            is AstNode.TernaryOp -> buildInsns(expression) {
                +compileExpression(expression.condition)
                val falseLabel = Insn.Label()
                +Insn.RawJumpIf(falseLabel, condition = false)
                +compileExpression(expression.trueExpr)
                val endLabel = Insn.Label()
                +Insn.RawDirectJump(endLabel)
                +falseLabel
                +compileExpression(expression.falseExpr)
                +endLabel
            }

            is AstNode.UnaryOp -> buildInsns(expression) {
                +compileExpression(expression.expr)
                if (expression.op == UnOp.NOT) {
                    +Insn.Not
                } else {
                    +Insn.MetaCall(0, expression.op.metamethod!!)
                }
            }
        }
    }

    private fun getLocal(name: String): Local? {
        return locals.firstOrNull { it.name == name }
    }

    private fun getUpvalue(name: String): Upvalue? {
        val found = upvalues.firstOrNull { it.name == name }
        if (found != null) return found
        if (enclosingCompiler == null) return null
        val local = enclosingCompiler.getLocal(name)
        if (local != null) {
            if (local.capturer == null) {
                local.capturer = Upvalue(name, local.index, enclosingCompiler.id)
            }
            val upvalue = local.capturer!!
            upvalues.add(upvalue)
            return upvalue
        }
        val upvalue = enclosingCompiler.getUpvalue(name)
        if (upvalue != null) {
            upvalues.add(upvalue)
            return upvalue
        }
        return null
    }

    companion object {
        fun compile(name: String, code: AstNode.Block): Chunk {
            return MetisCompiler(listOf(), null).compileCode(name, code)
        }
    }
}

private data class Loop(val start: Insn.Label, val end: Insn.Label, val scope: Int)

private data class Local(val name: String, val index: Int, val scope: Int, var capturer: Upvalue? = null)