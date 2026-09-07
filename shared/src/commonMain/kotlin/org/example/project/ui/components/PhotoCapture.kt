package org.example.project.ui.components

import androidx.compose.runtime.Composable

/** A photo the user chose or took, already encoded for the wire. */
data class CapturedPhoto(
    /** The bytes, base64-encoded, ready to be the body of a scan request. */
    val base64: String,
    val mimeType: String,
)

/**
 * The two ways a photo of a meal gets into the app.
 *
 * Both are handles rather than a screen: the camera and the gallery belong to the
 * platform, and the flow around them — the framing hint, the limit, what happens to the
 * estimate — belongs to the shared code that calls this.
 *
 * [available] is false where neither is wired up, so the flow can say so plainly rather
 * than offering a button that does nothing.
 */
interface PhotoCapture {
    val available: Boolean
    fun takePhoto()
    fun pickFromGallery()
}

/**
 * Remembers a capture bound to [onCaptured].
 *
 * The photo is resized and JPEG-encoded before it reaches the callback: the endpoint
 * caps what it accepts, and a modern phone's full-resolution frame is several times
 * that on its own.
 */
@Composable
expect fun rememberPhotoCapture(onCaptured: (CapturedPhoto) -> Unit): PhotoCapture
