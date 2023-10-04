package io.github.seggan.metis.parsing

import java.lang.ref.WeakReference
import java.nio.file.Path
import kotlin.io.path.readText

class CodeSource(val name: String, private val textGetter: (String) -> String) {

    private var textRef: WeakReference<String> = WeakReference(null)

    val text: String
        get() = textRef.get() ?: textGetter(name).also { textRef = WeakReference(it) }

    override fun equals(other: Any?) = other is CodeSource && other.name == name

    override fun hashCode() = name.hashCode()

    override fun toString() = "FileInfo($name)"

    companion object {
        fun constant(name: String, text: String) = CodeSource(name) { text }
        fun from(path: Path) = CodeSource(path.fileName.toString()) { path.readText() }
    }
}
