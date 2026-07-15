package io.github.seggan.metis.compilation

import io.github.seggan.metis.compilation.op.UnOp
import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.parsing.SyntaxException
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.value.Arity
import io.github.seggan.metis.runtime.value.MetisRuntimeException
import io.github.seggan.metis.runtime.value.MetisTable
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.filterTo
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.indexOf
import kotlin.collections.indexOfFirst
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.plus
import kotlin.collections.unzip
import kotlin.collections.withIndex
import kotlin.collections.zip
import kotlin.ranges.first
import kotlin.sequences.first
import kotlin.sequences.indexOf
import kotlin.text.first
import kotlin.text.indexOf

class Compiler private constructor(
    private val args: List<String>,
    private val enclosingCompiler: Compiler?
) : RegisterHolder {

    private val id = UUID.randomUUID()

    private val freeRegisters = ArrayDeque<Register>()
    private var registerCount = 0

    private val localStack = ArrayDeque<Local>()

    private val loopStack = ArrayDeque<LoopInfo>()
    private val errorScopeStack = ArrayDeque<Int>()

    private var scope = 0

    init {
        for ((i, arg) in args.withIndex()) {
            localStack.addFirst(Local(arg, 0, i, nextFreeRegister()))
        }
    }

    private fun compileCode(name: String, code: AstNode.Block): Chunk {
        check(scope >= 0) { "Cannot use a Compiler more than once" }
        val compiled = compileBlock(code, false).filterTo(mutableListOf()) { it.first != Insn.NoOp }
        for (marker in compiled.filter { it.first is Insn.Label }) {
            backpatch(compiled, marker.first as Insn.Label)
        }
        val (insns, spans) = compiled.unzip()
        return Chunk(name, insns, Arity(args.size, args.firstOrNull() == "self"), id, spans)
    }

    private fun compileStatements(statements: List<AstNode.Statement>): List<FullInsn> {
        return statements.flatMap(::compileStatement)
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
                +Insn.Index(nextFreeRegister(), target, index)
            }

            is AstNode.CombinedCall -> buildExpression(expression) {
                TODO()
            }

            is AstNode.Literal -> buildExpression(expression) {
                +Insn.SetValue(nextFreeRegister(), expression.value)
            }

            is AstNode.Var -> buildExpression(expression) {
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
            is AstNode.ListLiteral -> buildExpression(expression) {
                expression.values.forEach { value ->
                    +compileExpression(value)
                }
                +Insn.PushList(expression.values.size)
            }

            is AstNode.TableLiteral -> buildExpression(expression) {
                expression.values.forEach { (key, value) ->
                    +compileExpression(key)
                    +compileExpression(value)
                }
                +Insn.PushTable(expression.values.size)
            }

            is AstNode.ErrorLiteral -> compileErrorLiteral(expression)
        }
    }

    private fun compileUnOp(op: AstNode.UnaryOp) = buildInsns(op.span) {
        +compileExpression(op.expr)
        if (op.op == UnOp.NOT) {
            +Insn.Not
        } else {
            +Insn.MetaCall(0, op.op.metamethod!!)
        }
    }

    private fun compileTernaryOp(op: AstNode.TernaryOp) = buildInsns(op.span) {
        val end = Insn.Label()
        val falseLabel = Insn.Label()
        +compileExpression(op.condition)
        +Insn.RawJumpIf(falseLabel, false)
        +compileExpression(op.trueExpr)
        +Insn.RawJump(end)
        +falseLabel
        +compileExpression(op.falseExpr)
        +end
    }

    private fun compileFunctionDef(fn: AstNode.FunctionLiteral): List<FullInsn> {
        val compiler = Compiler(fn.args, this)
        val chunk = compiler.compileCode("<function>", fn.body)
        return listOf(Insn.PushClosure(chunk) to fn.span)
    }

    private fun compileErrorLiteral(error: AstNode.ErrorLiteral) = buildInsns(error.span) {
        +compileExpression(error.message)
        +Insn.MetaCall(0, "__str__")
        if (error.companionData != null) {
            +compileExpression(error.companionData)
        } else {
            +Insn.Push(MetisTable())
        }
        +Insn.PushError(error.type)
    }

    private fun compileWhile(statement: AstNode.While) = buildInsns(statement.span) {
        val start = Insn.Label()
        val end = Insn.Label()
        loopStack.push(LoopInfo(start, end, scope))
        +start
        +compileExpression(statement.condition)
        +Insn.RawJumpIf(end, false)
        +compileBlock(statement.body)
        +Insn.RawJump(start)
        +end
        loopStack.pop()
    }

    private fun compileFor(statement: AstNode.For) = buildInsns(statement.span) {
        +compileExpression(statement.iterable)
        +Insn.MetaCall(0, "__iter__")
        localStack.addFirst(Local("", scope, localStack.size))
        val start = Insn.Label()
        val end = Insn.Label()
        loopStack.push(LoopInfo(start, end, scope))
        +start
        +Insn.CopyUnder(0)
        +Insn.CopyUnder(0)
        +Insn.Push("hasNext")
        +Insn.Index
        +Insn.Call(1, true)
        +Insn.RawJumpIf(end, false)
        +Insn.CopyUnder(0)
        +Insn.CopyUnder(0)
        +Insn.Push("next")
        +Insn.Index
        +Insn.Call(1, true)
        localStack.addFirst(Local(statement.name, scope + 1, localStack.size))
        +compileBlock(statement.body)
        +Insn.RawJump(start)
        +end
        loopStack.pop()
        localStack.removeFirst()
        +Insn.Pop
    }

    private fun compileIf(statement: AstNode.If) = buildInsns(statement.span) {
        +compileExpression(statement.condition)
        val end = Insn.Label()
        +Insn.RawJumpIf(end, false)
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
        val excepts = statement.excepts.map {
            val label = Insn.Label()
            label to ErrorHandler(it.name, label)
        }
        for (except in excepts) {
            +Insn.PushErrorHandler(except.second)
        }
        val finallyLabel = Insn.Label()
        if (statement.finally != null) {
            +Insn.PushFinally(finallyLabel)
        }
        val endLabels = mutableListOf<Insn.Label>()
        val blockLabel = Insn.Label()
        +Insn.RawJump(blockLabel)
        for ((info, except) in excepts.zip(statement.excepts)) {
            val end = Insn.Label()
            endLabels.add(end)
            +info.first
            +Insn.PopErrorHandler
            localStack.addFirst(Local(except.variable ?: "", scope + 1, localStack.size))
            +compileBlock(except.body)
            +Insn.RawJump(end)
        }
        +blockLabel
        errorScopeStack.push(scope)
        +compileBlock(statement.body)
        errorScopeStack.pop()
        if (excepts.isNotEmpty()) +Insn.PopErrorHandler
        for (end in endLabels) {
            +end
        }
        repeat(excepts.size - 1) {
            +Insn.PopErrorHandler
        }
        if (statement.finally != null) {
            +finallyLabel
            +Insn.PopFinally
            +compileBlock(statement.finally)
            +Insn.Push(MetisRuntimeException.Finally())
            +Insn.ToBeUsed
            +Insn.Raise
        }
    }

    private fun compileImport(statement: AstNode.Import) = buildInsns(statement.span) {
        +Insn.Import(statement.name)
        +Insn.PostImport(statement.name, statement.global)
        if (!statement.global) {
            localStack.addFirst(Local(statement.name, scope, localStack.size))
        }
    }

    private fun compileVarDecl(decl: AstNode.VarDecl) = buildInsns(decl.span) {
        val oldLocal = resolveLocal(decl.name)
        if (oldLocal != null && oldLocal.scope == scope - 1) {
            throw SyntaxException("Variable '${decl.name}' has already been declared", 0, decl.span)
        }
        localStack.addFirst(Local(decl.name, scope, localStack.size))
        +compileExpression(decl.value)
        if (decl.visibility == Visibility.GLOBAL) {
            +Insn.CopyUnder(0)
            +Insn.SetGlobal(decl.name)
        }
    }

    private fun compileVarAssign(assign: AstNode.VarAssign): List<FullInsn> {
        return when (val target = assign.target) {
            is AstNode.Index -> buildInsns(target.span) {
                +compileExpression(target.target)
                +compileExpression(target.index)
                if (assign.type == null) {
                    +compileExpression(assign.value)
                } else {
                    +Insn.CopyUnder(1)
                    +Insn.CopyUnder(1)
                    +Insn.Index
                    assign.type.op.generateCode(
                        this,
                        // This will just consume the value on the stack
                        listOf(Insn.NoOp to assign.span),
                        compileExpression(assign.value)
                    )
                }
                +Insn.Set
            }

            is AstNode.Var -> buildInsns(target.span) {
                val name = target.name
                val local = resolveLocal(name)
                if (local != null) {
                    if (assign.type == null) {
                        +compileExpression(assign.value)
                    } else {
                        +Insn.GetLocal(local.index)
                        assign.type.op.generateCode(
                            this,
                            listOf(Insn.NoOp to assign.span),
                            compileExpression(assign.value)
                        )
                    }
                    +Insn.SetLocal(local.index)
                } else {
                    resolveUpvalue(name)?.let { upvalue ->
                        val index = upvalues.indexOf(upvalue)
                        if (assign.type == null) {
                            +compileExpression(assign.value)
                        } else {
                            +Insn.GetUpvalue(index)
                            assign.type.op.generateCode(
                                this,
                                listOf(Insn.NoOp to assign.span),
                                compileExpression(assign.value)
                            )
                        }
                        +Insn.SetUpvalue(index)
                    }
                }
                +Insn.UpdateGlobal(name)
            }
        }
    }

    private fun resolveLocal(name: String): Local? {
        return localStack.firstOrNull { it.name == name }
    }

    override fun nextFreeRegister(): Register = freeRegisters.removeFirstOrNull() ?: registerCount++

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

private data class Local(val name: String, val scope: Int, val index: Int, val register: Register)

private fun backpatch(insns: MutableList<FullInsn>, label: Insn.Label) {
    val markerIndex = insns.indexOfFirst { it.first == label }
    for (i in insns.indices) {
        val (insn, span) = insns[i]
        if (insn is Insn.RawJump && insn.label == label) {
            insns[i] = Insn.Jump(markerIndex - i - 1) to span
        } else if (insn is Insn.RawJumpIf && insn.label == label) {
            insns[i] = Insn.JumpIf(markerIndex - i - 1, insn.condition, insn.consume) to span
        }
    }
}