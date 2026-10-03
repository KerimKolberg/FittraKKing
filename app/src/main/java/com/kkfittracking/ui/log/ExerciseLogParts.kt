@file:OptIn(ExperimentalMaterial3Api::class)

package com.kkfittracking.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.HistorySession
import com.kkfittracking.model.PainEntry
import com.kkfittracking.model.SetValues
import com.kkfittracking.model.Side
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.formatSet
import com.kkfittracking.model.isBarbellLift
import com.kkfittracking.model.painCheck
import com.kkfittracking.model.progressionSuggestion
import com.kkfittracking.model.setLabels
import com.kkfittracking.ui.components.formatShortDate
import java.time.LocalDate

// The parts of the exercise log screen: input fields, choosers, history and the help before the first set.

@Composable
internal fun StepperField(
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
internal fun HistoryTab(state: ExerciseLogUiState, type: ExerciseType, currentDate: LocalDate) {
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

internal fun LazyListScope.historySessions(
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
internal fun SupersetBar(members: List<DayExercise>, currentId: String, onSelect: (String) -> Unit) {
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
internal fun SideChooser(side: Side?, onChoose: (Side) -> Unit) {
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
internal fun WeightUnitChooser(current: UnitSystem?, appUnits: UnitSystem, onChoose: (UnitSystem?) -> Unit) {
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
internal fun RecordBadge() {
    Icon(
        imageVector = Icons.Default.Star,
        contentDescription = "Personal record",
        modifier = Modifier
            .padding(start = 8.dp)
            .size(18.dp),
        tint = MaterialTheme.colorScheme.secondary,
    )
}

/**
 * Help before the first set: the suggested next step (held back or stepped down when a tendon
 * hurts), warm-up sets for a heavy lift, and the tendon check.
 */
internal fun LazyListScope.trainingHelp(
    state: ExerciseLogUiState,
    type: ExerciseType,
    units: UnitSystem,
    pain: List<PainEntry>,
    onUse: (SetValues) -> Unit,
    onRate: () -> Unit,
) {
    val exercise = state.exercise ?: return
    val checks = exercise.tendons.mapNotNull { painCheck(it, pain) }.associateBy { it.tendon }
    val worst = checks.values.maxOfOrNull { it.light }
    if (state.settings.progressionHints && state.sets.isEmpty()) {
        progressionSuggestion(type, state.plan, state.previousSession, units, worst)?.let { suggestion ->
            item(key = "suggestion") { SuggestionCard(suggestion, type, units, onUse = { onUse(suggestion.values) }) }
        }
    }
    if (state.settings.warmUpSets && type == ExerciseType.WEIGHT_REPS && state.sets.none { !it.values.isDropSet }) {
        val work = state.plan.weightKg ?: state.previousSession?.sets?.mapNotNull { it.values.weightKg }?.maxOrNull()
        if (work != null) {
            // Dumbbells and machines have no bar to start from.
            val barbell = isBarbellLift(exercise.name)
            item(key = "warmup") { WarmUpCard(work, if (barbell) state.settings.barKg else 0.0, units, showPlates = barbell) }
        }
    }
    if (exercise.tendons.isNotEmpty()) {
        item(key = "tendons") { TendonCheckCard(exercise.tendons, checks, onRate) }
    }
}
