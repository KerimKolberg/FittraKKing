package com.kkfittracking.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kkfittracking.model.ExerciseType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** An exercise's entry on a day: which exercise, and the day. */
data class EntryDay(val exerciseId: String, val date: LocalDate)

/** One row per set (or per exercise without sets) logged on a day. */
data class DayRow(
    val workoutExerciseId: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseType: ExerciseType,
    val categoryColor: Int,
    val setId: String?,
    val weightKg: Double?,
    val reps: Int?,
    val distanceMeters: Double?,
    val durationSeconds: Int?,
    val rpe: Int?,
    val comment: String?,
    val isDropSet: Boolean?,
    val side: String?,
    val supersetId: String?,
    val transitionSeconds: Int?,
    val roundRestSeconds: Int?,
    val supersetRounds: Int?,
    val supersetDropLast: Boolean,
    val memberRounds: Int?,
    val memberDropSet: Boolean?,
    val exercisePlan: String,
    val exerciseWeightUnit: String,
    val exercisePerSide: Boolean,
    val exerciseStyle: String,
    val exerciseCategoryId: String,
    val setCreatedAt: Long?,
)

/** Every logged set with its date and exercise details, for the game stats. */
data class LoggedSetRow(
    val date: LocalDate,
    val exerciseId: String,
    val categoryId: String,
    val exerciseType: ExerciseType,
    /** The stored training style name, possibly empty. */
    val exerciseStyle: String,
    @Embedded val set: WorkoutSetEntity,
)

/** A logged set with names, for CSV export. */
data class ExportRow(
    val date: LocalDate,
    val exerciseName: String,
    val categoryName: String,
    val exerciseType: ExerciseType,
    @Embedded val set: WorkoutSetEntity,
)

data class HistoryRow(
    val date: LocalDate,
    @Embedded val set: WorkoutSetEntity,
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE date = :date AND deletedAt IS NULL ORDER BY createdAt LIMIT 1")
    suspend fun getWorkoutForDate(date: LocalDate): WorkoutEntity?

    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity)

    @Query(
        """
        SELECT * FROM workout_exercises
        WHERE workoutId = :workoutId AND exerciseId = :exerciseId AND deletedAt IS NULL
        ORDER BY sortOrder LIMIT 1
        """,
    )
    suspend fun getWorkoutExercise(workoutId: String, exerciseId: String): WorkoutExerciseEntity?

    @Query("SELECT MAX(sortOrder) FROM workout_exercises WHERE workoutId = :workoutId AND deletedAt IS NULL")
    suspend fun maxExerciseSortOrder(workoutId: String): Int?

    @Query("SELECT MIN(sortOrder) FROM workout_exercises WHERE workoutId = :workoutId AND deletedAt IS NULL")
    suspend fun minExerciseSortOrder(workoutId: String): Int?

    @Insert
    suspend fun insertWorkoutExercise(workoutExercise: WorkoutExerciseEntity)

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSet(id: String): WorkoutSetEntity?

    @Query("SELECT MAX(sortOrder) FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId AND deletedAt IS NULL")
    suspend fun maxSetSortOrder(workoutExerciseId: String): Int?

    @Query(
        """
        SELECT we.exerciseId AS exerciseId, w.date AS date FROM workout_exercises we
        JOIN workouts w ON w.id = we.workoutId
        WHERE we.id = :workoutExerciseId
        """,
    )
    suspend fun entryDay(workoutExerciseId: String): EntryDay?

    /** The last day (epoch day) an exercise has a set on. */
    @Query(
        """
        SELECT MAX(w.date) FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND s.deletedAt IS NULL AND we.deletedAt IS NULL AND w.deletedAt IS NULL
        """,
    )
    suspend fun lastLoggedDay(exerciseId: String): Long?

    /** The weight of the last normal (not drop) set with a weight in an exercise's day entry. */
    @Query(
        """
        SELECT weightKg FROM workout_sets
        WHERE workoutExerciseId = :workoutExerciseId AND deletedAt IS NULL AND isDropSet = 0 AND weightKg IS NOT NULL
        ORDER BY sortOrder DESC LIMIT 1
        """,
    )
    suspend fun lastWorkingWeight(workoutExerciseId: String): Double?

    @Query("SELECT COUNT(*) FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId AND deletedAt IS NULL")
    suspend fun countSets(workoutExerciseId: String): Int

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity)

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    @Query("UPDATE workout_sets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSet(id: String, now: Long)

    @Query(
        """
        UPDATE workout_sets SET deletedAt = :now, updatedAt = :now
        WHERE workoutExerciseId = :workoutExerciseId AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSetsOf(workoutExerciseId: String, now: Long)

    @Query(
        """
        UPDATE workout_exercises
        SET supersetId = :supersetId, transitionSeconds = :transitionSeconds, roundRestSeconds = :roundRestSeconds,
            updatedAt = :now
        WHERE id IN (:ids)
        """,
    )
    suspend fun setSuperset(ids: List<String>, supersetId: String?, transitionSeconds: Int?, roundRestSeconds: Int?, now: Long)

    @Query(
        """
        UPDATE workout_exercises
        SET sortOrder = :sortOrder, supersetId = :supersetId, transitionSeconds = :transitionSeconds,
            roundRestSeconds = :roundRestSeconds, supersetRounds = :supersetRounds,
            supersetDropLast = :supersetDropLast, memberRounds = :memberRounds, memberDropSet = :memberDropSet,
            updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun arrange(
        id: String,
        sortOrder: Int,
        supersetId: String?,
        transitionSeconds: Int?,
        roundRestSeconds: Int?,
        supersetRounds: Int?,
        supersetDropLast: Boolean,
        memberRounds: Int?,
        memberDropSet: Boolean?,
        now: Long,
    )

    @Query(
        """
        UPDATE workout_exercises
        SET supersetRounds = :supersetRounds, supersetDropLast = :supersetDropLast, memberRounds = :memberRounds,
            memberDropSet = :memberDropSet, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun setSupersetPlan(
        id: String,
        supersetRounds: Int?,
        supersetDropLast: Boolean,
        memberRounds: Int?,
        memberDropSet: Boolean?,
        now: Long,
    )

    @Query(
        """
        UPDATE workout_exercises SET supersetId = NULL, transitionSeconds = NULL, roundRestSeconds = NULL,
            supersetRounds = NULL, supersetDropLast = 0, memberRounds = NULL, memberDropSet = NULL, updatedAt = :now
        WHERE supersetId = :supersetId AND deletedAt IS NULL
        """,
    )
    suspend fun clearSuperset(supersetId: String, now: Long)

    @Query("UPDATE workout_exercises SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteWorkoutExercise(id: String, now: Long)

    @Query("UPDATE workout_exercises SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteWorkoutExercises(ids: List<String>, now: Long)

    @Query(
        """
        UPDATE workout_sets SET deletedAt = :now, updatedAt = :now
        WHERE workoutExerciseId IN (:ids) AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSetsOfAll(ids: List<String>, now: Long)

    @Query(
        """
        SELECT we.id AS workoutExerciseId, e.id AS exerciseId, e.name AS exerciseName,
               e.type AS exerciseType, c.color AS categoryColor,
               s.id AS setId, s.weightKg AS weightKg, s.reps AS reps,
               s.distanceMeters AS distanceMeters, s.durationSeconds AS durationSeconds,
               s.rpe AS rpe, s.comment AS comment, s.isDropSet AS isDropSet, s.side AS side, we.supersetId AS supersetId,
               we.transitionSeconds AS transitionSeconds, we.roundRestSeconds AS roundRestSeconds,
               we.supersetRounds AS supersetRounds, we.supersetDropLast AS supersetDropLast,
               we.memberRounds AS memberRounds, we.memberDropSet AS memberDropSet, e.plan AS exercisePlan,
               e.weightUnit AS exerciseWeightUnit, e.perSide AS exercisePerSide, e.style AS exerciseStyle,
               e.categoryId AS exerciseCategoryId, s.createdAt AS setCreatedAt
        FROM workouts w
        JOIN workout_exercises we ON we.workoutId = w.id AND we.deletedAt IS NULL
        JOIN exercises e ON e.id = we.exerciseId
        JOIN categories c ON c.id = e.categoryId
        LEFT JOIN workout_sets s ON s.workoutExerciseId = we.id AND s.deletedAt IS NULL
        WHERE w.date = :date AND w.deletedAt IS NULL
        ORDER BY we.sortOrder, s.sortOrder
        """,
    )
    fun observeDay(date: LocalDate): Flow<List<DayRow>>

    @Query(
        """
        SELECT w.date AS date, s.*
        FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId AND we.deletedAt IS NULL
        JOIN workouts w ON w.id = we.workoutId AND w.deletedAt IS NULL
        WHERE we.exerciseId = :exerciseId AND s.deletedAt IS NULL
        ORDER BY w.date DESC, s.sortOrder
        """,
    )
    fun observeHistory(exerciseId: String): Flow<List<HistoryRow>>

    @Query(
        """
        SELECT DISTINCT w.date
        FROM workouts w
        JOIN workout_exercises we ON we.workoutId = w.id AND we.deletedAt IS NULL
        JOIN workout_sets s ON s.workoutExerciseId = we.id AND s.deletedAt IS NULL
        WHERE w.deletedAt IS NULL AND w.date BETWEEN :from AND :to
        """,
    )
    fun observeWorkoutDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>

    @Query(
        """
        SELECT w.date AS date, we.exerciseId AS exerciseId, e.categoryId AS categoryId,
               e.type AS exerciseType, e.style AS exerciseStyle, s.*
        FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId AND we.deletedAt IS NULL
        JOIN workouts w ON w.id = we.workoutId AND w.deletedAt IS NULL
        JOIN exercises e ON e.id = we.exerciseId
        WHERE s.deletedAt IS NULL
        ORDER BY w.date, we.sortOrder, s.sortOrder
        """,
    )
    fun observeAllSets(): Flow<List<LoggedSetRow>>

    @Query(
        """
        SELECT w.date AS date, e.name AS exerciseName, c.name AS categoryName, e.type AS exerciseType, s.*
        FROM workout_sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId AND we.deletedAt IS NULL
        JOIN workouts w ON w.id = we.workoutId AND w.deletedAt IS NULL
        JOIN exercises e ON e.id = we.exerciseId
        JOIN categories c ON c.id = e.categoryId
        WHERE s.deletedAt IS NULL
        ORDER BY w.date, we.sortOrder, s.sortOrder
        """,
    )
    suspend fun exportRows(): List<ExportRow>
}
