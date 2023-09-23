package io.github.seggan.metis.compilation

import io.github.seggan.metis.BinOp
import io.github.seggan.metis.UnOp
import io.github.seggan.metis.Visibility
import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.Arity
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.chunk.Upvalue

class Compiler private constructor(
    private val file: Pair<String, String>,
    private val args: List<String>,
    private val enclosingCompiler: Compiler?
) {

    constructor(filename: String, file: String) : this(filename to file, emptyList(), null)

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
        return Chunk(name, insns, Arity(args.size), upvalues, spans, file)
    }

    private fun compileStatements(statements: List<AstNode.Statement>): List<FullInsn> {
        return statements.flatMap(::compileStatement)
    }

    private fun compileBlock(block: AstNode.Block, remove: Boolean = true): List<FullInsn> {
        scope++
        return compileStatements(block) + exitScope(block.span, remove)
    }

    private fun exitScope(span: Span, remove: Boolean): List<FullInsn> {
        return buildList {
            val it = localStack.iterator()
            while (it.hasNext()) {
                val local = it.next()
                if (local.scope == scope) {
                    if (remove) it.remove()
                    if (local.capturing != null) {
                        add(Insn.CloseUpvalue(local.capturing!!) to span)
                    } else {
                        add(Insn.Pop to span)
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
            is AstNode.Break -> TODO()
            is AstNode.Continue -> TODO()
            is AstNode.For -> TODO()
            is AstNode.If -> compileIf(statement)
            is AstNode.Return -> buildList {
                addAll(compileExpression(statement.value))
                add(Insn.Return to statement.span)
                for (local in localStack) {
                    if (local.capturing != null) {
                        add(Insn.CloseUpvalue(local.capturing!!) to statement.span)
                    } else {
                        add(Insn.Pop to statement.span)
                    }
                }
                add(Insn.Finish to statement.span)
            }

            is AstNode.While -> TODO()
            is AstNode.Block -> compileBlock(statement)
        }
    }

    private fun compileExpression(expression: AstNode.Expression): List<FullInsn> {
        return when (expression) {
            is AstNode.BinaryOp -> compileBinOp(expression)
            is AstNode.UnaryOp -> compileUnOp(expression)

            is AstNode.Call -> buildList {
                var nargs = 0
                expression.args.forEach { arg ->
                    addAll(compileExpression(arg))
                    nargs++
                }
                addAll(compileExpression(expression.expr))
                add(Insn.Call(nargs) to expression.span)
            }

            is AstNode.Index -> buildList {
                addAll(compileExpression(expression.expr))
                addAll(compileExpression(expression.index))
                add(Insn.Index to expression.span)
            }

            is AstNode.ColonCall -> buildList {
                var nargs = 0
                addAll(compileExpression(expression.expr))
                expression.args.forEach { arg ->
                    addAll(compileExpression(arg))
                    nargs++
                }
                add(Insn.CopyUnder(nargs) to expression.span)
                add(Insn.Push(Value.String(expression.name)) to expression.span)
                add(Insn.Index to expression.span)
                add(Insn.Call(nargs + 1) to expression.span)
            }

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

            is AstNode.FunctionDef -> compileFunctionDef(expression)
        }
    }

    private fun compileBinOp(op: AstNode.BinaryOp): List<FullInsn> {
        return buildList {
            when (op.op) {
                BinOp.AND -> {
                    addAll(compileExpression(op.left))
                    val jump = Insn.JumpIfFalse(0, false)
                    add(jump to op.span)
                    add(Insn.Pop to op.span)
                    val right = compileExpression(op.right)
                    addAll(right)
                    jump.offset = right.size + 1
                }

                BinOp.OR -> {
                    addAll(compileExpression(op.left))
                    val jump = Insn.JumpIfTrue(0, false)
                    add(jump to op.span)
                    add(Insn.Pop to op.span)
                    val right = compileExpression(op.right)
                    addAll(right)
                    jump.offset = right.size + 1
                }

                else -> {
                    check(op.op.metamethod.isNotEmpty()) { "Cannot compile binary operator ${op.op}" }
                    addAll(compileExpression(op.left))
                    addAll(compileExpression(op.right))
                    add(Insn.CopyUnder(1) to op.span)
                    add(Insn.Push(Value.String(op.op.metamethod)) to op.span)
                    add(Insn.Index to op.span)
                    add(Insn.Call(2) to op.span)
                }
            }
        }
    }

    private fun compileUnOp(op: AstNode.UnaryOp): List<FullInsn> {
        return buildList {
            when (op.op) {
                UnOp.NOT -> {
                    addAll(compileExpression(op.expr))
                    add(Insn.Not to op.span)
                }

                UnOp.NEG -> {
                    addAll(compileExpression(op.expr))
                    add(Insn.CopyUnder(0) to op.span)
                    add(Insn.Push(Value.String("__mul__")) to op.span)
                    add(Insn.Index to op.span)
                    add(Insn.Call(1) to op.span)
                }
            }
        }
    }

    private fun compileFunctionDef(fn: AstNode.FunctionDef): List<FullInsn> {
        val compiler = Compiler(file, fn.args, this)
        val chunk = compiler.compileCode("<function>", fn.body)
        return listOf(Insn.PushClosure(chunk) to fn.span)
    }

    private fun compileIf(statement: AstNode.If): List<FullInsn> {
        return buildList {
            addAll(compileExpression(statement.condition))
            val jump = Insn.JumpIfFalse(0)
            add(jump to statement.span)
            val then = compileBlock(statement.body)
            addAll(then)
            if (statement.elseBody != null) {
                jump.offset = then.size + 1
                val jump2 = Insn.Jump(0)
                add(jump2 to statement.span)
                val elseBody = compileBlock(statement.elseBody)
                jump2.offset = elseBody.size
                addAll(elseBody)
            } else {
                jump.offset = then.size
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
            is AstNode.Index -> buildList {
                addAll(compileExpression(assign.value))
                addAll(compileExpression(target.index))
                addAll(compileExpression(target.expr))
                add(Insn.Set to target.span)
            }

            is AstNode.Var -> buildList {
                val name = target.name
                resolveLocal(name)?.let { local ->
                    addAll(compileExpression(assign.value))
                    add(Insn.SetLocal(local.index) to target.span)
                    return@buildList
                }
                resolveUpvalue(name)?.let { upvalue ->
                    addAll(compileExpression(assign.value))
                    add(Insn.SetUpvalue(upvalues.indexOf(upvalue)) to target.span)
                    return@buildList
                }
                addAll(compileExpression(assign.value))
                add(Insn.SetGlobal(name) to target.span)
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
}

private typealias FullInsn = Pair<Insn, Span>

private data class ResolvedUpvalue(val upvalue: Upvalue, val index: Int)

private data class Local(val name: String, val scope: Int, val index: Int, var capturing: Upvalue? = null)