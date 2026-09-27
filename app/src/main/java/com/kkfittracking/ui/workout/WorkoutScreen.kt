@file:OptIn(ExperimentalMaterial3Api::class)

package com.kkfittracking.ui.workout

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kkfittracking.data.health.HealthConnect
import com.kkfittracking.model.Block
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.DayWorkouts
import com.kkfittracking.model.GameStats
import com.kkfittracking.model.Routine
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.dayCompletion
import com.kkfittracking.model.formatNumber
import com.kkfittracking.model.formatSet
import com.kkfittracking.model.groupDay
import com.kkfittracking.model.setLabels
import com.kkfittracking.ui.components.formatFullDate
import com.kkfittracking.ui.components.relativeDayName
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
            HorizontalDivider()
            when {
                state.isLoading -> Unit
                state.exercises.isEmpty() -> EmptyDay(
                    onAddExercise = { onAddExercise(state.date) },
                    onUseRoutine = { choosingRoutine = true },
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

/** The day's training time and estimated calories. */
@Composable
private fun EnergyBar(energy: DayWorkouts) {
    val kinds = energy.sessions.map { it.kind.label }.distinct().joinToString(", ")
    Text(
        text = "🔥 About ${energy.kcal} kcal · ${energy.minutes} min" + (if (kinds.isNotEmpty()) " · $kinds" else "") +
            if (energy.bodyweightKnown) "" else " (typical bodyweight)",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** The day's steps and distance from Health Connect, or a button to connect it. */
@Composable
private fun StepsBar(steps: StepsState, units: UnitSystem, onConnect: () -> Unit, onInstall: () -> Unit) {
    val (text, action) = when (steps) {
        StepsState.Hidden -> return
        StepsState.NeedsInstall -> "👣 Daily steps need Health Connect" to ("Install" to onInstall)
        StepsState.NeedsPermission -> "👣 Show daily steps from Health Connect" to ("Connect" to onConnect)
        is StepsState.Loaded -> {
            val activity = steps.activity
            val distance = activity.meters?.takeIf { it > 0 }?.let {
                " · ${formatNumber(units.distanceFromMeters(it))} ${units.distanceUnit}"
            }.orEmpty()
            "👣 ${"%,d".format(activity.steps)} steps$distance" to null
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}

@Composable
private fun DateSwitcher(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToday)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = relativeDayName(date),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatFullDate(date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun EmptyDay(onAddExercise: () -> Unit, onUseRoutine: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Workout log empty", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing logged on this day yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddExercise) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start new workout")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onUseRoutine) {
            Text("Use a plan")
        }
    }
}

@Composable
private fun DayExerciseCard(
    exercise: DayExercise,
    units: UnitSystem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    selected: Boolean? = null,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ExerciseBody(exercise, units, onDelete, selected = selected)
    }
}

/** Several exercises done back to back, shown together in one card. */
@Composable
private fun SupersetCard(
    superset: Block.Superset<DayExercise>,
    units: UnitSystem,
    onOpen: (DayExercise) -> Unit,
    onDelete: (DayExercise) -> Unit,
    onUngroup: () -> Unit,
    /** The selected exercises while selecting; null when not selecting. */
    selection: Set<String>? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Superset · ${superset.items.size} exercises",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Superset options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Ungroup superset") },
                        onClick = {
                            menuOpen = false
                            onUngroup()
                        },
                    )
                }
            }
        }
        superset.items.forEachIndexed { index, exercise ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 22.dp))
            Box(Modifier.clickable { onOpen(exercise) }) {
                ExerciseBody(
                    exercise, units, onDelete = { onDelete(exercise) }, position = index + 1,
                    selected = selection?.let { exercise.workoutExerciseId in it },
                )
            }
        }
    }
}

/** An exercise's name, menu and sets, with its category color on the left. */
@Composable
private fun ExerciseBody(
    exercise: DayExercise,
    units: UnitSystem,
    onDelete: () -> Unit,
    position: Int? = null,
    /** While selecting: whether it is selected; null when not selecting. */
    selected: Boolean? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(Color(exercise.categoryColor)),
        )
        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = position?.let { "$it. ${exercise.exerciseName}" } ?: exercise.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (selected != null) {
                    Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                } else Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove from day") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            if (exercise.sets.isEmpty()) {
                Text(
                    text = "Not started yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val labels = setLabels(exercise.sets)
            exercise.sets.forEachIndexed { index, set ->
                Row(modifier = Modifier.padding(end = 16.dp, top = 2.dp)) {
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text = formatSet(set.values, exercise.exerciseType, exercise.weightUnits ?: units),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineChooserDialog(
    routines: List<Routine>,
    onChoose: (Routine) -> Unit,
    onManageRoutines: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a plan to this day") },
        text = {
            if (routines.isEmpty()) {
                Text("You have no plans yet. Create one, or add the starter plans.")
            } else {
                LazyColumn {
                    items(routines, key = { it.id }) { routine ->
                        ListItem(
                            modifier = Modifier.clickable { onChoose(routine) },
                            headlineContent = { Text(routine.name) },
                            supportingContent = {
                                val supersets = routine.supersetCount
                                Text(
                                    "${routine.exercises.size} exercises" + when (supersets) {
                                        0 -> ""
                                        1 -> " · 1 superset"
                                        else -> " · $supersets supersets"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onManageRoutines) { Text("Manage plans") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MenuItem(text: String, closeMenu: () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = {
            closeMenu()
            onClick()
        },
    )
}

/** Level, XP and this week's goal at a glance. Tapping it opens the achievements. */
@Composable
private fun GameSummaryBar(stats: GameStats, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Level ${stats.level}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "  ${stats.xpIntoLevel}/${stats.xpForLevel} XP",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = buildString {
                    if (stats.weekStreak > 0) append("🔥 ${stats.weekStreak} wk · ")
                    append("${stats.workoutsThisWeek}/${stats.weeklyGoal} this week")
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { stats.xpIntoLevel.toFloat() / stats.xpForLevel },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
