package uz.sadora.server.media

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Instant
import kotlin.uuid.Uuid
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.now

/** The files one media item may have on disk. */
enum class MediaVariant(val extension: String) {
    /** The photo, or the video itself. */
    FULL("full"),

    /** A photo's thumbnail, or a video's poster frame. */
    THUMB("thumb"),
}

/** The disk the media lives on, for the system health page. */
data class MediaDisk(val totalBytes: Long, val freeBytes: Long, val mediaBytes: Long) {
    val usedFraction: Double get() = if (totalBytes <= 0) 0.0 else 1.0 - freeBytes.toDouble() / totalBytes
}

/**
 * The bytes of message photos and videos, sealed, under one directory.
 *
 * Every file is [SegmentedCipher] output: nothing here is readable without the key kept
 * beside the item's row, so a copy of the directory — a backup, a disk taken out of the
 * server — holds nothing anyone can open. Files are named by the item's id and fanned out
 * by its first two characters so no directory grows to hundreds of thousands of entries.
 *
 * An upload arrives in pieces of [CHUNK_BYTES], each sealed as it lands in a `.part`
 * file. How much has arrived is read off that file's length, so an upload a restart cut
 * in half carries on where it stopped. The store knows nothing of owners or messages:
 * which files are still wanted is the table's answer, asked by [sweep].
 */
class MediaStore(private val root: File, private val clock: () -> Instant = ::now) {

    private val locks = Array(64) { Any() }
    private fun lockFor(id: Uuid) = locks[(id.hashCode() and 0x7FFFFFFF) % locks.size]

    private fun dirOf(id: Uuid) = File(root, id.toString().take(2))
    private fun fileOf(id: Uuid, variant: MediaVariant) = File(dirOf(id), "$id.${variant.extension}")
    private fun partOf(id: Uuid) = File(dirOf(id), "$id$PART")

    /** The plaintext bytes of an upload that have arrived so far. */
    fun received(id: Uuid): Long = plainLength(partOf(id).length())

    /**
     * Writes one piece at [offset]. A piece sent twice — its answer was lost on the way
     * back — lands where it did the first time; one from further on than what has
     * arrived, one that does not start on a segment, or one short of a whole piece
     * anywhere but at the end is refused, because the file would have a hole in it.
     */
    fun append(id: Uuid, cipher: SegmentedCipher, offset: Long, bytes: ByteArray, declaredSize: Long): Long = synchronized(lockFor(id)) {
        val part = partOf(id)
        val received = plainLength(part.length())
        val end = offset + bytes.size
        val wholePieces = bytes.size % SegmentedCipher.SEGMENT_BYTES == 0
        if (bytes.isEmpty() || bytes.size > CHUNK_BYTES || offset < 0 || offset > received ||
            offset % SegmentedCipher.SEGMENT_BYTES != 0L || end > declaredSize || (!wholePieces && end != declaredSize)
        ) {
            throw ConflictException(OUT_OF_ORDER)
        }
        part.parentFile.mkdirs()
        RandomAccessFile(part, "rw").use { file ->
            val first = offset / SegmentedCipher.SEGMENT_BYTES
            file.seek(SegmentedCipher.sealedOffset(first))
            var at = 0
            var index = first
            while (at < bytes.size) {
                val length = minOf(SegmentedCipher.SEGMENT_BYTES, bytes.size - at)
                file.write(cipher.seal(index++, bytes, at, length))
                at += length
            }
            file.setLength(file.filePointer)
        }
        part.setLastModified(clock().toEpochMilliseconds())
        end
    }

    /** The upload as it is, for reading while it is checked; [plainSize] is all of it. */
    fun part(id: Uuid, cipher: SegmentedCipher, plainSize: Long): SealedFile {
        if (received(id) != plainSize) throw ConflictException(INCOMPLETE)
        return SealedFile(partOf(id), cipher, plainSize)
    }

    /**
     * Changes bytes of a finished upload in place: each segment that [ranges] touch is
     * opened, given [byteAt] for every offset in a range, and sealed again where it was.
     * Sizes never change, so nothing else in the file moves.
     */
    fun editPart(id: Uuid, cipher: SegmentedCipher, plainSize: Long, ranges: List<LongRange>, byteAt: (Long) -> Byte): Unit = synchronized(lockFor(id)) {
        if (ranges.isEmpty()) return
        if (received(id) != plainSize) throw ConflictException(INCOMPLETE)
        val segment = SegmentedCipher.SEGMENT_BYTES.toLong()
        val touched = ranges.filter { !it.isEmpty() }
            .flatMap { (it.first / segment)..(it.last / segment) }
            .toSortedSet()
        RandomAccessFile(partOf(id), "rw").use { file ->
            touched.forEach { index ->
                val plainLength = minOf(segment, plainSize - index * segment).toInt()
                val sealed = ByteArray(plainLength + SegmentedCipher.OVERHEAD)
                file.seek(SegmentedCipher.sealedOffset(index))
                file.readFully(sealed)
                val plain = cipher.open(index, sealed)
                val from = index * segment
                ranges.forEach { range ->
                    val first = maxOf(range.first, from)
                    val last = minOf(range.last, from + plainLength - 1)
                    for (offset in first..last) plain[(offset - from).toInt()] = byteAt(offset)
                }
                file.seek(SegmentedCipher.sealedOffset(index))
                file.write(cipher.seal(index, plain))
            }
        }
    }

    /** The upload becomes the item's file as it is: a video, already sealed. */
    fun promotePart(id: Uuid): Unit = synchronized(lockFor(id)) {
        val part = partOf(id)
        if (!part.isFile) throw NotFoundException(UPLOAD_NOT_FOUND)
        Files.move(part.toPath(), fileOf(id, MediaVariant.FULL).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        fileOf(id, MediaVariant.FULL).setLastModified(clock().toEpochMilliseconds())
    }

    fun dropPart(id: Uuid): Unit = synchronized(lockFor(id)) {
        partOf(id).delete()
    }

    /** Seals [bytes] as the item's [variant], replacing what was there in one step. */
    fun write(id: Uuid, variant: MediaVariant, cipher: SegmentedCipher, bytes: ByteArray): Unit = synchronized(lockFor(id)) {
        val target = fileOf(id, variant)
        target.parentFile.mkdirs()
        val next = File(target.parentFile, "${target.name}$NEXT")
        next.outputStream().use { SealedFile.seal(cipher, bytes.inputStream(), it) }
        next.setLastModified(clock().toEpochMilliseconds())
        Files.move(next.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun exists(id: Uuid, variant: MediaVariant): Boolean = fileOf(id, variant).isFile

    /** The plaintext bytes [range] of the item's [variant], [plainSize] bytes in all. */
    fun open(id: Uuid, variant: MediaVariant, cipher: SegmentedCipher, plainSize: Long, range: LongRange): InputStream {
        val file = fileOf(id, variant)
        if (!file.isFile) throw NotFoundException(NOT_FOUND)
        return SealedFile(file, cipher, plainSize).open(range)
    }

    /** Every file of the item, the unfinished upload included. */
    fun delete(id: Uuid): Unit = synchronized(lockFor(id)) {
        MediaVariant.entries.forEach { fileOf(id, it).delete() }
        partOf(id).delete()
    }

    /**
     * Deletes the files of every item [known] does not answer for, among those last
     * written before [olderThan] — an upload that has not reached the table yet is not
     * an orphan. Rows go with an account or a thread by the schema's cascades; this is
     * how their files follow. Answers how many items went.
     */
    suspend fun sweep(olderThan: Instant, known: suspend (Set<Uuid>) -> Set<Uuid>): Int {
        val cutoff = olderThan.toEpochMilliseconds()
        val byId = root.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file -> runCatching { Uuid.parse(file.name.substringBefore('.')) }.getOrNull()?.let { it to file } }
            .groupBy({ it.first }, { it.second })
        val candidates = byId.filterValues { files -> files.all { it.lastModified() < cutoff } }.keys
        var removed = 0
        candidates.chunked(SWEEP_BATCH).forEach { batch ->
            val wanted = known(batch.toSet())
            batch.filter { it !in wanted }.forEach { id ->
                synchronized(lockFor(id)) { byId[id].orEmpty().forEach { it.delete() } }
                removed++
            }
        }
        return removed
    }

    /** How full the disk under the store is, without walking it. */
    fun usedFraction(): Double {
        root.mkdirs()
        val total = root.totalSpace
        return if (total <= 0) 0.0 else 1.0 - root.usableSpace.toDouble() / total
    }

    fun disk(): MediaDisk {
        root.mkdirs()
        val used = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return MediaDisk(root.totalSpace, root.usableSpace, used)
    }

    private fun plainLength(sealedLength: Long): Long {
        if (sealedLength <= 0) return 0
        val whole = SegmentedCipher.SEGMENT_BYTES + SegmentedCipher.OVERHEAD
        val full = sealedLength / whole
        val rest = sealedLength % whole
        return full * SegmentedCipher.SEGMENT_BYTES + if (rest > SegmentedCipher.OVERHEAD) rest - SegmentedCipher.OVERHEAD else 0
    }

    companion object {
        /** One megabyte, sixteen segments: what nginx lets through when nobody configured it otherwise. */
        const val CHUNK_BYTES = 1024 * 1024
        private const val PART = ".part"
        private const val NEXT = ".next"
        private const val SWEEP_BATCH = 500
        const val OUT_OF_ORDER = "Yuklash tartibi buzildi — qaytadan boshlang"
        const val INCOMPLETE = "Fayl to'liq yuklanmadi — qaytadan urinib ko'ring"
        const val UPLOAD_NOT_FOUND = "Yuklash topilmadi — qaytadan boshlang"
        const val NOT_FOUND = "Fayl topilmadi"
    }
}
