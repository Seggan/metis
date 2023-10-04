package io.github.seggan.metis.debug

import io.github.seggan.metis.parsing.Span
import io.github.seggan.metis.runtime.chunk.Insn

data class DebugInfo(val span: Span, val insn: Insn)
