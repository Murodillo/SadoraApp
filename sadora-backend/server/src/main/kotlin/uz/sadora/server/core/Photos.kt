package uz.sadora.server.core

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlin.uuid.Uuid
import uz.sadora.contract.PhotoUpload

/** A profile photo as stored: a square JPEG. */
class ProfilePhoto(val bytes: ByteArray, val sizePx: Int)

/**
 * Turns what a phone sends into what is stored: the picture is read from its own bytes
 * (a file that is not a JPEG or PNG is refused), cropped to its centre square, scaled
 * down to [MAX_PX] and written again as a JPEG. Re-encoding is the point — whatever
 * EXIF the camera wrote, a location among it, does not survive. The apps turn the
 * picture upright before sending, as they do for a chat photo.
 */
object Photos {
    const val MAX_PX = 512
    const val MAX_UPLOAD_BYTES = 8_000_000
    private val MIME = setOf("image/jpeg", "image/png")

    fun process(upload: PhotoUpload): ProfilePhoto {
        if (upload.mimeType !in MIME) throw ValidationException("image", "Faqat JPEG yoki PNG rasm")
        val text = upload.imageBase64.trim()
        if (text.isEmpty()) throw ValidationException("image", "Rasm bo'sh")
        if (text.length > MAX_UPLOAD_BYTES / 3 * 4 + 4) throw ValidationException("image", "Rasm juda katta — kichikroq qilib yuboring")
        val bytes = runCatching { Base64.decode(text) }.getOrElse { throw ValidationException("image", "Rasmni o'qib bo'lmadi") }
        val source = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: throw ValidationException("image", "Rasmni o'qib bo'lmadi")
        if (source.width < MIN_PX || source.height < MIN_PX) throw ValidationException("image", "Rasm juda kichik")

        val side = minOf(source.width, source.height)
        val target = minOf(side, MAX_PX)
        val square = BufferedImage(target, target, BufferedImage.TYPE_INT_RGB)
        square.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            // White under a transparent PNG, not the black a bare RGB canvas starts as.
            color = java.awt.Color.WHITE
            fillRect(0, 0, target, target)
            val x = (source.width - side) / 2
            val y = (source.height - side) / 2
            drawImage(source, 0, 0, target, target, x, y, x + side, y + side, null)
            dispose()
        }
        return ProfilePhoto(jpeg(square), target)
    }

    private fun jpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.85f
            }
            writer.write(null, IIOImage(image, null, null), params)
            writer.dispose()
        }
        return out.toByteArray()
    }

    private const val MIN_PX = 64

    // ---------------------------------------------------------------- where they are served

    /** Versioned by the upload time: a new photo is a new URL, so no cache holds the old one. */
    private fun v(at: Instant) = "?v=${at.toEpochMilliseconds()}"

    fun ownUrl(at: Instant) = "/v1/me/photo${v(at)}"
    fun doctorUrl(doctorId: Uuid, at: Instant) = "/v1/doctors/$doctorId/photo${v(at)}"
    fun conversationUrl(conversationId: Uuid, at: Instant) = "/v1/community/conversations/$conversationId/photo${v(at)}"
    /**
     * Her photo as her doctor reaches it, through the consultation. [ownUrl] is the path
     * stored on her account; its version carries over, so a new photo is a new URL here too.
     */
    fun conversationUrlFor(conversationId: Uuid, ownUrl: String?): String? =
        ownUrl?.substringAfter('?', "")?.takeIf { it.isNotEmpty() }
            ?.let { "/v1/community/conversations/$conversationId/photo?$it" }

    fun adminDoctorUrl(doctorId: Uuid, at: Instant) = "/v1/admin/doctors/$doctorId/photo${v(at)}"
}
