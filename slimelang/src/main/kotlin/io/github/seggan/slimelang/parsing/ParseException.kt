package io.github.seggan.slimelang.parsing

import io.github.seggan.slimelang.errors.SlException

class ParseException(message: String, span: Span) : SlException(message, span)