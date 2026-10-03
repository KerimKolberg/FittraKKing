package com.kkfittracking.data.backup

import android.database.SQLException
import androidx.room.withTransaction
import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.db.AppDatabase
import com.kkfittracking.data.db.BodyMeasurementEntity
import com.kkfittracking.data.db.CategoryEntity
import com.kkfittracking.data.db.ExerciseEntity
import com.kkfittracking.data.db.RoutineEntity
import com.kkfittracking.data.db.RoutineExerciseEntity
import com.kkfittracking.data.db.WorkoutEntity
import com.kkfittracking.data.db.WorkoutExerciseEntity
import com.kkfittracking.data.db.WorkoutSetEntity
import com.kkfittracking.model.AutoBackup
import com.kkfittracking.model.BodyMetric
import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.Settings
import com.kkfittracking.model.ThemeMode
import com.kkfittracking.model.UnitSystem
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException

class BackupRepository(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.backupDao()

    suspend fun createBackup(): BackupFile = database.withTransaction {
        BackupFile(
            formatVersion = BackupFile.CURRENT_FORMAT_VERSION,
            createdAt = now(),
            appVersion = appVersion,
            categories = dao.categories().map { it.toDto() },
            exercises = dao.exercises().map { it.toDto() },
            workouts = dao.workouts().map { it.toDto() },
            workoutExercises = dao.workoutExercises().map { it.toDto() },
            sets = dao.sets().map { it.toDto() },
            plans = dao.routines().map { it.toDto() },
            planExercises = dao.routineExercises().map { it.toDto() },
            bodyMeasurements = dao.bodyMeasurements().map { it.toDto() },
            settings = settingsRepository.settings.first().toDto(),
        )
    }

    /**
     * Replaces everything in the app with the backup. Runs in one transaction: if anything in the
     * file is wrong, nothing is changed.
     */
    suspend fun restore(file: BackupFile) {
        // Convert first, so a bad value fails before any data is touched.
        val categories = file.categories.map { it.toEntity() }
        val exercises = file.exercises.map { it.toEntity() }
        val workouts = file.workouts.map { it.toEntity() }
        val workoutExercises = file.workoutExercises.map { it.toEntity() }
        val sets = file.sets.map { it.toEntity() }
        val routines = file.plans.map { it.toEntity() }
        val routineExercises = file.planExercises.map { it.toEntity() }
        val body = file.bodyMeasurements.map { it.toEntity() }
        try {
            database.withTransaction {
                dao.deleteSets()
                dao.deleteWorkoutExercises()
                dao.deleteRoutineExercises()
                dao.deleteWorkouts()
                dao.deleteRoutines()
                dao.deleteBodyMeasurements()
                dao.deleteExercises()
                dao.deleteCategories()
                dao.insertCategories(categories)
                dao.insertExercises(exercises)
                dao.insertWorkouts(workouts)
                dao.insertWorkoutExercises(workoutExercises)
                dao.insertSets(sets)
                dao.insertRoutines(routines)
                dao.insertRoutineExercises(routineExercises)
                dao.insertBodyMeasurements(body)
            }
        } catch (e: SQLException) {
            throw BackupException("The backup file is damaged: its data does not fit together. Nothing was changed.", e)
        }
        file.settings?.let { settingsRepository.restore(it.toSettings()) }
    }
}

private inline fun <reified T : Enum<T>> enumOf(name: String, what: String): T =
    enumValues<T>().firstOrNull { it.name == name }
        ?: throw BackupException("Unknown $what \"$name\". The backup may be from a newer version of the app.")

private fun dateOf(text: String): LocalDate = try {
    LocalDate.parse(text)
} catch (e: DateTimeParseException) {
    throw BackupException("The backup file has an invalid date \"$text\".", e)
}

private fun CategoryEntity.toDto() = CategoryDto(id, name, color, sortOrder, createdAt, updatedAt, deletedAt)
private fun CategoryDto.toEntity() = CategoryEntity(id, name, color, sortOrder, createdAt, updatedAt, deletedAt)

private fun ExerciseEntity.toDto() = ExerciseDto(
    id = id, name = name, categoryId = categoryId, type = type.name, notes = notes, isCustom = isCustom,
    tempo = tempo, perSide = perSide, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    muscles = muscles, style = style, plan = plan, links = links, weightUnit = weightUnit,
)
private fun ExerciseDto.toEntity() = ExerciseEntity(
    id = id, name = name, categoryId = categoryId, type = enumOf<ExerciseType>(type, "exercise type"),
    notes = notes, isCustom = isCustom, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    tempo = tempo, perSide = perSide, muscles = muscles, style = style, plan = plan, links = links,
    weightUnit = weightUnit,
)

private fun WorkoutEntity.toDto() = WorkoutDto(id, date.toString(), comment, createdAt, updatedAt, deletedAt)
private fun WorkoutDto.toEntity() = WorkoutEntity(id, dateOf(date), comment, createdAt, updatedAt, deletedAt)

private fun WorkoutExerciseEntity.toDto() = WorkoutExerciseDto(
    id, workoutId, exerciseId, sortOrder, createdAt, updatedAt, deletedAt,
    supersetId, transitionSeconds, roundRestSeconds,
    supersetRounds, supersetDropLast, memberRounds, memberDropSet,
)
private fun WorkoutExerciseDto.toEntity() = WorkoutExerciseEntity(
    id, workoutId, exerciseId, sortOrder, createdAt, updatedAt, deletedAt,
    supersetId, transitionSeconds, roundRestSeconds,
    supersetRounds, supersetDropLast, memberRounds, memberDropSet,
)

private fun WorkoutSetEntity.toDto() = SetDto(
    id = id, workoutExerciseId = workoutExerciseId, sortOrder = sortOrder, weightKg = weightKg, reps = reps,
    distanceMeters = distanceMeters, durationSeconds = durationSeconds, rpe = rpe, comment = comment,
    isDropSet = isDropSet, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, side = side,
)
private fun SetDto.toEntity() = WorkoutSetEntity(
    id = id, workoutExerciseId = workoutExerciseId, sortOrder = sortOrder, weightKg = weightKg, reps = reps,
    distanceMeters = distanceMeters, durationSeconds = durationSeconds, comment = comment,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt, rpe = rpe, isDropSet = isDropSet,
    side = side,
)

private fun RoutineEntity.toDto() = PlanDto(id, name, notes, createdAt, updatedAt, deletedAt)
private fun PlanDto.toEntity() = RoutineEntity(id, name, notes, createdAt, updatedAt, deletedAt)

private fun RoutineExerciseEntity.toDto() = PlanExerciseDto(
    id, routineId, exerciseId, sortOrder, createdAt, updatedAt, deletedAt,
    supersetId, transitionSeconds, roundRestSeconds,
    supersetRounds, supersetDropLast, memberRounds, memberDropSet,
)
private fun PlanExerciseDto.toEntity() = RoutineExerciseEntity(
    id, planId, exerciseId, sortOrder, createdAt, updatedAt, deletedAt,
    supersetId, transitionSeconds, roundRestSeconds,
    supersetRounds, supersetDropLast, memberRounds, memberDropSet,
)

private fun BodyMeasurementEntity.toDto() =
    BodyMeasurementDto(id, date.toString(), metric.name, value, createdAt, updatedAt, deletedAt)
private fun BodyMeasurementDto.toEntity() = BodyMeasurementEntity(
    id, dateOf(date), enumOf<BodyMetric>(metric, "body measurement"), value, createdAt, updatedAt, deletedAt,
)

private fun Settings.toDto() = SettingsDto(
    unitSystem = unitSystem.name,
    restTimerSeconds = restTimerSeconds,
    autoStartRestTimer = autoStartRestTimer,
    themeMode = themeMode.name,
    weeklyGoal = weeklyGoal,
    dropSetsEnabled = dropSetsEnabled,
    dropSetPercent = dropSetPercent,
    supersetAutoAdvance = supersetAutoAdvance,
    supersetTransitionSeconds = supersetTransitionSeconds,
    sectionOrder = sectionOrder,
    styleOrder = styleOrder,
    sendWorkoutsToHealth = sendWorkoutsToHealth,
    sendCaloriesToHealth = sendCaloriesToHealth,
    autoBackup = autoBackup.name,
    reminderDays = reminderDays.sorted().map { it.name },
    reminderMinute = reminderMinute,
    progressionHints = progressionHints,
    warmUpSets = warmUpSets,
    barKg = barKg,
)

/** Unknown values fall back to the defaults: settings are not worth failing a restore over. */
private fun SettingsDto.toSettings(): Settings {
    val defaults = Settings()
    return Settings(
        unitSystem = UnitSystem.entries.firstOrNull { it.name == unitSystem && it.isAppWide } ?: defaults.unitSystem,
        restTimerSeconds = restTimerSeconds,
        autoStartRestTimer = autoStartRestTimer,
        themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: defaults.themeMode,
        weeklyGoal = weeklyGoal,
        dropSetsEnabled = dropSetsEnabled,
        dropSetPercent = dropSetPercent,
        supersetAutoAdvance = supersetAutoAdvance,
        supersetTransitionSeconds = supersetTransitionSeconds,
        sectionOrder = sectionOrder,
        styleOrder = styleOrder,
        sendWorkoutsToHealth = sendWorkoutsToHealth,
        sendCaloriesToHealth = sendCaloriesToHealth,
        autoBackup = AutoBackup.entries.firstOrNull { it.name == autoBackup } ?: defaults.autoBackup,
        reminderDays = reminderDays.mapNotNull { day -> DayOfWeek.entries.firstOrNull { it.name == day } }.toSet(),
        reminderMinute = reminderMinute,
        progressionHints = progressionHints,
        warmUpSets = warmUpSets,
        barKg = barKg,
    )
}
