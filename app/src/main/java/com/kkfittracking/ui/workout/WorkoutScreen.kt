@file:OptIn(ExperimentalMaterial3Api::class)

package com.kkfittracking.ui.workout

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kkfittracking.data.SportWarmUps
import com.kkfittracking.data.health.HealthConnect
import com.kkfittracking.model.Block
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.PainMoment
import com.kkfittracking.model.Routine
import com.kkfittracking.model.dayCompletion
import com.kkfittracking.model.groupDay
import com.kkfittracking.ui.components.PainRatingDialog
import com.kkfittracking.ui.components.formatFullDate
import com.kkfittracking.ui.components.rememberNotificationPermissionRequester
import com.kkfittracking.ui.guide.DayCompletionCard
import com.kkfittracking.ui.guide.GuideBar
import com.kkfittracking.ui.guide.MoveRestDialog
import java.time.LocalDate

@Composable
fun WorkoutScreen(
    onAddExercise: (LocalDate) -> Unit,
    onOpenExercise: (LocalDate, String) -> Unit,
    onOpenCalendar: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenBody: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenLoad: () -> Unit,
    onNewSuperset: (LocalDate) -> Unit,
    onSupersets: (LocalDate) -> Unit,
    viewModel: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val gameStats by viewModel.gameStats.collectAsStateWithLifecycle()
    val guide by viewModel.guide.collectAsStateWithLifecycle()
    val completion = remember(state.exercises) { dayCompletion(state.exercises) }
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val context = LocalContext.current
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshSteps()
    }
    // Steps keep coming in during the day: read them again whenever the screen comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSteps()
        onPauseOrDispose { }
    }
    var exerciseToDelete by remember { mutableStateOf<DayExercise?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var choosingRoutine by remember { mutableStateOf(false) }
    var routineWithSupersets by remember { mutableStateOf<Routine?>(null) }
    // Selecting exercises to remove several at once; null when not selecting.
    var selection by remember(state.date) { mutableStateOf<Set<String>?>(null) }
    var confirmRemoveSelected by remember { mutableStateOf(false) }
    var movingRest by remember { mutableStateOf(false) }
    val morningTendons by viewModel.morningTendons.collectAsStateWithLifecycle()
    var ratingMorning by remember { mutableStateOf(false) }
    var choosingWarmUp by remember { mutableStateOf(false) }
    val toggle = { exercise: DayExercise ->
        selection = selection?.let { if (exercise.workoutExerciseId in it) it - exercise.workoutExerciseId else it + exercise.workoutExerciseId }
    }

    LaunchedEffect(viewModel.guideOpens) {
        viewModel.guideOpens?.let {
            viewModel.consumeGuideOpen()
            onOpenExercise(state.date, it)
        }
    }

    Scaffold(
        topBar = {
            val selected = selection
            if (selected != null) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selection = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Stop selecting")
                        }
                    },
                    actions = {
                        val all = state.exercises.map { it.workoutExerciseId }.toSet()
                        TextButton(onClick = { selection = if (selected.containsAll(all)) emptySet() else all }) {
                            Text(if (selected.containsAll(all)) "None" else "All")
                        }
                        IconButton(onClick = { confirmRemoveSelected = true }, enabled = selected.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove the selected exercises")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Workout log") },
                    actions = {
                        IconButton(onClick = { onOpenCalendar(state.date) }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Calendar")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val close = { menuOpen = false }
                                MenuItem("Add a plan to this day", close) { choosingRoutine = true }
                                MenuItem("New superset", close) { onNewSuperset(state.date) }
                                MenuItem("Warm up for a sport…", close) { choosingWarmUp = true }
                                if (state.exercises.size >= 2) {
                                    MenuItem("Superset edit", close) { onSupersets(state.date) }
                                }
                                if (state.exercises.isNotEmpty()) {
                                    MenuItem("Select exercises to remove", close) { selection = emptySet() }
                                }
                                if (state.energy != null) {
                                    MenuItem("Send to Health Connect", close) {
                                        viewModel.sendToHealthConnect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                    }
                                }
                                if (completion.exercises.any { !it.isDone } && !guide.isActiveOn(state.date)) {
                                    MenuItem("Move what's left to another day", close) { movingRest = true }
                                }
                                if (state.exercises.isNotEmpty() && state.date != LocalDate.now()) {
                                    MenuItem("Copy exercises to today", close, viewModel::copyExercisesToToday)
                                }
                                HorizontalDivider()
                                MenuItem("Plans", close, onOpenRoutines)
                                MenuItem("Graphs & records", close, onOpenAnalysis)
                                MenuItem("Training load & tendons", close, onOpenLoad)
                                MenuItem("Body tracker", close, onOpenBody)
                                MenuItem("Progress & achievements", close, onOpenAchievements)
                                MenuItem("Settings", close, onOpenSettings)
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (state.exercises.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onAddExercise(state.date) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add exercise") },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            DateSwitcher(
                date = state.date,
                onPrevious = viewModel::showPreviousDay,
                onNext = viewModel::showNextDay,
                onToday = viewModel::showToday,
            )
            gameStats?.let { GameSummaryBar(it, onClick = onOpenAchievements) }
            state.energy?.let { EnergyBar(it) }
            StepsBar(
                steps = viewModel.steps,
                units = state.units,
                onConnect = { healthLauncher.launch(viewModel.healthPermissions) },
                onInstall = {
                    val uri = Uri.parse("market://details?id=${HealthConnect.PROVIDER_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")) }
                },
            )
            if (morningTendons.isNotEmpty() && state.date == LocalDate.now()) {
                MorningTendonCard(morningTendons, onRate = { ratingMorning = true }, onOpenLoad = onOpenLoad)
            }
            HorizontalDivider()
            when {
                state.isLoading -> Unit
                state.exercises.isEmpty() -> EmptyDay(
                    onAddExercise = { onAddExercise(state.date) },
                    onUseRoutine = { choosingRoutine = true },
                    onWarmUp = { choosingWarmUp = true },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selection == null) {
                        item(key = "guide") {
                            val session = guide.session
                            if (session != null) {
                                val guideDate = LocalDate.ofEpochDay(session.epochDay)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (guideDate != state.date) {
                                        Text(
                                            "Guided workout on ${formatFullDate(guideDate)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    GuideBar(
                                        state = guide,
                                        currentExerciseId = null,
                                        onGo = { onOpenExercise(guideDate, it) },
                                        onPause = viewModel::pauseGuide,
                                        onResume = viewModel::resumeGuide,
                                        onSkip = viewModel::skipInGuide,
                                        onStop = viewModel::stopGuide,
                                    )
                                }
                            } else if (completion.percent < 100) {
                                Button(
                                    onClick = {
                                        requestNotificationPermission()
                                        viewModel.startGuide()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (completion.done == 0) "Start workout" else "Continue workout")
                                }
                            }
                        }
                        if (completion.done > 0 && !guide.isActiveOn(state.date)) {
                            item(key = "completion") { DayCompletionCard(completion) }
                        }
                        item(key = "tools") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.exercises.size >= 2) {
                                    OutlinedButton(onClick = { onSupersets(state.date) }, modifier = Modifier.weight(1f)) {
                                        Text("Superset edit")
                                    }
                                }
                                OutlinedButton(onClick = { choosingWarmUp = true }) { Text("Warm-up") }
                                OutlinedButton(onClick = { selection = emptySet() }) { Text("Select") }
                            }
                        }
                    }
                    val blocks = groupDay(state.exercises)
                    items(blocks, key = { block ->
                        when (block) {
                            is Block.Single -> block.item.workoutExerciseId
                            is Block.Superset -> "superset-${block.id}"
                        }
                    }) { block ->
                        when (block) {
                            is Block.Single -> DayExerciseCard(
                                exercise = block.item,
                                units = state.units,
                                onClick = {
                                    if (selection != null) toggle(block.item) else onOpenExercise(state.date, block.item.exerciseId)
                                },
                                onDelete = { exerciseToDelete = block.item },
                                selected = selection?.let { block.item.workoutExerciseId in it },
                            )
                            is Block.Superset -> SupersetCard(
                                superset = block,
                                units = state.units,
                                onOpen = { if (selection != null) toggle(it) else onOpenExercise(state.date, it.exerciseId) },
                                selection = selection,
                                onDelete = { exerciseToDelete = it },
                                onUngroup = { viewModel.ungroupSuperset(block.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (choosingRoutine) {
        RoutineChooserDialog(
            routines = routines,
            onChoose = { routine ->
                choosingRoutine = false
                if (routine.supersetCount > 0) {
                    routineWithSupersets = routine
                } else {
                    viewModel.applyRoutine(routine.id, withSupersets = false)
                }
            },
            onManageRoutines = {
                choosingRoutine = false
                onOpenRoutines()
            },
            onDismiss = { choosingRoutine = false },
        )
    }

    routineWithSupersets?.let { routine ->
        val count = routine.supersetCount
        AlertDialog(
            onDismissRequest = { routineWithSupersets = null },
            title = { Text("Add ${routine.name}") },
            text = {
                Text(
                    if (count == 1) {
                        "This plan has a superset. Do it as a superset today, or its exercises one by one?"
                    } else {
                        "This plan has $count supersets. Do them as supersets today, or the exercises one by one?"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        routineWithSupersets = null
                        viewModel.applyRoutine(routine.id, withSupersets = true)
                    },
                ) { Text("With supersets") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        routineWithSupersets = null
                        viewModel.applyRoutine(routine.id, withSupersets = false)
                    },
                ) { Text("One by one") }
            },
        )
    }

    if (choosingWarmUp) {
        AlertDialog(
            onDismissRequest = { choosingWarmUp = false },
            title = { Text("Warm up for…") },
            text = {
                Column {
                    Text(
                        "5–8 minutes at the top of the day, then the guided workout starts with it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SportWarmUps.all.forEach { warmUp ->
                        TextButton(
                            onClick = {
                                choosingWarmUp = false
                                requestNotificationPermission()
                                viewModel.startWarmUp(warmUp)
                            },
                        ) { Text("${warmUp.sport} · ${warmUp.exercises.size} exercises") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingWarmUp = false }) { Text("Cancel") } },
        )
    }

    if (ratingMorning && morningTendons.isNotEmpty()) {
        PainRatingDialog(
            tendons = morningTendons,
            initialMoment = PainMoment.MORNING,
            onSave = { tendons, moment, score ->
                ratingMorning = false
                viewModel.ratePain(tendons, moment, score)
            },
            onDismiss = { ratingMorning = false },
        )
    }

    if (movingRest) {
        MoveRestDialog(
            from = state.date,
            leftCount = completion.exercises.count { !it.isDone },
            onMove = { to ->
                movingRest = false
                viewModel.moveUnfinished(to) { moved ->
                    val what = if (moved == 1) "1 exercise" else "$moved exercises"
                    Toast.makeText(context, "Moved $what to ${formatFullDate(to)}", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { movingRest = false },
        )
    }

    val toRemove = selection.orEmpty()
    if (confirmRemoveSelected && toRemove.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmRemoveSelected = false },
            title = { Text("Remove ${toRemove.size} exercises?") },
            text = { Text("They and their sets will be removed from this day.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExercises(toRemove)
                        confirmRemoveSelected = false
                        selection = null
                    },
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveSelected = false }) { Text("Cancel") } },
        )
    }

    exerciseToDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = { Text("Remove exercise?") },
            text = { Text("${exercise.exerciseName} and its ${exercise.sets.size} set(s) will be removed from this day.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExercise(exercise.workoutExerciseId)
                        exerciseToDelete = null
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToDelete = null }) { Text("Cancel") }
            },
        )
    }
}
