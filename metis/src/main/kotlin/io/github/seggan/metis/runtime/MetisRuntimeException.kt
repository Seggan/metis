package io.github.seggan.metis.runtime

import io.github.seggan.metis.MetisException
import io.github.seggan.metis.parsing.Span

class MetisRuntimeException(message: String, span: Span? = null) : MetisException(message, span)