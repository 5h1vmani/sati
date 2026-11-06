package com.padakanti.sati.log

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The three files of an export, as text. A file that is not in the ZIP is null. */
data class ZipContents(val practices: String?, val entries: String?, val sadhanas: String?)

sealed interface ZipRead {
    data class Ok(val contents: ZipContents) : ZipRead
    data object TooLarge : ZipRead
    data object Unreadable : ZipRead
}

/** Reads and writes the export ZIP with java.util.zip. */
object LogZip {
    const val PRACTICES = "practices.csv"
    const val ENTRIES = "entries.csv"
    const val SADHANAS = "sadhanas.json"

    /** An import file may not be larger than this, as stored, and may not unpack to more than this in all. */
    const val MAX_BYTES = 16 * 1024 * 1024

    /** An export has three files. A ZIP with many more is not one. */
    const val MAX_ENTRIES = 10

    /** Writes the three files to [out]. The caller closes [out]. */
    fun write(out: OutputStream, practicesCsv: String, entriesCsv: String, sadhanasJson: String) {
        val zip = ZipOutputStream(out)
        listOf(PRACTICES to practicesCsv, ENTRIES to entriesCsv, SADHANAS to sadhanasJson).forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        zip.finish()
    }

    /** A ZIP file starts with the bytes "PK" and 3, 4. */
    fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /** Reads at most [MAX_BYTES] from [input]. Null when there is more. */
    fun readLimited(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > MAX_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Reads the three files. A ZIP with more than [MAX_ENTRIES] entries, or
     * that unpacks to more than [MAX_BYTES] in all, is [ZipRead.TooLarge].
     * Entries the reader does not need are still counted, because the ZIP
     * stream has to unpack them to move on.
     */
    fun read(bytes: ByteArray): ZipRead = try {
        val found = mutableMapOf<String, String>()
        var budget = MAX_BYTES.toLong()
        var seen = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++seen > MAX_ENTRIES) return ZipRead.TooLarge
                val name = entry.name.substringAfterLast('/')
                val keep = !entry.isDirectory && name in listOf(PRACTICES, ENTRIES, SADHANAS)
                val data = java.io.ByteArrayOutputStream()
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    budget -= n
                    if (budget < 0) return ZipRead.TooLarge
                    if (keep) data.write(buffer, 0, n)
                }
                if (keep) found[name] = String(data.toByteArray(), Charsets.UTF_8)
            }
        }
        ZipRead.Ok(ZipContents(found[PRACTICES], found[ENTRIES], found[SADHANAS]))
    } catch (e: java.io.IOException) {
        ZipRead.Unreadable
    }
}
