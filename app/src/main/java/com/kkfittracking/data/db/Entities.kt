package com.kkfittracking.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kkfittracking.model.BodyMetric
import com.kkfittracking.model.ExerciseType
import java.time.LocalDate

// Sync-ready conventions (see ROADMAP.md): every table uses a UUID string primary key,
// createdAt/updatedAt epoch-millis timestamps, and soft deletes through a nullable deletedAt.
// Queries must always filter on `deletedAt IS NULL`.

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
    indices = [Index("categoryId")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    val type: ExerciseType,
    val notes: String,
    val isCustom: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Added in v3. */
    @ColumnInfo(defaultValue = "") val tempo: String = "",
    /** Added in v3. */
    @ColumnInfo(defaultValue = "0") val perSide: Boolean = false,
    /** [com.kkfittracking.model.Muscle] names, comma separated, main one first; empty when never chosen (added in v5). */
    @ColumnInfo(defaultValue = "") val muscles: String = "",
    /** A [com.kkfittracking.model.TrainingStyle] name, or empty when never chosen (added in v5). */
    @ColumnInfo(defaultValue = "") val style: String = "",
    /** A [com.kkfittracking.model.ExercisePlan] as JSON, or empty (added in v5). */
    @ColumnInfo(defaultValue = "") val plan: String = "",
    /** Video and page links, one per line (see [com.kkfittracking.model.ExerciseLinks], added in v5). */
    @ColumnInfo(defaultValue = "") val links: String = "",
    /**
     * This exercise's own weight unit, a [com.kkfittracking.model.UnitSystem] name (kg, lb or machine
     * levels); empty follows the app's setting (added in v7).
     */
    @ColumnInfo(defaultValue = "") val weightUnit: String = "",
)

@Entity(tableName = "workouts", indices = [Index("date")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val comment: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"]),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Exercises sharing this id are done as a superset (added in v4). */
    val supersetId: String? = null,
    /** Seconds to walk to the next superset exercise (added in v4). */
    val transitionSeconds: Int? = null,
    /** Seconds of rest after each round of the superset (added in v5). */
    val roundRestSeconds: Int? = null,
    /** Rounds planned for the superset (added in v6). */
    val supersetRounds: Int? = null,
    /** Every exercise of the superset ends its last round with a drop set (added in v6). */
    @ColumnInfo(defaultValue = "0") val supersetDropLast: Boolean = false,
    /** It joins only the last this many rounds of its superset (added in v6). */
    val memberRounds: Int? = null,
    /** Its own choice about a drop set on its last round; null follows the superset (added in v6). */
    val memberDropSet: Boolean? = null,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val sortOrder: Int,
    val weightKg: Double?,
    val reps: Int?,
    val distanceMeters: Double?,
    val durationSeconds: Int?,
    val comment: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Effort 1-10, added in v3. */
    val rpe: Int? = null,
    /** A drop set: done right after the previous set with less weight (added in v4). */
    @ColumnInfo(defaultValue = "0") val isDropSet: Boolean = false,
    /** A [com.kkfittracking.model.Side] name for one-sided sets; empty for both sides (added in v8). */
    @ColumnInfo(defaultValue = "") val side: String = "",
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"]),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Exercises of the plan sharing this id are done as a superset (added in v5). */
    val supersetId: String? = null,
    /** Seconds to walk to the next superset exercise (added in v5). */
    val transitionSeconds: Int? = null,
    /** Seconds of rest after each round of the superset (added in v5). */
    val roundRestSeconds: Int? = null,
    /** Rounds planned for the superset (added in v6). */
    val supersetRounds: Int? = null,
    /** Every exercise of the superset ends its last round with a drop set (added in v6). */
    @ColumnInfo(defaultValue = "0") val supersetDropLast: Boolean = false,
    /** It joins only the last this many rounds of its superset (added in v6). */
    val memberRounds: Int? = null,
    /** Its own choice about a drop set on its last round; null follows the superset (added in v6). */
    val memberDropSet: Boolean? = null,
)

/** One body measurement. The value is stored in base units: kg, percent, or cm (see [BodyMetric]). */
@Entity(tableName = "body_measurements", indices = [Index("metric", "date")])
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val metric: BodyMetric,
    val value: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
