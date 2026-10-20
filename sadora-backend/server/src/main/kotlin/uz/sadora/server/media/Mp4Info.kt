package uz.sadora.server.media

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** Random access to bytes that may be sealed on disk: a plain file in tests, a [SealedFile] in use. */
interface ByteSource {
    val size: Long

    /** Up to [length] bytes from [offset]; fewer only at the end. */
    fun read(offset: Long, length: Int): ByteArray

    companion object {
        fun of(bytes: ByteArray): ByteSource = object : ByteSource {
            override val size = bytes.size.toLong()
            override fun read(offset: Long, length: Int): ByteArray {
                if (offset >= bytes.size) return ByteArray(0)
                return bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + length).toInt())
            }
        }

        fun of(file: File): ByteSource = object : ByteSource {
            override val size = file.length()
            override fun read(offset: Long, length: Int): ByteArray = RandomAccessFile(file, "r").use { raf ->
                if (offset >= raf.length()) return ByteArray(0)
                raf.seek(offset)
                val out = ByteArray(minOf(length.toLong(), raf.length() - offset).toInt())
                raf.readFully(out)
                out
            }
        }

        fun of(sealed: SealedFile, plainSize: Long): ByteSource = object : ByteSource {
            override val size = plainSize
            override fun read(offset: Long, length: Int): ByteArray {
                if (offset >= plainSize || length <= 0) return ByteArray(0)
                val last = minOf(plainSize, offset + length) - 1
                return sealed.open(offset..last).use { it.readBytes() }
            }
        }
    }
}

/**
 * What the server needs to know about a video without decoding it: how long it plays and
 * how large it is shown. Read from the ISO base media boxes every MP4 and MOV share —
 * `ftyp` first, then the `moov` index wherever it sits — with no ffmpeg on the server.
 *
 * The size is the one a player shows: a phone held upright stores its picture sideways
 * and says so in the track's matrix, and the bubble must be tall, not wide.
 */
data class Mp4Info(val durationMs: Long, val width: Int, val height: Int) {

    companion object {
        /** The video's facts, or null when this is not a playable MP4 or MOV with a picture. */
        fun read(source: ByteSource): Mp4Info? = runCatching { parse(source) }.getOrNull()

        private const val MAX_INDEX_BYTES = 16 * 1024 * 1024
        private const val MAX_TOP_BOXES = 256

        private fun parse(source: ByteSource): Mp4Info? {
            var offset = 0L
            var first = true
            var moov: ByteBuffer? = null
            var boxes = 0
            while (offset + 8 <= source.size && boxes++ < MAX_TOP_BOXES) {
                val header = ByteBuffer.wrap(source.read(offset, 16))
                if (header.remaining() < 8) return null
                var size = header.int.toLong() and 0xFFFFFFFFL
                val type = header.fourCc()
                var headerSize = 8
                when (size) {
                    1L -> {
                        if (header.remaining() < 8) return null
                        size = header.long
                        headerSize = 16
                    }
                    0L -> size = source.size - offset
                }
                if (size < headerSize) return null
                if (first) {
                    if (type != "ftyp") return null
                    first = false
                }
                if (type == "moov") {
                    val body = size - headerSize
                    if (body > MAX_INDEX_BYTES || offset + size > source.size) return null
                    moov = ByteBuffer.wrap(source.read(offset + headerSize, body.toInt()))
                    break
                }
                offset += size
            }
            val index = moov ?: return null
            return readMoov(index)
        }

        private fun readMoov(moov: ByteBuffer): Mp4Info? {
            var durationMs: Long? = null
            var picture: Pair<Int, Int>? = null
            children(moov) { type, body ->
                when (type) {
                    "mvhd" -> durationMs = movieDuration(body)
                    "trak" -> if (picture == null) picture = videoTrackSize(body)
                }
            }
            val duration = durationMs ?: return null
            val (width, height) = picture ?: return null
            if (width <= 0 || height <= 0 || duration <= 0) return null
            return Mp4Info(duration, width, height)
        }

        private fun movieDuration(body: ByteBuffer): Long? {
            val version = body.get().toInt()
            body.position(body.position() + 3)
            val (timescale, duration) = if (version == 1) {
                body.position(body.position() + 16)
                body.int.toLong() and 0xFFFFFFFFL to body.long
            } else {
                body.position(body.position() + 8)
                (body.int.toLong() and 0xFFFFFFFFL) to (body.int.toLong() and 0xFFFFFFFFL)
            }
            if (timescale <= 0 || duration <= 0) return null
            return duration * 1000 / timescale
        }

        /** The shown size of a video track, or null for sound and anything else. */
        private fun videoTrackSize(trak: ByteBuffer): Pair<Int, Int>? {
            var size: Pair<Int, Int>? = null
            var isVideo = false
            children(trak) { type, body ->
                when (type) {
                    "tkhd" -> size = trackSize(body)
                    "mdia" -> children(body) { inner, innerBody ->
                        if (inner == "hdlr") {
                            innerBody.position(innerBody.position() + 8)
                            isVideo = innerBody.fourCc() == "vide"
                        }
                    }
                }
            }
            return size?.takeIf { isVideo }
        }

        private fun trackSize(body: ByteBuffer): Pair<Int, Int> {
            val version = body.get().toInt()
            body.position(body.position() + 3)
            // Times, track id, reserved, duration; then reserved, layer, group, volume, reserved.
            body.position(body.position() + if (version == 1) 32 else 20)
            body.position(body.position() + 16)
            val matrix = IntArray(9) { body.int }
            val width = body.int ushr 16
            val height = body.int ushr 16
            // a = matrix[0], b = matrix[1]: a quarter turn puts the picture's width on b.
            val quarterTurn = matrix[0] == 0 && matrix[1] != 0
            return if (quarterTurn) height to width else width to height
        }

        /** Calls [visit] with each child box of [parent] and a buffer over its body. */
        private fun children(parent: ByteBuffer, visit: (String, ByteBuffer) -> Unit) {
            val buffer = parent.slice()
            while (buffer.remaining() >= 8) {
                val start = buffer.position()
                var size = buffer.int.toLong() and 0xFFFFFFFFL
                val type = buffer.fourCc()
                var headerSize = 8
                if (size == 1L) {
                    if (buffer.remaining() < 8) return
                    size = buffer.long
                    headerSize = 16
                } else if (size == 0L) {
                    size = (buffer.limit() - start).toLong()
                }
                if (size < headerSize || start + size > buffer.limit()) return
                val body = buffer.duplicate().apply {
                    position(start + headerSize)
                    limit((start + size).toInt())
                }.slice()
                visit(type, body)
                buffer.position((start + size).toInt())
            }
        }

        private fun ByteBuffer.fourCc(): String {
            val bytes = ByteArray(4)
            get(bytes)
            return String(bytes, Charsets.ISO_8859_1)
        }
    }
}
