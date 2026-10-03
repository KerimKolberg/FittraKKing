package com.kkfittracking.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.PainCheck
import com.kkfittracking.model.Suggestion
import com.kkfittracking.model.Tendon
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.formatNumber
import com.kkfittracking.model.formatPlates
import com.kkfittracking.model.formatSet
import com.kkfittracking.model.plateLoad
import com.kkfittracking.model.warmUpSets
import com.kkfittracking.ui.components.PainDot

/** "Next: 62.5 kg × 8", why, and a button that fills it in. */
@Composable
fun SuggestionCard(suggestion: Suggestion, type: ExerciseType, units: UnitSystem, onUse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Suggested: " + formatSet(suggestion.values, type, units),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(suggestion.reason, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onUse) { Text("Use") }
        }
    }
}

/** Warm-up sets before [workKg], with the plates for each when it is a barbell lift. */
@Composable
fun WarmUpCard(workKg: Double, barKg: Double, units: UnitSystem, showPlates: Boolean) {
    val sets = warmUpSets(workKg, barKg, units)
    if (sets.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Warm-up before ${formatNumber(units.weightFromKg(workKg))} ${units.weightUnit}", style = MaterialTheme.typography.labelLarge)
            sets.forEach { set ->
                val plates = if (showPlates) plateLoad(set.weightKg, barKg, units)?.let { " · " + formatPlates(it) + " per side" }.orEmpty() else ""
                Text(
                    "${formatNumber(units.weightFromKg(set.weightKg))} ${units.weightUnit} × ${set.reps}$plates",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** What to load on each side of the bar for [totalKg]. */
@Composable
fun PlateCalculatorDialog(totalKg: Double, barKg: Double, units: UnitSystem, onDismiss: () -> Unit) {
    val load = plateLoad(totalKg, barKg, units)
    val total = "${formatNumber(units.weightFromKg(totalKg))} ${units.weightUnit}"
    val bar = "${formatNumber(units.weightFromKg(barKg))} ${units.weightUnit}"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plates for $total") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    load == null -> Text("$total is lighter than the $bar bar.")
                    else -> {
                        Text("On each side of the $bar bar:", style = MaterialTheme.typography.bodyMedium)
                        Text(formatPlates(load), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (!load.isExact) {
                            Text(
                                "The plates make ${formatNumber(units.weightFromKg(totalKg) - load.missing)} ${units.weightUnit}; " +
                                    "${formatNumber(load.missing)} ${units.weightUnit} cannot be made.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text("Change the bar in Settings → Training help.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** The tendons this exercise loads, their light from the pain log, and a button to rate them. */
@Composable
fun TendonCheckCard(tendons: List<Tendon>, checks: Map<Tendon, PainCheck>, onRate: () -> Unit) {
    if (tendons.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tendon check", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRate) { Text("Rate pain") }
            }
            tendons.forEach { tendon ->
                val check = checks[tendon]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PainDot(check?.light)
                    Column {
                        Text(
                            tendon.label + (check?.let { " · ${it.light.label}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            check?.reason ?: "Not rated yet: rate the pain before and after, and tomorrow morning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
