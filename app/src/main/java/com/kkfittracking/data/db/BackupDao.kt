package com.kkfittracking.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Reads and replaces every row, including soft-deleted ones, for backups. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM exercises") suspend fun exercises(): List<ExerciseEntity>
    @Query("SELECT * FROM workouts") suspend fun workouts(): List<WorkoutEntity>
    @Query("SELECT * FROM workout_exercises") suspend fun workoutExercises(): List<WorkoutExerciseEntity>
    @Query("SELECT * FROM workout_sets") suspend fun sets(): List<WorkoutSetEntity>
    @Query("SELECT * FROM routines") suspend fun routines(): List<RoutineEntity>
    @Query("SELECT * FROM routine_exercises") suspend fun routineExercises(): List<RoutineExerciseEntity>
    @Query("SELECT * FROM body_measurements") suspend fun bodyMeasurements(): List<BodyMeasurementEntity>
    @Query("SELECT * FROM tendon_pain") suspend fun tendonPain(): List<TendonPainEntity>

    // Children before parents, so foreign keys never point at a deleted row.
    @Query("DELETE FROM workout_sets") suspend fun deleteSets()
    @Query("DELETE FROM workout_exercises") suspend fun deleteWorkoutExercises()
    @Query("DELETE FROM routine_exercises") suspend fun deleteRoutineExercises()
    @Query("DELETE FROM workouts") suspend fun deleteWorkouts()
    @Query("DELETE FROM routines") suspend fun deleteRoutines()
    @Query("DELETE FROM body_measurements") suspend fun deleteBodyMeasurements()
    @Query("DELETE FROM tendon_pain") suspend fun deleteTendonPain()
    @Query("DELETE FROM exercises") suspend fun deleteExercises()
    @Query("DELETE FROM categories") suspend fun deleteCategories()

    @Insert suspend fun insertCategories(rows: List<CategoryEntity>)
    @Insert suspend fun insertExercises(rows: List<ExerciseEntity>)
    @Insert suspend fun insertWorkouts(rows: List<WorkoutEntity>)
    @Insert suspend fun insertWorkoutExercises(rows: List<WorkoutExerciseEntity>)
    @Insert suspend fun insertSets(rows: List<WorkoutSetEntity>)
    @Insert suspend fun insertRoutines(rows: List<RoutineEntity>)
    @Insert suspend fun insertRoutineExercises(rows: List<RoutineExerciseEntity>)
    @Insert suspend fun insertBodyMeasurements(rows: List<BodyMeasurementEntity>)
    @Insert suspend fun insertTendonPain(rows: List<TendonPainEntity>)
}
