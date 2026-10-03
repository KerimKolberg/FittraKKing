package com.kkfittracking.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingLoadTest {
    private val today = LocalDate.of(2026, 10, 3)

    private fun exercise(id: String, vararg muscles: Muscle, type: ExerciseType = ExerciseType.WEIGHT_REPS, tendons: List<Tendon> = emptyList()) =
        Exercise(id = id, name = id, categoryId = "c", type = type, notes = "", isCustom = false, muscles = muscles.toList(), tendons = tendons)

    private fun sessions(vararg days: Pair<LocalDate, List<SetValues>>) =
        days.map { (date, sets) -> HistorySession(date, sets.mapIndexed { i, v -> SetEntry("$date-$i", v) }) }

    @Test
    fun setsCountFullForTheMainMuscleAndHalfForTheOthers() {
        val bench = exercise("bench", Muscle.CHEST, Muscle.TRICEPS, tendons = listOf(Tendon.TRICEPS))
        val histories = mapOf(
            "bench" to sessions(
                today to List(3) { SetValues(60.0, 8) } + SetValues(45.0, 8, isDropSet = true),
                today.minusDays(10) to List(3) { SetValues(60.0, 8) },
            ),
        )
        val sets = setsPerMuscle(histories, listOf(bench), today.minusDays(6), today)
        assertEquals(3.5, sets.getValue(Muscle.CHEST), 1e-9)
        assertEquals(1.75, sets.getValue(Muscle.TRICEPS), 1e-9)
        assertEquals(mapOf(Tendon.TRICEPS to 3), setsPerTendon(histories, listOf(bench), today.minusDays(6), today))
        assertEquals(VolumeLevel.LIGHT, VolumeLevel.of(3.5))
        assertEquals(VolumeLevel.HIGH, VolumeLevel.of(12.0))
    }

    @Test
    fun aSuddenJumpInLoadIsFlagged() {
        val sport = exercise("volleyball", Muscle.SPORT, type = ExerciseType.SESSION)
        // An hour at effort 5 every week for three weeks, then three hours at effort 7.
        val history = (1..3).map { week -> today.minusWeeks(week.toLong()) to listOf(SetValues(durationSeconds = 3600, rpe = 5)) } +
            listOf(today to List(3) { SetValues(durationSeconds = 3600, rpe = 7) })
        val ratio = loadRatio(mapOf("volleyball" to sessions(*history.toTypedArray())), listOf(sport), today)
        assertEquals(3 * 60 * 7.0, ratio.thisWeek, 1e-9)
        assertTrue(ratio.ratio!! > 1.5)
        assertTrue(ratio.verdict.startsWith("A big jump"))
        // Too little history to compare.
        val fresh = loadRatio(mapOf("volleyball" to sessions(today to listOf(SetValues(durationSeconds = 3600)))), listOf(sport), today)
        assertNull(fresh.ratio)
    }

    @Test
    fun forgottenAreasListTheOftenForgottenAndTheLapsed() {
        val neck = exercise("chin tuck", Muscle.NECK, type = ExerciseType.TIME)
        val squat = exercise("squat", Muscle.QUADS, Muscle.GLUTES)
        val histories = mapOf(
            "chin tuck" to sessions(today.minusDays(3) to listOf(SetValues(durationSeconds = 30))),
            "squat" to sessions(today.minusDays(20) to listOf(SetValues(100.0, 5))),
        )
        val forgotten = forgottenAreas(histories, listOf(neck, squat), today)
        val muscles = forgotten.map { it.muscle }
        assertTrue(Muscle.NECK !in muscles)
        assertTrue(Muscle.QUADS in muscles && Muscle.FEET in muscles)
        assertEquals(20L, forgotten.single { it.muscle == Muscle.QUADS }.daysSince)
        assertNull(forgotten.single { it.muscle == Muscle.FEET }.daysSince)
        // Never trained comes first.
        assertNull(forgotten.first().daysSince)
    }
}
