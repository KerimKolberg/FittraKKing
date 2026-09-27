package com.kkfittracking.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.kkfittracking.model.ActivityKind
import com.kkfittracking.model.DayWorkouts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Steps and distance of a whole day, from every app and device that writes to Health Connect. */
data class DailyActivity(val steps: Long, val meters: Double?)

/**
 * Reads the daily steps and distance that Samsung Health, Google Fit, the watch and other apps
 * store in Health Connect (Health Connect removes the double counting when the phone and the watch
 * both count the same steps), and writes the finished workouts, so they show up in those apps.
 */
class HealthConnect(private val context: Context) {
    enum class Status { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

    /** Reading the daily steps and distance. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )

    private val workoutPermission = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    private val caloriesPermission = HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)

    /** Everything the app asks for at once: reading steps, writing workouts and their calories. */
    val allPermissions: Set<String> = permissions + workoutPermission + caloriesPermission

    fun status(): Status = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_UPDATE
        else -> Status.UNAVAILABLE
    }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        status() == Status.AVAILABLE && client.permissionController.getGrantedPermissions().containsAll(permissions)

    /** Whether finished workouts can be written. */
    suspend fun canWriteWorkouts(): Boolean =
        status() == Status.AVAILABLE && workoutPermission in client.permissionController.getGrantedPermissions()

    /**
     * Writes a day's workouts, replacing what this app wrote for that day before (Health Connect only
     * lets an app delete its own records), so a day can be sent again after changes. Calories go along
     * when [withCalories] and allowed. Returns how many workouts were written.
     */
    suspend fun writeWorkouts(date: LocalDate, workouts: DayWorkouts, withCalories: Boolean): Int {
        val zone = ZoneId.systemDefault()
        val granted = client.permissionController.getGrantedPermissions()
        if (workoutPermission !in granted) return 0
        val sessions = workouts.sessions
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val from = listOfNotNull(dayStart, sessions.minOfOrNull { Instant.ofEpochMilli(it.startMillis) }).min()
        val to = listOfNotNull(dayEnd, sessions.maxOfOrNull { Instant.ofEpochMilli(it.endMillis) }).max()
        val range = TimeRangeFilter.between(from, to)
        client.deleteRecords(ExerciseSessionRecord::class, range)
        val calories = withCalories && caloriesPermission in granted
        if (caloriesPermission in granted) client.deleteRecords(ActiveCaloriesBurnedRecord::class, range)
        if (sessions.isEmpty()) return 0
        val version = System.currentTimeMillis()
        val records = sessions.flatMap { session ->
            val start = Instant.ofEpochMilli(session.startMillis)
            val end = Instant.ofEpochMilli(maxOf(session.endMillis, session.startMillis + 60_000))
            val startOffset = zone.rules.getOffset(start)
            val endOffset = zone.rules.getOffset(end)
            listOfNotNull(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = startOffset,
                    endTime = end,
                    endZoneOffset = endOffset,
                    exerciseType = session.kind.exerciseType(),
                    title = session.title,
                    notes = session.notes,
                    metadata = Metadata(clientRecordId = session.key, clientRecordVersion = version),
                ),
                ActiveCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = startOffset,
                    endTime = end,
                    endZoneOffset = endOffset,
                    energy = Energy.kilocalories(session.kcal),
                    metadata = Metadata(clientRecordId = "${session.key}-kcal", clientRecordVersion = version),
                ).takeIf { calories && session.kcal > 0 },
            )
        }
        client.insertRecords(records)
        return sessions.size
    }

    /** The day's totals, or null when Health Connect cannot be read. */
    suspend fun dailyActivity(date: LocalDate): DailyActivity? = runCatching {
        val zone = ZoneId.systemDefault()
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    date.atStartOfDay(zone).toInstant(),
                    date.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )
        DailyActivity(steps = result[StepsRecord.COUNT_TOTAL] ?: 0, meters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters)
    }.getOrNull()

    private fun ActivityKind.exerciseType(): Int = when (this) {
        ActivityKind.STRENGTH -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        ActivityKind.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        ActivityKind.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        ActivityKind.HIKING -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        ActivityKind.BIKING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        ActivityKind.ROWING -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
        ActivityKind.SWIMMING -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        ActivityKind.ELLIPTICAL -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
        ActivityKind.STAIRS -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING
        ActivityKind.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        ActivityKind.YOGA -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
        ActivityKind.STRETCHING -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
        ActivityKind.MEDITATION -> ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING
        ActivityKind.DANCE -> ExerciseSessionRecord.EXERCISE_TYPE_DANCING
        ActivityKind.TENNIS -> ExerciseSessionRecord.EXERCISE_TYPE_TENNIS
        ActivityKind.TABLE_TENNIS -> ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS
        ActivityKind.VOLLEYBALL -> ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL
        ActivityKind.BADMINTON -> ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON
        ActivityKind.SQUASH -> ExerciseSessionRecord.EXERCISE_TYPE_SQUASH
        ActivityKind.SOCCER -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
        ActivityKind.BASKETBALL -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
        ActivityKind.CLIMBING -> ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING
        ActivityKind.MARTIAL_ARTS -> ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS
        ActivityKind.BOXING -> ExerciseSessionRecord.EXERCISE_TYPE_BOXING
        ActivityKind.JUMP_ROPE, ActivityKind.SPORT, ActivityKind.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    companion object {
        /** Where Health Connect is installed or updated on Android 13 and older. */
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}
