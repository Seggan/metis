package io.github.seggan.metis.compilation

import io.github.seggan.metis.parsing.AstNode
import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.parsing.SyntaxException
import io.github.seggan.metis.runtime.Arity
import io.github.seggan.metis.runtime.MetisRuntimeException
import io.github.seggan.metis.runtime.Value
import io.github.seggan.metis.runtime.chunk.Chunk
import io.github.seggan.metis.runtime.chunk.ErrorHandler
import io.github.seggan.metis.runtime.chunk.Insn
import io.github.seggan.metis.runtime.chunk.Upvalue
import io.github.seggan.metis.util.pop
import io.github.seggan.metis.util.push

/**
 * A compiler for Metis code. You may not call [compileCode] more than once.
 */
class Compiler private constructor(
    private val args: List<String>,
    private val enclosingCompiler: Compiler?
) {

    /**
     * Creates a new compiler
     */
    constructor() : this(emptyList(), null)

    private val localStack = ArrayDeque<Local>()
    private val upvalues = mutableListOf<Upvalue>()

    private val loopStack = ArrayDeque<LoopInfo>()
    private val errorScopeStack = ArrayDeque<Int>()

    private val depth: Int = enclosingCompiler?.depth ?: 0

    private var scope = 0

    init {
        for ((i, arg) in args.withIndex()) {
            localStack.addFirst(Local(arg, 0, i))
        }
    }

    /**
     * Compiles the given [code] into a [Chunk] with the given [name]. May only be called once per instance.
     *
     * @param name The name of the chunk.
     * @param code The code to compile.
     * @return The compiled chunk.
     */
    fun compileCode(name: String, code: AstNode.Block): Chunk {
        check(scope >= 0) { "Cannot use a Compiler more than once" }
        val compiled = compileBlock(code, false).toMutableList()
        for (marker in compiled.filter { it.first is Insn.Label }) {
            backpatch(compiled, marker.first as Insn.Label)
        }
        val (insns, spans) = compiled.unzip()
        return Chunk(name, insns, Arity(args.size, args.firstOrNull() == "self"), upvalues, spans)
    }

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
                if (local.capturing != null) {
                    +Insn.CloseUpvalue(local.capturing!!)
                } else {
                    +Insn.Pop
                }
            }
        }
        if (remove) scope--
    }

    private inline fun earlyExitScope(span: Span, cond: (Int) -> Boolean) = buildInsns(span) {
        for (local in localStack) {
            if (cond(local.scope)) {
                if (local.capturing != null) {
                    +Insn.CloseUpvalue(local.capturing!!)
                } else {
                    +Insn.Pop
                }
            }
        }
    }

    private fun compileStatement(statement: AstNode.Statement): List<FullInsn> {
        return when (statement) {
            is AstNode.Expression -> compileExpression(statement) + (Insn.Pop to statement.span)
            is AstNode.VarDecl -> compileVarDecl(statement)
            is AstNode.VarAssign -> compileVarAssign(statement)
            is AstNode.Return -> buildInsns(statement.span) {
                +compileExpression(statement.value)
                +Insn.ToBeUsed
                +earlyExitScope(statement.span) { true }
                +Insn.Return
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
                +compileExpression(statement.value)
                +Insn.ToBeUsed
                if (errorScopeStack.isNotEmpty()) {
                    val info = errorScopeStack.pop()
                    +earlyExitScope(statement.span) { it > info }
                }
                +Insn.Raise
            }

            is AstNode.Import -> compileImport(statement)
        }
    }

    private fun compileExpression(expression: AstNode.Expression): List<FullInsn> {
        return when (expression) {
            is AstNode.UnaryOp -> compileUnOp(expression)
            is AstNode.BinaryOp -> buildInsns(expression.span) {
                expression.op.generateCode(
                    this,
                    compileExpression(expression.left),
                    compileExpression(expression.right)
                )
            }

            is AstNode.TernaryOp -> compileTernaryOp(expression)

            is AstNode.Call -> buildInsns(expression.span) {
                expression.args.forEach { arg ->
                    +compileExpression(arg)
                }
                +compileExpression(expression.expr)
                +Insn.Call(expression.args.size, false)
            }

            is AstNode.Index -> buildInsns(expression.span) {
                +compileExpression(expression.target)
                +compileExpression(expression.index)
                +Insn.Index
            }

            is AstNode.CombinedCall -> buildInsns(expression.span) {
                +compileExpression(expression.expr)
                val args = expression.args.map(::compileExpression)
                args.forEach { arg ->
                    +arg
                }
                +Insn.CopyUnder(args.size)
                +Insn.Push(expression.name)
                +Insn.Index
                +Insn.Call(args.size + 1, true)
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

    private fun compileUnOp(op: AstNode.UnaryOp) = buildInsns(op.span) {
        when (op.op) {
            UnOp.NOT -> {
                +compileExpression(op.expr)
                +Insn.Not
            }

            UnOp.NEG -> {
                +compileExpression(op.expr)
                generateMetaCall("__neg__", 0)
            }
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
        generateMetaCall("__str__", 0)
        if (error.companionData != null) {
            +compileExpression(error.companionData)
        } else {
            +Insn.Push(Value.Table())
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
        generateMetaCall("__iter__", 0)
        localStack.addFirst(Local("", scope, localStack.size))
        val start = Insn.Label()
        val end = Insn.Label()
        loopStack.push(LoopInfo(start, end, scope))
        +start
        +Insn.CopyUnder(0)
        +Insn.CopyUnder(0)
        +Insn.Push("has_next")
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
                +compileExpression(assign.value)
                +Insn.Set
            }

            is AstNode.Var -> buildInsns(target.span) {
                val name = target.name
                +compileExpression(assign.value)
                +Insn.UpdateGlobal(name)
                resolveLocal(name)?.let { local ->
                    +Insn.SetLocal(local.index)
                    return@buildInsns
                }
                resolveUpvalue(name)?.let { upvalue ->
                    +Insn.SetUpvalue(upvalues.indexOf(upvalue))
                    return@buildInsns
                }
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

private data class LoopInfo(val start: Insn.Label, val end: Insn.Label, val scope: Int)

private data class Local(val name: String, val scope: Int, val index: Int, var capturing: Upvalue? = null)