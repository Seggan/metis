package io.github.seggan.metis.parsing

import java.lang.ref.WeakReference
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Represents a place where source code can be retrieved from.
 *
 * @property name The name of the source.
 * @property textGetter A function that retrieves the source code from the name.
 */
class CodeSource(val name: String, private val textGetter: (String) -> String) {

    private var textRef: WeakReference<String> = WeakReference(null)

    val text: String
        get() = textRef.get() ?: textGetter(name).also { textRef = WeakReference(it) }

    override fun equals(other: Any?) = other is CodeSource && other.name == name

    override fun hashCode() = name.hashCode()

    override fun toString() = "FileInfo($name)"

    companion object {
        /**
         * Creates a [CodeSource] from a string.
         *
         * @param name The name of the source.
         * @param text The source code.
         * @return The created [CodeSource].
         */
        fun constant(name: String, text: String) = CodeSource(name) { text }

        /**
         * Creates a [CodeSource] from a [Path]. Will use the base filename as the name.
         *
         * @param path The path to the source.
         * @return The created [CodeSource].
         */
        fun fromPath(path: Path) = CodeSource(path.fileName.toString()) { path.readText() }
    }
}
