package io.github.seggan.metis.compilation

import io.github.seggan.metis.compilation.op.Metamethod
import io.github.seggan.metis.compilation.op.UnOp
import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.parsing.SyntaxException
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.value.MetisTable
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.filterNotTo
import kotlin.collections.firstOrNull
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.indexOfFirst
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.mutableSetOf
import kotlin.collections.plus
import kotlin.collections.toList
import kotlin.collections.unzip
import kotlin.collections.withIndex

class Compiler private constructor(
    private val args: List<String>,
    private val enclosingCompiler: Compiler?
) : RegisterHolder {

    private val id = UUID.randomUUID()

    private val freeRegisters = mutableSetOf<Register>()
    private var registerCount = 0

    private val localStack = ArrayDeque<Local>()

    private val loopStack = ArrayDeque<LoopInfo>()
    private val errorScopeStack = ArrayDeque<Int>()

    private var scope = 0

    init {
        for ((i, arg) in args.withIndex()) {
            localStack.addFirst(Local(arg, 0, nextFreeRegister()))
        }
    }

    private fun compileCode(name: String, code: AstNode.Block): Chunk {
        check(scope >= 0) { "Cannot use a Compiler more than once" }
        val compiled = compileBlock(code, false).filterNotTo(mutableListOf()) { (insn, _) ->
            insn == Insn.NoOp ||
                    (insn is Insn.Clear && insn.registers.isEmpty()) ||
                    (insn is Insn.Move && insn.src == insn.dest)
        }
        for (marker in compiled.filter { it.first is Insn.Label }) {
            backpatch(compiled, marker.first as Insn.Label)
        }
        val (insns, spans) = compiled.unzip()
        return Chunk(name, insns, registerCount, args.size, id, spans)
    }

    private fun compileStatements(statements: List<AstNode.Statement>): List<FullInsn> {
        return statements.flatMap { compileStatement(it) + (Insn.Clear(freeRegisters.toList()) to it.span) }
    }

    private fun compileBlock(block: AstNode.Block, remove: Boolean = true): List<FullInsn> {
        scope++
        return compileStatements(block) + exitScope(block.span, remove)
    }

    private fun exitScope(span: Span, remove: Boolean) = buildInsns(span) {
        val it = localStack.iterator()
        while (it.hasNext()) {
            val local = it.next()
            if (local.scope == scope) {
                if (remove) it.remove()
            }
        }
        if (remove) scope--
    }

    private inline fun earlyExitScope(span: Span, cond: (Int) -> Boolean) = buildInsns(span) {
        for (local in localStack) {
            if (cond(local.scope)) {
                freeRegisters.add(local.register)
            }
        }
    }

    private fun compileStatement(statement: AstNode.Statement): List<FullInsn> {
        return when (statement) {
            is AstNode.Expression -> {
                val (expr, reg) = compileExpression(statement)
                freeRegisters.add(reg)
                expr
            }

            is AstNode.VarDecl -> compileVarDecl(statement)
            is AstNode.VarAssign -> compileVarAssign(statement)
            is AstNode.Return -> buildInsns(statement.span) {
                val reg = +compileExpression(statement.value)
                +earlyExitScope(statement.span) { true }
                +Insn.Return(reg)
            }

            is AstNode.While -> compileWhile(statement)
            is AstNode.For -> compileFor(statement)
            is AstNode.Break -> buildInsns(statement.span) {
                val info = loopStack.lastOrNull() ?: throw SyntaxException(
                    "Cannot break outside of a loop",
                    0,
                    statement.span
                )
                +earlyExitScope(statement.span) { it > info.scope }
                +Insn.RawJump(info.end)
            }

            is AstNode.Continue -> buildInsns(statement.span) {
                val info = loopStack.lastOrNull() ?: throw SyntaxException(
                    "Cannot continue outside of a loop",
                    0,
                    statement.span
                )
                +earlyExitScope(statement.span) { it > info.scope }
                +Insn.RawJump(info.start)
            }

            is AstNode.If -> compileIf(statement)
            is AstNode.Block -> compileBlock(statement)
            is AstNode.DoExcept -> compileDoExcept(statement)
            is AstNode.Raise -> buildInsns(statement.span) {
                val reg = +compileExpression(statement.value)
                if (errorScopeStack.isNotEmpty()) {
                    val info = errorScopeStack.pop()
                    +earlyExitScope(statement.span) { it > info }
                }
                TODO()
            }

            is AstNode.Import -> compileImport(statement)
        }
    }

    private fun compileExpression(expression: AstNode.Expression): Pair<List<FullInsn>, Register> {
        return when (expression) {
            is AstNode.UnaryOp -> compileUnOp(expression)
            is AstNode.BinaryOp -> buildExpression(expression) {
                expression.op.generateCode(
                    this,
                    this@Compiler,
                    { compileExpression(expression.left) },
                    { compileExpression(expression.right) }
                )
            }

            is AstNode.TernaryOp -> compileTernaryOp(expression)

            is AstNode.Call -> buildExpression(expression) {
                val registers = expression.args.map { arg ->
                    +compileExpression(arg)
                }
                val expr = +compileExpression(expression.expr)
                freeRegisters(registers)
                freeRegisters(expr)
                +Insn.Call(nextFreeRegister(), expr, registers)
            }

            is AstNode.Index -> buildExpression(expression) {
                val target = +compileExpression(expression.target)
                val index = +compileExpression(expression.index)
                freeRegisters(target, index)
                +Insn.MetaCall(nextFreeRegister(), target, Metamethod.INDEX, listOf(index))
            }

            is AstNode.CombinedCall -> buildExpression(expression) {
                TODO()
            }

            is AstNode.Literal -> buildExpression(expression) {
                +Insn.SetValue(nextFreeRegister(), expression.value)
            }

            is AstNode.Var -> buildExpression(expression) {
                val name = expression.name
                val local = resolveLocal(name)
                if (local != null) {
                    +Insn.Move(nextFreeRegister(), local.register)
                } else {
                    +Insn.GetGlobal(nextFreeRegister(), name)
                }
            }

            is AstNode.FunctionLiteral -> compileFunctionDef(expression)
            is AstNode.ListLiteral -> buildExpression(expression) {
                val values = expression.values.map { value ->
                    +compileExpression(value)
                }
                freeRegisters(values)
                +Insn.ConstructList(nextFreeRegister(), values)
            }

            is AstNode.TableLiteral -> buildExpression(expression) {
                val values = expression.values.map { (key, value) ->
                    +compileExpression(key) to +compileExpression(value)
                }
                values.forEach { freeRegisters(it.first, it.second) }
                +Insn.ConstructTable(nextFreeRegister(), values)
            }

            is AstNode.ErrorLiteral -> compileErrorLiteral(expression)
        }
    }

    private fun compileUnOp(op: AstNode.UnaryOp) = buildExpression(op) {
        val expr = +compileExpression(op.expr)
        freeRegisters(expr)
        if (op.op == UnOp.NOT) {
            +Insn.Not(nextFreeRegister(), expr)
        } else {
            +Insn.MetaCall(nextFreeRegister(), expr, op.op.metamethod!!, emptyList())
        }
    }

    private fun compileTernaryOp(op: AstNode.TernaryOp) = buildExpression(op) {
        val end = Insn.Label()
        val falseLabel = Insn.Label()
        val dest = +compileExpression(op.condition)
        +Insn.RawJumpIf(falseLabel, condition = false, dest)
        val trueExpr = +compileExpression(op.trueExpr)
        +Insn.Move(dest, trueExpr)
        freeRegisters(trueExpr)
        +Insn.RawJump(end)
        +falseLabel
        val falseExpr = +compileExpression(op.falseExpr)
        +Insn.Move(dest, falseExpr)
        freeRegisters(falseExpr)
        +end
        dest
    }

    private fun compileFunctionDef(fn: AstNode.FunctionLiteral): Pair<List<FullInsn>, Register> {
        val compiler = Compiler(fn.args, this)
        val chunk = compiler.compileCode("<function>", fn.body)
        return TODO()
    }

    private fun compileErrorLiteral(error: AstNode.ErrorLiteral) = buildExpression(error) {
        val expr = +compileExpression(error.message)
        freeRegisters(expr)
        val dest = nextFreeRegister()
        +Insn.MetaCall(dest, expr, Metamethod.STR, emptyList())
        val companion = if (error.companionData != null) {
            +compileExpression(error.companionData)
        } else {
            +Insn.SetValue(nextFreeRegister(), MetisTable())
        }
        freeRegisters(companion)
        +Insn.ConstructError(dest, error.type, dest, companion)
    }

    private fun compileWhile(statement: AstNode.While) = buildInsns(statement.span) {
        val start = Insn.Label()
        val end = Insn.Label()
        loopStack.push(LoopInfo(start, end, scope))
        +start
        val cond = +compileExpression(statement.condition)
        +Insn.RawJumpIf(end, condition = false, cond)
        +compileBlock(statement.body)
        +Insn.RawJump(start)
        +end
        freeRegisters(cond)
        loopStack.pop()
    }

    private fun compileFor(statement: AstNode.For) = buildInsns(statement.span) {
        val iter = +compileExpression(statement.iterable)
        +Insn.MetaCall(iter, iter, Metamethod.ITER, emptyList())
        val start = Insn.Label()
        val end = Insn.Label()
        loopStack.push(LoopInfo(start, end, scope))
        +start
        val temp = nextFreeRegister()
        +Insn.SetValue(nextFreeRegister(), "hasNext")
        +Insn.MetaCall(dest = temp, target = iter, method = Metamethod.INDEX, args = listOf(temp))
        +Insn.Call(dest = temp, target = temp, args = listOf(temp))
        +Insn.RawJumpIf(end, condition = false, temp)
        +Insn.SetValue(temp, "next")
        +Insn.MetaCall(dest = temp, target = iter, method = Metamethod.INDEX, args = listOf(temp))
        +Insn.Call(dest = temp, target = temp, args = listOf(temp))
        localStack.push(Local(statement.name, scope, temp))
        +compileBlock(statement.body)
        localStack.pop()
        +Insn.RawJump(start)
        +end
        loopStack.pop()
        freeRegisters(iter, temp)
    }

    private fun compileIf(statement: AstNode.If) = buildInsns(statement.span) {
        val cond = +compileExpression(statement.condition)
        val end = Insn.Label()
        +Insn.RawJumpIf(end, condition = false, cond)
        freeRegisters(cond)
        +compileBlock(statement.body)
        if (statement.elseBody != null) {
            val realEnd = Insn.Label()
            +Insn.RawJump(realEnd)
            +end
            +compileBlock(statement.elseBody)
            +realEnd
        } else {
            +end
        }
    }

    private fun compileDoExcept(statement: AstNode.DoExcept) = buildInsns(statement.span) {
        TODO()
    }

    private fun compileImport(statement: AstNode.Import) = buildInsns(statement.span) {
        TODO()
        if (!statement.global) {
            //localStack.addFirst(Local(statement.name, scope, localStack.size))
        }
    }

    private fun compileVarDecl(decl: AstNode.VarDecl) = buildInsns(decl.span) {
        val oldLocal = resolveLocal(decl.name)
        if (oldLocal != null && oldLocal.scope == scope - 1) {
            throw SyntaxException("Variable '${decl.name}' has already been declared", 0, decl.span)
        }
        val value = +compileExpression(decl.value)
        if (decl.visibility == Visibility.GLOBAL) {
            +Insn.SetGlobal(decl.name, value)
        } else {
            localStack.addFirst(Local(decl.name, scope, value))
            value.name = decl.name
        }
    }

    private fun compileVarAssign(assign: AstNode.VarAssign): List<FullInsn> {
        return when (val target = assign.target) {
            is AstNode.Index -> buildInsns(target.span) {
                val rTarget = +compileExpression(target.target)
                val rIndex = +compileExpression(target.index)
                val rValue = if (assign.type == null) {
                    +compileExpression(assign.value)
                } else {
                    assign.type.op.generateCode(
                        this,
                        this@Compiler,
                        {
                            buildExpression(target.target) {
                                +Insn.MetaCall(
                                    dest = nextFreeRegister(),
                                    target = rTarget,
                                    method = Metamethod.INDEX,
                                    args = listOf(rIndex)
                                )
                            }
                        },
                        { compileExpression(assign.value) }
                    )
                }
                freeRegisters(rTarget, rIndex, rValue)
                val trash = nextFreeRegister()
                freeRegisters(trash)
                +Insn.MetaCall(dest = trash, target = rTarget, method = Metamethod.SET, args = listOf(rIndex, rValue))
            }

            is AstNode.Var -> buildInsns(target.span) {
                val name = target.name
                val value = +compileExpression(assign.value)
                val local = resolveLocal(name)
                if (local != null) {
                    val value = if (assign.type == null) {
                        +compileExpression(assign.value)
                    } else {
                        assign.type.op.generateCode(
                            this,
                            this@Compiler,
                            {
                                buildExpression(assign.target) {
                                    +Insn.Move(nextFreeRegister(), local.register)
                                }
                            },
                            { compileExpression(assign.value) }
                        )
                    }
                    +Insn.Move(local.register, value)
                } else {
                    +Insn.UpdateGlobal(name, value)
                }
                freeRegisters(value)
            }
        }
    }

    private fun resolveLocal(name: String): Local? {
        return localStack.firstOrNull { it.name == name }
    }

    override fun nextFreeRegister(): Register {
        val iter = freeRegisters.iterator()
        if (iter.hasNext()) {
            return iter.next().also { iter.remove() }
        }
        return Register(registerCount++)
    }

    override fun freeRegisters(registers: List<Register>) {
        for (register in registers) {
            freeRegisters.add(register)
        }
    }

    companion object {
        /**
         * Compiles the given [code] into a [Chunk] with the given [name]
         *
         * @param name The name of the chunk.
         * @param code The code to compile.
         * @return The compiled chunk.
         */
        fun compile(name: String, code: AstNode.Block): Chunk {
            return Compiler(emptyList(), null).compileCode(name, code)
        }
    }
}

private data class LoopInfo(val start: Insn.Label, val end: Insn.Label, val scope: Int)

private data class Local(val name: String, val scope: Int, val register: Register)

private fun backpatch(insns: MutableList<FullInsn>, label: Insn.Label) {
    val markerIndex = insns.indexOfFirst { it.first == label }
    for (i in insns.indices) {
        val (insn, span) = insns[i]
        if (insn is Insn.RawJump && insn.label == label) {
            insns[i] = Insn.Jump(markerIndex - i - 1) to span
        } else if (insn is Insn.RawJumpIf && insn.label == label) {
            insns[i] = Insn.JumpIf(markerIndex - i - 1, insn.condition, insn.register) to span
        }
    }
}