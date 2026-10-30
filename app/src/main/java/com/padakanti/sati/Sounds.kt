package com.padakanti.sati

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * The bell sound. The built-in bell lives in res/raw. A chosen sound is
 * copied into device-protected app storage, so it keeps working if the
 * original file moves, and plays before the first unlock after a restart.
 */
object Sounds {
    /** Larger files are refused. A bell is seconds long; 20 MB is far beyond that. */
    const val MAX_BYTES = 20L * 1024 * 1024

    sealed interface ImportResult {
        data class Ok(val name: String) : ImportResult
        data object TooLarge : ImportResult
        data object Unplayable : ImportResult
        data object Failed : ImportResult
    }

    private fun dir(context: Context) = context.createDeviceProtectedStorageContext().filesDir
    private fun customFile(context: Context) = File(dir(context), "custom_sound")

    fun setSource(context: Context, player: MediaPlayer) {
        val custom = customFile(context)
        if (Store(context).soundName != null && custom.exists()) {
            player.setDataSource(custom.path)
        } else {
            context.resources.openRawResourceFd(R.raw.bell).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
        }
    }

    /**
     * Copies the file at [uri] into app storage and checks that it plays.
     * Runs file work, so call it off the main thread. Imports run one at a
     * time, and each copies into its own temporary file, so two quick picks
     * (for example across a screen rotation) cannot corrupt the sound.
     */
    @Synchronized
    fun importFrom(context: Context, uri: Uri): ImportResult {
        val incoming = File.createTempFile("incoming_", ".sound", dir(context))
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                incoming.outputStream().use { out -> copyCapped(input, out) }
            } ?: return ImportResult.Failed
            if (!copied) return ImportResult.TooLarge
            if (!playable(incoming)) return ImportResult.Unplayable
            if (!incoming.renameTo(customFile(context))) return ImportResult.Failed
            val name = displayName(context, uri) ?: "Chosen sound"
            Store(context).soundName = name
            return ImportResult.Ok(name)
        } catch (e: Exception) {
            return ImportResult.Failed
        } finally {
            // After a successful rename the file is gone, so this only cleans up failures.
            incoming.delete()
        }
    }

    fun useDefault(context: Context) {
        customFile(context).delete()
        Store(context).soundName = null
    }

    /** Copies at most [MAX_BYTES]. Returns false when the input is larger. */
    private fun copyCapped(input: java.io.InputStream, out: java.io.OutputStream): Boolean {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return true
            total += n
            if (total > MAX_BYTES) return false
            out.write(buf, 0, n)
        }
    }

    private fun playable(file: File): Boolean {
        val player = MediaPlayer()
        return try {
            player.setDataSource(file.path)
            player.prepare()
            true
        } catch (e: Exception) {
            false
        } finally {
            player.release()
        }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
}
