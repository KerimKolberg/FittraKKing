package com.kkfittracking.guide

import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.WorkoutRepository
import com.kkfittracking.model.DayCompletion
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.GuideSession
import com.kkfittracking.model.GuideSummary
import com.kkfittracking.model.GuideTarget
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.dayCompletion
import com.kkfittracking.model.dayWorkouts
import com.kkfittracking.model.guideTarget
import com.kkfittracking.model.upcomingExercises
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** A guided workout as the screens, the notification and (later) the watch show it. */
data class GuideState(
    val session: GuideSession? = null,
    val day: List<DayExercise> = emptyList(),
    /** Where to go now; null once everything planned is done (or skipped). */
    val target: GuideTarget? = null,
    val completion: DayCompletion = DayCompletion(emptyList()),
    /** The exercises after [target], to get ready for. */
    val upcoming: List<String> = emptyList(),
) {
    val isActive: Boolean get() = session != null

    fun isActiveOn(date: LocalDate): Boolean = session?.epochDay == date.toEpochDay()
}

/**
 * The play button: walks through a day's plan, one exercise after the other. Logging a set moves
 * the guide on by itself, since the target comes from what is logged. Lives as long as the app
 * process; [onStarted] brings up the ongoing notification that keeps the process alive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidedWorkout(
    scope: CoroutineScope,
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val onStarted: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    /** The latest bodyweight in kg, for the calories in the summary. */
    private val bodyweightKg: () -> Double? = { null },
    /** Called when a workout ends, e.g. to send it to Health Connect. */
    private val onStopped: (LocalDate) -> Unit = {},
) {
    private val session = MutableStateFlow<GuideSession?>(null)

    val state: StateFlow<GuideState> = session
        .flatMapLatest { current ->
            if (current == null) {
                flowOf(GuideState())
            } else {
                combine(workoutRepository.observeDay(LocalDate.ofEpochDay(current.epochDay)), settingsRepository.settings) { day, settings ->
                    val target = guideTarget(day, current.skipped, settings)
                    GuideState(
                        session = current,
                        day = day,
                        target = target,
                        completion = dayCompletion(day),
                        upcoming = target?.let { upcomingExercises(day, it, current.skipped, settings) }.orEmpty(),
                    )
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, GuideState())

    /** Whether a guided workout runs, right away (the [state] follows a moment later). */
    val isRunning: Boolean get() = session.value != null

    private val _summary = MutableStateFlow<GuideSummary?>(null)

    /** The summary of the workout that just ended, until it was shown. */
    val summary: StateFlow<GuideSummary?> = _summary.asStateFlow()

    /** Starts guiding [date]'s plan, and returns the first exercise to do. */
    suspend fun start(date: LocalDate): GuideTarget? {
        session.value = GuideSession(epochDay = date.toEpochDay(), startedAtMillis = now())
        _summary.value = null
        onStarted()
        return targetNow()
    }

    fun pause() = session.update { it.pause(now()) }

    fun resume() = session.update { it.resume(now()) }

    /** Leaves an exercise out today; the guide goes on with the next one. */
    fun skip(exerciseId: String) = session.update { it.skip(exerciseId) }

    /** Ends the workout: what was logged stays, and a summary shows what was done and what was not. */
    fun stop() {
        val current = session.value ?: return
        val state = state.value
        val completion = if (state.session?.epochDay == current.epochDay) state.completion else DayCompletion(emptyList())
        val day = if (state.session?.epochDay == current.epochDay) state.day else emptyList()
        val energy = dayWorkouts(day, bodyweightKg(), UnitSystem.METRIC, now())
        _summary.value = GuideSummary(
            current.epochDay, current.activeMillis(now()), completion, stoppedEarly = state.target != null,
            kcal = energy.kcal.takeIf { day.any { it.sets.isNotEmpty() } },
            bodyweightKnown = energy.bodyweightKnown,
        )
        session.value = null
        onStopped(LocalDate.ofEpochDay(current.epochDay))
    }

    fun consumeSummary() {
        _summary.value = null
    }

    /**
     * After a set was saved on [date]: the exercise to go to now, read fresh from the database.
     * Logging during a pause ends the pause. Null when no guided workout runs on that day, or when
     * everything is done.
     */
    suspend fun afterSetLogged(date: LocalDate): GuideTarget? {
        val current = session.value?.takeIf { it.epochDay == date.toEpochDay() } ?: return null
        if (current.isPaused) resume()
        return targetNow()
    }

    private suspend fun targetNow(): GuideTarget? {
        val current = session.value ?: return null
        val day = workoutRepository.observeDay(LocalDate.ofEpochDay(current.epochDay)).first()
        return guideTarget(day, current.skipped, settingsRepository.settings.first())
    }

    private fun MutableStateFlow<GuideSession?>.update(change: (GuideSession) -> GuideSession) {
        value = value?.let(change)
    }
}
