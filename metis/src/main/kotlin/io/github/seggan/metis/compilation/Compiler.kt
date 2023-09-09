package io.github.seggan.metis.compilation

import io.github.seggan.metis.Visibility
import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.values.Arity
import io.github.seggan.metis.runtime.values.Value

class Compiler private constructor(
    private val file: Pair<String, String>,
    private val args: List<String>,
    private val enclosingCompiler: Compiler?
) {

    constructor(filename: String, file: String) : this(filename to file, emptyList(), null)

    private val localStack = ArrayDeque<ArrayDeque<String>>()

    init {
        localStack.addFirst(ArrayDeque(args))
    }

    fun compileCode(name: String, code: List<AstNode.Statement>): Chunk {
        val (insns, spans) = compileStatements(code).unzip()
        return Chunk(name, insns, Arity(args.size), spans, file)
    }

    private fun compileStatements(statements: List<AstNode.Statement>): List<FullInsn> {
        return statements.flatMap(::compileStatement)
    }

    private fun compileBlock(block: AstNode.Block): List<FullInsn> {
        localStack.addFirst(ArrayDeque())
        val ret = compileStatements(block).toMutableList()
        for (local in localStack.removeFirst()) {
            ret.add(Insn.Pop to block.span)
        }
        return ret
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
            is AstNode.Return -> compileExpression(statement.value) + (Insn.Return to statement.span)
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
                val index = resolveLocal(name)
                if (index != -1) {
                    return listOf(Insn.GetLocal(index) to expression.span)
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

    private fun resolveLocal(name: String): Int {
        var index = localStack.sumOf { it.size }
        for (scope in localStack) {
            for (local in scope) {
                index--
                if (name == local) {
                    return index
                }
            }
        }
        return -1
    }

    private fun compileVarDecl(decl: AstNode.VarDecl): List<FullInsn> {
        return if (decl.visibility == Visibility.GLOBAL) {
            buildList {
                add(Insn.GetGlobals to decl.span)
                addAll(compileExpression(decl.value))
                add(Insn.SetImm(decl.name) to decl.span)
            }
        } else {
            localStack.first().addFirst(decl.name)
            compileExpression(decl.value)
        }
    }
}

private typealias FullInsn = Pair<Insn, Span>