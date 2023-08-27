package io.github.seggan.slimelang.runtime

import io.github.seggan.slimelang.errors.SlException
import io.github.seggan.slimelang.parsing.Span

class SlRuntimeException(message: String, span: Span? = null) : SlException(message, span)