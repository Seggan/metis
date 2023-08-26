package io.github.seggan.slimelang.runtime

import io.github.seggan.slimelang.parsing.Span

class SlRuntimeException(message: String, var span: Span? = null) : RuntimeException(message)