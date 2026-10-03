package com.kkfittracking.data.backup

import android.content.Context
import android.net.Uri
import com.kkfittracking.data.BodyRepository
import com.kkfittracking.data.ExerciseRepository
import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.db.WorkoutDao
import com.kkfittracking.model.CsvExport
import com.kkfittracking.model.ExportFile
import com.kkfittracking.model.ExportSet
import com.kkfittracking.model.SetValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime

/** Reads and writes backup and export files: picked with the system file picker, or in Downloads/KK-Fittracking. */
class DataTransfer(
    private val context: Context,
    private val backups: BackupRepository,
    private val workoutDao: WorkoutDao,
    private val bodyRepository: BodyRepository,
    private val settingsRepository: SettingsRepository,
    private val exerciseRepository: ExerciseRepository,
    private val downloads: DownloadsFolder,
) {
    suspend fun writeBackup(uri: Uri) {
        write(uri, backupText())
        settingsRepository.setLastBackupAt(System.currentTimeMillis())
    }

    suspend fun readBackup(uri: Uri): BackupFile = BackupJson.decode(read(uri))

    /** Restores the backup, then files a backup from an earlier version under the current library. */
    suspend fun restore(file: BackupFile) {
        backups.restore(file)
        exerciseRepository.syncBuiltIns()
    }

    suspend fun exportWorkouts(uri: Uri) = write(uri, workoutsCsv())

    suspend fun exportBodyMeasurements(uri: Uri) = write(uri, bodyCsv())

    val canSaveToDownloads: Boolean get() = downloads.isAvailable

    /**
     * One tap: a backup and both spreadsheets, saved to Downloads/KK-Fittracking with today's date
     * and time in their names. Returns the folder they are in.
     */
    suspend fun saveAllToDownloads(): String {
        val at = LocalDateTime.now()
        downloads.save(ExportFile.BACKUP.fileName(at), ExportFile.BACKUP.mimeType, backupText())
        settingsRepository.setLastBackupAt(System.currentTimeMillis())
        downloads.save(ExportFile.WORKOUTS.fileName(at), ExportFile.WORKOUTS.mimeType, workoutsCsv())
        downloads.save(ExportFile.BODY.fileName(at), ExportFile.BODY.mimeType, bodyCsv())
        return downloads.shownPath
    }

    /** The automatic backup: a dated file in Downloads/KK-Fittracking; only the newest few are kept. */
    suspend fun autoBackup() {
        val file = ExportFile.AUTO_BACKUP
        downloads.save(file.fileName(LocalDateTime.now()), file.mimeType, backupText())
        settingsRepository.setLastBackupAt(System.currentTimeMillis())
        downloads.pruneAutoBackups()
    }

    private suspend fun backupText(): String = BackupJson.encode(backups.createBackup())

    private suspend fun workoutsCsv(): String {
        val units = settingsRepository.settings.first().unitSystem
        val sets = workoutDao.exportRows().map { row ->
            ExportSet(
                date = row.date,
                exercise = row.exerciseName,
                category = row.categoryName,
                type = row.exerciseType,
                values = SetValues(
                    weightKg = row.set.weightKg,
                    reps = row.set.reps,
                    distanceMeters = row.set.distanceMeters,
                    durationSeconds = row.set.durationSeconds,
                    rpe = row.set.rpe,
                    note = row.set.comment,
                    isDropSet = row.set.isDropSet,
                ),
            )
        }
        return CsvExport.workouts(sets, units)
    }

    private suspend fun bodyCsv(): String {
        val units = settingsRepository.settings.first().unitSystem
        return CsvExport.bodyMeasurements(bodyRepository.measurements.first(), units)
    }

    private suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates, so overwriting a longer file leaves no old bytes behind.
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the file for writing.")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the file.")
        stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
