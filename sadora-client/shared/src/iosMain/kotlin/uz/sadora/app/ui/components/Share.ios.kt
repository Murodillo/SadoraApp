package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareAction(): (String) -> Unit = remember {
    { text ->
        val controller = UIActivityViewController(listOf(text), null)
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(controller, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@Composable
actual fun rememberImageShareAction(): (png: ByteArray, fileName: String) -> Unit = remember {
    { png, _ ->
        val image = png.usePinned { pinned ->
            platform.UIKit.UIImage(data = platform.Foundation.NSData.create(bytes = pinned.addressOf(0), length = png.size.toULong()))
        }
        val controller = UIActivityViewController(listOf(image), null)
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(controller, animated = true, completion = null)
    }
}

actual fun androidx.compose.ui.graphics.ImageBitmap.encodePng(): ByteArray =
    org.jetbrains.skia.Image.makeFromBitmap(asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
