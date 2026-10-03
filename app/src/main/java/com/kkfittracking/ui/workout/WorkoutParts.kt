@file:OptIn(ExperimentalMaterial3Api::class)

package com.kkfittracking.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kkfittracking.model.Block
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.DayWorkouts
import com.kkfittracking.model.GameStats
import com.kkfittracking.model.Routine
import com.kkfittracking.model.Tendon
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.formatNumber
import com.kkfittracking.model.formatSet
import com.kkfittracking.model.setLabels
import com.kkfittracking.ui.components.formatFullDate
import com.kkfittracking.ui.components.relativeDayName
import java.time.LocalDate

// The parts of the workout screen: the bars at the top, the day's exercise cards and the dialogs.

/** The day's training time and estimated calories. */
@Composable
internal fun EnergyBar(energy: DayWorkouts) {
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
internal fun StepsBar(steps: StepsState, units: UnitSystem, onConnect: () -> Unit, onInstall: () -> Unit) {
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
internal fun DateSwitcher(
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
internal fun EmptyDay(onAddExercise: () -> Unit, onUseRoutine: () -> Unit, onWarmUp: () -> Unit) {
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
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onWarmUp) {
            Text("Warm up for a sport")
        }
    }
}

@Composable
internal fun DayExerciseCard(
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
internal fun SupersetCard(
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
internal fun ExerciseBody(
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
internal fun RoutineChooserDialog(
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
internal fun MenuItem(text: String, closeMenu: () -> Unit, onClick: () -> Unit) {
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
internal fun GameSummaryBar(stats: GameStats, onClick: () -> Unit) {
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

/** "How do your tendons feel this morning?" after a session with pain ratings. */
@Composable
internal fun MorningTendonCard(tendons: List<Tendon>, onRate: () -> Unit, onOpenLoad: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Morning tendon check", style = MaterialTheme.typography.titleSmall)
            Text(
                "How do they feel after yesterday? " + tendons.joinToString(", ") { it.label },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRate) { Text("Rate now") }
                TextButton(onClick = onOpenLoad) { Text("Tendon overview") }
            }
        }
    }
}
