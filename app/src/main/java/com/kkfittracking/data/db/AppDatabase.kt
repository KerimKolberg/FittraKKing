package com.kkfittracking.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec

@Database(
    entities = [
        CategoryEntity::class,
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        BodyMeasurementEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        // v2 adds routines and body measurements (new tables only).
        AutoMigration(from = 1, to = 2),
        // v3 adds exercise tempo and per-side flag, and set effort (RPE).
        AutoMigration(from = 2, to = 3),
        // v4 adds drop sets and supersets.
        AutoMigration(from = 3, to = 4),
        // v5 adds the library levels (muscle, style), exercise plans and links, and plan supersets.
        AutoMigration(from = 4, to = 5, spec = Version5Migration::class),
        // v6 adds superset rounds and each exercise's part in them.
        AutoMigration(from = 5, to = 6),
        // v7 adds an exercise's own weight unit (kg, lb or machine levels).
        AutoMigration(from = 6, to = 7),
        // v8 adds the side (left or right) of one-sided sets.
        AutoMigration(from = 7, to = 8),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun routineDao(): RoutineDao

    abstract fun bodyDao(): BodyDao

    abstract fun backupDao(): BackupDao

    companion object {
        // Once the app is released, every schema change needs a Migration: never use destructive migrations.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitness.db").build()
    }
}

/**
 * Drop sets are now planned on the exercise, so the day's drop set plan columns of v4 go. The
 * sets themselves keep their drop set flag.
 */
@DeleteColumn.Entries(
    DeleteColumn(tableName = "workout_exercises", columnName = "dropSetMode"),
    DeleteColumn(tableName = "workout_exercises", columnName = "plannedSets"),
)
class Version5Migration : AutoMigrationSpec
