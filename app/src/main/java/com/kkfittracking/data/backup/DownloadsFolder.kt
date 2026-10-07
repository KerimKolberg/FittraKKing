package com.kkfittracking.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.kkfittracking.model.ExportFile
import com.kkfittracking.model.autoBackupsToDelete
import com.kkfittracking.model.renamedFromOldName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Downloads/FitTraKKing: files saved there without a file picker, through MediaStore (Android 10
 * and later, no storage permission needed). They stay when the app is uninstalled.
 */
class DownloadsFolder(private val context: Context) {
    /** Android 9 and older would need a storage permission; there the file picker is used instead. */
    val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Where the files go, as the Files app shows it. */
    val shownPath: String get() = "Downloads/${ExportFile.FOLDER}"

    /** Saves [text] as a new file named [name]. */
    suspend fun save(name: String, mimeType: String, text: String) = withContext(Dispatchers.IO) {
        if (!isAvailable) throw IOException("Saving to Downloads needs Android 10 or later. Use \"Save as…\" instead.")
        saveQ(name, mimeType, text)
    }

    /** Deletes all but the newest automatic backups this app saved. */
    suspend fun pruneAutoBackups() = withContext(Dispatchers.IO) {
        if (isAvailable) pruneQ()
    }

    /**
     * Moves the backups and exports from Downloads/KK-Fittracking (the app's old name) to
     * Downloads/FitTraKKing, with the new name in front. Only files this installation of the app
     * saved can be moved; others stay where they are. Returns how many were moved.
     */
    suspend fun moveFromOldFolder(): Int = withContext(Dispatchers.IO) {
        if (isAvailable) moveQ() else 0
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun moveQ(): Int {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val old = mutableMapOf<Long, String>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(OLD_RELATIVE_PATH),
            null,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) old[cursor.getLong(id)] = cursor.getString(name)
        }
        return old.count { (id, name) ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.MediaColumns.DISPLAY_NAME, renamedFromOldName(name))
            }
            // A file another app (or an earlier installation) saved cannot be moved; it stays.
            runCatching { resolver.update(ContentUris.withAppendedId(collection, id), values, null, null) > 0 }.getOrDefault(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveQ(name: String, mimeType: String, text: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            // Hidden from other apps until it is completely written.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create the file in Downloads.")
        try {
            val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the file for writing.")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun pruneQ() {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val files = mutableMapOf<String, Long>()
        // Only the app's own files are listed, without a storage permission.
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf(RELATIVE_PATH, ExportFile.AUTO_BACKUP.namePrefix + "%"),
            null,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) files[cursor.getString(name)] = cursor.getLong(id)
        }
        autoBackupsToDelete(files.keys.toList()).forEach { name ->
            files[name]?.let { resolver.delete(ContentUris.withAppendedId(collection, it), null, null) }
        }
    }

    companion object {
        private val RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/${ExportFile.FOLDER}/"
        private val OLD_RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/${ExportFile.OLD_NAME}/"
    }
}
