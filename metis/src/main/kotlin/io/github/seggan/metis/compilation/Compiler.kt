package io.github.seggan.metis.compilation

import io.github.seggan.metis.Visibility
import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.chunk.Upvalue
import io.github.seggan.metis.runtime.values.Arity
import io.github.seggan.metis.runtime.values.Value

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

    fun compileCode(name: String, code: List<AstNode.Statement>): Chunk {
        check(scope >= 0) { "Cannot use a Compiler more than once" }
        val (insns, spans) = compileStatements(code).unzip()
        return Chunk(name, insns, Arity(args.size), upvalues, spans, file)
    }

    private fun compileStatements(statements: List<AstNode.Statement>): List<FullInsn> {
        return statements.flatMap(::compileStatement)
    }

    private fun compileBlock(block: AstNode.Block): List<FullInsn> {
        scope++
        return compileStatements(block) + exitScope(block.span)
    }

    private fun exitScope(span: Span): List<FullInsn> {
        return buildList {
            while (localStack.isNotEmpty()) {
                val local = localStack.first()
                if (local.scope == scope) {
                    localStack.removeFirst()
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
            is AstNode.Break -> TODO()
            is AstNode.Continue -> TODO()
            is AstNode.Do -> TODO()
            is AstNode.For -> TODO()
            is AstNode.If -> TODO()
            is AstNode.Return -> buildList {
                addAll(compileExpression(statement.value))
                add(Insn.Return to statement.span)
                while (localStack.isNotEmpty()) {
                    addAll(exitScope(statement.span))
                }
                add(Insn.Finish to statement.span)
            }

            is AstNode.VarAssign -> TODO()
            is AstNode.While -> TODO()
            is AstNode.Block -> compileBlock(statement)
        }
    }

    private fun compileExpression(expression: AstNode.Expression): List<FullInsn> {
        return when (expression) {
            is AstNode.BinaryOp -> buildList {
                addAll(compileExpression(expression.left))
                addAll(compileExpression(expression.right))
                add(Insn.BinaryOp(expression.op) to expression.span)
            }

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

            is AstNode.ColonCall -> buildList {
                var nargs = 0
                addAll(compileExpression(expression.expr))
                expression.args.forEach { arg ->
                    addAll(compileExpression(arg))
                    nargs++
                }
                add(Insn.CopyUnder(nargs) to expression.span)
                add(Insn.IndexImm(expression.name) to expression.span)
                add(Insn.Call(nargs + 1) to expression.span)
            }

            is AstNode.Literal -> listOf(Insn.Push(expression.value) to expression.span)
            is AstNode.UnaryOp -> listOf(Insn.UnaryOp(expression.op) to expression.span)
            is AstNode.Var -> {
                val name = expression.name
                val local = resolveLocal(name)
                if (local != null) {
                    return listOf(Insn.GetLocal(local.index) to expression.span)
                }
                val upvalue = resolveUpvalue(name)
                if (upvalue != null) {
                    return listOf(Insn.GetUpvalue(upvalues.indexOf(upvalue)) to expression.span)
                }
                listOf(
                    Insn.GetGlobals to expression.span,
                    Insn.IndexImm(name) to expression.span
                )
            }

            is AstNode.FunctionDef -> compileFunctionDef(expression)
        }
    }

    private fun compileFunctionDef(fn: AstNode.FunctionDef): List<FullInsn> {
        val compiler = Compiler(file, fn.args, this)
        val chunk = compiler.compileCode("<function>", fn.body)
        return listOf(Insn.Push(chunk) to fn.span)
    }

    private fun compileVarDecl(decl: AstNode.VarDecl): List<FullInsn> {
        return if (decl.visibility == Visibility.GLOBAL) {
            buildList {
                add(Insn.GetGlobals to decl.span)
                addAll(compileExpression(decl.value))
                add(Insn.SetImm(decl.name) to decl.span)
            }
        } else {
            localStack.addFirst(Local(decl.name, scope, localStack.size))
            compileExpression(decl.value)
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