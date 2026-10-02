package com.kkfittracking.data

import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.MAX_SUPERSET_SIZE
import com.kkfittracking.model.Muscle
import com.kkfittracking.model.Tendon
import com.kkfittracking.model.TrainingStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInExercisesTest {
    @Test
    fun starterPlansOnlyUseBuiltInExercises() {
        val ids = BuiltInExercises.exercises.map { it.id }.toSet()
        StarterPlans.plans.forEach { plan ->
            plan.exercises.forEach { name ->
                assertTrue("$name in ${plan.name} is not a built-in exercise", StarterPlans.exerciseId(name) in ids)
            }
            plan.setPlans.keys.forEach { assertTrue("$it has a set plan but is not in ${plan.name}", it in plan.exercises) }
            plan.supersets.forEach { group ->
                assertTrue("Supersets in ${plan.name} need 2 to $MAX_SUPERSET_SIZE exercises", group.size in 2..MAX_SUPERSET_SIZE)
                assertTrue("Superset exercises must be in ${plan.name}", plan.exercises.containsAll(group))
            }
        }
    }

    @Test
    fun everyExerciseIsInAKnownSection() {
        val sections = BuiltInExercises.regions.map { it.key }.toSet()
        BuiltInExercises.exercises.forEach {
            assertTrue("${it.name} has no section", it.regionKey in sections)
            assertTrue("${it.name} needs a muscle", Muscle.OTHER !in it.muscles)
            assertEquals("${it.name} lists a muscle twice", it.muscles.size, it.muscles.toSet().size)
        }
        // Every section has exercises, and interval HIIT is under Cardio.
        assertEquals(sections, BuiltInExercises.exercises.map { it.regionKey }.toSet())
        val hiit = BuiltInExercises.exercises.filter { it.style == TrainingStyle.HIIT }
        assertTrue(hiit.isNotEmpty())
        assertTrue(hiit.all { it.type != ExerciseType.INTERVALS || (it.regionKey == "cardio" && it.plan.rounds != null) })
    }

    @Test
    fun theNordicCurlIsUnderLegsHamstringsEccentric() {
        val nordic = BuiltInExercises.exercises.single { it.name == "Nordic Hamstring Curl" }
        assertEquals("legs", nordic.regionKey)
        assertEquals(Muscle.HAMSTRINGS, nordic.muscle)
        assertEquals(TrainingStyle.ECCENTRIC, nordic.style)
    }

    @Test
    fun hiitMovedToCardioAndYogaCountsTwice() {
        val tabata = BuiltInExercises.exercises.single { it.name == "Tabata" }
        assertEquals("cardio", tabata.regionKey)
        assertEquals("sports", tabata.movedFrom)
        val dog = BuiltInExercises.exercises.single { it.name == "Downward Dog" }
        assertEquals(listOf(TrainingStyle.YOGA, TrainingStyle.STRETCHING), dog.styles)
        // Poses that were already there count as yoga too.
        assertTrue(TrainingStyle.YOGA in BuiltInExercises.exercises.single { it.name == "Child's Pose" }.styles)
        assertEquals("mind", BuiltInExercises.exercises.single { it.name == "Meditation" }.regionKey)
        assertEquals(TrainingStyle.DANCE, BuiltInExercises.exercises.single { it.name == "Salsa" }.style)
    }

    @Test
    fun compoundExercisesTrainSeveralMuscles() {
        fun muscles(name: String) = BuiltInExercises.exercises.single { it.name == name }.muscles
        assertEquals(listOf(Muscle.LOWER_BACK, Muscle.HAMSTRINGS, Muscle.GLUTES), muscles("Deadlift").take(3))
        assertTrue(Muscle.HIPS in muscles("World's Greatest Stretch"))
        assertEquals("full-body", BuiltInExercises.exercises.single { it.name == "World's Greatest Stretch" }.regionKey)
    }

    @Test
    fun tendonsNameRealExercises() {
        val names = BuiltInExercises.exercises.map { it.name }.toSet()
        BuiltInTendons.exerciseNames.forEach { assertTrue("$it is not a built-in exercise", it in names) }
        val spanish = BuiltInExercises.exercises.single { it.name == "Spanish Squat Hold" }
        assertEquals(listOf(Tendon.PATELLAR, Tendon.QUADRICEPS), BuiltInTendons.of(spanish.id))
        // From a real plan: the slow pelvic drop is there, filed under glutes, and loads the gluteal tendons and the IT band.
        val drop = BuiltInExercises.exercises.single { it.name == "Slow Single-Leg Pelvic Drop" }
        assertEquals(Muscle.GLUTES, drop.muscle)
        assertEquals(listOf(Tendon.GLUTEAL, Tendon.ILIOTIBIAL), BuiltInTendons.of(drop.id))
        // Every tendon has exercises for it.
        val covered = BuiltInExercises.exercises.flatMap { BuiltInTendons.of(it.id) }.toSet()
        assertEquals(Tendon.entries.toSet(), covered)
        // The ball between the heels turns a calf raise into arch work for the posterior tibial tendon.
        val ballSqueeze = BuiltInExercises.exercises.single { it.name == "Heel Raise with Ball Squeeze" }
        assertTrue(Tendon.POSTERIOR_TIBIAL in BuiltInTendons.of(ballSqueeze.id))
        // Every grip for the finger tendons; the thumb and the neck and feet are not forgotten.
        val pocket = BuiltInExercises.exercises.single { it.name == "Two-Finger Pocket Hang" }
        assertEquals(listOf(Tendon.FINGER_FLEXORS), BuiltInTendons.of(pocket.id))
        val thumb = BuiltInExercises.exercises.single { it.name == "Thumb Band Extension" }
        assertEquals(listOf(Tendon.THUMB), BuiltInTendons.of(thumb.id))
        assertEquals(Muscle.NECK, BuiltInExercises.exercises.single { it.name == "Chin Tuck Hold" }.muscle)
        assertEquals("back", BuiltInExercises.exercises.single { it.name == "Chin Tuck Hold" }.regionKey)
        assertTrue(Muscle.FEET in BuiltInExercises.exercises.single { it.name == "Short Foot Hold" }.muscles)
    }

    @Test
    fun sportsHaveStrengthAndMobilityPlans() {
        val names = StarterPlans.plans.map { it.name }.toSet()
        listOf("Climbing", "Tennis").forEach { sport ->
            assertTrue("$sport strength" in names)
            assertTrue("$sport mobility" in names)
        }
        assertTrue("Swim dryland" in names)
        assertTrue("Swim mobility" in names)
        listOf("Kickboxing skills", "Kickboxing strength", "Kickboxing mobility", "Volleyball skills", "Volleyball prehab")
            .forEach { assertTrue(it, it in names) }
        // Landing from block jumps again and again is what wears the patellar tendon in volleyball.
        val block = BuiltInExercises.exercises.single { it.name == "Block Jump" }
        assertTrue(Tendon.PATELLAR in BuiltInTendons.of(block.id))
        val holds = StarterPlans.plans.single { it.name == "Tendon isometrics" }
        assertEquals(holds.exercises.toSet(), holds.setPlans.keys)
    }

    @Test
    fun retiredCategoriesAreNotSections() {
        val sections = BuiltInExercises.regions.map { it.key }.toSet()
        BuiltInExercises.retiredCategories.forEach {
            assertTrue(it.key !in sections)
            assertNotNull(BuiltInExercises.retired(it.id))
        }
        assertEquals(
            listOf("triceps", "biceps", "mobility", "stretching", "isometrics", "tendons", "plyometrics"),
            BuiltInExercises.retiredCategories.map { it.key },
        )
    }

    @Test
    fun idsAreUniqueAndStable() {
        val sectionIds = BuiltInExercises.regions.map { it.id }
        val exerciseIds = BuiltInExercises.exercises.map { it.id }
        assertEquals(sectionIds.size, sectionIds.toSet().size)
        assertEquals(exerciseIds.size, exerciseIds.toSet().size)

        // These ids are stored on users' devices, so they must never change between versions.
        assertEquals(
            "7211e2e6-ffde-3a55-96dc-bb98b77057c6",
            BuiltInExercises.exercises.single { it.name == "Flat Barbell Bench Press" }.id,
        )
        // "Abs" was renamed to "Core" but keeps its id.
        assertEquals(BuiltInExercises.stableId("category", "abs"), BuiltInExercises.regions.single { it.name == "Core" }.id)
        assertEquals("abs", BuiltInExercises.regionKeyOf(BuiltInExercises.stableId("category", "abs")))
    }

    @Test
    fun noEarlierBuiltInExerciseWasLost() {
        val names = BuiltInExercises.exercises.map { it.name }.toSet()
        EARLIER_NAMES.forEach { assertTrue("$it is missing", it in names) }
        assertTrue(BuiltInExercises.exercises.size > EARLIER_NAMES.size + 100)
    }

    private companion object {
        /** Every built-in exercise of version 0.1: their ids are on users' devices. */
        val EARLIER_NAMES = listOf(
            "Overhead Press", "Seated Dumbbell Press", "Arnold Dumbbell Press", "Push Press", "Lateral Dumbbell Raise",
            "Front Dumbbell Raise", "Rear Delt Dumbbell Raise", "Face Pull", "Upright Barbell Row",
            "Close Grip Barbell Bench Press", "Rope Push Down", "V-Bar Push Down", "Parallel Bar Triceps Dip",
            "Overhead Dumbbell Triceps Extension", "EZ-Bar Skullcrusher", "Barbell Curl", "Dumbbell Curl", "Hammer Curl",
            "Incline Dumbbell Curl", "EZ-Bar Preacher Curl", "Cable Curl", "Concentration Curl",
            "Flat Barbell Bench Press", "Flat Dumbbell Bench Press", "Incline Barbell Bench Press",
            "Incline Dumbbell Bench Press", "Decline Barbell Bench Press", "Machine Chest Press", "Flat Dumbbell Fly",
            "Cable Crossover", "Push Up", "Deadlift", "Barbell Row", "One-Arm Dumbbell Row", "T-Bar Row",
            "Seated Cable Row", "Lat Pulldown", "Pull Up", "Chin Up", "Rack Pull", "Back Extension", "Barbell Squat",
            "Front Squat", "Romanian Deadlift", "Leg Press", "Leg Extension Machine", "Seated Leg Curl Machine",
            "Lying Leg Curl Machine", "Bulgarian Split Squat", "Walking Lunge", "Barbell Hip Thrust",
            "Standing Calf Raise", "Seated Calf Raise", "Crunch", "Hanging Leg Raise", "Cable Crunch", "Ab Wheel Rollout",
            "Plank", "Side Plank", "Running", "Walking", "Cycling", "Rowing Machine", "Elliptical Trainer", "Swimming",
            "Stair Climber", "Jump Rope", "Hip CARs", "Shoulder CARs", "Thoracic Rotation", "Ankle Dorsiflexion Rocks",
            "90/90 Hip Switches", "Cat-Cow", "World's Greatest Stretch", "Deep Squat Hold", "Hamstring Stretch",
            "Hip Flexor Stretch", "Couch Stretch", "Pigeon Stretch", "Calf Stretch", "Doorway Chest Stretch",
            "Child's Pose", "Dead Hang", "Wall Sit", "Spanish Squat Hold", "Split Squat Hold", "Copenhagen Plank",
            "Isometric Mid-Thigh Pull", "Glute Bridge Hold", "Single-Leg Calf Raise Hold", "Hollow Body Hold",
            "Nordic Hamstring Curl", "Eccentric Heel Drop", "Decline Board Squat", "Heavy Slow Leg Press", "Tyler Twist",
            "Eccentric Wrist Extension", "Reverse Nordic Curl", "Tibialis Raise", "Box Jump", "Broad Jump", "Depth Jump",
            "Lateral Bound", "Pogo Hops", "Tuck Jump", "Skater Jump", "Medicine Ball Slam", "Tennis", "Table Tennis",
            "Volleyball", "Padel", "Badminton", "Squash", "Football", "Basketball", "Climbing", "Martial Arts",
        )
    }
}
