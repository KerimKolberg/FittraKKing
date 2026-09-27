package com.kkfittracking.watch

import android.app.Application
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.kkfittracking.wear.WatchCommand
import com.kkfittracking.wear.WatchState
import com.kkfittracking.wear.WearJson
import com.kkfittracking.wear.WearPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

enum class Connection { SEARCHING, CONNECTED, NO_PHONE }

/**
 * The watch side of the guided workout. The phone keeps the data and decides what comes next; the
 * watch shows it, lets the user enter the set, and buzzes when the rest is over.
 */
class WatchViewModel(application: Application) : AndroidViewModel(application), DataClient.OnDataChangedListener {
    private val dataClient = Wearable.getDataClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val capabilityClient = Wearable.getCapabilityClient(application)

    var state by mutableStateOf<WatchState?>(null)
        private set

    var connection by mutableStateOf(Connection.SEARCHING)
        private set

    /** The values being entered, starting from the phone's suggestion for each new set. */
    var weight by mutableStateOf(0.0)
        private set
    var reps by mutableStateOf(0)
        private set
    var seconds by mutableStateOf(0)
        private set
    var distance by mutableStateOf(0.0)
        private set
    var height by mutableStateOf(0.0)
        private set
    var intensity by mutableStateOf(0)
        private set

    /** For one-sided exercises: "LEFT" or "RIGHT". */
    var side by mutableStateOf<String?>(null)
        private set

    fun chooseSide(value: String) {
        side = value
    }

    /** The heart rate and activity recording, from the watch's sensors. */
    val health = HealthTracker(application)

    /** What the recording measured, saved with the next set. */
    private var trackedNote: String = ""

    /** Whether the heart rate, activity and location permissions were given. */
    var hasHeartRatePermission by mutableStateOf(false)
        private set
    private var hasLocationPermission = false
    private var visible = false

    /** A short note after an action, such as "Saved ✓". */
    var note by mutableStateOf<String?>(null)
        private set

    /** When the running hold started, or null. */
    var holdStartedAt by mutableStateOf<Long?>(null)
        private set

    /** The seconds to hold; 0 counts up with no target. */
    var holdTargetSeconds by mutableStateOf(0)
        private set

    private var holdJob: Job? = null

    /** Which set the entered values belong to, so a new set starts from its own suggestion. */
    private var inputKey: String? = null
    private var restJob: Job? = null
    private var restEndsAt: Long? = null

    init {
        dataClient.addListener(this)
        refresh()
        viewModelScope.launch { health.reattach() }
    }

    override fun onCleared() {
        dataClient.removeListener(this)
        health.measureHeartRate(false)
    }

    fun onPermissions(heartRate: Boolean, location: Boolean) {
        hasHeartRatePermission = heartRate
        hasLocationPermission = location
        updateHeartRate()
    }

    /** The app is on screen (or dimmed on it), so the heart rate is worth measuring. */
    fun onVisible(isVisible: Boolean) {
        visible = isVisible
        updateHeartRate()
    }

    private fun updateHeartRate() {
        health.measureHeartRate(visible && hasHeartRatePermission && state?.active == true)
    }

    /** Reads the last state the phone published, and asks it for a fresh one. */
    fun refresh() {
        connection = Connection.SEARCHING
        viewModelScope.launch {
            try {
                val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WearPaths.STATE).build()
                val items = dataClient.getDataItems(uri).await()
                try {
                    items.forEach { item -> decode(item)?.let(::show) }
                } finally {
                    items.release()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing stored yet; the phone's answer to Sync follows.
            }
            send(WatchCommand.Sync)
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearPaths.STATE) {
                decode(event.dataItem)?.let(::show)
            }
        }
    }

    private fun decode(item: DataItem): WatchState? =
        DataMapItem.fromDataItem(item).dataMap.getByteArray(WearPaths.STATE_KEY)?.let(WearJson::decodeState)

    private fun show(new: WatchState) {
        val old = state
        if (old != null && new.sentAtMillis < old.sentAtMillis) return
        state = new
        WatchStore.saveState(getApplication(), WearJson.encode(new))
        WatchSession.update(getApplication(), new)
        updateHeartRate()
        connection = Connection.CONNECTED
        val key = "${new.epochDay}|${new.exerciseId}|${new.step}"
        if (key != inputKey) {
            inputKey = key
            weight = new.weight ?: 0.0
            reps = new.reps ?: 0
            seconds = new.seconds ?: 0
            distance = new.distance ?: 0.0
            height = new.height ?: 0.0
            intensity = new.intensity ?: 0
            side = new.side
            trackedNote = ""
            cancelHold()
            // A new exercise: a short buzz to look at the watch.
            if (old?.exerciseId != null && new.exerciseId != null && old.exerciseId != new.exerciseId) vibrate(0, 150)
        }
        watchRest(new.restEndsAtMillis)
    }

    /** Buzzes when the phone's rest timer runs out. */
    private fun watchRest(endsAt: Long?) {
        if (endsAt == restEndsAt) return
        restEndsAt = endsAt
        restJob?.cancel()
        val wait = (endsAt ?: return) - System.currentTimeMillis()
        if (wait <= 0) return
        restJob = viewModelScope.launch {
            delay(wait)
            vibrate(0, 400, 200, 400)
        }
    }

    /** A tap moves the fine step (0.5 kg, 1 lb, 1 level); a long press the big one (5 kg, 10 lb, 5 levels). */
    fun changeWeight(direction: Int, big: Boolean = false) {
        val step = if (big) state?.weightBigStep ?: 5.0 else state?.weightStep ?: 0.5
        weight = (((weight + direction * step) * 100).roundToInt() / 100.0).coerceAtLeast(0.0)
    }

    // Typed values, from the watch keyboard.

    fun typeWeight(value: Double) {
        weight = value.coerceAtLeast(0.0)
    }

    fun typeReps(value: Double) {
        reps = value.roundToInt().coerceAtLeast(0)
    }

    fun typeSeconds(value: Double) {
        seconds = value.roundToInt().coerceAtLeast(0)
    }

    fun typeDistance(value: Double) {
        distance = value.coerceAtLeast(0.0)
    }

    fun typeHeight(value: Double) {
        height = value.coerceAtLeast(0.0)
    }

    // The hold timer: counts down the seconds to hold, then keeps buzzing and counts on (shown as −0:10)
    // until Done, so a longer hold is timed too.

    fun startHold() {
        val now = System.currentTimeMillis()
        holdTargetSeconds = seconds
        holdStartedAt = now
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            if (holdTargetSeconds <= 0) return@launch
            delay(holdTargetSeconds * 1000L)
            while (holdStartedAt != null) {
                vibrate(0, 500)
                delay(1_500)
            }
        }
    }

    /** Ends the hold: the time held (target plus any extra) goes into the set, ready to log. */
    fun finishHold() {
        val started = holdStartedAt ?: return
        seconds = ((System.currentTimeMillis() - started) / 1000).toInt()
        cancelHold()
    }

    fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
        holdStartedAt = null
    }

    fun changeReps(direction: Int) {
        reps = (reps + direction).coerceAtLeast(0)
    }

    fun changeSeconds(direction: Int) {
        seconds = (seconds + direction * 5).coerceAtLeast(0)
    }

    fun changeDistance(direction: Int) {
        distance = (((distance + direction * 0.1) * 10).roundToInt() / 10.0).coerceAtLeast(0.0)
    }

    fun changeHeight(direction: Int) {
        height = (height + direction).coerceAtLeast(0.0)
    }

    fun changeIntensity(direction: Int) {
        intensity = (intensity + direction).coerceIn(1, 10)
    }

    /** Starts recording the current exercise with the watch's sensors. */
    fun startTracking() {
        val kind = state?.track ?: return
        viewModelScope.launch {
            health.start(kind, hasLocationPermission)?.let { note = it }
            updateHeartRate()
        }
    }

    /** Stops recording and fills in the time, distance and a note with steps and heart rate. */
    fun finishTracking() {
        viewModelScope.launch {
            val done = health.finish() ?: return@launch
            seconds = done.seconds(System.currentTimeMillis())
            done.distanceMeters?.takeIf { it > 0 }?.let { meters ->
                val perUnit = if (state?.distanceUnit == "mi") 1609.344 else 1000.0
                distance = ((meters / perUnit) * 100).roundToInt() / 100.0
            }
            trackedNote = done.summary
            if (intensity == 0) intensity = 5
            note = "Check the values, then log"
        }
    }

    /** Sends the set to the phone, which saves it and moves the guide on. */
    fun logSet() {
        val current = state ?: return
        val exerciseId = current.exerciseId ?: return
        val fields = current.fields
        val command = WatchCommand.Log(
            epochDay = current.epochDay,
            exerciseId = exerciseId,
            weight = weight.takeIf { fields.weight },
            reps = reps.takeIf { fields.reps && it > 0 },
            seconds = seconds.takeIf { fields.seconds && it > 0 },
            distance = distance.takeIf { fields.distance && it > 0 },
            height = height.takeIf { fields.height && it > 0 },
            intensity = intensity.takeIf { fields.intensity && it > 0 },
            note = trackedNote.ifEmpty { health.heartRate?.let { "♥ $it bpm" }.orEmpty() },
            isDrop = current.isDrop,
            side = side.takeIf { fields.perSide },
        )
        if (!fields.weight && command.reps == null && command.seconds == null && command.distance == null) {
            note = "Enter the values first"
            return
        }
        launchSend(command, done = "Saved ✓")
    }

    fun start() = launchSend(WatchCommand.Start)

    fun pause() = launchSend(WatchCommand.Pause)

    fun resume() = launchSend(WatchCommand.Resume)

    fun skip() {
        state?.exerciseId?.let { launchSend(WatchCommand.Skip(it), done = "Skipped") }
    }

    fun stop() = launchSend(WatchCommand.Stop, done = "Workout ended")

    fun clearNote() {
        note = null
    }

    private fun launchSend(command: WatchCommand, done: String? = null) {
        viewModelScope.launch {
            if (send(command) && done != null) note = done
        }
    }

    /** Sends a command to the phone app; false when no phone with the app is in reach. */
    private suspend fun send(command: WatchCommand): Boolean = try {
        val nodes = capabilityClient.getCapability(WearPaths.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
        val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        if (phone == null) {
            connection = Connection.NO_PHONE
            false
        } else {
            messageClient.sendMessage(phone.id, WearPaths.COMMAND, WearJson.encode(command)).await()
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        connection = Connection.NO_PHONE
        false
    }

    private fun vibrate(vararg pattern: Long) {
        val context = getApplication<Application>()
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
