package uz.sadora.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * The little markdown a model writes, drawn instead of shown: `**bold**`, `*italic*`,
 * `# headings`, `* ` and `- ` list items, and `` `code` `` lose their marks and keep
 * their meaning. Anything else is plain text, so a lone star ("5 * 3") stays a star.
 */
fun chatMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.trim().replace(Regex("\n{3,}"), "\n\n").lines()
    lines.forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        val line = raw.trimEnd()
        val heading = Regex("^\\s*#{1,6}\\s+(.*)$").find(line)
        val bullet = Regex("^(\\s*)[*\\-•]\\s+(.*)$").find(line)
        when {
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                inline(heading.groupValues[1])
            }
            bullet != null -> {
                append(if (bullet.groupValues[1].length >= 2) "    ◦ " else "• ")
                inline(bullet.groupValues[2])
            }
            else -> inline(line)
        }
    }
}

/** Bold, italic and code spans inside one line. An unclosed mark is kept as written. */
private fun AnnotatedString.Builder.inline(line: String) {
    var i = 0
    while (i < line.length) {
        val bold = line.startsWith("**", i) || line.startsWith("__", i)
        val mark = when {
            bold -> line.substring(i, i + 2)
            line[i] == '*' || line[i] == '`' -> line[i].toString()
            else -> null
        }
        val close = mark?.let { closingMark(line, i, it) }
        if (mark == null || close == null) {
            append(line[i])
            i++
            continue
        }
        val inner = line.substring(i + mark.length, close)
        when (mark) {
            "**", "__" -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { inline(inner) }
            "*" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inline(inner) }
            else -> append(inner)
        }
        i = close + mark.length
    }
}

/**
 * Where the span opened by [mark] at [start] ends, or null when it is not a span: an
 * opening mark has a word right after it and a closing one a word right before it.
 */
private fun closingMark(line: String, start: Int, mark: String): Int? {
    val from = start + mark.length
    if (from >= line.length || line[from].isWhitespace()) return null
    var at = line.indexOf(mark, from)
    while (at != -1) {
        val single = mark.length == 1 && mark != "`"
        // `*a **b** c*` — a star that is half of a pair is not this span's end.
        val partOfPair = single && (line.getOrNull(at + 1) == '*' || line.getOrNull(at - 1) == '*')
        if (at > from && !line[at - 1].isWhitespace() && !partOfPair) return at
        at = line.indexOf(mark, at + if (partOfPair) 2 else 1)
    }
    return null
}
