package com.kkfittracking.guide

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.WorkoutRepository
import com.kkfittracking.model.NextStep
import com.kkfittracking.model.SetValues
import com.kkfittracking.model.Settings
import com.kkfittracking.model.Side
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.guideSuggestion
import com.kkfittracking.model.nextStep
import com.kkfittracking.model.supersetContextOf
import com.kkfittracking.model.trackKind
import com.kkfittracking.timer.RestTimer
import com.kkfittracking.wear.WatchCommand
import com.kkfittracking.wear.WatchFields
import com.kkfittracking.wear.WatchState
import com.kkfittracking.wear.WearJson
import com.kkfittracking.wear.WearPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Connects the guided workout with the watch app over the Wear OS Data Layer (Bluetooth, or Wi-Fi
 * when the watch is away from the phone): publishes what to do now as a data item, and carries out
 * what the watch sends, such as a logged set. The phone stays the one place the data lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val guide: GuidedWorkout,
    private val workouts: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val restTimer: RestTimer,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var last: WatchState? = null

    /** The running rest as the watch needs it: when it ends, not every second of it. */
    private data class Rest(val running: Boolean, val totalSeconds: Int, val label: String?)

    fun start() {
        scope.launch {
            val rest = restTimer.state.map { Rest(it.isRunning, it.totalSeconds, it.label) }.distinctUntilChanged()
            val today = workouts.observeDay(LocalDate.now()).map { it.size }.distinctUntilChanged()
            combine(guide.state, rest, settingsRepository.settings, today) { state, restNow, settings, count ->
                Inputs(state, restNow, settings, count)
            }
                .mapLatest { build(it) }
                .collect { publish(it) }
        }
    }

    private data class Inputs(val guide: GuideState, val rest: Rest, val settings: Settings, val exercisesToday: Int)

    private suspend fun build(inputs: Inputs): WatchState {
        val settings = inputs.settings
        val units = settings.unitSystem
        val base = WatchState(
            exercisesToday = inputs.exercisesToday,
            weightUnit = units.weightUnit,
            weightStep = fineStep(units),
            weightBigStep = bigStep(units),
            distanceUnit = units.distanceUnit,
            heightUnit = units.lengthUnit,
            sentAtMillis = now(),
        )
        val state = inputs.guide
        val session = state.session ?: return base
        val target = state.target
        val exercise = target?.let { t -> state.day.firstOrNull { it.exerciseId == t.exerciseId } }
        val suggestion = if (target != null) {
            val date = LocalDate.ofEpochDay(session.epochDay)
            val lastSession = workouts.observeHistory(target.exerciseId).first().firstOrNull { it.date < date }?.sets.orEmpty()
            guideSuggestion(state.day, target, settings, lastSession)
        } else {
            SetValues()
        }
        val type = exercise?.exerciseType
        // The exercise's own weight unit: kg, lb or a machine's levels.
        val weightUnits = exercise?.weightUnits ?: units
        val restEndsAt = if (inputs.rest.running) now() + restTimer.state.value.remainingSeconds * 1000L else null
        return base.copy(
            active = true,
            paused = session.isPaused,
            epochDay = session.epochDay,
            exerciseId = target?.exerciseId,
            exerciseName = target?.name.orEmpty(),
            step = target?.step.orEmpty(),
            position = target?.position ?: 0,
            of = target?.of ?: state.day.size,
            isDrop = target?.isDrop == true,
            fields = type?.let {
                WatchFields(
                    weight = it.usesWeight, reps = it.usesReps, seconds = it.usesTime, distance = it.usesDistance,
                    height = it.usesHeight, intensity = it.usesIntensity, repsLabel = it.repsLabel,
                    perSide = exercise.perSide,
                )
            } ?: WatchFields(),
            track = exercise?.let { trackKind(it.exerciseName, it.exerciseType) },
            weight = suggestion.weightKg?.let { oneDecimal(weightUnits.weightFromKg(it)) },
            weightUnit = weightUnits.weightUnit,
            weightStep = fineStep(weightUnits),
            weightBigStep = bigStep(weightUnits),
            upcoming = state.upcoming,
            side = (target?.side ?: Side.LEFT.takeIf { exercise?.perSide == true })?.name,
            reps = suggestion.reps,
            seconds = suggestion.durationSeconds,
            distance = suggestion.distanceMeters?.takeIf { type?.usesDistance == true }?.let { oneDecimal(units.distanceFromMeters(it)) },
            height = suggestion.distanceMeters?.takeIf { type?.usesHeight == true }?.let { oneDecimal(units.heightFromMeters(it)) },
            intensity = suggestion.rpe,
            percent = state.completion.percent,
            trainingSinceMillis = if (session.isPaused) null else session.startedAtMillis + session.pausedMillis,
            trainingMillis = session.activeMillis(now()),
            restEndsAtMillis = restEndsAt,
            restLabel = inputs.rest.label.takeIf { restEndsAt != null },
            allDone = target == null,
        )
    }

    private suspend fun publish(state: WatchState) {
        last = state
        try {
            val request = PutDataMapRequest.create(WearPaths.STATE).apply {
                dataMap.putByteArray(WearPaths.STATE_KEY, WearJson.encode(state))
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No watch, or no Google Play services: the phone works on its own.
            Log.d(TAG, "Could not reach the watch", e)
        }
    }

    /** Carries out what the watch asked for. */
    suspend fun handle(command: WatchCommand) {
        when (command) {
            WatchCommand.Sync -> last?.let { publish(it.copy(sentAtMillis = now())) }
            WatchCommand.Start -> if (!guide.isRunning) guide.start(LocalDate.now())
            WatchCommand.Pause -> {
                restTimer.stop()
                guide.pause()
            }
            WatchCommand.Resume -> guide.resume()
            is WatchCommand.Skip -> guide.skip(command.exerciseId)
            WatchCommand.Stop -> guide.stop()
            is WatchCommand.Log -> log(command)
        }
    }

    /** Saves a set done on the watch, then starts the rest (or not, before a drop set) as the phone would. */
    private suspend fun log(command: WatchCommand.Log) {
        val date = LocalDate.ofEpochDay(command.epochDay)
        val settings = settingsRepository.settings.first()
        val units = settings.unitSystem
        val weightUnits = workouts.observeDay(date).first().firstOrNull { it.exerciseId == command.exerciseId }?.weightUnits ?: units
        val values = SetValues(
            weightKg = command.weight?.let { weightUnits.weightToKg(it) },
            reps = command.reps,
            distanceMeters = command.distance?.let { units.distanceToMeters(it) } ?: command.height?.let { units.heightToMeters(it) },
            durationSeconds = command.seconds,
            rpe = command.intensity?.coerceIn(1, 10),
            note = command.note,
            isDropSet = command.isDrop,
            side = Side.of(command.side),
        )
        workouts.addSet(date, command.exerciseId, values)
        val day = workouts.observeDay(date).first()
        val entry = day.firstOrNull { it.exerciseId == command.exerciseId } ?: return
        val step = nextStep(
            entry.exerciseId, entry.exerciseType, entry.plan, entry.sets, supersetContextOf(day, entry.exerciseId), settings, entry.perSide,
        )
        val target = guide.afterSetLogged(date)
        val seconds = when (step) {
            is NextStep.Rest -> step.seconds
            is NextStep.Transition -> step.seconds
            else -> 0
        }
        if (settings.autoStartRestTimer && seconds > 0) {
            restTimer.start(seconds, label = target?.let { "Next: ${it.name} · ${it.step}" })
        } else {
            restTimer.stop()
        }
    }

    private companion object {
        const val TAG = "WatchBridge"

        fun oneDecimal(value: Double): Double = (value * 10).roundToInt() / 10.0

        /** A tap on the watch's + or −: half kilos, single pounds or levels. */
        fun fineStep(units: UnitSystem): Double = if (units == UnitSystem.METRIC) 0.5 else 1.0

        /** A long press: 5 kg, 10 lb or 5 levels. */
        fun bigStep(units: UnitSystem): Double = if (units == UnitSystem.IMPERIAL) 10.0 else 5.0
    }
}
