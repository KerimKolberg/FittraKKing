package com.kkfittracking.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** How often a backup is saved to Downloads/KK-Fittracking by itself. */
enum class AutoBackup(val label: String, val days: Long?) {
    OFF("Off", null),
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
}

/**
 * The files saved to Downloads/KK-Fittracking: named with what they hold and when they were made,
 * so they sort by kind and then by date, e.g. "KK-Fittracking_backup_2026-10-03_14-30.json".
 */
enum class ExportFile(val kind: String, val extension: String, val mimeType: String) {
    BACKUP("backup", "json", "application/json"),
    AUTO_BACKUP("auto-backup", "json", "application/json"),
    WORKOUTS("workouts", "csv", "text/csv"),
    BODY("body", "csv", "text/csv"),
    ;

    fun fileName(at: LocalDateTime): String = "${PREFIX}_${kind}_${at.format(STAMP)}.$extension"

    /** The start every file of this kind shares, for finding the older ones. */
    val namePrefix: String get() = "${PREFIX}_${kind}_"

    companion object {
        const val PREFIX = "KK-Fittracking"

        /** The folder inside Downloads. */
        const val FOLDER = "KK-Fittracking"

        /** Automatic backups kept; older ones are deleted. Backups saved by hand are never deleted. */
        const val AUTO_BACKUPS_KEPT = 8

        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
    }
}

/** Of the automatic backups' file names, the ones to delete so that only the newest [keep] stay. */
fun autoBackupsToDelete(names: List<String>, keep: Int = ExportFile.AUTO_BACKUPS_KEPT): List<String> =
    names.filter { it.startsWith(ExportFile.AUTO_BACKUP.namePrefix) }.sortedDescending().drop(keep)
