package com.kkfittracking.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The backup file: plain JSON with every row of every table (soft-deleted rows included, so a
 * future cloud sync still sees the deletions). Dates are ISO strings like "2026-09-25", and
 * enums are stored by name.
 *
 * Compatibility rules: new fields get defaults, so older backups keep loading. Bump
 * [CURRENT_FORMAT_VERSION] only for changes older app versions cannot read.
 */
@Serializable
data class BackupFile(
    val formatVersion: Int,
    val createdAt: Long,
    val appVersion: String = "",
    val categories: List<CategoryDto> = emptyList(),
    val exercises: List<ExerciseDto> = emptyList(),
    val workouts: List<WorkoutDto> = emptyList(),
    val workoutExercises: List<WorkoutExerciseDto> = emptyList(),
    val sets: List<SetDto> = emptyList(),
    val plans: List<PlanDto> = emptyList(),
    val planExercises: List<PlanExerciseDto> = emptyList(),
    val bodyMeasurements: List<BodyMeasurementDto> = emptyList(),
    val settings: SettingsDto? = null,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** What a backup contains, shown before restoring it. */
data class BackupSummary(val createdAt: Long, val workouts: Int, val sets: Int, val plans: Int, val bodyMeasurements: Int)

fun BackupFile.summary() = BackupSummary(
    createdAt = createdAt,
    workouts = workouts.count { it.deletedAt == null },
    sets = sets.count { it.deletedAt == null },
    plans = plans.count { it.deletedAt == null },
    bodyMeasurements = bodyMeasurements.count { it.deletedAt == null },
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val color: Int,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val categoryId: String,
    val type: String,
    val notes: String = "",
    val isCustom: Boolean,
    val tempo: String = "",
    val perSide: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val muscles: String = "",
    val style: String = "",
    val plan: String = "",
    val links: String = "",
    val weightUnit: String = "",
)

@Serializable
data class WorkoutDto(
    val id: String,
    val date: String,
    val comment: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class WorkoutExerciseDto(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val supersetId: String? = null,
    val transitionSeconds: Int? = null,
    val roundRestSeconds: Int? = null,
    val supersetRounds: Int? = null,
    val supersetDropLast: Boolean = false,
    val memberRounds: Int? = null,
    val memberDropSet: Boolean? = null,
)

@Serializable
data class SetDto(
    val id: String,
    val workoutExerciseId: String,
    val sortOrder: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val distanceMeters: Double? = null,
    val durationSeconds: Int? = null,
    val rpe: Int? = null,
    val comment: String = "",
    val isDropSet: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val side: String = "",
)

@Serializable
data class PlanDto(
    val id: String,
    val name: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class PlanExerciseDto(
    val id: String,
    val planId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val supersetId: String? = null,
    val transitionSeconds: Int? = null,
    val roundRestSeconds: Int? = null,
    val supersetRounds: Int? = null,
    val supersetDropLast: Boolean = false,
    val memberRounds: Int? = null,
    val memberDropSet: Boolean? = null,
)

@Serializable
data class BodyMeasurementDto(
    val id: String,
    val date: String,
    val metric: String,
    val value: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class SettingsDto(
    val unitSystem: String,
    val restTimerSeconds: Int,
    val autoStartRestTimer: Boolean,
    val themeMode: String,
    val weeklyGoal: Int = 3,
    val dropSetsEnabled: Boolean = true,
    val dropSetPercent: Int = 20,
    val supersetAutoAdvance: Boolean = true,
    val supersetTransitionSeconds: Int = 15,
    val sectionOrder: List<String> = emptyList(),
    val styleOrder: List<String> = emptyList(),
    val sendWorkoutsToHealth: Boolean = true,
    val sendCaloriesToHealth: Boolean = false,
    val autoBackup: String = "WEEKLY",
    val reminderDays: List<String> = emptyList(),
    val reminderMinute: Int = 17 * 60,
    val progressionHints: Boolean = true,
    val warmUpSets: Boolean = true,
    val barKg: Double = 20.0,
)

/** A backup that cannot be restored, with a message for the user. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupJson {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("This is not a KK-Fittracking backup file, or it is damaged.", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("This is not a KK-Fittracking backup file, or it is damaged.", e)
        }
        if (file.formatVersion > BackupFile.CURRENT_FORMAT_VERSION) {
            throw BackupException("This backup was made by a newer version of the app. Update the app first.")
        }
        return file
    }
}
