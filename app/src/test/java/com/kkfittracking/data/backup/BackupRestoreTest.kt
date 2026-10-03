package com.kkfittracking.data.backup

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kkfittracking.data.BodyRepository
import com.kkfittracking.data.BuiltInExercises
import com.kkfittracking.data.ExerciseRepository
import com.kkfittracking.data.PainRepository
import com.kkfittracking.data.RoutineRepository
import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.StarterPlans
import com.kkfittracking.data.WorkoutRepository
import com.kkfittracking.data.db.AppDatabase
import com.kkfittracking.model.ArrangedExercise
import com.kkfittracking.model.BodyMetric
import com.kkfittracking.model.ExerciseLink
import com.kkfittracking.model.ExercisePlan
import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.Muscle
import com.kkfittracking.model.PainEntry
import com.kkfittracking.model.PainMoment
import com.kkfittracking.model.SetValues
import com.kkfittracking.model.Tendon
import com.kkfittracking.model.TrainingStyle
import com.kkfittracking.model.UnitSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupRestoreTest {
    private val day = LocalDate.of(2026, 9, 25)
    private val bench = StarterPlans.exerciseId("Flat Barbell Bench Press")
    private val squat = StarterPlans.exerciseId("Barbell Squat")
    private val databases = mutableListOf<AppDatabase>()

    /** One phone: a database and its settings. */
    private inner class Phone {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build().also { databases += it }
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(
                produceFile = { Files.createTempDirectory("settings").resolve("test.preferences_pb").toFile() },
            ),
        )
        val exercises = ExerciseRepository(database.exerciseDao())
        val workouts = WorkoutRepository(database)
        val plans = RoutineRepository(database)
        val body = BodyRepository(database.bodyDao())
        val pain = PainRepository(database.painDao())
        val backups = BackupRepository(database, settings, "test")
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun backupAndRestoreOnAnotherPhone() = runTest {
        val old = Phone()
        old.exercises.syncBuiltIns()
        old.workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 5))
        val deleted = old.workouts.addSet(day, bench, SetValues(weightKg = 100.0, reps = 3))
        old.workouts.deleteSet(deleted)
        old.plans.addStarterPlans()
        old.body.saveMeasurement(BodyMetric.BODYWEIGHT, day, 80.0)
        old.workouts.addSet(day, squat, SetValues(weightKg = 140.0, reps = 3, isDropSet = true))
        val supersetId = old.workouts.createSuperset(day, listOf(bench, squat), transitionSeconds = 20)
        old.settings.setUnitSystem(UnitSystem.IMPERIAL)
        old.settings.setReminderDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        old.pain.rate(Tendon.ACHILLES, day, PainMoment.AFTER, 4)
        old.exercises.saveExercise(null, "My Custom Lift", old.exercises.categories.first().first().id,
            ExerciseType.WEIGHT_REPS, "", tempo = "4-0-1-0", perSide = true, styles = listOf(TrainingStyle.ECCENTRIC))
        old.exercises.savePlan(bench, ExercisePlan(sets = 1, dropSets = true, drops = 3))
        old.exercises.saveLinks(bench, listOf(ExerciseLink("https://youtu.be/abc")))
        val push = old.plans.routines.first().single { it.name == "Push" }
        old.plans.arrangePlan(
            push.exercises.mapIndexed { index, exercise ->
                ArrangedExercise(exercise.id, index, "push-superset".takeIf { index < 2 }, 25, 150)
            },
        )

        val text = BackupJson.encode(old.backups.createBackup())

        val new = Phone()
        new.exercises.syncBuiltIns()
        new.workouts.addSet(day, squat, SetValues(weightKg = 140.0, reps = 3))
        new.backups.restore(BackupJson.decode(text))

        val restoredDay = new.workouts.observeDay(day).first()
        assertEquals(listOf(bench, squat), restoredDay.map { it.exerciseId })
        assertEquals(listOf(5), restoredDay.first().sets.map { it.values.reps })
        assertTrue(restoredDay.last().sets.single().values.isDropSet)
        assertEquals(listOf(supersetId, supersetId), restoredDay.map { it.supersetId })
        assertEquals(20, restoredDay.first().transitionSeconds)
        assertEquals(StarterPlans.plans.size, new.plans.routines.first().size)
        assertEquals(80.0, new.body.measurements.first().single().value, 0.0)
        assertEquals(UnitSystem.IMPERIAL, new.settings.settings.first().unitSystem)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), new.settings.settings.first().reminderDays)
        assertEquals(PainEntry(new.pain.entries.first().single().id, day, Tendon.ACHILLES, PainMoment.AFTER, 4), new.pain.entries.first().single())
        val custom = new.exercises.exercises.first().single { it.name == "My Custom Lift" }
        assertEquals("4-0-1-0", custom.tempo)
        assertTrue(custom.perSide)
        assertEquals(TrainingStyle.ECCENTRIC, custom.style)
        val restoredBench = new.exercises.getExercise(bench)!!
        assertEquals(ExercisePlan(sets = 1, dropSets = true, drops = 3), restoredBench.plan)
        assertEquals(listOf(ExerciseLink("https://youtu.be/abc")), restoredBench.links)
        val restoredPush = new.plans.routines.first().single { it.name == "Push" }
        assertEquals(1, restoredPush.supersetCount)
        assertEquals(150, restoredPush.exercises.first().roundRestSeconds)
        // Deleted rows travel too, so a future sync still knows about the deletion.
        assertEquals(3, new.database.backupDao().sets().size)
    }

    /** A backup made before the library had sections, muscles and styles. */
    @Test
    fun anOlderBackupIsFiledUnderTheNewSections() = runTest {
        val phone = Phone()
        phone.exercises.syncBuiltIns()
        val nordic = StarterPlans.exerciseId("Nordic Hamstring Curl")
        phone.workouts.addSet(day, nordic, SetValues(reps = 5))
        val backup = phone.backups.createBackup()
        val tendons = CategoryDto(BuiltInExercises.stableId("category", "tendons"), "Tendons & eccentrics", 0, 11, 1, 1)
        val older = backup.copy(
            categories = backup.categories + tendons,
            exercises = backup.exercises.map {
                if (it.id == nordic) it.copy(categoryId = tendons.id, muscles = "", style = "") else it
            },
        )

        val newPhone = Phone()
        newPhone.backups.restore(older)
        // What restoring from the settings screen does after the restore.
        newPhone.exercises.syncBuiltIns()

        val filed = newPhone.exercises.getExercise(nordic)!!
        assertEquals(BuiltInExercises.stableId("category", "legs"), filed.categoryId)
        assertEquals(Muscle.HAMSTRINGS, filed.muscle)
        assertTrue(newPhone.exercises.categories.first().none { it.id == tendons.id })
        assertEquals(listOf(5), newPhone.workouts.observeDay(day).first().single().sets.map { it.values.reps })
    }

    @Test
    fun aDamagedBackupChangesNothing() = runTest {
        val phone = Phone()
        phone.exercises.syncBuiltIns()
        phone.workouts.addSet(day, squat, SetValues(weightKg = 140.0, reps = 3))
        val backup = phone.backups.createBackup()

        // Two categories with the same id cannot both be stored.
        val damaged = backup.copy(categories = backup.categories + backup.categories.first())
        assertThrows(BackupException::class.java) { runBlocking { phone.backups.restore(damaged) } }
        assertEquals(listOf(squat), phone.workouts.observeDay(day).first().map { it.exerciseId })

        val unknownType = backup.copy(exercises = backup.exercises.map { it.copy(type = "FLYING") })
        assertThrows(BackupException::class.java) { runBlocking { phone.backups.restore(unknownType) } }
        assertEquals(listOf(squat), phone.workouts.observeDay(day).first().map { it.exerciseId })
    }
}
