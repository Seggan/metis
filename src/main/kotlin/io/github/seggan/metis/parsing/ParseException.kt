package io.github.seggan.metis.parsing

import io.github.seggan.metis.MetisException

open class ParseException(message: String, val consumed: Int, span: Span) : MetisException(message, mutableListOf(span))

class UnexpectedTokenException(val token: Token, val expected: List<Token.Type>, consumed: Int, span: Span) :
    ParseException("Expected ${expected.joinToString(" or ")}, got ${token.type}", consumed, span)