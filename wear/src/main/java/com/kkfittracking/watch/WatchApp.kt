package com.kkfittracking.watch

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.input.RemoteInputIntentHelper
import com.kkfittracking.wear.WatchState
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun WatchApp(viewModel: WatchViewModel, ambient: Boolean) {
    MaterialTheme {
        if (ambient) AmbientScreen(viewModel.state) else InteractiveScreen(viewModel)
    }
}

@Composable
private fun InteractiveScreen(viewModel: WatchViewModel) {
    val state = viewModel.state
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            viewModel.note?.let { note ->
                item {
                    LaunchedEffect(note) {
                        delay(2_000)
                        viewModel.clearNote()
                    }
                    Text(note, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center)
                }
            }
            when {
                state == null -> item { Connecting(viewModel) }
                !state.active -> item { NotRunning(state, viewModel) }
                state.paused -> item { Paused(state, viewModel) }
                state.allDone -> item { AllDone(state, viewModel) }
                else -> item { Guiding(state, viewModel) }
            }
        }
    }
}

@Composable
private fun Connecting(viewModel: WatchViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (viewModel.connection == Connection.NO_PHONE) {
                "Phone not found. Keep Bluetooth on and FitTraKKing installed on the phone."
            } else {
                "Connecting to your phone…"
            },
            textAlign = TextAlign.Center,
        )
        WideChip("Try again", onClick = viewModel::refresh)
    }
}

@Composable
private fun NotRunning(state: WatchState, viewModel: WatchViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No workout running", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        if (state.exercisesToday > 0) {
            val count = if (state.exercisesToday == 1) "1 exercise" else "${state.exercisesToday} exercises"
            Text("Today: $count", style = MaterialTheme.typography.caption2)
            WideChip("▶ Start workout", primary = true, onClick = viewModel::start)
        } else {
            Text(
                "Nothing on today's log yet. Add a plan to today on the phone.",
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
            )
            WideChip("Refresh", onClick = viewModel::refresh)
        }
    }
}

@Composable
private fun Paused(state: WatchState, viewModel: WatchViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⏸ Paused", style = MaterialTheme.typography.title2)
        Text("${state.percent}% done · ${formatTime(state.trainingMillis)}", style = MaterialTheme.typography.caption2)
        if (state.exerciseName.isNotEmpty()) {
            Text("Next: ${state.exerciseName}", style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
        }
        WideChip("Resume", primary = true, onClick = viewModel::resume)
        StopChip(viewModel)
    }
}

@Composable
private fun AllDone(state: WatchState, viewModel: WatchViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🏁 All done", style = MaterialTheme.typography.title2)
        Text("${state.percent}% of the plan · ${trainingTime(state)}", style = MaterialTheme.typography.caption2)
        StopChip(viewModel, label = "Finish workout")
    }
}

@Composable
private fun Guiding(state: WatchState, viewModel: WatchViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val heart = viewModel.health.heartRate?.let { " · ♥ $it" }.orEmpty()
        Text(
            "${state.position}/${state.of} · ${state.percent}% · ${trainingTime(state)}$heart",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
        Text(
            state.exerciseName,
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(state.step, style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.secondary)
        if (state.upcoming.isNotEmpty()) {
            // What comes after, to get ready and set up the equipment.
            Text(
                "Then: " + state.upcoming.joinToString(" → "),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RestCountdown(state)
        val tracking = viewModel.health.tracking
        if (tracking != null) {
            Tracking(tracking, state, viewModel)
        } else {
            if (state.track != null) WideChip("▶ Record with the watch", onClick = viewModel::startTracking)
            Inputs(state, viewModel)
            WideChip(if (state.isDrop) "✓ Log drop set" else "✓ Log set", primary = true, onClick = viewModel::logSet)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactButton(onClick = viewModel::pause) { Text("⏸") }
            if (!state.isDrop) CompactButton(onClick = viewModel::skip) { Text("⏭") }
        }
        StopChip(viewModel)
    }
}

/** Steppers for the values this exercise records. Tap a value to type it; hold + or − for bigger steps. */
@Composable
private fun Inputs(state: WatchState, viewModel: WatchViewModel) {
    val fields = state.fields
    if (fields.perSide) {
        // Left or right: a left and a right set make one set.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("LEFT" to "Left", "RIGHT" to "Right").forEach { (value, label) ->
                Chip(
                    onClick = { viewModel.chooseSide(value) },
                    label = { Text(label) },
                    colors = if (viewModel.side == value) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
    if (fields.weight) {
        Stepper(
            value = "${formatNumber(viewModel.weight)} ${state.weightUnit}",
            onChange = { direction, big -> viewModel.changeWeight(direction, big) },
            typeLabel = "Weight (${state.weightUnit})",
            onTyped = viewModel::typeWeight,
        )
    }
    if (fields.reps) {
        Stepper(
            value = "${viewModel.reps} ${fields.repsLabel.lowercase()}",
            onChange = { direction, big -> viewModel.changeReps(if (big) direction * 5 else direction) },
            typeLabel = fields.repsLabel,
            onTyped = viewModel::typeReps,
        )
    }
    if (fields.distance) {
        Stepper(
            value = "${formatNumber(viewModel.distance)} ${state.distanceUnit}",
            onChange = { direction, big -> viewModel.changeDistance(if (big) direction * 10 else direction) },
            typeLabel = "Distance (${state.distanceUnit})",
            onTyped = viewModel::typeDistance,
        )
    }
    if (fields.height) {
        Stepper(
            value = "${formatNumber(viewModel.height)} ${state.heightUnit}",
            onChange = { direction, big -> viewModel.changeHeight(if (big) direction * 10 else direction) },
            typeLabel = "Height (${state.heightUnit})",
            onTyped = viewModel::typeHeight,
        )
    }
    if (fields.seconds) {
        HoldTimer(viewModel)
        if (viewModel.holdStartedAt == null) {
            Stepper(
                value = formatTime(viewModel.seconds * 1000L),
                onChange = { direction, big -> viewModel.changeSeconds(if (big) direction * 6 else direction) },
                typeLabel = "Seconds",
                onTyped = viewModel::typeSeconds,
            )
        }
    }
    if (fields.intensity) {
        val effort = viewModel.intensity.takeIf { it > 0 }?.let { "Effort $it/10" } ?: "Effort –"
        Stepper(effort, onChange = { direction, _ -> viewModel.changeIntensity(direction) })
    }
}

/**
 * A timer for holds: counts down the seconds set above, then buzzes until Done and keeps counting,
 * shown as −0:10, so a longer hold is timed too. Done puts the time held into the set.
 */
@Composable
private fun HoldTimer(viewModel: WatchViewModel) {
    val started = viewModel.holdStartedAt
    if (started == null) {
        val target = viewModel.seconds
        WideChip(if (target > 0) "⏱ Start ${formatTime(target * 1000L)} timer" else "⏱ Start timer") { viewModel.startHold() }
        return
    }
    val elapsed = rememberNow() - started
    val target = viewModel.holdTargetSeconds * 1000L
    val left = target - elapsed
    val over = target > 0 && left <= 0
    Text(
        text = when {
            target == 0L -> formatTime(elapsed)
            over -> "−" + formatTime(-left)
            else -> formatTime(left + 999)
        },
        style = MaterialTheme.typography.display2,
        color = if (over) MaterialTheme.colors.error else MaterialTheme.colors.onSurface,
    )
    if (over) Text("Held ${formatTime(elapsed)}", style = MaterialTheme.typography.caption2)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(
            onClick = viewModel::finishHold,
            label = { Text("✓ Done") },
            colors = ChipDefaults.primaryChipColors(),
        )
        CompactButton(onClick = viewModel::cancelHold) { Text("✕") }
    }
}

/** Live values while the watch records an activity, and the button to finish it. */
@Composable
private fun Tracking(tracking: TrackedActivity, state: WatchState, viewModel: WatchViewModel) {
    val now = rememberNow()
    Text(formatTime(tracking.seconds(now) * 1000L), style = MaterialTheme.typography.display3)
    val perUnit = if (state.distanceUnit == "mi") 1609.344 else 1000.0
    val details = listOfNotNull(
        tracking.distanceMeters?.let { "${formatNumber(((it / perUnit) * 100).toInt() / 100.0)} ${state.distanceUnit}" },
        tracking.steps?.let { "$it steps" },
        tracking.heartRate?.let { "♥ $it" },
    )
    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
    WideChip("■ Finish", primary = true, onClick = viewModel::finishTracking)
}

/** The rest the phone's timer is counting down, and what comes after it. */
@Composable
private fun RestCountdown(state: WatchState) {
    val endsAt = state.restEndsAtMillis ?: return
    val now = rememberNow()
    val left = endsAt - now
    if (left <= 0) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Rest ${formatTime(left + 999)}", style = MaterialTheme.typography.title1, fontWeight = FontWeight.Bold)
        state.restLabel?.let { Text(it, style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center) }
    }
}

/** − value +: a tap on − or + moves one step, a long press a big one; a tap on the value opens the keyboard. */
@Composable
private fun Stepper(
    value: String,
    onChange: (direction: Int, big: Boolean) -> Unit,
    typeLabel: String? = null,
    onTyped: (Double) -> Unit = {},
) {
    val type = rememberNumberInput(typeLabel.orEmpty(), onTyped)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StepButton("−", onClick = { onChange(-1, false) }, onLongClick = { onChange(-1, true) })
        Text(
            value,
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(84.dp)
                .then(if (typeLabel != null) Modifier.clickable { type() } else Modifier),
        )
        StepButton("+", onClick = { onChange(1, false) }, onLongClick = { onChange(1, true) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepButton(label: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colors.primary)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colors.onPrimary, style = MaterialTheme.typography.title3)
    }
}

/** Opens the watch keyboard (or voice) for a number; "," works as a decimal point. */
@Composable
private fun rememberNumberInput(label: String, onValue: (Double) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        RemoteInput.getResultsFromIntent(data)?.getCharSequence(NUMBER_KEY)?.toString()
            ?.trim()?.replace(',', '.')?.toDoubleOrNull()?.let(onValue)
    }
    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val input = RemoteInput.Builder(NUMBER_KEY).setLabel(label).build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
        launcher.launch(intent)
    }
}

private const val NUMBER_KEY = "number"

@Composable
private fun WideChip(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Stop asks for a second tap, so a brush of the sleeve cannot end the workout. */
@Composable
private fun StopChip(viewModel: WatchViewModel, label: String = "Stop workout") {
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        LaunchedEffect(Unit) {
            delay(3_000)
            confirming = false
        }
    }
    WideChip(if (confirming) "Tap again to end" else label) {
        if (confirming) {
            confirming = false
            viewModel.stop()
        } else {
            confirming = true
        }
    }
}

/** Dimmed always-on view: black, little text, no buttons. Updated about once a minute. */
@Composable
private fun AmbientScreen(state: WatchState?) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state == null || !state.active -> Text("FitTraKKing", color = Color.White)
                state.paused -> Text("Paused", color = Color.White)
                state.allDone -> Text("All done", color = Color.White)
                else -> {
                    Text(state.exerciseName, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                    Text(state.step, color = Color.Gray)
                    state.restEndsAtMillis?.takeIf { it > System.currentTimeMillis() }?.let {
                        Text("Resting", color = Color.Gray)
                    }
                }
            }
        }
    }
}

/** The current time, updated every second while shown. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun trainingTime(state: WatchState): String {
    val since = state.trainingSinceMillis ?: return formatTime(state.trainingMillis)
    return formatTime(rememberNow() - since)
}

private fun formatTime(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
