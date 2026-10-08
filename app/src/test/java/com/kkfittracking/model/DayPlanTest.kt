package com.kkfittracking.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlanTest {
    private fun sets(count: Int, drops: Int = 0) =
        List(count) { SetEntry("s$it", SetValues(100.0, 8)) } +
            List(drops) { SetEntry("d$it", SetValues(80.0, 8, isDropSet = true)) }

    private fun exercise(
        id: String,
        plan: ExercisePlan = ExercisePlan(),
        done: Int = 0,
        drops: Int = 0,
        type: ExerciseType = ExerciseType.WEIGHT_REPS,
        superset: String? = null,
        rounds: Int? = null,
        memberRounds: Int? = null,
    ) = DayExercise(
        workoutExerciseId = "we-$id", exerciseId = id, exerciseName = id, exerciseType = type, categoryColor = 0,
        sets = if (type.isSession) sets(done).map { SetEntry(it.id, SetValues(durationSeconds = 3600)) } else sets(done, drops),
        supersetId = superset, supersetRounds = rounds, memberRounds = memberRounds, plan = plan,
    )

    @Test
    fun theGuideGoesThroughTheDayInOrder() {
        val day = listOf(
            exercise("bench", ExercisePlan(sets = 3)),
            exercise("curl"),
            exercise("tennis", type = ExerciseType.SESSION),
        )
        assertEquals(GuideTarget("bench", "bench", "Set 1 of 3", 1, 3), guideTarget(day))
        val benchDone = day.mapIndexed { i, e -> if (i == 0) exercise("bench", ExercisePlan(sets = 3), done = 3) else e }
        // Without a set plan, the guide expects three sets.
        assertEquals("curl" to "Set 1 of 3", guideTarget(benchDone)!!.let { it.exerciseId to it.step })
        assertEquals("tennis" to "Session", guideTarget(benchDone, skipped = setOf("curl"))!!.let { it.exerciseId to it.step })
        val allDone = listOf(
            exercise("bench", ExercisePlan(sets = 3), done = 3),
            exercise("curl", done = 4),
            exercise("tennis", type = ExerciseType.SESSION, done = 1),
        )
        assertNull(guideTarget(allDone))
    }

    @Test
    fun aDueDropSetComesRightAfterItsSet() {
        val plan = ExercisePlan(sets = 2, dropSets = true, drops = 2)
        val target = guideTarget(listOf(exercise("bench", plan, done = 2), exercise("row")))!!
        assertEquals("bench", target.exerciseId)
        assertEquals("Drop 1 of 2", target.step)
        assertTrue(target.isDrop)
        assertEquals("row", guideTarget(listOf(exercise("bench", plan, done = 2, drops = 2), exercise("row")))?.exerciseId)
    }

    @Test
    fun aSupersetGoesRoundByRound() {
        // Bench and row for 3 rounds; curl joins the last round only.
        fun day(bench: Int, row: Int, curl: Int) = listOf(
            exercise("bench", done = bench, superset = "s", rounds = 3),
            exercise("row", done = row, superset = "s", rounds = 3),
            exercise("curl", done = curl, superset = "s", rounds = 3, memberRounds = 1),
            exercise("plank", ExercisePlan(sets = 1)),
        )
        fun next(bench: Int, row: Int, curl: Int) = guideTarget(day(bench, row, curl))?.let { "${it.exerciseId} ${it.step}" }
        assertEquals("bench Round 1 of 3", next(0, 0, 0))
        assertEquals("row Round 1 of 3", next(1, 0, 0))
        assertEquals("bench Round 2 of 3", next(1, 1, 0))
        assertEquals("row Round 3 of 3", next(3, 2, 0))
        assertEquals("curl Round 3 of 3", next(3, 3, 0))
        assertEquals("plank Set 1 of 1", next(3, 3, 1))
    }

    @Test
    fun howMuchOfTheDayIsDone() {
        val day = listOf(
            exercise("bench", ExercisePlan(sets = 3, dropSets = true, drops = 1), done = 3, drops = 1),
            exercise("row", ExercisePlan(sets = 4), done = 2),
            exercise("curl", ExercisePlan(sets = 3)),
            exercise("tennis", type = ExerciseType.SESSION),
        )
        val completion = dayCompletion(day)
        // Bench 4/4, row 2/4, curl 0/3, tennis 0/1: 6 of 12.
        assertEquals(50, completion.percent)
        assertEquals(listOf("bench"), completion.finished.map { it.exerciseId })
        assertEquals(listOf("row"), completion.partly.map { it.exerciseId })
        assertEquals(listOf("curl", "tennis"), completion.notStarted.map { it.exerciseId })
        assertEquals("3 of 3 sets · 1 of 1 drops", completion.finished.single().label)
        assertEquals("Session not done", completion.notStarted.last().label)
        // Extra sets do not count past the plan.
        assertEquals(100, dayCompletion(listOf(exercise("row", ExercisePlan(sets = 2), done = 5))).percent)
    }

    @Test
    fun supersetRoundsAreThePlan() {
        val day = listOf(
            exercise("bench", ExercisePlan(sets = 5), done = 3, superset = "s", rounds = 3),
            exercise("curl", done = 1, superset = "s", rounds = 3, memberRounds = 1),
        )
        val completion = dayCompletion(day)
        assertEquals(listOf(3, 1), completion.exercises.map { it.plannedSets })
        assertEquals(100, completion.percent)
    }

    @Test
    fun pausesDoNotCountAsTrainingTime() {
        var session = GuideSession(epochDay = 0, startedAtMillis = 0)
        session = session.pause(60_000)
        assertTrue(session.isPaused)
        assertEquals(60_000, session.activeMillis(600_000))
        // Pausing twice keeps the first pause.
        assertEquals(session, session.pause(90_000))
        session = session.resume(300_000)
        assertEquals(120_000, session.activeMillis(360_000))
        assertEquals(setOf("curl"), session.skip("curl").skipped)
    }

    @Test
    fun theGuideSuggestsWhatToLift() {
        val settings = Settings(dropSetPercent = 20)
        fun target(day: List<DayExercise>) = guideTarget(day, settings = settings)!!
        // Nothing today: the plan's reps over last session's weight.
        val fresh = listOf(exercise("bench", ExercisePlan(sets = 3, reps = 10)))
        val last = listOf(SetEntry("x", SetValues(90.0, 6)))
        assertEquals(SetValues(weightKg = 90.0, reps = 10), guideSuggestion(fresh, target(fresh), settings, last))
        // After a set today: the same again.
        val started = listOf(exercise("bench", ExercisePlan(sets = 3), done = 1))
        assertEquals(SetValues(100.0, 8), guideSuggestion(started, target(started), settings, last))
        // A due drop set: 20% lighter.
        val dropping = listOf(exercise("bench", ExercisePlan(sets = 2, dropSets = true), done = 2))
        assertEquals(SetValues(weightKg = 80.0, reps = 8, isDropSet = true), guideSuggestion(dropping, target(dropping), settings))
    }

    @Test
    fun theUnfinishedPartMovesToAnotherDay() {
        val day = listOf(
            exercise("bench", ExercisePlan(sets = 3), done = 3),
            exercise("row", ExercisePlan(sets = 4), done = 2),
            exercise("curl", ExercisePlan(sets = 3)),
            // A 3-round superset: 1 round done, 2 left.
            exercise("squat", done = 1, superset = "s", rounds = 3),
            exercise("lunge", done = 1, superset = "s", rounds = 3),
        )
        val move = unfinishedPart(day)
        assertEquals(listOf("row", "curl", "squat", "lunge"), move.toAdd.map { it.exerciseId })
        // Only the exercise not started leaves the day; the row keeps its two sets here.
        assertEquals(listOf("we-curl"), move.toRemove)
        assertEquals(listOf(2, 2), move.toAdd.filter { it.supersetId == "s" }.map { it.supersetRounds })
        assertTrue(unfinishedPart(listOf(exercise("bench", ExercisePlan(sets = 1), done = 1))).toAdd.isEmpty())
    }

    @Test
    fun whatComesNextToGetReady() {
        val day = listOf(
            exercise("bench", superset = "s", rounds = 2),
            exercise("row", superset = "s", rounds = 2),
            exercise("curl", ExercisePlan(sets = 2)),
            exercise("plank", ExercisePlan(sets = 1)),
            exercise("stretch", ExercisePlan(sets = 1)),
        )
        val now = guideTarget(day)!!
        assertEquals("bench", now.exerciseId)
        // The row of the same superset first, then the next exercises of the day.
        assertEquals(listOf("row", "curl", "plank"), upcomingExercises(day, now))
        assertEquals(listOf("row", "plank"), upcomingExercises(day, now, skipped = setOf("curl"), count = 2))
    }

    @Test
    fun aLeftAndARightSetMakeOneSet() {
        fun sided(vararg sides: Side) = sides.mapIndexed { i, side -> SetEntry("s$i", SetValues(20.0, 10, side = side)) }
        assertEquals(1, sided(Side.LEFT, Side.RIGHT, Side.LEFT).fullSets(perSide = true))
        assertEquals(Side.RIGHT, sided(Side.LEFT, Side.RIGHT, Side.LEFT).sideDue(perSide = true))
        assertNull(sided(Side.LEFT, Side.RIGHT).sideDue(perSide = true))

        // In a 3-round superset, the left set of the curl is followed by its right one, not the row.
        val superset = SupersetContext(listOf("curl", "row"), transitionSeconds = 20, roundRestSeconds = 90, rounds = 3)
        assertEquals(
            NextStep.OtherSide(Side.RIGHT),
            nextStep("curl", ExerciseType.WEIGHT_REPS, ExercisePlan(), sided(Side.LEFT), superset, Settings(), perSide = true),
        )
        assertEquals(
            NextStep.Transition("row", 20),
            nextStep("curl", ExerciseType.WEIGHT_REPS, ExercisePlan(), sided(Side.LEFT, Side.RIGHT), superset, Settings(), perSide = true),
        )

        // The guide stays on the exercise for the other side, and counts both sides as one set.
        val day = listOf(
            exercise("curl", ExercisePlan(sets = 3)).copy(sets = sided(Side.LEFT), perSide = true),
            exercise("row", ExercisePlan(sets = 3)),
        )
        val target = guideTarget(day)!!
        assertEquals("curl", target.exerciseId)
        assertEquals(Side.RIGHT, target.side)
        assertEquals("Set 1 of 3 · right", target.step)
        val bothSides = listOf(day[0].copy(sets = sided(Side.LEFT, Side.RIGHT)), day[1])
        assertEquals("Set 2 of 3", guideTarget(bothSides)!!.step)
        assertEquals(1, dayCompletion(bothSides).exercises.first().doneSets)
    }

    @Test
    fun sidesStopCountingWhenTheExerciseIsNoLongerOneSided() {
        fun sided(vararg sides: Side) = sides.mapIndexed { i, side -> SetEntry("s$i", SetValues(20.0, 10, side = side)) }
        // Left and right turned off after one left set: that set counts, and no right side is due.
        assertEquals(1, sided(Side.LEFT).fullSets(perSide = false))
        assertNull(sided(Side.LEFT).sideDue(perSide = false))
        // A finished left and right pair still counts once.
        assertEquals(1, sided(Side.LEFT, Side.RIGHT).fullSets(perSide = false))
        assertEquals(
            NextStep.Rest(90, null),
            nextStep("curl", ExerciseType.WEIGHT_REPS, ExercisePlan(), sided(Side.LEFT), null, Settings(), perSide = false),
        )
        // The guide moves on instead of waiting for the right side.
        val day = listOf(
            exercise("curl", ExercisePlan(sets = 2)).copy(sets = sided(Side.LEFT), perSide = false),
            exercise("row", ExercisePlan(sets = 3)),
        )
        val target = guideTarget(day)!!
        assertNull(target.side)
        assertEquals("Set 2 of 2", target.step)
    }

    @Test
    fun theWeightTypedInLastBeatsThePlansWeight() {
        val settings = Settings()
        val day = listOf(exercise("bench", ExercisePlan(sets = 3, reps = 8, weightKg = 25.0)))
        val target = guideTarget(day, settings = settings)!!
        // Last time 27.5 kg was lifted: that, not the plan's 25 kg, with the plan's reps.
        val last = listOf(SetEntry("x", SetValues(27.5, 10)))
        assertEquals(SetValues(weightKg = 27.5, reps = 8), guideSuggestion(day, target, settings, last))
        // Never lifted before: the plan's weight to start with.
        assertEquals(SetValues(weightKg = 25.0, reps = 8), guideSuggestion(day, target, settings, emptyList()))
    }
}
