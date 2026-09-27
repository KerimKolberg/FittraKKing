@file:OptIn(ExperimentalMaterial3Api::class)

package com.kkfittracking.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kkfittracking.R
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.HistorySession
import com.kkfittracking.model.Side
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.dropStepLabel
import com.kkfittracking.model.formatDuration
import com.kkfittracking.model.formatSet
import com.kkfittracking.model.parseDecimal
import com.kkfittracking.model.planProgress
import com.kkfittracking.model.setLabels
import com.kkfittracking.model.supersetProgress
import com.kkfittracking.timer.IntervalTimerState
import com.kkfittracking.timer.RestTimerState
import com.kkfittracking.ui.components.formatShortDate
import com.kkfittracking.ui.components.rememberNotificationPermissionRequester
import com.kkfittracking.ui.guide.GuideBar
import java.time.LocalDate

@Composable
fun ExerciseLogScreen(
    onBack: () -> Unit,
    onEditExercise: (String) -> Unit,
    onSwitchExercise: (String) -> Unit,
    viewModel: ExerciseLogViewModel = viewModel(factory = ExerciseLogViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timer by viewModel.timerState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showTimer by remember { mutableStateOf(false) }
    var showPlans by remember { mutableStateOf(false) }
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val intervals by viewModel.intervalState.collectAsStateWithLifecycle()
    val guide by viewModel.guide.collectAsStateWithLifecycle()
    var editingPlan by remember { mutableStateOf(false) }
    var editingDrops by remember { mutableStateOf(false) }
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.switchTo) {
        viewModel.switchTo?.let {
            viewModel.consumeSwitch()
            onSwitchExercise(it)
        }
    }

    LaunchedEffect(viewModel.celebration) {
        viewModel.celebration?.let { message ->
            viewModel.consumeCelebration()
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.exercise?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatShortDate(viewModel.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TimerAction(timer = timer, onClick = { showTimer = true })
                    IconButton(onClick = { showPlans = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Add to plans")
                    }
                    IconButton(onClick = { onEditExercise(viewModel.exerciseId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit exercise")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Track") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("History") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Progress") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("About") })
            }
            if (guide.isActiveOn(viewModel.date)) {
                GuideBar(
                    state = guide,
                    currentExerciseId = viewModel.exerciseId,
                    onGo = onSwitchExercise,
                    onPause = viewModel::pauseGuide,
                    onResume = viewModel::resumeGuide,
                    onSkip = viewModel::skipInGuide,
                    onStop = viewModel::stopGuide,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            if (state.superset.isNotEmpty()) {
                SupersetBar(state.superset, currentId = viewModel.exerciseId, onSelect = onSwitchExercise)
            }
            val transitionLabel = timer.label
            if (timer.isRunning && transitionLabel != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(transitionLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = formatDuration(timer.remainingSeconds),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            val exercise = state.exercise
            when {
                exercise == null -> Unit
                selectedTab == 0 -> TrackTab(
                    viewModel = viewModel,
                    state = state,
                    type = exercise.type,
                    intervals = intervals,
                    onEditPlan = { editingPlan = true },
                    onEditDrops = { editingDrops = true },
                    onStartIntervals = {
                        requestNotificationPermission()
                        viewModel.startIntervals()
                    },
                    onSave = {
                        if (viewModel.selectedSetId == null && state.settings.autoStartRestTimer) {
                            requestNotificationPermission()
                        }
                        viewModel.save()
                    },
                )
                selectedTab == 1 -> HistoryTab(state = state, type = exercise.type, currentDate = viewModel.date)
                selectedTab == 2 -> ProgressTab(history = state.history, type = exercise.type, units = state.units)
                else -> AboutTab(
                    exercise = exercise,
                    sectionName = categories.firstOrNull { it.id == exercise.categoryId }?.name,
                    onSaveDescription = viewModel::saveDescription,
                    onAddLink = viewModel::addLink,
                    onRemoveLink = viewModel::removeLink,
                    onEditExercise = { onEditExercise(viewModel.exerciseId) },
                )
            }
        }
    }

    if (showPlans) {
        PlansDialog(
            exerciseName = state.exercise?.name.orEmpty(),
            exerciseId = viewModel.exerciseId,
            plans = plans,
            onToggle = viewModel::setInPlan,
            onCreatePlan = viewModel::createPlanWithExercise,
            onDismiss = { showPlans = false },
        )
    }

    val exercise = state.exercise
    if ((editingPlan || editingDrops) && exercise != null) {
        SetPlanDialog(
            plan = state.plan,
            type = exercise.type,
            units = state.units,
            defaultRestSeconds = state.settings.restTimerSeconds,
            defaultPercent = state.settings.dropSetPercent,
            enteredWeightKg = parseDecimal(viewModel.input.weight)?.let(state.units::weightToKg)
                ?: state.sets.lastOrNull()?.values?.weightKg
                ?: state.previousSession?.sets?.lastOrNull()?.values?.weightKg,
            inSuperset = state.superset.isNotEmpty(),
            onSave = {
                editingPlan = false
                editingDrops = false
                viewModel.savePlan(it)
            },
            onClear = {
                editingPlan = false
                viewModel.clearPlan()
            },
            onDismiss = {
                editingPlan = false
                editingDrops = false
            },
            dropOnly = editingDrops,
        )
    }

    if (showTimer) {
        RestTimerDialog(
            timer = timer,
            defaultSeconds = state.settings.restTimerSeconds,
            onStart = { seconds ->
                requestNotificationPermission()
                viewModel.startTimer(seconds)
            },
            onStop = viewModel::stopTimer,
            onAddSeconds = viewModel::addTimerSeconds,
            onDismiss = { showTimer = false },
        )
    }
}

@Composable
private fun TimerAction(timer: RestTimerState, onClick: () -> Unit) {
    if (timer.isRunning) {
        TextButton(onClick = onClick) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = "Rest timer")
            Spacer(Modifier.width(4.dp))
            Text(formatDuration(timer.remainingSeconds))
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(painterResource(R.drawable.ic_timer), contentDescription = "Rest timer")
        }
    }
}

@Composable
private fun TrackTab(
    viewModel: ExerciseLogViewModel,
    state: ExerciseLogUiState,
    type: ExerciseType,
    intervals: IntervalTimerState,
    onEditPlan: () -> Unit,
    onEditDrops: () -> Unit,
    onStartIntervals: () -> Unit,
    onSave: () -> Unit,
) {
    val input = viewModel.input
    val units = state.units
    val isEditing = viewModel.selectedSetId != null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.exercise?.let { exercise ->
                    val hints = listOfNotNull(
                        exercise.tempo.takeIf { it.isNotBlank() }?.let { "Tempo $it (down-pause-up-pause, seconds)" },
                        "Log each side as its own set".takeIf { exercise.perSide },
                    )
                    if (hints.isNotEmpty()) {
                        Text(
                            text = hints.joinToString("\n"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (type == ExerciseType.INTERVALS) {
                        IntervalCard(
                            timer = intervals,
                            plan = state.plan,
                            exerciseId = exercise.id,
                            onStart = onStartIntervals,
                            onPause = viewModel::pauseIntervals,
                            onResume = viewModel::resumeIntervals,
                            onFinish = viewModel::finishIntervals,
                            onReset = viewModel::resetIntervals,
                            onChange = { high, low, rounds -> viewModel.changeIntervals(high, low, rounds) },
                        )
                    } else if (!type.isSession) {
                        SetPlanCard(
                            plan = state.plan,
                            type = type,
                            progress = state.supersetContext?.let { supersetProgress(it, exercise.id, state.plan, state.sets) }
                                ?: planProgress(state.plan, state.sets, state.dueDrop?.takeIf { viewModel.dropMode }),
                            inSuperset = state.superset.isNotEmpty(),
                            defaultPercent = state.settings.dropSetPercent,
                            units = units,
                            onEdit = onEditPlan,
                        )
                    }
                }
                if (state.exercise?.perSide == true) {
                    SideChooser(input.side, onChoose = viewModel::chooseSide)
                }
                if (type.usesWeight) {
                    StepperField(
                        label = if (type == ExerciseType.WEIGHT_REPS) {
                            "Weight (${units.weightUnit})"
                        } else {
                            "Added weight (${units.weightUnit}, optional)"
                        },
                        value = input.weight,
                        onValueChange = { viewModel.updateInput(input.copy(weight = it)) },
                        onDecrement = { viewModel.adjustWeight(-1) },
                        onIncrement = { viewModel.adjustWeight(1) },
                        keyboardType = KeyboardType.Decimal,
                    )
                    WeightUnitChooser(
                        current = state.exercise?.weightUnits,
                        appUnits = state.settings.unitSystem,
                        onChoose = viewModel::setWeightUnit,
                    )
                }
                if (type.usesReps) {
                    StepperField(
                        label = type.repsLabel,
                        value = input.reps,
                        onValueChange = { viewModel.updateInput(input.copy(reps = it)) },
                        onDecrement = { viewModel.adjustReps(-1) },
                        onIncrement = { viewModel.adjustReps(1) },
                        keyboardType = KeyboardType.Number,
                    )
                }
                if (type.usesDistance) {
                    OutlinedTextField(
                        value = input.distance,
                        onValueChange = { viewModel.updateInput(input.copy(distance = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Distance (${units.distanceUnit})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (type.usesHeight) {
                    OutlinedTextField(
                        value = input.height,
                        onValueChange = { viewModel.updateInput(input.copy(height = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Height or distance (${units.lengthUnit}, optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (type.usesTime) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = input.minutes,
                            onValueChange = { viewModel.updateInput(input.copy(minutes = it)) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = input.seconds,
                            onValueChange = { viewModel.updateInput(input.copy(seconds = it)) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Seconds") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    // Holds and timed sets: time them here, the result fills the fields above.
                    if (type == ExerciseType.TIME || type == ExerciseType.TIME_WEIGHT) {
                        val planned = (input.minutes.toIntOrNull() ?: 0) * 60 + (input.seconds.toIntOrNull() ?: 0)
                        HoldTimerCard(targetSeconds = planned, onDone = viewModel::setHeldSeconds)
                    }
                }
                if (type.usesIntensity) {
                    OutlinedTextField(
                        value = input.rpe,
                        onValueChange = { viewModel.updateInput(input.copy(rpe = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Intensity, 1 (easy) to 10 (max), optional") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = input.note,
                        onValueChange = { viewModel.updateInput(input.copy(note = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Note, e.g. \"doubles, won 6-4\" (optional)") },
                    )
                }
                viewModel.errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (!isEditing && viewModel.canUseDropSets(state)) {
                    val step = state.plan.dropStepLabel(state.settings.dropSetPercent, units)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = viewModel.dropMode,
                            onClick = viewModel::toggleDropMode,
                            label = {
                                Text(if (viewModel.dropMode) "Drop sets on: $step each" else "Drop set ($step)")
                            },
                        )
                        IconButton(onClick = onEditDrops) {
                            Icon(Icons.Default.Edit, contentDescription = "Drop set settings")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Text(if (isEditing) "Update" else "Save")
                    }
                    if (isEditing) {
                        OutlinedButton(onClick = viewModel::deleteSelectedSet, modifier = Modifier.weight(1f)) {
                            Text("Delete")
                        }
                    } else {
                        OutlinedButton(onClick = viewModel::clearInput, modifier = Modifier.weight(1f)) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        state.previousSession?.let { previous ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "Last time · ${formatShortDate(previous.date)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = previous.sets.joinToString(", ") { formatSet(it.values, type, units) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            HorizontalDivider()
        }

        if (state.sets.isEmpty()) {
            item {
                Text(
                    text = "No sets logged yet. Enter your values and tap Save.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val labels = setLabels(state.sets)
        itemsIndexed(state.sets, key = { _, set -> set.id }) { index, set ->
            val selected = set.id == viewModel.selectedSetId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { viewModel.selectSet(set) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = labels[index],
                    modifier = Modifier.width(if (set.values.isDropSet) 48.dp else 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (set.values.isDropSet) TextAlign.End else TextAlign.Start,
                )
                if (set.values.isDropSet) Spacer(Modifier.width(8.dp))
                Text(
                    text = formatSet(set.values, type, units),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (set.id in state.recordSetIds) RecordBadge()
            }
        }
    }
}

@Composable
private fun StepperField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    keyboardType: KeyboardType,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onDecrement) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            label = { Text(label) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        FilledTonalIconButton(onClick = onIncrement) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun HistoryTab(state: ExerciseLogUiState, type: ExerciseType, currentDate: LocalDate) {
    if (state.history.isEmpty()) {
        Text(
            text = "No history yet for this exercise.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        historySessions(state.history, type, state.units, currentDate, state.recordSetIds)
    }
}

private fun LazyListScope.historySessions(
    sessions: List<HistorySession>,
    type: ExerciseType,
    units: UnitSystem,
    currentDate: LocalDate,
    recordSetIds: Set<String>,
) {
    sessions.forEach { session ->
        item(key = session.date.toEpochDay()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = formatShortDate(session.date) + if (session.date == currentDate) " (this workout)" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val labels = setLabels(session.sets)
                    session.sets.forEachIndexed { index, set ->
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = labels[index],
                                modifier = Modifier.width(28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(formatSet(set.values, type, units))
                            if (set.id in recordSetIds) RecordBadge()
                        }
                    }
                }
            }
        }
    }
}

/** The exercises of a superset, with the current one highlighted. Tap one to switch to it. */
@Composable
private fun SupersetBar(members: List<DayExercise>, currentId: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        val position = members.indexOfFirst { it.exerciseId == currentId } + 1
        Text(
            text = "Superset · exercise $position of ${members.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(members, key = { _, member -> member.exerciseId }) { index, member ->
                FilterChip(
                    selected = member.exerciseId == currentId,
                    onClick = { if (member.exerciseId != currentId) onSelect(member.exerciseId) },
                    label = { Text("${index + 1}. ${member.exerciseName}") },
                )
            }
        }
    }
}

/** Left or right: which side this set of a one-arm or one-leg exercise is. Both sides make one set. */
@Composable
private fun SideChooser(side: Side?, onChoose: (Side) -> Unit) {
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Side.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = side == option,
                    onClick = { onChoose(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, Side.entries.size),
                ) { Text(option.label) }
            }
        }
        Text(
            "A left and a right set count as one set.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * This exercise's weight unit: kg, lb, or a machine's own levels (pin numbers), for machines that
 * show neither. Remembered per exercise.
 */
@Composable
private fun WeightUnitChooser(current: UnitSystem?, appUnits: UnitSystem, onChoose: (UnitSystem?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(
                "Unit: " + when (current) {
                    null -> "${appUnits.weightUnit} (app setting)"
                    UnitSystem.LEVELS -> "machine levels"
                    else -> current.weightUnit
                } + " ▾",
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                null to "App setting (${appUnits.weightUnit})",
                UnitSystem.METRIC to "kg",
                UnitSystem.IMPERIAL to "lb",
                UnitSystem.LEVELS to "Machine levels (pin or stack number)",
            ).forEach { (unit, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onChoose(unit)
                    },
                )
            }
        }
    }
}

/** Marks a set that beat every earlier set of the exercise. */
@Composable
private fun RecordBadge() {
    Icon(
        imageVector = Icons.Default.Star,
        contentDescription = "Personal record",
        modifier = Modifier
            .padding(start = 8.dp)
            .size(18.dp),
        tint = MaterialTheme.colorScheme.secondary,
    )
}
