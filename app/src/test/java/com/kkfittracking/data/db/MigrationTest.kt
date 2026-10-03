package com.kkfittracking.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kkfittracking.data.BodyRepository
import com.kkfittracking.data.ExerciseRepository
import com.kkfittracking.data.PainRepository
import com.kkfittracking.data.RoutineRepository
import com.kkfittracking.data.WorkoutRepository
import com.kkfittracking.model.PainMoment
import com.kkfittracking.model.Tendon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Builds a real database the way an older app version left it (from the exported schema files),
 * then opens it with the current app. Room runs the migrations and fails if the result does not
 * match the current schema exactly; the checks below make sure no data was lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"
    private val day = LocalDate.of(2026, 9, 1)
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(name)
    }

    private fun schemaFile(version: Int): File {
        val path = "schemas/com.kkfittracking.data.db.AppDatabase/$version.json"
        return listOf(File(path), File("app/$path")).first { it.exists() }
    }

    /** Creates the database exactly as schema [version] defines it, then lets [seed] add rows. */
    private fun createOldDatabase(version: Int, seed: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(name)
        val schema = Json.parseToJsonElement(schemaFile(version).readText()).jsonObject.getValue("database").jsonObject
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        schema.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            fun sql(template: String) = template.replace("\${TABLE_NAME}", table)
            db.execSQL(sql(entity.jsonObject.getValue("createSql").jsonPrimitive.content))
            entity.jsonObject["indices"]?.jsonArray?.forEach {
                db.execSQL(sql(it.jsonObject.getValue("createSql").jsonPrimitive.content))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.seed()
        db.version = version
        db.close()
    }

    /** Rows using only the version 1 columns, which every later version still has. */
    private fun SQLiteDatabase.seedVersion1Rows() {
        execSQL(
            "INSERT INTO categories (id, name, color, sortOrder, createdAt, updatedAt) " +
                "VALUES ('c', 'Chest', -1, 0, 1, 1)",
        )
        execSQL(
            "INSERT INTO exercises (id, name, categoryId, type, notes, isCustom, createdAt, updatedAt) " +
                "VALUES ('e', 'Bench', 'c', 'WEIGHT_REPS', '', 1, 1, 1)",
        )
        execSQL(
            "INSERT INTO workouts (id, date, comment, createdAt, updatedAt) " +
                "VALUES ('w', ${day.toEpochDay()}, '', 1, 1)",
        )
        execSQL(
            "INSERT INTO workout_exercises (id, workoutId, exerciseId, sortOrder, createdAt, updatedAt) " +
                "VALUES ('we', 'w', 'e', 0, 1, 1)",
        )
        execSQL(
            "INSERT INTO workout_sets (id, workoutExerciseId, sortOrder, weightKg, reps, comment, createdAt, updatedAt) " +
                "VALUES ('s', 'we', 0, 100.0, 5, '', 1, 1)",
        )
    }

    private fun openCurrent(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            .also { database = it }

    @Test
    fun fromVersion1() = runTest {
        createOldDatabase(1) { seedVersion1Rows() }
        val db = openCurrent()

        val logged = WorkoutRepository(db).observeDay(day).first().single()
        assertEquals("Bench", logged.exerciseName)
        val set = logged.sets.single().values
        assertEquals(100.0, set.weightKg!!, 0.0)
        assertEquals(5, set.reps)
        assertNull(set.rpe)

        val exercise = ExerciseRepository(db.exerciseDao()).getExercise("e")!!
        assertEquals("", exercise.tempo)
        assertFalse(exercise.perSide)
        assertEquals(0, RoutineRepository(db).routines.first().size)
    }

    @Test
    fun fromVersion2() = runTest {
        createOldDatabase(2) {
            seedVersion1Rows()
            execSQL("INSERT INTO routines VALUES ('r', 'Push', '', 1, 1, NULL)")
            execSQL("INSERT INTO routine_exercises VALUES ('re', 'r', 'e', 0, 1, 1, NULL)")
            execSQL("INSERT INTO body_measurements VALUES ('b', ${day.toEpochDay()}, 'BODYWEIGHT', 80.5, 1, 1, NULL)")
        }
        val db = openCurrent()

        assertEquals(listOf("e"), RoutineRepository(db).exerciseIds("r"))
        assertEquals(80.5, BodyRepository(db.bodyDao()).measurements.first().single().value, 0.0)
        assertEquals(5, WorkoutRepository(db).observeDay(day).first().single().sets.single().values.reps)
    }

    @Test
    fun fromVersion3() = runTest {
        createOldDatabase(3) {
            seedVersion1Rows()
            execSQL("UPDATE exercises SET tempo = '5-0-1-0', perSide = 1 WHERE id = 'e'")
            execSQL("UPDATE workout_sets SET rpe = 8 WHERE id = 's'")
        }
        val db = openCurrent()

        val logged = WorkoutRepository(db).observeDay(day).first().single()
        val set = logged.sets.single().values
        assertEquals(8, set.rpe)
        assertFalse(set.isDropSet)
        assertNull(logged.supersetId)
        assertEquals("5-0-1-0", ExerciseRepository(db.exerciseDao()).getExercise("e")!!.tempo)
    }

    @Test
    fun fromVersion8() = runTest {
        createOldDatabase(8) { seedVersion1Rows() }
        val db = openCurrent()

        // The workouts stay, and the new tendon pain log starts empty and takes ratings.
        assertEquals(1, WorkoutRepository(db).observeDay(day).first().single().sets.size)
        val pain = PainRepository(db.painDao())
        assertTrue(pain.entries.first().isEmpty())
        pain.rate(Tendon.PATELLAR, day, PainMoment.AFTER, 3)
        assertEquals(3, pain.entries.first().single().score)
    }

    @Test
    fun fromVersion7() = runTest {
        createOldDatabase(7) { seedVersion1Rows() }
        val db = openCurrent()

        // Sets logged before sides existed count as whole sets.
        assertNull(WorkoutRepository(db).observeDay(day).first().single().sets.single().values.side)
    }

    @Test
    fun fromVersion6() = runTest {
        createOldDatabase(6) { seedVersion1Rows() }
        val db = openCurrent()

        val exercise = ExerciseRepository(db.exerciseDao()).getExercise("e")!!
        // Exercises follow the app's unit until one is chosen for them.
        assertNull(exercise.weightUnits)
        assertNull(WorkoutRepository(db).observeDay(day).first().single().weightUnits)
    }

    @Test
    fun fromVersion5() = runTest {
        createOldDatabase(5) {
            seedVersion1Rows()
            execSQL("UPDATE workout_exercises SET supersetId = 'ss', roundRestSeconds = 90 WHERE id = 'we'")
            execSQL("UPDATE exercises SET muscles = 'CHEST,TRICEPS', style = 'STRENGTH' WHERE id = 'e'")
        }
        val db = openCurrent()

        val logged = WorkoutRepository(db).observeDay(day).first().single()
        assertEquals(90, logged.roundRestSeconds)
        assertNull(logged.supersetRounds)
        assertFalse(logged.supersetDropLast)
        assertNull(logged.memberDropSet)
        assertEquals(2, ExerciseRepository(db.exerciseDao()).getExercise("e")!!.muscles.size)
    }

    @Test
    fun fromVersion4() = runTest {
        createOldDatabase(4) {
            seedVersion1Rows()
            execSQL(
                "UPDATE workout_exercises SET supersetId = 'ss', transitionSeconds = 20, dropSetMode = 1, " +
                    "plannedSets = 4 WHERE id = 'we'",
            )
            execSQL("UPDATE workout_sets SET isDropSet = 1 WHERE id = 's'")
            execSQL("INSERT INTO routines (id, name, notes, createdAt, updatedAt) VALUES ('r', 'Push', '', 1, 1)")
            execSQL(
                "INSERT INTO routine_exercises (id, routineId, exerciseId, sortOrder, createdAt, updatedAt) " +
                    "VALUES ('re', 'r', 'e', 0, 1, 1)",
            )
        }
        val db = openCurrent()

        // The day's drop set plan columns are gone; everything else is kept.
        val logged = WorkoutRepository(db).observeDay(day).first().single()
        assertEquals("ss", logged.supersetId)
        assertEquals(20, logged.transitionSeconds)
        assertNull(logged.roundRestSeconds)
        assertTrue(logged.sets.single().values.isDropSet)

        val exercise = ExerciseRepository(db.exerciseDao()).getExercise("e")!!
        assertTrue(exercise.plan.isEmpty)
        assertTrue(exercise.links.isEmpty())
        val plan = RoutineRepository(db).routines.first().single()
        assertEquals(listOf("e"), plan.exercises.map { it.exerciseId })
        assertNull(plan.exercises.single().supersetId)
    }
}
