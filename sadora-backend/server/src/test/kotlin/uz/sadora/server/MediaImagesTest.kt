package uz.sadora.server

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.server.core.ValidationException
import uz.sadora.server.media.MediaImages

class MediaImagesTest {

    private fun jpeg(width: Int, height: Int, paint: (BufferedImage) -> Unit = {}): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color.GRAY; fillRect(0, 0, width, height); dispose() }
        paint(image)
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpeg", it) }.toByteArray()
    }

    /**
     * An APP1 Exif block, little-endian, with the orientation tag and a GPS block holding
     * a latitude reference — what a phone camera writes — spliced in after SOI.
     */
    private fun withExif(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put('I'.code.toByte()); put('I'.code.toByte()); putShort(42); putInt(8)
            // IFD0 at 8: two entries.
            putShort(2)
            putShort(0x0112); putShort(3); putInt(1); putShort(orientation.toShort()); putShort(0)
            putShort(0x8825.toShort()); putShort(4); putInt(1); putInt(38)
            putInt(0)
            // GPS IFD at 38: GPSLatitudeRef = "N".
            putShort(1)
            putShort(1); putShort(2); putInt(2); put('N'.code.toByte()); put(0); putShort(0)
            putInt(0)
        }.array()
        val payload = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun size(bytes: ByteArray): Pair<Int, Int> =
        ImageIO.read(ByteArrayInputStream(bytes)).let { it.width to it.height }

    private fun hasApp1(bytes: ByteArray): Boolean =
        (0 until bytes.size - 1).any { bytes[it] == 0xFF.toByte() && bytes[it + 1] == 0xE1.toByte() }

    @Test
    fun `a large photo is cut to 2048 and a 480 thumbnail`() {
        val processed = MediaImages.process(jpeg(3000, 1500))
        assertEquals(2048 to 1024, size(processed.full))
        assertEquals(2048 to 1024, processed.width to processed.height)
        assertEquals(480 to 240, size(processed.thumb))
    }

    @Test
    fun `a small photo keeps its size`() {
        val processed = MediaImages.process(jpeg(300, 400))
        assertEquals(300 to 400, size(processed.full))
        assertEquals(300 to 400, size(processed.thumb))
    }

    @Test
    fun `where it was taken and the camera's notes do not survive`() {
        val source = withExif(jpeg(400, 300), orientation = 1)
        assertTrue(hasApp1(source))
        val processed = MediaImages.process(source)
        assertFalse(hasApp1(processed.full), "no Exif in the photo")
        assertFalse(hasApp1(processed.thumb), "no Exif in the thumbnail")
    }

    @Test
    fun `a photo the camera stored sideways comes out upright`() {
        // Orientation 6: the stored picture is turned a quarter right to be shown.
        // Exif: row 0 is shown as the right-hand side, so the stored top-left corner — red
        // here — is shown at the top right.
        val stored = jpeg(400, 300) { image ->
            image.createGraphics().apply { color = Color.RED; fillRect(0, 0, 60, 60); dispose() }
        }
        val processed = MediaImages.process(withExif(stored, orientation = 6))
        assertEquals(300 to 400, size(processed.full))
        assertEquals(300 to 400, processed.width to processed.height)
        val shown = ImageIO.read(ByteArrayInputStream(processed.full))
        assertTrue(Color(shown.getRGB(300 - 20, 20)).red > 200, "top right is red")
        assertTrue(Color(shown.getRGB(20, 20)).red < 200, "top left is not")
    }

    @Test
    fun `bytes that are not a photo are refused`() {
        assertFailsWith<ValidationException> { MediaImages.process(ByteArray(500) { 3 }) }
        assertFailsWith<ValidationException> { MediaImages.process(ByteArray(0)) }
    }

    @Test
    fun `a picture that claims a giant size is refused before it is decoded`() {
        assertFailsWith<ValidationException> { MediaImages.process(pngHeader(60_000, 60_000)) }
    }

    /** A PNG that is only its signature and a header naming [width] by [height]. */
    private fun pngHeader(width: Int, height: Int): ByteArray {
        val ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(2).put(0).put(0).put(0).array()
        val type = "IHDR".toByteArray()
        val crc = CRC32().apply { update(type); update(ihdr) }.value.toInt()
        return byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) +
            ByteBuffer.allocate(4).putInt(13).array() + type + ihdr + ByteBuffer.allocate(4).putInt(crc).array()
    }
}
