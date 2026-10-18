package uz.sadora.doctor.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberOpenDocument(): (bytes: ByteArray, fileName: String, mimeType: String) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { bytes, fileName, mimeType ->
            runCatching {
                // Under the cache folder the FileProvider exposes, replaced by the next one
                // opened; the system may clear it whenever it likes.
                val folder = File(context.cacheDir, "shared").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val file = File(folder, cacheFileName(fileName)).apply { writeBytes(bytes) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(view)
                } catch (_: ActivityNotFoundException) {
                    // No PDF viewer: the share sheet, so it can at least reach Drive or a messenger.
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                }
            }.isSuccess
        }
    }
}
