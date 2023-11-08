package io.github.seggan.metis.util

/**
 * Converts string escapes to their actual characters.
 *
 * @return The unescaped string.
 */
fun String.escape(): String {
    val sb = StringBuilder()
    var i = 0
    while (i < length) {
        val c = this[i++]
        if (c == '\\') {
            when (val next = this[i++]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'x' -> {
                    val hex = this.substring(i, i + 2)
                    sb.append(hex.toInt(16).toChar())
                    i += 2
                }

                'u' -> {
                    val hex = this.substring(i, i + 4)
                    sb.append(hex.toInt(16).toChar())
                    i += 4
                }

                else -> sb.append(next)
            }
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}