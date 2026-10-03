package com.kkfittracking.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kkfittracking.model.PainLight
import com.kkfittracking.model.PainMoment
import com.kkfittracking.model.Tendon
import kotlin.math.roundToInt

/** The traffic light's colours. */
fun PainLight.color(): Color = when (this) {
    PainLight.GREEN -> Color(0xFF2E7D32)
    PainLight.YELLOW -> Color(0xFFF9A825)
    PainLight.RED -> Color(0xFFC62828)
}

@Composable
fun PainDot(light: PainLight?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .background(light?.color() ?: MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

/** Rates one or more tendons' pain from 0 to 10 at a moment. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PainRatingDialog(
    tendons: List<Tendon>,
    initialMoment: PainMoment,
    onSave: (tendons: List<Tendon>, moment: PainMoment, score: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var moment by remember { mutableStateOf(initialMoment) }
    var chosen by remember { mutableStateOf(tendons.toSet()) }
    var score by remember { mutableFloatStateOf(0f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How much does it hurt?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PainMoment.entries.forEach {
                        FilterChip(selected = moment == it, onClick = { moment = it }, label = { Text(it.label) })
                    }
                }
                if (tendons.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tendons.forEach { tendon ->
                            FilterChip(
                                selected = tendon in chosen,
                                onClick = { chosen = if (tendon in chosen) chosen - tendon else chosen + tendon },
                                label = { Text(tendon.label) },
                            )
                        }
                    }
                }
                Text(
                    "${score.roundToInt()} / 10 · " + painWords(score.roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        score.roundToInt() > 5 -> PainLight.RED.color()
                        score.roundToInt() > 2 -> PainLight.YELLOW.color()
                        else -> PainLight.GREEN.color()
                    },
                )
                Slider(value = score, onValueChange = { score = it }, valueRange = 0f..10f, steps = 9)
                Text(
                    when (moment) {
                        PainMoment.MORNING -> "Rate it in the first minutes after getting up, e.g. on the first steps or a squat."
                        else -> "Up to 5 is acceptable during and after training, if it settles by the next morning."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(chosen.toList(), moment, score.roundToInt()) }, enabled = chosen.isNotEmpty()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun painWords(score: Int): String = when (score) {
    0 -> "no pain"
    in 1..2 -> "barely there"
    in 3..5 -> "noticeable, acceptable"
    in 6..7 -> "too much"
    else -> "severe"
}
