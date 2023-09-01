package io.github.seggan.metis.parsing

import io.github.seggan.metis.errors.MetisException

class ParseException(message: String, span: Span) : MetisException(message, span)