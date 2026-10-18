package uz.sadora.doctor.ui.components

import androidx.compose.runtime.Composable

/**
 * Hands a file the app downloaded to the phone: a PDF viewer if one is there, else the
 * share sheet. False when nothing on the phone could take it.
 */
@Composable
expect fun rememberOpenDocument(): (bytes: ByteArray, fileName: String, mimeType: String) -> Boolean

/** [name] safe to write as one file in the cache: no folders, nothing the file system refuses. */
internal fun cacheFileName(name: String, fallback: String = "report.pdf"): String {
    val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
        // By hand rather than a \p{L} class: Kotlin/Native's regex reads that class differently.
        .map { if (it.isLetterOrDigit() || it in "._ -") it else '_' }.joinToString("")
        .trim().trimStart('.')
    return cleaned.takeIf { it.isNotEmpty() }?.take(MaxFileName) ?: fallback
}

private const val MaxFileName = 96
