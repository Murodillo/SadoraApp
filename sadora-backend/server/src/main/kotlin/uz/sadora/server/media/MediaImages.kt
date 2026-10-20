package uz.sadora.server.media

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt
import uz.sadora.server.core.Photos
import uz.sadora.server.core.ValidationException

/** A photo as kept: the picture and its thumbnail, both freshly written JPEGs. */
class ProcessedImage(
    val full: ByteArray,
    val width: Int,
    val height: Int,
    val thumb: ByteArray,
)

/**
 * Every photo in a message is decoded and written again, never kept as it arrived.
 *
 * Re-encoding is the guarantee: the JPEG that comes out carries pixels and nothing else,
 * so where a photo was taken and with which phone cannot reach the other side whatever
 * the app did or did not strip. A camera that stored the picture sideways and said so
 * in its notes is turned upright first, because the note goes with everything else.
 *
 * The size claimed in a file's header is checked before its pixels are decoded: a small
 * file can claim a picture large enough to fill the heap.
 */
object MediaImages {
    const val FULL_PX = 2048
    const val THUMB_PX = 480
    private const val MAX_PIXELS = 40_000_000L
    const val UNREADABLE = "Rasmni o'qib bo'lmadi"

    fun process(bytes: ByteArray): ProcessedImage {
        val upright = decodeUpright(bytes)
        val full = scaledWithin(upright, FULL_PX)
        val thumb = scaledWithin(full, THUMB_PX)
        return ProcessedImage(Photos.jpeg(full), full.width, full.height, Photos.jpeg(thumb))
    }

    /** Only the thumbnail: a video's poster frame. */
    fun thumbnail(bytes: ByteArray): ByteArray = Photos.jpeg(scaledWithin(decodeUpright(bytes), THUMB_PX))

    private fun decodeUpright(bytes: ByteArray): BufferedImage {
        if (bytes.isEmpty()) throw ValidationException("image", UNREADABLE)
        val source = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: throw ValidationException("image", UNREADABLE)
        val decoded = source.use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull()
                ?: throw ValidationException("image", UNREADABLE)
            try {
                reader.input = stream
                val width = runCatching { reader.getWidth(0) }.getOrNull()
                val height = runCatching { reader.getHeight(0) }.getOrNull()
                if (width == null || height == null || width <= 0 || height <= 0) throw ValidationException("image", UNREADABLE)
                if (width.toLong() * height > MAX_PIXELS) throw ValidationException("image", "Rasm juda katta — kichikroq qilib yuboring")
                runCatching { reader.read(0) }.getOrNull() ?: throw ValidationException("image", UNREADABLE)
            } finally {
                reader.dispose()
            }
        }
        return oriented(decoded, exifOrientation(bytes))
    }

    /** [image] drawn as Exif [orientation] says it is shown, on white where it was transparent. */
    private fun oriented(image: BufferedImage, orientation: Int): BufferedImage {
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        val transform = when (orientation) {
            2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)
            3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)
            4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)
            5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
            6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)
            7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)
            8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)
            else -> AffineTransform()
        }
        val sideways = orientation in 5..8
        val out = BufferedImage(
            if (sideways) image.height else image.width,
            if (sideways) image.width else image.height,
            BufferedImage.TYPE_INT_RGB,
        )
        out.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, out.width, out.height)
            drawImage(image, transform, null)
            dispose()
        }
        return out
    }

    /**
     * [image] no larger than [limit] on its long side. Halved in steps first, then drawn
     * at the size: one bicubic jump from thousands of pixels to hundreds drops detail
     * unevenly and the thumbnail looks scratched.
     */
    private fun scaledWithin(image: BufferedImage, limit: Int): BufferedImage {
        val long = max(image.width, image.height)
        if (long <= limit) return image
        val scale = limit.toDouble() / long
        val targetW = (image.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (image.height * scale).roundToInt().coerceAtLeast(1)
        var current = image
        while (current.width / 2 >= targetW * 2 && current.height / 2 >= targetH * 2) {
            current = draw(current, current.width / 2, current.height / 2)
        }
        return draw(current, targetW, targetH)
    }

    private fun draw(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(image, 0, 0, width, height, null)
            dispose()
        }
        return out
    }

    /**
     * The Exif orientation of a JPEG, 1 when it has none. Reads only the APP1 block's
     * first directory; anything malformed is treated as upright rather than refused.
     */
    internal fun exifOrientation(bytes: ByteArray): Int = runCatching {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return 1
        var at = 2
        while (at + 4 <= bytes.size) {
            if (bytes[at] != 0xFF.toByte()) return 1
            val marker = bytes[at + 1].toInt() and 0xFF
            // Start of scan: the pixels begin and no more headers follow.
            if (marker == 0xDA || marker == 0xD9) return 1
            val length = ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
            if (marker == 0xE1 && length > 8 && String(bytes, at + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                return orientationInTiff(ByteBuffer.wrap(bytes, at + 10, length - 8).slice())
            }
            at += 2 + length
        }
        1
    }.getOrDefault(1)

    private fun orientationInTiff(tiff: ByteBuffer): Int {
        tiff.order(if (tiff.get(0) == 'I'.code.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val ifd = tiff.getInt(4)
        val entries = tiff.getShort(ifd).toInt() and 0xFFFF
        for (i in 0 until entries) {
            val entry = ifd + 2 + i * 12
            if ((tiff.getShort(entry).toInt() and 0xFFFF) == 0x0112) {
                return (tiff.getShort(entry + 8).toInt() and 0xFFFF).takeIf { it in 1..8 } ?: 1
            }
        }
        return 1
    }
}
