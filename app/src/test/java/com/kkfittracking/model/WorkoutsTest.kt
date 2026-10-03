package com.kkfittracking.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutsTest {
    private val minute = 60_000L
    private val evening = 1_800_000_000_000L

    private fun exercise(
        name: String,
        type: ExerciseType = ExerciseType.WEIGHT_REPS,
        style: TrainingStyle = TrainingStyle.STRENGTH,
        vararg sets: SetEntry,
    ) = DayExercise(
        workoutExerciseId = "we-$name", exerciseId = name, exerciseName = name, exerciseType = type, categoryColor = 0,
        sets = sets.toList(), styles = listOf(style),
    )

    private fun set(id: String, atMinute: Int, values: SetValues = SetValues(60.0, 8)) =
        SetEntry(id, values, loggedAtMillis = evening + atMinute * minute)

    @Test
    fun gymWorkIsOneWorkoutFromTheFirstToTheLastSet() {
        val day = listOf(
            exercise("Pull Up", ExerciseType.REPS, TrainingStyle.STRENGTH, set("a", 0), set("b", 3), set("c", 6)),
            exercise("Wall Sit", ExerciseType.TIME_WEIGHT, TrainingStyle.ISOMETRIC, set("d", 9), set("e", 12)),
        )
        val workouts = dayWorkouts(day, bodyweightKg = 80.0, UnitSystem.METRIC, evening)
        val session = workouts.sessions.single()
        assertEquals(ActivityKind.STRENGTH, session.kind)
        // Two minutes before the first set to the last one.
        assertEquals(evening - 2 * minute, session.startMillis)
        assertEquals(evening + 12 * minute, session.endMillis)
        assertEquals(14, session.minutes)
        // (3 sets at MET 3.5 + 2 at 3.0) / 5 = 3.3 × 3.5 × 80 / 200 × 14 min ≈ 65 kcal.
        assertEquals(65, workouts.kcal)
        assertEquals("Pull Up: 3 sets\nWall Sit: 2 sets", session.notes)
        assertTrue(workouts.bodyweightKnown)
    }

    @Test
    fun aLongBreakStartsANewWorkout() {
        val day = listOf(
            exercise("Hip CARs", ExerciseType.REPS, TrainingStyle.MOBILITY, set("a", 0), set("b", 4)),
            exercise("Deadlift", ExerciseType.WEIGHT_REPS, TrainingStyle.STRENGTH, set("c", 300), set("d", 305)),
        )
        val sessions = dayWorkouts(day, null, UnitSystem.METRIC, evening).sessions
        assertEquals(listOf(ActivityKind.STRETCHING, ActivityKind.STRENGTH), sessions.map { it.kind })
    }

    @Test
    fun runsAndSportsAreWorkoutsOfTheirOwn() {
        val run = exercise(
            "Running", ExerciseType.DISTANCE_TIME, TrainingStyle.CARDIO,
            set("r", 40, SetValues(distanceMeters = 5_000.0, durationSeconds = 25 * 60)),
        )
        val tennis = exercise(
            "Tennis", ExerciseType.SESSION, TrainingStyle.SPORT,
            set("t", 200, SetValues(durationSeconds = 60 * 60, rpe = 5)),
        )
        val lunges = exercise("Walking Lunge", ExerciseType.WEIGHT_REPS, TrainingStyle.STRENGTH, set("l", 0))
        val workouts = dayWorkouts(listOf(lunges, run, tennis), bodyweightKg = 70.0, UnitSystem.METRIC, evening)
        // The walking lunge is gym work, not a walk.
        assertEquals(listOf(ActivityKind.STRENGTH, ActivityKind.RUNNING, ActivityKind.TENNIS), workouts.sessions.map { it.kind })
        val running = workouts.sessions[1]
        assertEquals(evening + 15 * minute, running.startMillis)
        // 12 km/h: MET 12.36 × 3.5 × 70 / 200 × 25 min ≈ 378.5 kcal.
        assertEquals(378.5, running.kcal, 0.1)
        assertEquals("5 km · 25:00", running.notes)
        // An hour of tennis at usual effort (RPE 5): MET 7.3 × 3.5 × 70 / 200 × 60 ≈ 536.6 kcal.
        assertEquals(536.6, workouts.sessions[2].kcal, 0.1)
    }

    @Test
    fun caloriesTheWatchMeasuredAreUsedAsTheyAre() {
        val ride = exercise(
            "Cycling", ExerciseType.DISTANCE_TIME, TrainingStyle.CARDIO,
            set("c", 0, SetValues(distanceMeters = 20_000.0, durationSeconds = 3_600, note = "8,432 steps · ♥ avg 141 bpm · 612 kcal")),
        )
        val session = dayWorkouts(listOf(ride), null, UnitSystem.METRIC, evening).sessions.single()
        assertEquals(612.0, session.kcal, 1e-9)
        assertTrue(session.kcalMeasured)
    }

    @Test
    fun withoutABodyweightATypicalOneIsUsed() {
        val workouts = dayWorkouts(listOf(exercise("Plank", ExerciseType.TIME, TrainingStyle.ISOMETRIC, set("p", 0))), null, UnitSystem.METRIC, evening)
        assertFalse(workouts.bodyweightKnown)
        assertEquals(TYPICAL_BODYWEIGHT_KG, workouts.bodyweightKg, 1e-9)

        val weights = listOf(
            BodyMeasurement("a", LocalDate.of(2026, 9, 1), BodyMetric.BODYWEIGHT, 81.0),
            BodyMeasurement("b", LocalDate.of(2026, 9, 20), BodyMetric.BODYWEIGHT, 80.0),
            BodyMeasurement("c", LocalDate.of(2026, 9, 30), BodyMetric.BODYWEIGHT, 79.0),
            BodyMeasurement("d", LocalDate.of(2026, 9, 25), BodyMetric.WAIST, 85.0),
        )
        assertEquals(80.0, bodyweightOn(LocalDate.of(2026, 9, 27), weights)!!, 1e-9)
    }

    @Test
    fun activityKindsFromNames() {
        fun kind(name: String, type: ExerciseType, style: TrainingStyle) = activityKind(exercise(name, type, style))
        assertEquals(ActivityKind.TABLE_TENNIS, kind("Table Tennis", ExerciseType.SESSION, TrainingStyle.SPORT))
        assertEquals(ActivityKind.CLIMBING, kind("Bouldering", ExerciseType.SESSION, TrainingStyle.SPORT))
        assertEquals(ActivityKind.MARTIAL_ARTS, kind("Kickboxing", ExerciseType.SESSION, TrainingStyle.SPORT))
        assertEquals(ActivityKind.BOXING, kind("Heavy Bag Rounds", ExerciseType.INTERVALS, TrainingStyle.HIIT))
        assertEquals(ActivityKind.HIIT, kind("Tabata", ExerciseType.INTERVALS, TrainingStyle.HIIT))
        assertEquals(ActivityKind.RUNNING, kind("Hill Sprints", ExerciseType.INTERVALS, TrainingStyle.HIIT))
        assertEquals(ActivityKind.DANCE, kind("Salsa", ExerciseType.SESSION, TrainingStyle.DANCE))
        // Whole words: a spinal twist is no spin class, a crunch no run.
        assertEquals(ActivityKind.OTHER, kind("Supine Spinal Twist", ExerciseType.TIME, TrainingStyle.STRETCHING))
        assertEquals(ActivityKind.HIIT, kind("Crunch Intervals", ExerciseType.INTERVALS, TrainingStyle.HIIT))
        assertEquals(ActivityKind.BIKING, kind("Spinning", ExerciseType.DISTANCE_TIME, TrainingStyle.CARDIO))
    }
}
