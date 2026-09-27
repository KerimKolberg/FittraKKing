package com.kkfittracking.data.health

import com.kkfittracking.data.BodyRepository
import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.WorkoutRepository
import com.kkfittracking.model.bodyweightOn
import com.kkfittracking.model.dayWorkouts
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** Sends a day's workouts to Health Connect: at the end of a guided workout, or from the day's menu. */
class HealthWorkouts(
    private val healthConnect: HealthConnect,
    private val workouts: WorkoutRepository,
    private val body: BodyRepository,
    private val settings: SettingsRepository,
) {
    sealed interface Result {
        /** Health Connect is missing, or writing workouts was not allowed. */
        data object NotConnected : Result

        data class Sent(val workouts: Int, val kcal: Int, val withCalories: Boolean) : Result
    }

    /** Sends the workouts of [date] (replacing what was sent before), whatever the setting. */
    suspend fun send(date: LocalDate): Result {
        if (healthConnect.status() != HealthConnect.Status.AVAILABLE || !runCatching { healthConnect.canWriteWorkouts() }.getOrDefault(false)) {
            return Result.NotConnected
        }
        val current = settings.settings.first()
        val day = workouts.observeDay(date).first()
        val fallbackEnd = date.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val planned = dayWorkouts(day, bodyweightOn(date, body.measurements.first()), current.unitSystem, fallbackEnd)
        val written = runCatching { healthConnect.writeWorkouts(date, planned, current.sendCaloriesToHealth) }.getOrElse { return Result.NotConnected }
        return Result.Sent(written, planned.kcal, current.sendCaloriesToHealth)
    }

    /** At the end of a workout: sends it when the setting is on and Health Connect is connected. */
    suspend fun sendIfEnabled(date: LocalDate) {
        if (settings.settings.first().sendWorkoutsToHealth) send(date)
    }
}
