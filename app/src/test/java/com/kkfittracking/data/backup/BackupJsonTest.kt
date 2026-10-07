package com.kkfittracking.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    private val sample = BackupFile(
        formatVersion = BackupFile.CURRENT_FORMAT_VERSION,
        createdAt = 1_000,
        appVersion = "0.1.0",
        exercises = listOf(
            ExerciseDto(
                id = "e1", name = "Tabata", categoryId = "c1", type = "INTERVALS", isCustom = false, createdAt = 1, updatedAt = 1,
                muscles = "SPORT", style = "HIIT", plan = """{"highSeconds":20,"lowSeconds":10,"rounds":8}""",
                links = "Tabata explained | https://youtu.be/abc",
            ),
        ),
        workouts = listOf(WorkoutDto("w1", "2026-09-25", "", 1, 1)),
        planExercises = listOf(PlanExerciseDto("pe1", "p1", "e1", 0, 1, 1, supersetId = "s", transitionSeconds = 15, roundRestSeconds = 90)),
        sets = listOf(
            SetDto("s1", "we1", 0, weightKg = 100.0, reps = 5, createdAt = 1, updatedAt = 1),
            SetDto("s2", "we1", 1, weightKg = 100.0, reps = 5, createdAt = 1, updatedAt = 2, deletedAt = 2),
        ),
    )

    @Test
    fun roundTrip() {
        assertEquals(sample, BackupJson.decode(BackupJson.encode(sample)))
    }

    @Test
    fun summaryCountsOnlyLiveRows() {
        assertEquals(1, sample.summary().sets)
        assertEquals(1, sample.summary().workouts)
    }

    @Test
    fun olderFilesWithoutNewFieldsStillLoad() {
        val old = """{"formatVersion":1,"createdAt":5,"sets":[{"id":"s","workoutExerciseId":"we","sortOrder":0,
            |"reps":8,"createdAt":1,"updatedAt":1}],"someFutureField":true}""".trimMargin()
        val file = BackupJson.decode(old)
        assertEquals(null, file.sets.single().rpe)
        assertEquals("", file.sets.single().comment)

        // Version 0.1 stored a drop set plan per day; it is left out now.
        val withDayPlans = """{"formatVersion":1,"createdAt":5,"workoutExercises":[{"id":"we","workoutId":"w",
            |"exerciseId":"e","sortOrder":0,"createdAt":1,"updatedAt":1,"dropSetMode":1,"plannedSets":4}],
            |"exercises":[{"id":"e","name":"Bench","categoryId":"c","type":"WEIGHT_REPS","isCustom":false,
            |"createdAt":1,"updatedAt":1}]}""".trimMargin()
        val older = BackupJson.decode(withDayPlans)
        assertEquals(null, older.workoutExercises.single().roundRestSeconds)
        assertEquals("", older.exercises.single().plan)
        assertEquals("", older.exercises.single().muscles)
    }

    @Test
    fun rejectsOtherFilesAndNewerVersions() {
        val notJson = assertThrows(BackupException::class.java) { BackupJson.decode("hello") }
        assertTrue(notJson.message!!.contains("not a FitTraKKing backup"))
        val newer = assertThrows(BackupException::class.java) {
            BackupJson.decode("""{"formatVersion":99,"createdAt":1}""")
        }
        assertTrue(newer.message!!.contains("newer version"))
    }
}
