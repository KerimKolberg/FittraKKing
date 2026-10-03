package com.kkfittracking.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ExportFilesTest {
    private val at = LocalDateTime.of(2026, 10, 3, 14, 30)

    @Test
    fun filesAreNamedByAppKindAndDate() {
        assertEquals("KK-Fittracking_backup_2026-10-03_14-30.json", ExportFile.BACKUP.fileName(at))
        assertEquals("KK-Fittracking_workouts_2026-10-03_14-30.csv", ExportFile.WORKOUTS.fileName(at))
        assertEquals("KK-Fittracking_body_2026-10-03_14-30.csv", ExportFile.BODY.fileName(at))
        assertEquals("KK-Fittracking_auto-backup_2026-10-03_14-30.json", ExportFile.AUTO_BACKUP.fileName(at))
    }

    @Test
    fun onlyTheOldestAutomaticBackupsAreDeleted() {
        val autos = (1..10).map { ExportFile.AUTO_BACKUP.fileName(at.minusWeeks(it.toLong())) }
        val byHand = ExportFile.BACKUP.fileName(at.minusYears(1))
        val deleted = autoBackupsToDelete((autos + byHand).shuffled(), keep = 8)
        // The two oldest automatic ones; a backup saved by hand is never deleted.
        assertEquals(autos.takeLast(2).toSet(), deleted.toSet())
    }

    @Test
    fun theNextReminderIsOnAChosenDayAfterNow() {
        val saturday = LocalDateTime.of(2026, 10, 3, 18, 0)
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY)
        // 17:30 today has passed, so Monday.
        assertEquals(LocalDateTime.of(2026, 10, 5, 17, 30), nextReminder(saturday, days, 17 * 60 + 30))
        // 19:00 is still to come today.
        assertEquals(LocalDateTime.of(2026, 10, 3, 19, 0), nextReminder(saturday, days, 19 * 60))
        // Only Saturdays, already past: next week.
        assertEquals(LocalDateTime.of(2026, 10, 10, 7, 0), nextReminder(saturday, setOf(DayOfWeek.SATURDAY), 7 * 60))
        assertNull(nextReminder(saturday, emptySet(), 7 * 60))
        assertEquals("7:05", formatMinuteOfDay(7 * 60 + 5))
    }
}
