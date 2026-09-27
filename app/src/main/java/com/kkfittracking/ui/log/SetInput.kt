package com.kkfittracking.ui.log

import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.SetValues
import com.kkfittracking.model.Side
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.formatNumber
import com.kkfittracking.model.parseDecimal

/** The text the user typed into the set fields, in their display units. */
data class SetInput(
    val weight: String = "",
    val reps: String = "",
    val distance: String = "",
    val minutes: String = "",
    val seconds: String = "",
    val height: String = "",
    val rpe: String = "",
    val note: String = "",
    /** For one-sided exercises: the side of this set. */
    val side: Side? = null,
) {
    sealed interface Result {
        data class Valid(val values: SetValues) : Result
        data class Invalid(val message: String) : Result
    }

    /** Checks the fields needed by [type] and converts them to stored units. */
    fun toSetValues(type: ExerciseType, units: UnitSystem): Result {
        var weightKg: Double? = null
        var repCount: Int? = null
        var distanceMeters: Double? = null
        var durationSeconds: Int? = null
        var effort: Int? = null

        if (type.usesWeight && (type == ExerciseType.WEIGHT_REPS || weight.isNotBlank())) {
            val value = parseDecimal(weight.ifBlank { "0" })
            if (value == null || value < 0) return Result.Invalid("Enter a valid weight")
            weightKg = units.weightToKg(value)
        }
        if (type.usesReps) {
            val value = reps.trim().toIntOrNull()
            if (value == null || value <= 0) return Result.Invalid("Enter the number of ${type.repsLabel.lowercase()}")
            repCount = value
        }
        if (type.usesDistance && distance.isNotBlank()) {
            val value = parseDecimal(distance)
            if (value == null || value < 0) return Result.Invalid("Enter a valid distance")
            distanceMeters = units.distanceToMeters(value)
        }
        if (type.usesHeight && height.isNotBlank()) {
            val value = parseDecimal(height)
            if (value == null || value < 0) return Result.Invalid("Enter a valid height or distance")
            distanceMeters = units.heightToMeters(value)
        }
        if (type.usesIntensity && rpe.isNotBlank()) {
            val value = rpe.trim().toIntOrNull()
            if (value == null || value !in 1..10) return Result.Invalid("Intensity is a number from 1 to 10")
            effort = value
        }
        if (type.usesTime && (minutes.isNotBlank() || seconds.isNotBlank())) {
            val min = minutes.trim().ifEmpty { "0" }.toIntOrNull()
            val sec = seconds.trim().ifEmpty { "0" }.toIntOrNull()
            if (min == null || sec == null || min < 0 || sec < 0) return Result.Invalid("Enter a valid time")
            durationSeconds = min * 60 + sec
        }
        if (type == ExerciseType.DISTANCE_TIME && (distanceMeters ?: 0.0) <= 0.0 && (durationSeconds ?: 0) <= 0) {
            return Result.Invalid("Enter a distance or a time")
        }
        val needsTime = type == ExerciseType.TIME || type == ExerciseType.TIME_WEIGHT || type == ExerciseType.SESSION
        if (needsTime && (durationSeconds ?: 0) <= 0) {
            return Result.Invalid("Enter a time")
        }
        return Result.Valid(
            SetValues(
                weightKg = weightKg,
                reps = repCount,
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                rpe = effort,
                note = if (type.usesIntensity) note.trim() else "",
                side = side,
            ),
        )
    }

    fun adjustWeight(direction: Int, units: UnitSystem): SetInput {
        val current = parseDecimal(weight) ?: 0.0
        return copy(weight = formatNumber((current + direction * units.weightStep).coerceAtLeast(0.0)))
    }

    fun adjustReps(direction: Int): SetInput {
        val current = reps.trim().toIntOrNull() ?: 0
        return copy(reps = (current + direction).coerceAtLeast(0).toString())
    }

    companion object {
        /** Fills the fields from a stored set, converted to [units]. */
        fun from(values: SetValues, units: UnitSystem) = SetInput(
            weight = values.weightKg?.let { formatNumber(units.weightFromKg(it)) }.orEmpty(),
            reps = values.reps?.toString().orEmpty(),
            distance = values.distanceMeters?.let { formatNumber(units.distanceFromMeters(it)) }.orEmpty(),
            height = values.distanceMeters?.let { formatNumber(units.heightFromMeters(it)) }.orEmpty(),
            rpe = values.rpe?.toString().orEmpty(),
            note = values.note,
            minutes = values.durationSeconds?.let { (it / 60).toString() }.orEmpty(),
            seconds = values.durationSeconds?.let { (it % 60).toString() }.orEmpty(),
            side = values.side,
        )
    }
}
