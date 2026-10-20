package uz.sadora.server.media

import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM over a file in fixed [SEGMENT_BYTES] pieces, so any byte range of a video
 * can be read — a player seeking to the middle — without decrypting what comes before it.
 *
 * Each segment is sealed on its own: a fresh 12-byte nonce, then the ciphertext, then the
 * 16-byte tag. The segment's index is the associated data, so segments cannot be swapped
 * inside a file; every file has its own key, so they cannot be swapped between files; and
 * the plaintext size is kept beside the key, so a file cut short fails at the missing
 * segment instead of reading as a shorter video.
 */
class SegmentedCipher(dataKey: ByteArray) {

    init {
        require(dataKey.size == KEY_BYTES) { "A media key must be $KEY_BYTES bytes" }
    }

    private val key = SecretKeySpec(dataKey, "AES")
    private val random = SecureRandom()

    /** One segment, sealed: nonce, ciphertext, tag. */
    fun seal(index: Long, plain: ByteArray, from: Int = 0, length: Int = plain.size - from): ByteArray {
        require(length in 1..SEGMENT_BYTES) { "A segment holds 1..$SEGMENT_BYTES bytes" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(index))
        val out = ByteArray(NONCE_BYTES + length + TAG_BYTES)
        nonce.copyInto(out)
        cipher.doFinal(plain, from, length, out, NONCE_BYTES)
        return out
    }

    /** The plaintext of a sealed segment; throws when it is not the segment [index] under this key. */
    fun open(index: Long, sealed: ByteArray, length: Int = sealed.size): ByteArray {
        require(length > OVERHEAD) { "Sealed segment too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
        cipher.updateAAD(aad(index))
        return cipher.doFinal(sealed, NONCE_BYTES, length - NONCE_BYTES)
    }

    private fun aad(index: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(index).array()

    companion object {
        const val KEY_BYTES = 32
        const val SEGMENT_BYTES = 64 * 1024
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private const val TAG_BITS = TAG_BYTES * 8
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** What sealing adds to each segment. */
        const val OVERHEAD = NONCE_BYTES + TAG_BYTES

        /** The bytes on disk for [plainSize] bytes of content. */
        fun sealedSize(plainSize: Long): Long {
            if (plainSize <= 0) return 0
            val segments = (plainSize + SEGMENT_BYTES - 1) / SEGMENT_BYTES
            return plainSize + segments * OVERHEAD
        }

        /** Where segment [index] starts on disk. */
        fun sealedOffset(index: Long): Long = index * (SEGMENT_BYTES + OVERHEAD)

        fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
    }
}

/** A file sealed by [SegmentedCipher], read by plaintext range. */
class SealedFile(private val file: File, private val cipher: SegmentedCipher, private val plainSize: Long) {

    /**
     * The plaintext bytes [range], decrypted a segment at a time as they are read. A
     * segment that fails to open — another key, a changed byte, a file cut short — fails
     * the read rather than handing back what it was.
     */
    fun open(range: LongRange): InputStream {
        require(range.first >= 0 && range.last < plainSize && !range.isEmpty()) { "Range $range outside 0 until $plainSize" }
        return RangeStream(range)
    }

    private inner class RangeStream(range: LongRange) : InputStream() {
        private val raf = RandomAccessFile(file, "r")
        private var position = range.first
        private val end = range.last + 1
        private var segmentIndex = -1L
        private var segment: ByteArray = ByteArray(0)
        private val buffer = ByteArray(SegmentedCipher.SEGMENT_BYTES + SegmentedCipher.OVERHEAD)

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (position >= end) return -1
            val index = position / SegmentedCipher.SEGMENT_BYTES
            if (index != segmentIndex) load(index)
            val inSegment = (position - index * SegmentedCipher.SEGMENT_BYTES).toInt()
            val count = minOf(len.toLong(), (segment.size - inSegment).toLong(), end - position).toInt()
            segment.copyInto(b, off, inSegment, inSegment + count)
            position += count
            return count
        }

        private fun load(index: Long) {
            val plainLength = minOf(SegmentedCipher.SEGMENT_BYTES.toLong(), plainSize - index * SegmentedCipher.SEGMENT_BYTES).toInt()
            val sealedLength = plainLength + SegmentedCipher.OVERHEAD
            raf.seek(SegmentedCipher.sealedOffset(index))
            var read = 0
            while (read < sealedLength) {
                val n = raf.read(buffer, read, sealedLength - read)
                if (n < 0) throw EOFException("Sealed file ends inside segment $index")
                read += n
            }
            segment = cipher.open(index, buffer, sealedLength)
            segmentIndex = index
        }

        override fun close() = raf.close()
    }

    companion object {
        /** Seals all of [input] into [output], a segment at a time. Answers the plaintext size. */
        fun seal(cipher: SegmentedCipher, input: InputStream, output: OutputStream): Long {
            val piece = ByteArray(SegmentedCipher.SEGMENT_BYTES)
            var index = 0L
            var total = 0L
            while (true) {
                val n = input.readNBytes(piece, 0, piece.size)
                if (n <= 0) break
                output.write(cipher.seal(index++, piece, 0, n))
                total += n
                if (n < piece.size) break
            }
            return total
        }
    }
}
