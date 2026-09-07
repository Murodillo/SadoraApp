package org.example.project.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * `UIImagePickerController` for both the camera and the library.
 *
 * The delegate is held by the returned object rather than by the picker, because
 * UIKit's delegate reference is weak and a delegate that is only referenced by the
 * presentation would be collected before the user finishes choosing.
 *
 * Info.plist must carry `NSCameraUsageDescription` and
 * `NSPhotoLibraryUsageDescription`; without them iOS terminates the app on present.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoCapture(onCaptured: (CapturedPhoto) -> Unit): PhotoCapture {
    val callback = rememberUpdatedState(onCaptured)

    return remember {
        object : PhotoCapture {
            private val pickerDelegate = object : NSObject(),
                UIImagePickerControllerDelegateProtocol,
                UINavigationControllerDelegateProtocol {

                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>,
                ) {
                    val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                    picker.dismissViewControllerAnimated(true, null)
                    image?.encode()?.let { callback.value(it) }
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    picker.dismissViewControllerAnimated(true, null)
                }
            }

            override val available = true

            override fun takePhoto() =
                present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

            override fun pickFromGallery() =
                present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)

            private fun present(source: UIImagePickerControllerSourceType) {
                if (!UIImagePickerController.isSourceTypeAvailable(source)) return
                val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
                val picker = UIImagePickerController()
                picker.sourceType = source
                picker.delegate = pickerDelegate
                root.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

/** Scaled to fit [MaxEdge] and encoded as JPEG, matching what the endpoint accepts. */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.encode(): CapturedPhoto? {
    val width = size.useContents { width }
    val height = size.useContents { height }
    val longest = maxOf(width, height)
    val scale = if (longest <= 0.0) 1.0 else minOf(1.0, MaxEdge / longest)
    val targetWidth = width * scale
    val targetHeight = height * scale

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), false, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val resized = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    val data: NSData = UIImageJPEGRepresentation(resized ?: this, JpegQuality) ?: return null
    return CapturedPhoto(
        base64 = data.base64EncodedStringWithOptions(0u),
        mimeType = "image/jpeg",
    )
}

private const val MaxEdge = 1024.0
private const val JpegQuality = 0.82
