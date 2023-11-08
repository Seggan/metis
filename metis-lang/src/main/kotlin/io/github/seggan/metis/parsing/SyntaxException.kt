package io.github.seggan.metis.parsing

import io.github.seggan.metis.util.MetisException

open class SyntaxException(message: String, val consumed: Int, span: Span) :
    MetisException(message, mutableListOf(span))

class UnexpectedTokenException(val token: Token, val expected: List<Token.Type>, consumed: Int, span: Span) :
    SyntaxException("Got ${token.type}, expected ${expected.joinToString(" or ")}", consumed, span)