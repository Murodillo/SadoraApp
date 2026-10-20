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
 * A box that carries notes rather than the video — where it was filmed, the phone, a
 * title — at [start] in the file, [end] exclusive.
 */
data class Mp4Box(val start: Long, val end: Long, val headerSize: Int) {
    /** What blanking it writes at [offset]: `free` over its type, zeros over its contents; null elsewhere. */
    fun blankedAt(offset: Long): Byte? = when {
        offset in (start + 4) until (start + 8) -> FREE[(offset - start - 4).toInt()]
        offset in (start + headerSize) until end -> 0
        else -> null
    }

    /** The bytes blanking changes: its type and its contents, never its size. */
    val edited: LongRange get() = (start + 4) until end

    private companion object {
        val FREE = "free".toByteArray(Charsets.ISO_8859_1)
    }
}

/**
 * What the server needs to know about a video without decoding it: how long it plays and
 * how large it is shown. Read from the ISO base media boxes every MP4 and MOV share —
 * `ftyp` first, then the `moov` index wherever it sits — with no ffmpeg on the server.
 *
 * The size is the one a player shows: a phone held upright stores its picture sideways
 * and says so in the track's matrix, and the bubble must be tall, not wide.
 *
 * [metadataBoxes] are the boxes a phone writes its notes into — `udta` and `meta` in the
 * index and in each track, and anything at the top level a player has no use for. A
 * video's location is in one of them; blanking them ([Mp4Box.blankedAt]) turns each into
 * a `free` box of the same size, so no offset in the file moves and it still plays.
 */
data class Mp4Info(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val metadataBoxes: List<Mp4Box> = emptyList(),
) {

    companion object {
        /** The video's facts, or null when this is not a playable MP4 or MOV with a picture. */
        fun read(source: ByteSource): Mp4Info? = runCatching { parse(source) }.getOrNull()

        /** Writes [box] blanked through [set]: every byte it changes, one at a time. */
        fun blank(box: Mp4Box, set: (Long, Byte) -> Unit) {
            for (offset in box.edited) set(offset, box.blankedAt(offset) ?: continue)
        }

        private const val MAX_INDEX_BYTES = 16 * 1024 * 1024
        private const val MAX_TOP_BOXES = 256

        /** What a player needs at the top level; every other top-level box is notes. */
        private val STRUCTURAL = setOf("ftyp", "moov", "mdat", "free", "skip", "wide", "pnot", "moof", "mfra", "sidx", "styp")

        /** Where notes live inside the index and inside each track. */
        private val NOTES = setOf("udta", "meta", "uuid")

        private fun parse(source: ByteSource): Mp4Info? {
            var offset = 0L
            var first = true
            var info: Mp4Info? = null
            val notes = mutableListOf<Mp4Box>()
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
                if (size < headerSize || offset + size > source.size) {
                    // A box running past the end: a file cut short, which is fine only if the index came first.
                    if (info != null) break else return null
                }
                if (first) {
                    if (type != "ftyp") return null
                    first = false
                }
                when {
                    type == "moov" && info == null -> {
                        val body = size - headerSize
                        if (body > MAX_INDEX_BYTES) return null
                        info = readMoov(ByteBuffer.wrap(source.read(offset + headerSize, body.toInt())), offset + headerSize, notes) ?: return null
                    }
                    type !in STRUCTURAL -> notes += Mp4Box(offset, offset + size, headerSize)
                }
                offset += size
            }
            return info?.copy(metadataBoxes = notes.sortedBy { it.start })
        }

        private fun readMoov(moov: ByteBuffer, base: Long, notes: MutableList<Mp4Box>): Mp4Info? {
            var durationMs: Long? = null
            var picture: Pair<Int, Int>? = null
            children(moov, base) { type, body, box ->
                when (type) {
                    "mvhd" -> durationMs = movieDuration(body)
                    "trak" -> {
                        val size = videoTrackSize(body, box.start + box.headerSize, notes)
                        if (picture == null) picture = size
                    }
                    in NOTES -> notes += box
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

        /** The shown size of a video track, or null for sound and anything else; its notes go to [notes]. */
        private fun videoTrackSize(trak: ByteBuffer, base: Long, notes: MutableList<Mp4Box>): Pair<Int, Int>? {
            var size: Pair<Int, Int>? = null
            var isVideo = false
            children(trak, base) { type, body, box ->
                when (type) {
                    "tkhd" -> size = trackSize(body)
                    "mdia" -> children(body, box.start + box.headerSize) { inner, innerBody, _ ->
                        if (inner == "hdlr") {
                            innerBody.position(innerBody.position() + 8)
                            isVideo = innerBody.fourCc() == "vide"
                        }
                    }
                    in NOTES -> notes += box
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

        /**
         * Calls [visit] with each child box of [parent] — its type, a buffer over its body,
         * and where it sits in the file, [base] being where [parent]'s body starts.
         */
        private fun children(parent: ByteBuffer, base: Long, visit: (String, ByteBuffer, Mp4Box) -> Unit) {
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
                visit(type, body, Mp4Box(base + start, base + start + size, headerSize))
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
