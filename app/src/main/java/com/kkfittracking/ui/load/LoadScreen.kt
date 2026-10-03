@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kkfittracking.ui.load

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kkfittracking.data.BuiltInExercises
import com.kkfittracking.model.Muscle
import com.kkfittracking.model.PainCheck
import com.kkfittracking.model.PainMoment
import com.kkfittracking.model.Tendon
import com.kkfittracking.model.VolumeLevel
import com.kkfittracking.model.formatNumber
import com.kkfittracking.ui.components.TendonIcon
import com.kkfittracking.ui.log.PainDot
import com.kkfittracking.ui.log.PainRatingDialog
import java.time.LocalDate

/** Training load and tendons: the pain log, this week per muscle and tendon, the load trend and what was forgotten. */
@Composable
fun LoadScreen(onBack: () -> Unit, viewModel: LoadViewModel = viewModel(factory = LoadViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var rating by remember { mutableStateOf<List<Tendon>?>(null) }
    var choosingTendon by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training load & tendons") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Heading("Tendon check", "Rate pain 0–10 before and after training and the next morning.") }
            if (state.checks.isEmpty()) {
                item {
                    Text(
                        "No ratings yet. Rate a tendon from here, or from the tendon check on an exercise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.checks, key = { "check-${it.tendon}" }) { check ->
                PainCheckCard(check, onRate = { rating = listOf(check.tendon) })
            }
            item {
                OutlinedButton(onClick = { choosingTendon = true }, modifier = Modifier.fillMaxWidth()) { Text("Rate a tendon") }
            }

            item { Heading("Training load", "Effort × minutes this week, against your usual week of the last four.") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "This week ${formatNumber(state.ratio.thisWeek.roundTo10())} · usual ${formatNumber(state.ratio.usualWeek.roundTo10())}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(state.ratio.verdict, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item { Heading("This week per muscle", "Sets in the last 7 days (other muscles an exercise trains count half).") }
            item { MuscleMap(state.muscleSets) }

            if (state.tendonSets.isNotEmpty()) {
                item { Heading("Tendons loaded this week", null) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tendonSets.entries.sortedByDescending { it.value }.forEach { (tendon, sets) ->
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TendonIcon(tendon, size = 20.dp)
                                    Text(" ${tendon.label.substringBefore(" (")}: $sets", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            item { Heading("Not trained lately", "Easy-to-forget areas, and anything you trained lately, with no set in 2 weeks.") }
            if (state.forgotten.isEmpty()) {
                item { Text("Nothing forgotten. 👏", style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.forgotten, key = { "forgotten-${it.muscle}" }) { area ->
                Row(Modifier.fillMaxWidth()) {
                    Text(area.muscle.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        area.daysSince?.let { "$it days ago" } ?: "never",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    rating?.let { tendons ->
        PainRatingDialog(
            tendons = tendons,
            initialMoment = PainMoment.MORNING,
            onSave = { chosen, moment, score ->
                rating = null
                viewModel.rate(chosen, moment, score)
            },
            onDismiss = { rating = null },
        )
    }
    if (choosingTendon) {
        AlertDialog(
            onDismissRequest = { choosingTendon = false },
            title = { Text("Which tendon?") },
            text = {
                LazyColumn {
                    items(Tendon.entries) { tendon ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    choosingTendon = false
                                    rating = listOf(tendon)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TendonIcon(tendon, size = 32.dp)
                            Text("  ${tendon.label}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingTendon = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Heading(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PainCheckCard(check: PainCheck, onRate: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onRate)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PainDot(check.light)
                Text(check.tendon.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(check.light.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            val dayText = if (check.day == LocalDate.now()) "today" else check.day.toString()
            Text(
                "Session $dayText: before ${check.before ?: "–"} · after ${check.after ?: "–"} · next morning ${check.nextMorning ?: "–"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(check.reason, style = MaterialTheme.typography.bodySmall)
            Text(check.light.advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Every muscle by body section, coloured by how many sets it got this week. */
@Composable
private fun MuscleMap(sets: Map<Muscle, Double>) {
    val regions = BuiltInExercises.regions
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        regions.forEach { region ->
            val muscles = Muscle.entries.filter { it.regionKey == region.key && it != Muscle.OTHER }
            if (muscles.isEmpty() || region.key in setOf("cardio", "sports", "mind", "full-body")) return@forEach
            Text(region.name, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                muscles.forEach { muscle ->
                    val count = sets[muscle] ?: 0.0
                    val level = VolumeLevel.of(count)
                    Surface(shape = RoundedCornerShape(8.dp), color = level.color()) {
                        Text(
                            "${muscle.label} ${formatNumber(count)}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (level == VolumeLevel.NONE) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                        )
                    }
                }
            }
        }
        Text(
            "Grey: none · light green: under 4 · green: 4–9 · dark green: 10–20 · orange: over 20 sets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VolumeLevel.color(): Color = when (this) {
    VolumeLevel.NONE -> MaterialTheme.colorScheme.surfaceVariant
    VolumeLevel.LIGHT -> Color(0xFF81C784)
    VolumeLevel.MODERATE -> Color(0xFF43A047)
    VolumeLevel.HIGH -> Color(0xFF1B5E20)
    VolumeLevel.VERY_HIGH -> Color(0xFFEF6C00)
}

private fun Double.roundTo10(): Double = Math.round(this / 10) * 10.0
