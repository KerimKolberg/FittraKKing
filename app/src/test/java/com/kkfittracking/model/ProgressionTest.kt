package com.kkfittracking.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressionTest {
    private fun session(vararg values: SetValues) =
        HistorySession(LocalDate.of(2026, 10, 1), values.mapIndexed { i, v -> SetEntry("s$i", v) })

    private val metric = UnitSystem.METRIC

    @Test
    fun allSetsAtTheTargetAddTheSmallestJump() {
        val last = session(SetValues(60.0, 8), SetValues(60.0, 8), SetValues(60.0, 8))
        val next = progressionSuggestion(ExerciseType.WEIGHT_REPS, ExercisePlan(sets = 3, reps = 8), last, metric)!!
        assertEquals(SetValues(62.5, 8), next.values)
        // Light dumbbells go up by 1 kg.
        val light = progressionSuggestion(ExerciseType.WEIGHT_REPS, ExercisePlan(reps = 12), session(SetValues(12.0, 12)), metric)!!
        assertEquals(12.0 + 1.0, light.values.weightKg!!, 1e-9)
    }

    @Test
    fun aMissedSetKeepsTheWeightAndAddsARep() {
        val last = session(SetValues(60.0, 8), SetValues(60.0, 7), SetValues(60.0, 6), SetValues(45.0, 10, isDropSet = true))
        val next = progressionSuggestion(ExerciseType.WEIGHT_REPS, ExercisePlan(sets = 3, reps = 8), last, metric)!!
        assertEquals(SetValues(60.0, 7), next.values)
        // Fewer sets than planned is not ready either.
        val short = progressionSuggestion(ExerciseType.WEIGHT_REPS, ExercisePlan(sets = 3, reps = 8), session(SetValues(60.0, 8)), metric)!!
        assertEquals(60.0, short.values.weightKg!!, 1e-9)
    }

    @Test
    fun aSoreTendonHoldsOrStepsBack() {
        val last = session(SetValues(60.0, 8), SetValues(60.0, 8))
        val hold = progressionSuggestion(ExerciseType.WEIGHT_REPS, ExercisePlan(reps = 8), last, metric, PainLight.YELLOW)!!
        assertEquals(SetValues(60.0, 8), hold.values)
        val back = progressionSuggestion(ExerciseType.WEIGHT_REPS, ExercisePlan(reps = 8), last, metric, PainLight.RED)!!
        assertEquals(54.0, back.values.weightKg!!, 1e-9)
    }

    @Test
    fun holdsGetLongerThenHeavier() {
        val short = progressionSuggestion(ExerciseType.TIME, ExercisePlan(), session(SetValues(durationSeconds = 30)), metric)!!
        assertEquals(35, short.values.durationSeconds)
        val long = progressionSuggestion(
            ExerciseType.TIME_WEIGHT, ExercisePlan(), session(SetValues(weightKg = 20.0, durationSeconds = 45)), metric,
        )!!
        assertEquals(SetValues(weightKg = 22.5, durationSeconds = 45), long.values)
        // Bodyweight reps: one more than the best.
        val reps = progressionSuggestion(ExerciseType.REPS, ExercisePlan(), session(SetValues(reps = 10), SetValues(reps = 8)), metric)!!
        assertEquals(11, reps.values.reps)
        // Nothing to go on, or nothing to progress.
        assertNull(progressionSuggestion(ExerciseType.WEIGHT_REPS, ExercisePlan(), null, metric))
        assertNull(progressionSuggestion(ExerciseType.DISTANCE_TIME, ExercisePlan(), session(SetValues(distanceMeters = 5000.0)), metric))
    }

    @Test
    fun theSideOfOneSidedSetsCarriesOver() {
        val last = session(SetValues(20.0, 10, side = Side.LEFT), SetValues(20.0, 10, side = Side.RIGHT))
        val next = progressionSuggestion(ExerciseType.WEIGHT_REPS, ExercisePlan(reps = 10), last, metric)!!
        assertEquals(Side.LEFT, next.values.side)
        assertTrue(next.reason.isNotBlank())
    }

    @Test
    fun platesPerSideAndWarmUps() {
        val load = plateLoad(totalKg = 102.5, barKg = 20.0, units = metric)!!
        assertEquals(listOf(25.0, 15.0, 1.25), load.perSide)
        assertTrue(load.isExact)
        assertEquals("25 + 15 + 1.25", formatPlates(load))
        assertEquals("2×25", formatPlates(plateLoad(120.0, 20.0, metric)!!))
        assertEquals("Just the bar", formatPlates(plateLoad(20.0, 20.0, metric)!!))
        assertNull(plateLoad(15.0, 20.0, metric))
        // 101 kg cannot be made with 1.25 kg as the smallest plate.
        assertEquals(1.0, plateLoad(101.0, 20.0, metric)!!.missing, 1e-9)

        val ramp = warmUpSets(workKg = 100.0, barKg = 20.0, units = metric)
        assertEquals(listOf(20.0 to 10, 40.0 to 8, 60.0 to 5, 80.0 to 3), ramp.map { it.weightKg to it.reps })
        assertEquals(emptyList<WarmUpSet>(), warmUpSets(30.0, 20.0, metric))
        // Dumbbells: no bar to start from.
        assertEquals(listOf(12.5 to 8, 17.5 to 5, 25.0 to 3), warmUpSets(30.0, 0.0, metric).map { it.weightKg to it.reps })
        assertEquals(emptyList<WarmUpSet>(), warmUpSets(100.0, 0.0, UnitSystem.LEVELS))
        assertTrue(isBarbellLift("Barbell Squat"))
        assertTrue(!isBarbellLift("Goblet Squat"))
    }
}
