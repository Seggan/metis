package io.github.seggan.metis.compilation

import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.Arity
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.chunk.Label
import io.github.seggan.metis.runtime.chunk.Upvalue

class Compiler private constructor(
    private val args: List<String>,
    private val enclosingCompiler: Compiler?
) {

    constructor() : this(emptyList(), null)

    private val localStack = ArrayDeque<Local>()
    private val upvalues = mutableListOf<Upvalue>()

    private val depth: Int = enclosingCompiler?.depth ?: 0

    private var scope = 0

    init {
        for ((i, arg) in args.withIndex()) {
            localStack.addFirst(Local(arg, 0, i))
        }
    }

    fun compileCode(name: String, code: AstNode.Block): Chunk {
        check(scope >= 0) { "Cannot use a Compiler more than once" }
        val (insns, spans) = compileBlock(code, false).unzip()
        return Chunk(name, insns, Arity(args.size), upvalues, spans)
    }

    private fun compileStatements(statements: List<AstNode.Statement>): List<FullInsn> {
        return statements.flatMap(::compileStatement)
    }

    private fun compileBlock(block: AstNode.Block, remove: Boolean = true): List<FullInsn> {
        scope++
        return compileStatements(block) + exitScope(block.span, remove)
    }

    private fun exitScope(span: Span, remove: Boolean): List<FullInsn> {
        return buildInsns(span) {
            val it = localStack.iterator()
            while (it.hasNext()) {
                val local = it.next()
                if (local.scope == scope) {
                    if (remove) it.remove()
                    if (local.capturing != null) {
                        +Insn.CloseUpvalue(local.capturing!!)
                    } else {
                        +Insn.Pop
                    }
                }
            }
            scope--
        }
    }

    private fun compileStatement(statement: AstNode.Statement): List<FullInsn> {
        return when (statement) {
            is AstNode.Expression -> compileExpression(statement) + (Insn.Pop to statement.span)
            is AstNode.VarDecl -> compileVarDecl(statement)
            is AstNode.VarAssign -> compileVarAssign(statement)
            is AstNode.Return -> buildInsns(statement.span) {
                +compileExpression(statement.value)
                +Insn.Return
                for (local in localStack) {
                    if (local.capturing != null) {
                        +Insn.CloseUpvalue(local.capturing!!)
                    } else {
                        +Insn.Pop
                    }
                }
                +Insn.Finish
            }

            is AstNode.While -> compileWhile(statement)
            is AstNode.For -> compileFor(statement)
            is AstNode.Break -> TODO()
            is AstNode.Continue -> TODO()
            is AstNode.If -> compileIf(statement)
            is AstNode.Block -> compileBlock(statement)
        }
    }

    private fun compileExpression(expression: AstNode.Expression): List<FullInsn> {
        return when (expression) {
            is AstNode.BinaryOp -> compileBinOp(expression)
            is AstNode.UnaryOp -> compileUnOp(expression)

            is AstNode.Call -> buildInsns(expression.span) {
                expression.args.forEach { arg ->
                    +compileExpression(arg)
                }
                +compileExpression(expression.expr)
                +Insn.Call(expression.args.size)
            }

            is AstNode.Index -> buildInsns(expression.span) {
                +compileExpression(expression.target)
                +compileExpression(expression.index)
                +Insn.Index
            }

            is AstNode.ColonCall -> generateColonCall(
                compileExpression(expression.expr),
                expression.name,
                expression.args.map(::compileExpression),
                expression.span
            )

            is AstNode.Literal -> listOf(Insn.Push(expression.value) to expression.span)
            is AstNode.Var -> {
                val name = expression.name
                resolveLocal(name)?.let { local ->
                    return listOf(Insn.GetLocal(local.index) to expression.span)
                }
                resolveUpvalue(name)?.let { upvalue ->
                    return listOf(Insn.GetUpvalue(upvalues.indexOf(upvalue)) to expression.span)
                }
                listOf(Insn.GetGlobal(name) to expression.span)
            }

            is AstNode.FunctionLiteral -> compileFunctionDef(expression)
            is AstNode.ListLiteral -> buildInsns(expression.span) {
                expression.values.forEach { value ->
                    +compileExpression(value)
                }
                +Insn.PushList(expression.values.size)
            }

            is AstNode.TableLiteral -> buildInsns(expression.span) {
                expression.values.forEach { (key, value) ->
                    +compileExpression(key)
                    +compileExpression(value)
                }
                +Insn.PushTable(expression.values.size)
            }

            is AstNode.ErrorLiteral -> compileErrorLiteral(expression)
        }
    }

    private fun compileBinOp(op: AstNode.BinaryOp): List<FullInsn> {
        return buildInsns(op.span) {
            op.op.generateCode(this, compileExpression(op.left), compileExpression(op.right))
        }
    }

    private fun compileUnOp(op: AstNode.UnaryOp): List<FullInsn> {
        return buildInsns(op.span) {
            when (op.op) {
                UnOp.NOT -> {
                    +compileExpression(op.expr)
                    +Insn.Not
                }

                UnOp.NEG -> {
                    +compileExpression(op.expr)
                    +Insn.CopyUnder(0)
                    +Insn.Push(Value.String("__mul__"))
                    +Insn.Index
                    +Insn.Call(1)
                }
            }
        }
    }

    private fun compileFunctionDef(fn: AstNode.FunctionLiteral): List<FullInsn> {
        val compiler = Compiler(fn.args, this)
        val chunk = compiler.compileCode("<function>", fn.body)
        return listOf(Insn.PushClosure(chunk) to fn.span)
    }

    private fun compileErrorLiteral(error: AstNode.ErrorLiteral): List<FullInsn> {
        return buildInsns(error.span) {
            +compileExpression(error.message)
            +Insn.CopyUnder(0)
            +Insn.Push(Value.String("__str__"))
            +Insn.Index
            +Insn.Call(1)
            if (error.companionData != null) {
                +compileExpression(error.companionData)
            } else {
                +Insn.Push(Value.Table())
            }
            +Insn.PushError(error.type)
        }
    }

    private fun compileWhile(statement: AstNode.While): List<FullInsn> {
        return buildInsns(statement.span) {
            val start = Label()
            +start
            +compileExpression(statement.condition)
            val end = Label()
            +Insn.JumpIf(start, false)
            +compileBlock(statement.body)
            +end
            +Insn.Jump(start)
        }
    }

    private fun compileFor(statement: AstNode.For): List<FullInsn> {
        return buildInsns(statement.span) {
            +compileExpression(statement.iterable)
            +Insn.CopyUnder(0)
            +Insn.Push(Value.String("__iter__"))
            +Insn.Index
            +Insn.Call(1)
            localStack.addFirst(Local("", scope, localStack.size))
            val start = Label()
            +start
            +Insn.CopyUnder(0)
            +Insn.CopyUnder(0)
            +Insn.Push(Value.String("has_next"))
            +Insn.Index
            +Insn.Call(1)
            val end = Label()
            +Insn.JumpIf(end, false)
            +Insn.CopyUnder(0)
            +Insn.CopyUnder(0)
            +Insn.Push(Value.String("next"))
            +Insn.Index
            +Insn.Call(1)
            localStack.addFirst(Local(statement.name, scope + 1, localStack.size))
            +compileBlock(statement.body)
            +Insn.Jump(start)
            +end
            localStack.removeFirst()
            +Insn.Pop
        }
    }

    private fun compileIf(statement: AstNode.If): List<FullInsn> {
        return buildInsns(statement.span) {
            +compileExpression(statement.condition)
            val end = Label()
            +Insn.JumpIf(end, false)
            +compileBlock(statement.body)
            if (statement.elseBody != null) {
                val realEnd = Label()
                +Insn.Jump(realEnd)
                +end
                +compileBlock(statement.elseBody)
                +realEnd
            } else {
                +end
            }
        }
    }

    private fun compileVarDecl(decl: AstNode.VarDecl): List<FullInsn> {
        return if (decl.visibility == Visibility.GLOBAL) {
            compileExpression(decl.value) + (Insn.SetGlobal(decl.name) to decl.span)
        } else {
            localStack.addFirst(Local(decl.name, scope, localStack.size))
            compileExpression(decl.value)
        }
    }

    private fun compileVarAssign(assign: AstNode.VarAssign): List<FullInsn> {
        return when (val target = assign.target) {
            is AstNode.Index -> buildInsns(target.span) {
                +compileExpression(target.target)
                +compileExpression(target.index)
                +compileExpression(assign.value)
                +Insn.Set
            }

            is AstNode.Var -> buildInsns(target.span) {
                val name = target.name
                resolveLocal(name)?.let { local ->
                    +compileExpression(assign.value)
                    +Insn.SetLocal(local.index)
                    return@buildInsns
                }
                resolveUpvalue(name)?.let { upvalue ->
                    +compileExpression(assign.value)
                    +Insn.SetUpvalue(upvalues.indexOf(upvalue))
                    return@buildInsns
                }
                +compileExpression(assign.value)
                +Insn.SetGlobal(name)
            }
        }
    }

    private fun resolveLocal(name: String): Local? {
        return localStack.firstOrNull { it.name == name }
    }

    private fun resolveUpvalue(name: String): Upvalue? {
        val found = upvalues.firstOrNull { it.name == name }
        if (found != null) return found
        if (enclosingCompiler == null) return null
        val local = enclosingCompiler.resolveLocal(name)
        if (local != null) {
            if (local.capturing == null) {
                local.capturing = Upvalue(name, local.index, enclosingCompiler.depth)
            }
            val upvalue = local.capturing!!
            upvalues.add(upvalue)
            return upvalue
        }
        val resolved = enclosingCompiler.resolveUpvalue(name)
        if (resolved != null) {
            upvalues.add(resolved)
            return resolved
        }
        return null
    }

    internal companion object {
        fun generateColonCall(
            expr: List<FullInsn>,
            name: String,
            args: List<List<FullInsn>>,
            span: Span
        ) = buildInsns(span) {
            +expr
            args.forEach { arg ->
                +arg
            }
            +Insn.CopyUnder(args.size)
            +Insn.Push(Value.String(name))
            +Insn.Index
            +Insn.Call(args.size + 1)
        }
    }
}

private data class ResolvedUpvalue(val upvalue: Upvalue, val index: Int)

private data class Local(val name: String, val scope: Int, val index: Int, var capturing: Upvalue? = null)