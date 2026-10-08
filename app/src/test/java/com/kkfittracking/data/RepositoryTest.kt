package com.kkfittracking.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kkfittracking.data.db.AppDatabase
import com.kkfittracking.data.db.CategoryEntity
import com.kkfittracking.data.db.ExerciseEntity
import com.kkfittracking.guide.GuidedWorkout
import com.kkfittracking.model.ActivityKind
import com.kkfittracking.model.ArrangedExercise
import com.kkfittracking.model.BodyMetric
import com.kkfittracking.model.ExerciseLink
import com.kkfittracking.model.ExercisePlan
import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.Muscle
import com.kkfittracking.model.PlannedExercise
import com.kkfittracking.model.SetValues
import com.kkfittracking.model.Side
import com.kkfittracking.model.TrainingStyle
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.dayWorkouts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.time.LocalDate

/** Runs the repositories against a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var routines: RoutineRepository
    private lateinit var body: BodyRepository
    private var clock = 1_000L

    private val bench = BuiltInExercises.stableId("exercise", "flat-barbell-bench-press")
    private val squat = BuiltInExercises.stableId("exercise", "barbell-squat")
    private val day = LocalDate.of(2026, 9, 24)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        exercises = ExerciseRepository(database.exerciseDao(), now = { clock++ })
        workouts = WorkoutRepository(database, now = { clock++ })
        routines = RoutineRepository(database, now = { clock++ })
        body = BodyRepository(database.bodyDao(), now = { clock++ })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun builtInsAreAddedOnceAndKeepUserChanges() = runTest {
        exercises.syncBuiltIns()
        val total = BuiltInExercises.exercises.size
        assertEquals(total, exercises.exercises.first().size)
        assertEquals(BuiltInExercises.regions.map { it.name }, exercises.categories.first().map { it.name })
        // Built-ins arrive filed under their muscle and style, HIIT with its timings.
        val nordic = exercises.getExercise(StarterPlans.exerciseId("Nordic Hamstring Curl"))!!
        assertEquals(Muscle.HAMSTRINGS, nordic.muscle)
        assertEquals(TrainingStyle.ECCENTRIC, nordic.style)
        assertEquals(8, exercises.getExercise(StarterPlans.exerciseId("Tabata"))!!.plan.rounds)

        exercises.saveExercise(bench, "Bench Press", exercises.getExercise(bench)!!.categoryId, ExerciseType.WEIGHT_REPS, "")
        exercises.deleteExercise(squat)
        exercises.syncBuiltIns()

        val list = exercises.exercises.first()
        assertEquals(total - 1, list.size)
        assertEquals("Bench Press", list.single { it.id == bench }.name)
    }

    @Test
    fun customExercisesCanBeCreated() = runTest {
        exercises.syncBuiltIns()
        val categoryId = exercises.categories.first().first().id
        val id = exercises.saveExercise(null, "  Landmine Press ", categoryId, ExerciseType.WEIGHT_REPS, "")
        val saved = exercises.getExercise(id)
        assertNotNull(saved)
        assertEquals("Landmine Press", saved!!.name)
        assertTrue(saved.isCustom)
    }

    @Test
    fun setsOfOneExerciseShareOneDayEntry() = runTest {
        exercises.syncBuiltIns()
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        workouts.addSet(day, squat, SetValues(weightKg = 140.0, reps = 3))
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 4))

        val logged = workouts.observeDay(day).first()
        assertEquals(listOf(bench, squat), logged.map { it.exerciseId })
        assertEquals(listOf(5, 4), logged[0].sets.map { it.values.reps })
        assertEquals("Flat Barbell Bench Press", logged[0].exerciseName)
    }

    @Test
    fun updatingASet() = runTest {
        exercises.syncBuiltIns()
        val setId = workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        workouts.updateSet(setId, SetValues(weightKg = 102.5, reps = 5))
        assertEquals(102.5, workouts.observeDay(day).first()[0].sets[0].values.weightKg!!, 0.0)
    }

    @Test
    fun deletingTheLastSetRemovesTheExerciseFromTheDay() = runTest {
        exercises.syncBuiltIns()
        val first = workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        val second = workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))

        workouts.deleteSet(first)
        assertEquals(1, workouts.observeDay(day).first()[0].sets.size)

        workouts.deleteSet(second)
        assertTrue(workouts.observeDay(day).first().isEmpty())
        assertTrue(workouts.observeWorkoutDates(day, day).first().isEmpty())

        // Logging again after that starts a fresh entry.
        workouts.addSet(day, bench, SetValues(weightKg = 90.0, reps = 8))
        assertEquals(listOf(8), workouts.observeDay(day).first().single().sets.map { it.values.reps })
    }

    @Test
    fun removingAnExerciseFromTheDay() = runTest {
        exercises.syncBuiltIns()
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        workouts.addSet(day, squat, SetValues(weightKg = 140.0, reps = 3))
        val benchEntry = workouts.observeDay(day).first().first { it.exerciseId == bench }

        workouts.deleteWorkoutExercise(benchEntry.workoutExerciseId)

        assertEquals(listOf(squat), workouts.observeDay(day).first().map { it.exerciseId })
        assertTrue(workouts.observeHistory(bench).first().isEmpty())
    }

    @Test
    fun historyIsGroupedByDateNewestFirst() = runTest {
        exercises.syncBuiltIns()
        val earlier = day.minusDays(3)
        workouts.addSet(earlier, bench, SetValues(weightKg = 95.0, reps = 5))
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 4))

        val history = workouts.observeHistory(bench).first()
        assertEquals(listOf(day, earlier), history.map { it.date })
        assertEquals(listOf(5, 4), history[0].sets.map { it.values.reps })

        val dates = workouts.observeWorkoutDates(day.minusDays(30), day).first()
        assertEquals(setOf(day, earlier), dates)
    }

    @Test
    fun deletedExercisesStillShowInPastWorkouts() = runTest {
        exercises.syncBuiltIns()
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        exercises.deleteExercise(bench)

        assertEquals(listOf(bench), workouts.observeDay(day).first().map { it.exerciseId })
        assertTrue(exercises.exercises.first().none { it.id == bench })
    }

    @Test
    fun routinesKeepTheirOrder() = runTest {
        exercises.syncBuiltIns()
        val row = BuiltInExercises.stableId("exercise", "barbell-row")
        val id = routines.createRoutine("Full body")
        routines.addExercise(id, squat)
        routines.addExercise(id, bench)
        routines.addExercise(id, row)

        val routine = routines.observeRoutine(id).first()!!
        assertEquals(listOf(squat, bench, row), routine.exercises.map { it.exerciseId })

        routines.moveInPlan(id, 2, -1)
        assertEquals(listOf(squat, row, bench), routines.exerciseIds(id))

        routines.removeExercise(routine.exercises[0].id)
        assertEquals(listOf(row, bench), routines.exerciseIds(id))

        routines.deleteRoutine(id)
        assertTrue(routines.routines.first().isEmpty())
    }

    @Test
    fun plannedExercisesAppearWithoutSetsAndAreReusedWhenLogging() = runTest {
        exercises.syncBuiltIns()
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        workouts.addExercisesToDay(day, listOf(bench, squat, squat))

        val planned = workouts.observeDay(day).first()
        assertEquals(listOf(bench, squat), planned.map { it.exerciseId })
        assertTrue(planned[1].sets.isEmpty())
        // A day with only planned exercises is not a workout day on the calendar.
        val tomorrow = day.plusDays(1)
        workouts.addExercisesToDay(tomorrow, listOf(bench))
        assertEquals(1, workouts.observeDay(tomorrow).first().size)
        assertTrue(workouts.observeWorkoutDates(tomorrow, tomorrow).first().isEmpty())

        workouts.addSet(day, squat, SetValues(weightKg = 140.0, reps = 3))
        val logged = workouts.observeDay(day).first()
        assertEquals(2, logged.size)
        assertEquals(1, logged[1].sets.size)
    }

    @Test
    fun oneBodyMeasurementPerMetricAndDay() = runTest {
        body.saveMeasurement(BodyMetric.BODYWEIGHT, day.minusDays(7), 81.0)
        body.saveMeasurement(BodyMetric.BODYWEIGHT, day, 80.5)
        body.saveMeasurement(BodyMetric.BODYWEIGHT, day, 80.0)
        body.saveMeasurement(BodyMetric.WAIST, day, 84.0)

        val all = body.measurements.first()
        val weights = all.filter { it.metric == BodyMetric.BODYWEIGHT }
        assertEquals(listOf(day, day.minusDays(7)), weights.map { it.date })
        assertEquals(80.0, weights.first().value, 0.0)

        body.deleteMeasurement(weights.first().id)
        assertEquals(listOf(81.0), body.measurements.first().filter { it.metric == BodyMetric.BODYWEIGHT }.map { it.value })
    }

    @Test
    fun starterPlansAreAddedOnceAndShareExercises() = runTest {
        exercises.syncBuiltIns()
        assertEquals(StarterPlans.plans.size, routines.addStarterPlans())
        assertEquals(0, routines.addStarterPlans())

        val plans = routines.routines.first().associateBy { it.name }
        val incline = StarterPlans.exerciseId("Incline Barbell Bench Press")
        assertTrue(plans.getValue("Push").exercises.any { it.exerciseId == incline })

        // The same exercise can be in several plans, but only once per plan.
        val upper = plans.getValue("Upper body")
        routines.addExercise(upper.id, incline)
        routines.addExercise(upper.id, incline)
        assertEquals(1, routines.exerciseIds(upper.id).count { it == incline })
        routines.removeExerciseFromRoutine(upper.id, incline)
        assertTrue(incline !in routines.exerciseIds(upper.id))
        assertTrue(incline in routines.exerciseIds(plans.getValue("Push").id))

        // A plan in blocks: each block is a superset of 3 rounds, and the exercises get sets, reps and weights.
        val lower = plans.getValue("KK Lower body")
        assertEquals(5, lower.blocks.size)
        assertTrue(lower.exercises.filter { it.supersetId != null }.all { it.supersetRounds == 3 })
        val drop = exercises.getExercise(StarterPlans.exerciseId("Slow Single-Leg Pelvic Drop"))!!
        assertEquals(ExercisePlan(sets = 3, reps = 8, weightKg = 36.0), drop.plan)
    }

    @Test
    fun gameStatsFollowTheLog() = runTest {
        exercises.syncBuiltIns()
        val settingsFile = Files.createTempDirectory("settings").resolve("test.preferences_pb").toFile()
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(produceFile = { settingsFile }))
        val game = GameRepository(database.workoutDao(), settings)
        assertEquals(0, game.stats.first().xp)

        workouts.addSet(LocalDate.now(), StarterPlans.exerciseId("Box Jump"), SetValues(reps = 5, distanceMeters = 0.6))
        val stats = game.stats.first()
        assertEquals(1, stats.totalWorkouts)
        assertTrue(stats.xp > 0)
    }

    @Test
    fun supersetsGroupExercisesOnADay() = runTest {
        exercises.syncBuiltIns()
        val row = BuiltInExercises.stableId("exercise", "barbell-row")
        // Bench is already on the day with a set; squat and row are added by the superset.
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        val supersetId = workouts.createSuperset(day, listOf(bench, squat, row))

        val logged = workouts.observeDay(day).first()
        assertEquals(listOf(bench, squat, row), logged.map { it.exerciseId })
        assertTrue(logged.all { it.supersetId == supersetId })
        assertEquals(1, logged.first().sets.size)

        workouts.ungroupSuperset(supersetId)
        assertTrue(workouts.observeDay(day).first().all { it.supersetId == null })
    }

    @Test
    fun dropSetsAreStored() = runTest {
        exercises.syncBuiltIns()
        workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 8))
        val drop = workouts.addSet(day, bench, SetValues(weightKg = 80.0, reps = 6, isDropSet = true))
        val sets = workouts.observeDay(day).first().single().sets
        assertEquals(listOf(false, true), sets.map { it.values.isDropSet })

        workouts.updateSet(drop, SetValues(weightKg = 75.0, reps = 6, isDropSet = true))
        assertTrue(workouts.observeHistory(bench).first().single().sets.last().values.isDropSet)
    }

    @Test
    fun arrangingADay() = runTest {
        exercises.syncBuiltIns()
        val row = BuiltInExercises.stableId("exercise", "barbell-row")
        workouts.addExercisesToDay(day, listOf(bench, squat, row))
        val entries = workouts.observeDay(day).first()

        workouts.arrangeDay(
            listOf(
                ArrangedExercise(entries[2].workoutExerciseId, 0, null, null, null),
                ArrangedExercise(entries[0].workoutExerciseId, 1, "s", 15, 120),
                ArrangedExercise(entries[1].workoutExerciseId, 2, "s", 15, 120),
            ),
        )

        val arranged = workouts.observeDay(day).first()
        assertEquals(listOf(row, bench, squat), arranged.map { it.exerciseId })
        assertEquals(listOf(null, "s", "s"), arranged.map { it.supersetId })
        assertEquals(15, arranged[1].transitionSeconds)
        assertEquals(120, arranged[2].roundRestSeconds)

        workouts.ungroupSuperset("s")
        assertTrue(workouts.observeDay(day).first().all { it.supersetId == null && it.roundRestSeconds == null })
    }

    /** A plan arranged with "Superset edit", then added to a day on a day with extra energy, and on a normal day. */
    @Test
    fun planSupersetsComeAlongToTheDayWhenChosen() = runTest {
        exercises.syncBuiltIns()
        val row = StarterPlans.exerciseId("Barbell Row")
        val curl = StarterPlans.exerciseId("Barbell Curl")
        val plan = routines.createRoutine("Upper")
        listOf(bench, row, curl, squat).forEach { routines.addExercise(plan, it) }
        val entries = routines.observeRoutine(plan).first()!!.exercises
        routines.arrangePlan(
            listOf(
                ArrangedExercise(entries[0].id, 0, null, null, null),
                ArrangedExercise(entries[1].id, 1, "plan-superset", 20, 120),
                ArrangedExercise(entries[2].id, 2, "plan-superset", 20, 120),
                ArrangedExercise(entries[3].id, 3, null, null, null),
            ),
        )
        assertEquals(1, routines.observeRoutine(plan).first()!!.supersetCount)

        workouts.addPlannedExercises(day, routines.plannedExercises(plan), withSupersets = true)
        val withSupersets = workouts.observeDay(day).first()
        assertEquals(listOf(bench, row, curl, squat), withSupersets.map { it.exerciseId })
        val daySuperset = withSupersets[1].supersetId
        assertNotNull(daySuperset)
        // The day gets its own superset id, so ungrouping it there leaves the plan alone.
        assertNotEquals("plan-superset", daySuperset)
        assertEquals(daySuperset, withSupersets[2].supersetId)
        assertEquals(20, withSupersets[2].transitionSeconds)
        assertEquals(120, withSupersets[1].roundRestSeconds)
        assertNull(withSupersets[0].supersetId)

        val normalDay = day.plusDays(1)
        workouts.addPlannedExercises(normalDay, routines.plannedExercises(plan), withSupersets = false)
        assertTrue(workouts.observeDay(normalDay).first().all { it.supersetId == null })
    }

    @Test
    fun plansMoveSupersetsAsAWholeAndDropLonelySupersets() = runTest {
        exercises.syncBuiltIns()
        val row = StarterPlans.exerciseId("Barbell Row")
        val curl = StarterPlans.exerciseId("Barbell Curl")
        val plan = routines.createRoutine("Upper")
        listOf(bench, row, curl, squat).forEach { routines.addExercise(plan, it) }
        val entries = routines.observeRoutine(plan).first()!!.exercises
        routines.arrangePlan(
            entries.mapIndexed { index, entry ->
                val inSuperset = index == 1 || index == 2
                ArrangedExercise(entry.id, index, "s".takeIf { inSuperset }, null, null)
            },
        )

        // The blocks are bench, the superset, squat: moving bench down puts it after the superset.
        routines.moveInPlan(plan, 0, 1)
        assertEquals(listOf(row, curl, bench, squat), routines.exerciseIds(plan))

        // Removing one exercise of a superset of two leaves an exercise on its own.
        routines.removeExercise(entries[1].id)
        assertTrue(routines.observeRoutine(plan).first()!!.exercises.all { it.supersetId == null })
    }

    @Test
    fun starterPlansCanHaveSupersets() = runTest {
        exercises.syncBuiltIns()
        routines.addStarterPlans()
        val arms = routines.routines.first().single { it.name == "Arms supersets" }
        assertEquals(2, arms.supersetCount)
        // Two supersets of two, then the last exercise on its own.
        assertEquals(4, arms.exercises.count { it.supersetId != null })
        assertNull(arms.exercises.last().supersetId)
    }

    @Test
    fun setPlansDescriptionsAndLinksAreSaved() = runTest {
        exercises.syncBuiltIns()
        val plan = ExercisePlan(sets = 3, reps = 8, restSeconds = 150, dropSets = true, drops = 2, dropPercent = 25)
        exercises.savePlan(bench, plan)
        exercises.saveNotes(bench, "  Feet flat, shoulder blades back. ")
        val link = ExerciseLink("https://www.youtube.com/watch?v=abc", "Bench setup")
        exercises.saveLinks(bench, listOf(link))

        val saved = exercises.getExercise(bench)!!
        assertEquals(plan, saved.plan)
        assertEquals("Feet flat, shoulder blades back.", saved.notes)
        assertEquals(listOf(link), saved.links)

        // Editing the exercise keeps its plan; the muscle and style can be changed.
        exercises.saveExercise(bench, "Bench", saved.categoryId, ExerciseType.WEIGHT_REPS, saved.notes, muscles = listOf(Muscle.OTHER, Muscle.TRICEPS), styles = listOf(TrainingStyle.ECCENTRIC))
        val edited = exercises.getExercise(bench)!!
        assertEquals(plan, edited.plan)
        assertEquals(listOf(link), edited.links)
        assertEquals(TrainingStyle.ECCENTRIC, edited.style)
        assertEquals(listOf(Muscle.OTHER, Muscle.TRICEPS), edited.muscles)
    }

    /** An install from before the library had sections, muscles and styles. */
    @Test
    fun oldCategoriesAreFiledUnderTheNewSections() = runTest {
        val dao = database.exerciseDao()
        fun category(key: String, name: String, order: Int) =
            CategoryEntity(BuiltInExercises.stableId("category", key), name, 0, order, 1, 1)
        fun exercise(id: String, name: String, categoryKey: String, custom: Boolean = false) = ExerciseEntity(
            id = id, name = name, categoryId = BuiltInExercises.stableId("category", categoryKey),
            type = ExerciseType.REPS, notes = "", isCustom = custom, createdAt = 1, updatedAt = 1,
        )
        val nordic = StarterPlans.exerciseId("Nordic Hamstring Curl")
        val plank = StarterPlans.exerciseId("Plank")
        val hipCars = StarterPlans.exerciseId("Hip CARs")
        dao.insertCategoriesIfMissing(
            listOf(category("abs", "Abs", 6), category("tendons", "Tendons & eccentrics", 11), category("mobility", "Mobility", 8)),
        )
        dao.insertExercisesIfMissing(
            listOf(
                exercise(nordic, "Nordic Hamstring Curl", "tendons"),
                exercise(plank, "Plank", "abs"),
                exercise(hipCars, "Hip CARs", "mobility").copy(deletedAt = 5),
                exercise("custom-1", "Cossack Squat", "mobility", custom = true),
            ),
        )
        workouts.addSet(day, nordic, SetValues(reps = 5))

        exercises.syncBuiltIns()

        val movedNordic = exercises.getExercise(nordic)!!
        assertEquals(BuiltInExercises.stableId("category", "legs"), movedNordic.categoryId)
        assertEquals(Muscle.HAMSTRINGS, movedNordic.muscle)
        // Built-ins arrive with every muscle they train.
        val deadlift = exercises.getExercise(StarterPlans.exerciseId("Deadlift"))!!
        assertEquals(listOf(Muscle.LOWER_BACK, Muscle.HAMSTRINGS, Muscle.GLUTES), deadlift.muscles.take(3))
        assertEquals(TrainingStyle.ECCENTRIC, movedNordic.style)
        val filedPlank = exercises.getExercise(plank)!!
        assertEquals(BuiltInExercises.stableId("category", "abs"), filedPlank.categoryId)
        assertEquals(TrainingStyle.ISOMETRIC, filedPlank.style)
        val cossack = exercises.getExercise("custom-1")!!
        assertEquals(BuiltInExercises.stableId("category", "full-body"), cossack.categoryId)
        assertEquals(TrainingStyle.MOBILITY, cossack.style)
        // A built-in the user deleted is filed too, but stays deleted.
        assertEquals(BuiltInExercises.stableId("category", "legs"), exercises.getExercise(hipCars)!!.categoryId)
        assertTrue(exercises.exercises.first().none { it.id == hipCars })

        // "Abs" is now "Core"; the old categories are gone from the library.
        assertEquals(BuiltInExercises.regions.map { it.name }, exercises.categories.first().map { it.name })
        // Every built-in but the deleted one, plus the user's own exercise.
        assertEquals(BuiltInExercises.exercises.size - 1 + 1, exercises.exercises.first().size)
        // Logged workouts are untouched.
        assertEquals(listOf(5), workouts.observeDay(day).first().single().sets.map { it.values.reps })

        // Running it again changes nothing.
        val before = database.backupDao().exercises().associate { it.id to it.updatedAt }
        exercises.syncBuiltIns()
        assertEquals(before, database.backupDao().exercises().associate { it.id to it.updatedAt })
    }

    @Test
    fun aDaysExercisesCanBeAddedWithTheirSupersets() = runTest {
        exercises.syncBuiltIns()
        workouts.addPlannedExercises(
            day,
            listOf(PlannedExercise(bench, "a", 10, 60), PlannedExercise(squat, "a", 10, 60), PlannedExercise(bench, "a")),
            withSupersets = true,
        )
        val logged = workouts.observeDay(day).first()
        assertEquals(listOf(bench, squat), logged.map { it.exerciseId })
        assertEquals(logged[0].supersetId, logged[1].supersetId)
        assertEquals(60, logged[0].roundRestSeconds)
    }

    /** An install of version 0.2: HIIT in Sports, Child's Pose only a stretch. */
    @Test
    fun laterCatalogChangesReachEarlierInstalls() = runTest {
        exercises.syncBuiltIns()
        val tabata = StarterPlans.exerciseId("Tabata")
        val childsPose = StarterPlans.exerciseId("Child's Pose")
        val sports = BuiltInExercises.stableId("category", "sports")
        val dao = database.exerciseDao()
        dao.updateExercises(
            listOf(
                dao.getExercise(tabata)!!.copy(categoryId = sports, muscles = "SPORT"),
                dao.getExercise(childsPose)!!.copy(style = "STRETCHING"),
            ),
        )

        exercises.syncBuiltIns()

        val moved = exercises.getExercise(tabata)!!
        assertEquals(BuiltInExercises.stableId("category", "cardio"), moved.categoryId)
        assertEquals(Muscle.CARDIO, moved.muscle)
        assertEquals(listOf(TrainingStyle.STRETCHING, TrainingStyle.YOGA), exercises.getExercise(childsPose)!!.styles)
    }

    @Test
    fun supersetRoundsAreSavedAndComeAlongFromPlans() = runTest {
        exercises.syncBuiltIns()
        val row = StarterPlans.exerciseId("Barbell Row")
        val plan = routines.createRoutine("Upper")
        listOf(bench, row).forEach { routines.addExercise(plan, it) }
        val entries = routines.observeRoutine(plan).first()!!.exercises
        routines.arrangePlan(
            listOf(
                ArrangedExercise(entries[0].id, 0, "s", 20, 120, supersetRounds = 3, supersetDropLast = false, memberDropSet = true),
                ArrangedExercise(entries[1].id, 1, "s", 20, 120, supersetRounds = 3, memberRounds = 1),
            ),
        )
        workouts.addPlannedExercises(day, routines.plannedExercises(plan), withSupersets = true)
        val logged = workouts.observeDay(day).first()
        assertEquals(listOf(3, 3), logged.map { it.supersetRounds })
        assertEquals(true, logged[0].memberDropSet)
        assertEquals(1, logged[1].memberRounds)

        workouts.deleteWorkoutExercises(logged.map { it.workoutExerciseId })
        assertTrue(workouts.observeDay(day).first().isEmpty())
    }

    @Test
    fun theGuideFollowsTheDaysPlan() = runTest {
        exercises.syncBuiltIns()
        exercises.savePlan(bench, ExercisePlan(sets = 2))
        exercises.savePlan(squat, ExercisePlan(sets = 1))
        workouts.addPlannedExercises(day, listOf(PlannedExercise(bench), PlannedExercise(squat)), withSupersets = false)
        val settingsFile = Files.createTempDirectory("settings").resolve("test.preferences_pb").toFile()
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(produceFile = { settingsFile }))
        var notifications = 0
        val guide = GuidedWorkout(backgroundScope, workouts, settings, onStarted = { notifications++ }, now = { clock })

        assertEquals(bench, guide.start(day)?.exerciseId)
        assertEquals(1, notifications)
        workouts.addSet(day, bench, SetValues(100.0, 5))
        assertEquals("Set 2 of 2", guide.afterSetLogged(day)?.step)
        workouts.addSet(day, bench, SetValues(100.0, 5))
        // The bench's sets are done: on to the squat.
        assertEquals(squat, guide.afterSetLogged(day)?.exerciseId)
        // Sets on another day do not move this workout.
        assertNull(guide.afterSetLogged(day.plusDays(1)))

        val state = guide.state.first { it.completion.percent == 67 }
        assertEquals(listOf(squat), state.completion.notStarted.map { it.exerciseId })
        guide.stop()
        val summary = guide.summary.value
        assertNotNull(summary)
        assertTrue(summary!!.stoppedEarly)
        assertEquals(67, summary.completion.percent)
        assertEquals(false, guide.isRunning)
    }

    @Test
    fun theRestOfADayMovesToAnotherDay() = runTest {
        exercises.syncBuiltIns()
        exercises.savePlan(bench, ExercisePlan(sets = 3))
        exercises.savePlan(squat, ExercisePlan(sets = 3))
        workouts.addPlannedExercises(day, listOf(PlannedExercise(bench), PlannedExercise(squat)), withSupersets = false)
        workouts.addSet(day, bench, SetValues(100.0, 5))

        assertEquals(2, workouts.moveUnfinished(day, day.plusDays(1)))
        // The bench keeps its set here; the squat, not started, left the day.
        assertEquals(listOf(bench), workouts.observeDay(day).first().map { it.exerciseId })
        assertEquals(listOf(bench, squat), workouts.observeDay(day.plusDays(1)).first().map { it.exerciseId })
        assertEquals(0, workouts.moveUnfinished(day, day))
    }

    @Test
    fun aDaysWorkoutsComeFromWhenTheSetsWereLogged() = runTest {
        exercises.syncBuiltIns()
        clock = 1_800_000_000_000L
        workouts.addSet(day, bench, SetValues(80.0, 8))
        clock += 3 * 60_000L
        workouts.addSet(day, bench, SetValues(80.0, 8))

        val logged = workouts.observeDay(day).first().single()
        assertEquals(TrainingStyle.STRENGTH, logged.styles.first())
        assertTrue(logged.sets.all { it.loggedAtMillis != null })
        val session = dayWorkouts(listOf(logged), 80.0, UnitSystem.METRIC, clock).sessions.single()
        assertEquals(ActivityKind.STRENGTH, session.kind)
        assertEquals(5, session.minutes)
    }

    @Test
    fun aSideIsKeptOnlyWhileTheExerciseIsOneSided() = runTest {
        exercises.syncBuiltIns()
        val curl = exercises.getExercise(bench)!!
        // One-sided: the side is kept.
        exercises.saveExercise(bench, curl.name, curl.categoryId, curl.type, curl.notes, perSide = true)
        workouts.addSet(day, bench, SetValues(20.0, 10, side = Side.LEFT))
        // Left and right turned off in the middle of the workout: the next set has no side, even
        // when the screen (or the watch) still sends one.
        exercises.saveExercise(bench, curl.name, curl.categoryId, curl.type, curl.notes, perSide = false)
        val second = workouts.addSet(day, bench, SetValues(20.0, 10, side = Side.RIGHT))
        workouts.updateSet(second, SetValues(22.5, 10, side = Side.LEFT))
        val sets = workouts.observeDay(day).first().single().sets
        assertEquals(listOf(Side.LEFT, null), sets.map { it.values.side })
        assertEquals(22.5, sets.last().values.weightKg!!, 0.0)
    }

    @Test
    fun thePlansWeightFollowsTheWeightTypedInLast() = runTest {
        exercises.syncBuiltIns()
        exercises.savePlan(bench, ExercisePlan(sets = 3, reps = 8, weightKg = 25.0))
        workouts.addSet(day, bench, SetValues(27.5, 8))
        assertEquals(27.5, exercises.getExercise(bench)!!.plan.weightKg!!, 0.0)
        // A drop set does not change it, a heavier set does.
        workouts.addSet(day, bench, SetValues(20.0, 8, isDropSet = true))
        val heavy = workouts.addSet(day, bench, SetValues(30.0, 6))
        assertEquals(30.0, exercises.getExercise(bench)!!.plan.weightKg!!, 0.0)
        // A mistyped set deleted: back to the weight of the set before it.
        workouts.deleteSet(heavy)
        assertEquals(27.5, exercises.getExercise(bench)!!.plan.weightKg!!, 0.0)
        // Fixing a set on an older day leaves the plan alone.
        val older = workouts.addSet(day.minusDays(7), bench, SetValues(22.5, 8))
        workouts.updateSet(older, SetValues(23.0, 8))
        assertEquals(27.5, exercises.getExercise(bench)!!.plan.weightKg!!, 0.0)
        // A plan without a weight keeps planning none.
        exercises.savePlan(squat, ExercisePlan(sets = 3, reps = 5))
        workouts.addSet(day, squat, SetValues(100.0, 5))
        assertNull(exercises.getExercise(squat)!!.plan.weightKg)
    }
}
