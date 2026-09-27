package com.kkfittracking.model

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formats a number with at most two decimals and no trailing zeros, e.g. 100.0 -> "100", 102.5 -> "102.5". */
fun formatNumber(value: Double): String {
    val format = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(value)
}

/** Parses user input, accepting either "." or "," as the decimal separator. */
fun parseDecimal(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Formats seconds as "m:ss", or "h:mm:ss" from one hour up. */
fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}

/** A one-line summary of a set, e.g. "100 kg × 5", "12 reps", "5 km · 25:00", "8 rounds · 4:00". */
fun formatSet(values: SetValues, type: ExerciseType, units: UnitSystem): String {
    val parts = mutableListOf<String>()
    if (type.usesWeight && values.weightKg != null && (values.weightKg > 0 || type == ExerciseType.WEIGHT_REPS)) {
        val weight = "${formatNumber(units.weightFromKg(values.weightKg))} ${units.weightUnit}"
        parts += if (type.usesReps && values.reps != null) "$weight × ${values.reps}" else weight
    } else if (type.usesReps && values.reps != null) {
        val unit = if (type == ExerciseType.INTERVALS) "round" else "rep"
        parts += if (values.reps == 1) "1 $unit" else "${values.reps} ${unit}s"
    }
    if (type.usesHeight && values.distanceMeters != null) {
        parts += "${formatNumber(units.heightFromMeters(values.distanceMeters))} ${units.lengthUnit}"
    }
    if (type.usesDistance && values.distanceMeters != null) {
        parts += "${formatNumber(units.distanceFromMeters(values.distanceMeters))} ${units.distanceUnit}"
    }
    if (type.usesTime && values.durationSeconds != null) {
        parts += formatDuration(values.durationSeconds)
    }
    if (type.usesIntensity && values.rpe != null) {
        parts += "RPE ${values.rpe}"
    }
    if (type.usesIntensity && values.note.isNotBlank()) {
        parts += values.note
    }
    val text = parts.joinToString(" · ")
    return values.side?.let { "${it.short} · $text" } ?: text
}
