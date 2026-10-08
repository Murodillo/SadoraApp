package uz.sadora.app.ui.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareAction(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            // The chooser is started from a composable, which may not be an Activity
            // context, so the task flag is what keeps it from throwing there.
            context.startActivity(
                Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@Composable
actual fun rememberImageShareAction(): (png: ByteArray, fileName: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { png, fileName ->
            // Written under the cache's own folder, which the file provider exposes and
            // nothing else; the system may clear it whenever it likes, which is fine.
            val folder = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            val file = java.io.File(folder, fileName).apply { writeBytes(png) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }
}

actual fun androidx.compose.ui.graphics.ImageBitmap.encodePng(): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
