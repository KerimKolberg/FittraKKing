package com.kkfittracking.wear

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where things go on the Wear OS Data Layer. */
object WearPaths {
    /** The phone publishes the guided workout here as a data item; the watch listens. */
    const val STATE = "/kk/guide/state"

    /** The key of the encoded [WatchState] in that data item. */
    const val STATE_KEY = "state"

    /** The watch sends [WatchCommand]s to the phone as messages on this path. */
    const val COMMAND = "/kk/guide/command"

    /** Announced by the phone app, so the watch knows which connected device to talk to. */
    const val PHONE_CAPABILITY = "kk_fittracking_phone"
}

/** What the watch asks for a set: which values it shows steppers for. */
@Serializable
data class WatchFields(
    val weight: Boolean = false,
    val reps: Boolean = false,
    val seconds: Boolean = false,
    /** Distance, in [WatchState.distanceUnit]. */
    val distance: Boolean = false,
    /** Jump height or distance, in [WatchState.heightUnit]. */
    val height: Boolean = false,
    /** Effort from 1 (very easy) to 10 (maximal). */
    val intensity: Boolean = false,
    /** "Reps", or "Rounds". */
    val repsLabel: String = "Reps",
    /** Done one side at a time: each set is a left or a right one. */
    val perSide: Boolean = false,
)

/** What the watch can record with its sensors (Health Services), for cardio and sessions. */
@Serializable
enum class TrackKind { RUNNING, WALKING, HIKING, BIKING, ROWING, SWIMMING, ELLIPTICAL, STAIRS, HIIT, SPORT, WORKOUT }

/**
 * The guided workout as the watch shows it. Weights are in the user's unit ([weightUnit]), so the
 * watch only shows and steps them. Times are wall-clock milliseconds.
 */
@Serializable
data class WatchState(
    /** A guided workout runs. */
    val active: Boolean = false,
    val paused: Boolean = false,
    val epochDay: Long = 0,
    /** Exercises on today's log, so the watch can offer to start. */
    val exercisesToday: Int = 0,
    val exerciseId: String? = null,
    val exerciseName: String = "",
    /** "Set 2 of 3", "Round 2 of 3", "Drop 1 of 2". */
    val step: String = "",
    val position: Int = 0,
    val of: Int = 0,
    val isDrop: Boolean = false,
    val fields: WatchFields = WatchFields(),
    /** The exercise can be recorded with the watch's sensors (time, distance, steps, heart rate). */
    val track: TrackKind? = null,
    /** Suggested values: the last set, the plan, or the drop set's lighter weight. */
    val weight: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val distance: Double? = null,
    val height: Double? = null,
    val intensity: Int? = null,
    /** For one-sided exercises: the side to do now, "LEFT" or "RIGHT". */
    val side: String? = null,
    /** The exercises after this one, to get ready for. */
    val upcoming: List<String> = emptyList(),
    val weightUnit: String = "kg",
    val distanceUnit: String = "km",
    val heightUnit: String = "cm",
    /** A tap on + or −; a long press moves [weightBigStep]. */
    val weightStep: Double = 0.5,
    val weightBigStep: Double = 5.0,
    val percent: Int = 0,
    /** When the training time started, pauses taken out; null while paused. */
    val trainingSinceMillis: Long? = null,
    val trainingMillis: Long = 0,
    /** When the running rest ends, and what comes after it. */
    val restEndsAtMillis: Long? = null,
    val restLabel: String? = null,
    /** Everything planned is done. */
    val allDone: Boolean = false,
    /** Changes with every publish, so each one reaches the watch. */
    val sentAtMillis: Long = 0,
)

/** What the watch asks the phone to do. */
@Serializable
sealed interface WatchCommand {
    /** Send the current state. */
    @Serializable @SerialName("sync")
    data object Sync : WatchCommand

    /** Start guiding today's plan. */
    @Serializable @SerialName("start")
    data object Start : WatchCommand

    @Serializable @SerialName("pause")
    data object Pause : WatchCommand

    @Serializable @SerialName("resume")
    data object Resume : WatchCommand

    @Serializable @SerialName("skip")
    data class Skip(val exerciseId: String) : WatchCommand

    @Serializable @SerialName("stop")
    data object Stop : WatchCommand

    /** A set done on the watch, for [exerciseId] on [epochDay]; weight in the unit the state gave. */
    @Serializable @SerialName("log")
    data class Log(
        val epochDay: Long,
        val exerciseId: String,
        val weight: Double? = null,
        val reps: Int? = null,
        val seconds: Int? = null,
        /** In the state's distance unit. */
        val distance: Double? = null,
        /** In the state's height unit. */
        val height: Double? = null,
        val intensity: Int? = null,
        /** "LEFT" or "RIGHT" for a one-sided set. */
        val side: String? = null,
        /** E.g. "♥ 132 bpm" or "8,432 steps · ♥ avg 141 bpm". */
        val note: String = "",
        val isDrop: Boolean = false,
    ) : WatchCommand
}

/** Turns the messages into bytes and back. Unknown fields from a newer app are ignored. */
object WearJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(state: WatchState): ByteArray = json.encodeToString(WatchState.serializer(), state).encodeToByteArray()

    fun decodeState(bytes: ByteArray): WatchState? =
        runCatching { json.decodeFromString(WatchState.serializer(), bytes.decodeToString()) }.getOrNull()

    fun encode(command: WatchCommand): ByteArray =
        json.encodeToString(WatchCommand.serializer(), command).encodeToByteArray()

    fun decodeCommand(bytes: ByteArray): WatchCommand? =
        runCatching { json.decodeFromString(WatchCommand.serializer(), bytes.decodeToString()) }.getOrNull()
}
