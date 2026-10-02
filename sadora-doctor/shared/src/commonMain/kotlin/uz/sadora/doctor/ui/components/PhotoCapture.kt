package uz.sadora.doctor.ui.components

import androidx.compose.runtime.Composable

/** A photo she chose, already encoded for the wire. */
data class CapturedPhoto(
    /** The bytes, base64-encoded, ready to be a document in her application. */
    val base64: String,
    val mimeType: String,
)

/**
 * The gallery, for the photos of her diploma and licence and for her profile photo; the
 * camera too, for the profile photo. Ported from the client app, where it also feeds the
 * food scanner.
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

    /** A camera to take a new photo with — false on the simulator and on a phone without one. */
    val cameraAvailable: Boolean get() = false

    /** The system camera; the photo arrives upright through the same callback. */
    fun takePhoto() {}
}

/**
 * Remembers a capture bound to [onCaptured].
 *
 * Either source hands over an upright image: the EXIF turn a camera writes is applied
 * before encoding, because the server does not read it.
 *
 * The photo is resized and JPEG-encoded before it reaches the callback: the endpoint
 * caps what it accepts, and a modern phone's full-resolution frame is several times
 * that on its own.
 */
@Composable
expect fun rememberPhotoCapture(onCaptured: (CapturedPhoto) -> Unit): PhotoCapture
