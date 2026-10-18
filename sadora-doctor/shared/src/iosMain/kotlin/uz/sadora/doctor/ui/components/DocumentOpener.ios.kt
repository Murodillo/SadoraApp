package uz.sadora.doctor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

/**
 * Written to the temporary folder and offered through the share sheet, which previews a
 * PDF at its top and opens it in Files, Books or a messenger.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberOpenDocument(): (bytes: ByteArray, fileName: String, mimeType: String) -> Boolean = remember {
    { bytes, fileName, _ ->
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + cacheFileName(fileName))
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        if (root == null || bytes.isEmpty() || !data.writeToURL(url, atomically = true)) {
            false
        } else {
            val controller = UIActivityViewController(listOf(url), null)
            // On an iPad the sheet is a popover and needs somewhere to point.
            controller.popoverPresentationController?.sourceView = root.view
            (root.presentedViewController ?: root).presentViewController(controller, animated = true, completion = null)
            true
        }
    }
}
