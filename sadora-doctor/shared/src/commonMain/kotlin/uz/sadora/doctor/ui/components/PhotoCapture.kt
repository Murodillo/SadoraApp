package uz.sadora.doctor.ui.components

import androidx.compose.runtime.Composable

/** A photo she chose, already encoded for the wire. */
data class CapturedPhoto(
    /** The bytes, base64-encoded, ready to be a document in her application. */
    val base64: String,
    val mimeType: String,
)

/**
 * The gallery, for the photos of her diploma and licence. Ported from the client app,
 * where it also feeds the food scanner.
 *
 * A handle rather than a screen: the picker belongs to the platform, and the flow around
 * it — how many pages, which kind each one is — belongs to the shared code that calls this.
 *
 * [available] is false where it is not wired up, so the flow can say so plainly rather
 * than offering a button that does nothing.
 */
interface PhotoCapture {
    val available: Boolean
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
