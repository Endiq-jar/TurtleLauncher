/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.filemanager.logic.compress

import com.endiq.turtlelauncher.filemanager.os.FmLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tukaani.xz.ArrayCache
import org.tukaani.xz.FinishableWrapperOutputStream
import org.tukaani.xz.LZMA2Options
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.BitSet
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "SolidSevenZ"

/**
 * Writes password-protected 7z archives with a single-folder (solid) layout, so reading needs only one AES key derivation
 * Key derivation and container layout are compatible with the commons-compress reader
 */
object SolidSevenZWriter {

    private const val SIGNATURE_HEADER_SIZE = 32
    private const val NUM_CYCLES_POWER = 19
    private const val BUFFER_SIZE = 64 * 1024

    // NID of the 7z NextHeader (matches commons-compress NIDs)
    private const val N_END = 0x00
    private const val N_HEADER = 0x01
    private const val N_MAIN_STREAMS_INFO = 0x04
    private const val N_FILES_INFO = 0x05
    private const val N_PACK_INFO = 0x06
    private const val N_UNPACK_INFO = 0x07
    private const val N_SUB_STREAMS_INFO = 0x08
    private const val N_SIZE = 0x09
    private const val N_CRC = 0x0A
    private const val N_FOLDER = 0x0B
    private const val N_CODERS_UNPACK_SIZE = 0x0C
    private const val N_NUM_UNPACK_STREAM = 0x0D
    private const val N_EMPTY_STREAM = 0x0E
    private const val N_EMPTY_FILE = 0x0F
    private const val N_NAME = 0x11

    private val SEVEN_Z_SIGNATURE = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

    /**
     * Writes all entries into a single folder, producing a password-protected solid 7z archive.
     * @param sources the entries to compress
     * @param output the output archive path (already including the .7z suffix)
     * @param options compression parameters ([CompressOptions.password] must be non-empty)
     * @param total total file count (progress denominator)
     * @param bytesTotal total source bytes (byte-progress denominator)
     * @param onProgress progress callback (completed, total, currentName), per file
     * @param checkCancel cancellation check; returning true cancels the operation
     * @param onBytes byte progress callback (processed bytes, total bytes)
     */
    suspend fun write(
        sources: List<Path>,
        output: Path,
        options: CompressOptions,
        total: Int,
        bytesTotal: Long,
        onProgress: (completed: Int, total: Int, currentName: String?) -> Unit,
        checkCancel: () -> Boolean,
        onBytes: (bytesDone: Long, bytesTotal: Long) -> Unit = { _, _ -> }
    ): CompressSummary = withContext(Dispatchers.IO) {
        val password = options.password ?: throw IllegalArgumentException("SolidSevenZ requires a password")
        require(sources.isNotEmpty()) { "No sources to compress" }

        val dictSize = dictionarySize(options.level)
        val dictPropsByte = lzma2DictPropsByte(dictSize)
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val aesKey = deriveKey(password, NUM_CYCLES_POWER, EMPTY_SALT)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))

        val entries = mutableListOf<EntryInfo>()
        val folderCrc = CRC32()
        var totalUncompressed = 0L
        val state = ProgressState()

        val openOptions = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        Files.newByteChannel(output, *openOptions).use { channel ->
            channel.position(SIGNATURE_HEADER_SIZE.toLong())
            // Prevents CipherOutputStream.close() from closing the channel too (the underlying stream close is replaced with a no-op)
            val channelOut = NoCloseOutputStream(Channels.newOutputStream(channel))
            val packedCounter = CountingCrcOutputStream(channelOut)
            val aesOut = AesCbcZeroPadOutputStream(packedCounter, cipher)
            val lzma2Counter = CountingOutputStream(aesOut)
            // LZMA2OutputStream is package-private and must be created via the LZMA2Options factory
            val lzma2Options = LZMA2Options().apply { setDictSize(dictSize) }
            val lzma2 = lzma2Options.getOutputStream(
                FinishableWrapperOutputStream(lzma2Counter),
                ArrayCache.getDefaultCache()
            )

            for (source in sources) {
                if (checkCancel()) throw CancellationException()
                val name = source.fileName?.toString() ?: "entry"
                totalUncompressed += addSource(
                    lzma2 = lzma2,
                    source = source,
                    entryRoot = name,
                    entries = entries,
                    folderCrc = folderCrc,
                    state = state,
                    total = total,
                    bytesTotal = bytesTotal,
                    onProgress = onProgress,
                    onBytes = onBytes,
                    checkCancel = checkCancel
                )
            }

            lzma2.finish()
            aesOut.finish()

            val packedSize = packedCounter.count
            val packedCrc = packedCounter.crcValue
            val lzma2PackedLen = lzma2Counter.count

            // Write the NextHeader followed by the 32-byte signature header
            val header = buildHeader(
                entries = entries,
                packedSize = packedSize,
                packedCrc = packedCrc,
                lzma2PackedLen = lzma2PackedLen,
                totalUncompressed = totalUncompressed,
                folderCrc = folderCrc.value.toInt(),
                dictPropsByte = dictPropsByte,
                iv = iv
            )
            channel.position(SIGNATURE_HEADER_SIZE.toLong() + packedSize)
            writeFully(channel, header)

            val signature = buildSignatureHeader(
                nextHeaderOffset = packedSize,
                nextHeaderSize = header.size.toLong(),
                nextHeaderCrc = crc32Of(header)
            )
            channel.position(0)
            writeFully(channel, signature)
        }
        CompressSummary(output, total)
    }


    private class ProgressState {
        var completed = 0
        var bytesDone = 0L
    }

    private class EntryInfo(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val hasStream: Boolean,
        val emptyFile: Boolean
    )

    private fun addSource(
        lzma2: OutputStream,
        source: Path,
        entryRoot: String,
        entries: MutableList<EntryInfo>,
        folderCrc: CRC32,
        state: ProgressState,
        total: Int,
        bytesTotal: Long,
        onProgress: (Int, Int, String?) -> Unit,
        onBytes: (Long, Long) -> Unit,
        checkCancel: () -> Boolean
    ): Long {
        val attrs = Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attrs.isSymbolicLink) {
            FmLog.info(TAG, "Skip symlink during compress: $source")
            return 0L
        }
        if (attrs.isDirectory) {
            entries += EntryInfo(name = "$entryRoot/", isDirectory = true, size = 0, hasStream = false, emptyFile = false)
            var bytes = 0L
            for (child in sortedChildren(source)) {
                if (checkCancel()) throw CancellationException()
                val childName = child.fileName?.toString() ?: continue
                bytes += addSource(
                    lzma2, child, "$entryRoot/$childName", entries, folderCrc,
                    state, total, bytesTotal, onProgress, onBytes, checkCancel
                )
            }
            return bytes
        }

        var size = 0L
        Files.newInputStream(source).use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                if (checkCancel()) throw CancellationException()
                val n = input.read(buf)
                if (n < 0) break
                lzma2.write(buf, 0, n)
                folderCrc.update(buf, 0, n)
                size += n
                state.bytesDone += n
                onBytes(state.bytesDone, bytesTotal)
            }
        }
        entries += EntryInfo(
            name = entryRoot,
            isDirectory = false,
            size = size,
            hasStream = size > 0L,
            emptyFile = size == 0L
        )
        state.completed++
        onProgress(state.completed, total, entryRoot)
        return size
    }

    private fun sortedChildren(dir: Path): List<Path> {
        return runCatching { Files.newDirectoryStream(dir).use { it.toList() } }
            .getOrElse { emptyList() }
            .sortedBy { it.fileName?.toString() ?: it.toString() }
    }

    private fun buildHeader(
        entries: List<EntryInfo>,
        packedSize: Long,
        packedCrc: Int,
        lzma2PackedLen: Long,
        totalUncompressed: Long,
        folderCrc: Int,
        dictPropsByte: Int,
        iv: ByteArray
    ): ByteArray {
        val nonEmpty = entries.filter { it.hasStream }
        val numFiles = entries.size

        val bos = ByteArrayOutputStream()
        val out = DataOutputStream(bos)

        out.write(N_HEADER)
        out.write(N_MAIN_STREAMS_INFO)

        if (nonEmpty.isNotEmpty()) {
            // ---- kPackInfo ----
            out.write(N_PACK_INFO)
            writeUint64(out, 0) // packPos
            writeUint64(out, 1) // numPackStreams = 1
            out.write(N_SIZE)
            writeUint64(out, packedSize)
            out.write(N_CRC)
            out.write(1)   // allDefined
            writeIntLE(out, packedCrc)
            out.write(N_END)

            // ---- kUnpackInfo ----
            out.write(N_UNPACK_INFO)
            out.write(N_FOLDER)
            writeUint64(out, 1)  // numFolders = 1
            out.write(0)            // external
            writeFolder(out, dictPropsByte, iv)       // coders: [AES256SHA256, LZMA2] + bindPair
            out.write(N_CODERS_UNPACK_SIZE)
            writeUint64(out, lzma2PackedLen)    // coder0 (AES) output = LZMA2 compressed stream length
            writeUint64(out, totalUncompressed) // coder1 (LZMA2) output = total uncompressed length
            out.write(N_CRC)
            out.write(1)            // allDefined
            writeIntLE(out, folderCrc)
            out.write(N_END)

            // ---- kSubStreamsInfo ----
            out.write(N_SUB_STREAMS_INFO)
            out.write(N_NUM_UNPACK_STREAM)
            writeUint64(out, nonEmpty.size.toLong())
            out.write(N_SIZE)
            // Only the first N-1 unpack sizes are written; the reader derives the last one (folder.getUnpackSize() - sum)
            for (i in 0 until nonEmpty.size - 1) {
                writeUint64(out, nonEmpty[i].size)
            }
            out.write(N_END)
        }

        out.write(N_END) // end of kMainStreamsInfo

        // ---- kFilesInfo ----
        out.write(N_FILES_INFO)
        writeUint64(out, numFiles.toLong())

        if (entries.any { !it.hasStream }) {
            val emptyStreamIndices = entries.mapIndexedNotNull { i, e -> if (!e.hasStream) i else null }
            // kEmptyStream: marks empty streams (directories / empty files) across all entries
            val emptyStreamBits = BitSet()
            emptyStreamIndices.forEach { emptyStreamBits.set(it) }
            writeBitsProperty(out, N_EMPTY_STREAM, emptyStreamBits, numFiles)

            // kEmptyFile: marks "empty file" among the empty-stream entries (as opposed to directories)
            if (emptyStreamIndices.any { entries[it].emptyFile }) {
                val emptyFileBits = BitSet()
                emptyStreamIndices.forEachIndexed { j, i -> if (entries[i].emptyFile) emptyFileBits.set(j) }
                writeBitsProperty(out, N_EMPTY_FILE, emptyFileBits, emptyStreamIndices.size)
            }
        }

        // kName: all entry names, UTF-16LE, terminated by 0x0000
        out.write(N_NAME)
        val nameBytes = ByteArrayOutputStream()
        for (e in entries) {
            nameBytes.write(e.name.toByteArray(Charsets.UTF_16LE))
            nameBytes.write(0)
            nameBytes.write(0)
        }
        val names = nameBytes.toByteArray()
        writeUint64(out, 1L + names.size)   // includes the external byte
        out.write(0)                        // external = 0
        out.write(names)

        out.write(N_END) // end of kFilesInfo
        out.write(N_END) // end of kHeader

        out.flush()
        return bos.toByteArray()
    }

    private fun writeFolder(out: DataOutputStream, dictPropsByte: Int, iv: ByteArray) {
        writeUint64(out, 2) // numCoders

        // coder0: AES256SHA256（methodId = 0x06F10701）
        out.write(0x24)     // idSize=4 | hasAttributes=0x20
        out.write(0x06)
        out.write(0xF1)
        out.write(0x07)
        out.write(0x01)
        writeUint64(out, (2 + iv.size).toLong()) // properties length
        out.write(0x53)     // numCyclesPower=19 | iv present (0x40)
        out.write(0x0F)     // saltSize=0 | ivSize-1=15
        out.write(iv)

        // coder1: LZMA2（methodId = 0x21）
        out.write(0x21)     // idSize=1 | hasAttributes=0x20
        out.write(0x21)
        writeUint64(out, 1) // properties length
        out.write(dictPropsByte)

        // bindPair (inIndex, outIndex) = (1, 0): AES output -> LZMA2 input
        writeUint64(out, 1)
        writeUint64(out, 0)
    }

    private fun writeBitsProperty(out: DataOutputStream, nid: Int, bits: BitSet, length: Int) {
        out.write(nid)
        val packed = packBits(bits, length)
        writeUint64(out, packed.size.toLong())
        out.write(packed)
    }

    private fun packBits(bits: BitSet, length: Int): ByteArray {
        val bytes = ByteArray((length + 7) / 8)
        for (i in 0 until length) {
            if (bits.get(i)) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (0x80 shr (i % 8))).toByte()
            }
        }
        return bytes
    }

    private fun buildSignatureHeader(nextHeaderOffset: Long, nextHeaderSize: Long, nextHeaderCrc: Int): ByteArray {
        val b = ByteBuffer.allocate(SIGNATURE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.put(SEVEN_Z_SIGNATURE)
        b.put(0).put(2)                 // version (major=0, minor=2)
        b.putInt(0)                     // start header CRC placeholder
        b.putLong(nextHeaderOffset)
        b.putLong(nextHeaderSize)
        b.putInt(nextHeaderCrc)
        val crc = CRC32()
        crc.update(b.array(), 12, 20)   // CRC over the 20-byte start header
        b.putInt(8, crc.value.toInt())
        return b.array()
    }



    private fun deriveKey(password: String, numCyclesPower: Int, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val extra = ByteArray(8)
        val passwordBytes = password.toByteArray(Charsets.UTF_16LE)
        val iterations = 1L shl numCyclesPower
        for (j in 0 until iterations) {
            digest.update(salt)
            digest.update(passwordBytes)
            digest.update(extra)
            for (k in extra.indices) {
                extra[k] = (extra[k] + 1).toByte()
                if (extra[k] != 0.toByte()) break
            }
        }
        return digest.digest()
    }

    private fun lzma2DictPropsByte(dictSize: Int): Int {
        val lead = Integer.numberOfLeadingZeros(dictSize)
        val secondBit = (dictSize ushr (30 - lead)) - 2
        return (19 - lead) * 2 + secondBit
    }

    private fun dictionarySize(level: Int?): Int {
        val l = level?.coerceIn(1, 9) ?: 5
        return 1 shl (18 + l.coerceAtMost(7))
    }

    private fun crc32Of(bytes: ByteArray): Int {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value.toInt()
    }

    private fun writeFully(channel: SeekableByteChannel, bytes: ByteArray) {
        val buf = ByteBuffer.wrap(bytes)
        while (buf.hasRemaining()) channel.write(buf)
    }

    private fun writeUint64(out: DataOutputStream, value: Long) {
        var v = value
        var firstByte = 0
        var mask = 0x80
        var i = 0
        while (i < 8) {
            if (v < 1L shl 7 * (i + 1)) {
                firstByte = (firstByte.toLong() or (v ushr 8 * i)).toInt()
                break
            }
            firstByte = (firstByte.toLong() or mask.toLong()).toInt()
            mask = mask ushr 1
            i++
        }
        out.write(firstByte)
        while (i > 0) {
            out.write((v and 0xFF).toInt())
            v = v ushr 8
            i--
        }
    }

    private fun writeIntLE(out: DataOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private val EMPTY_SALT = ByteArray(0)



    private class NoCloseOutputStream(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = out.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun flush() = out.flush()
        override fun close() = out.flush()
    }

    private open class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count: Long = 0L
            protected set
        override fun write(b: Int) {
            out.write(b)
            count++
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    private class CountingCrcOutputStream(out: OutputStream) : CountingOutputStream(out) {
        private val crc = CRC32()
        val crcValue: Int get() = crc.value.toInt()
        override fun write(b: Int) {
            super.write(b)
            crc.update(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            super.write(b, off, len)
            crc.update(b, off, len)
        }
    }

    private class AesCbcZeroPadOutputStream(
        private val out: OutputStream,
        cipher: Cipher
    ) : OutputStream() {
        private val cipherOut = CipherOutputStream(out, cipher)
        private val blockSize = cipher.blockSize
        private val buffer = ByteArray(blockSize)
        private var count = 0

        private fun flushBuffer() {
            cipherOut.write(buffer)
            count = 0
            Arrays.fill(buffer, 0)
        }

        override fun write(b: Int) {
            buffer[count++] = (b and 0xFF).toByte()
            if (count == blockSize) flushBuffer()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len == 0) return
            var gap = if (len + count > blockSize) blockSize - count else len
            System.arraycopy(b, off, buffer, count, gap)
            count += gap
            if (count == blockSize) {
                flushBuffer()
                if (len - gap >= blockSize) {
                    val multi = (len - gap) / blockSize * blockSize
                    cipherOut.write(b, off + gap, multi)
                    gap += multi
                }
                System.arraycopy(b, off + gap, buffer, 0, len - gap)
                count = len - gap
            }
        }

        fun finish() {
            if (count > 0) cipherOut.write(buffer)
            cipherOut.close()
        }
    }
}
