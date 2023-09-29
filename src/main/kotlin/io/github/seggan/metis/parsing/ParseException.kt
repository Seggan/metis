package io.github.seggan.metis.parsing

import io.github.seggan.metis.MetisException

class ParseException(message: String, val consumed: Int, span: Span) : MetisException(message, mutableListOf(span))