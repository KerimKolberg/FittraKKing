package com.kkfittracking.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** The plates for one side of the bar, in the user's unit, heaviest first; [missing] is what they cannot make up. */
data class PlateLoad(val perSide: List<Double>, val missing: Double) {
    val isExact: Boolean get() = abs(missing) < 0.01
}

/** The usual gym plates, in the unit's own numbers. */
fun platesOf(units: UnitSystem): List<Double> = when (units) {
    UnitSystem.IMPERIAL -> listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    else -> listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
}

/** The plates to put on each side for [totalKg] on a [barKg] bar; null when the weight is less than the bar. */
fun plateLoad(totalKg: Double, barKg: Double, units: UnitSystem): PlateLoad? {
    if (units == UnitSystem.LEVELS) return null
    val total = units.weightFromKg(totalKg)
    val bar = units.weightFromKg(barKg)
    if (total < bar - 0.01) return null
    var side = (total - bar) / 2
    val plates = mutableListOf<Double>()
    for (plate in platesOf(units)) {
        while (side >= plate - 0.001) {
            plates += plate
            side -= plate
        }
    }
    return PlateLoad(plates, (side * 2 * 100).roundToInt() / 100.0)
}

/** "2×20 + 1.25" style text for one side. */
fun formatPlates(load: PlateLoad): String {
    if (load.perSide.isEmpty()) return "Just the bar"
    return load.perSide.groupBy { it }.entries.joinToString(" + ") { (plate, list) ->
        if (list.size > 1) "${list.size}×${formatNumber(plate)}" else formatNumber(plate)
    }
}

/** A lighter set before the work sets. */
data class WarmUpSet(val weightKg: Double, val reps: Int)

/**
 * Warm-up sets before [workKg]: the empty bar, then about 40%, 60% and 80% with fewer reps each
 * time, rounded to what the plates make; sets that would be no lighter than the work are left out.
 * Nothing for light weights, which need no ramp.
 */
fun warmUpSets(workKg: Double, barKg: Double, units: UnitSystem): List<WarmUpSet> {
    if (workKg < barKg + 20.0) return emptyList()
    val step = units.weightToKg(if (units == UnitSystem.IMPERIAL) 5.0 else 2.5)
    fun rounded(kg: Double) = maxOf(barKg, (kg / step).roundToInt() * step)
    val ramp = listOf(0.0 to 10, 0.4 to 8, 0.6 to 5, 0.8 to 3).map { (part, reps) ->
        WarmUpSet(if (part == 0.0) barKg else rounded(workKg * part), reps)
    }
    return ramp.filter { it.weightKg < workKg - 0.01 }.distinctBy { it.weightKg }
}

/** Barbell lifts, by name: where the plate calculator and warm-ups make sense. */
fun isBarbellLift(name: String): Boolean {
    val lower = name.lowercase()
    return listOf("barbell", "squat", "deadlift", "bench press", "overhead press", "clean", "snatch", "hip thrust", "trap bar", "good morning")
        .any { it in lower } && listOf("dumbbell", "kettlebell", "goblet", "pistol", "sissy", "jump", "hold", "bodyweight").none { it in lower }
}
