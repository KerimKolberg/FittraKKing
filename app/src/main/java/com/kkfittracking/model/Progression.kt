package com.kkfittracking.model

import kotlin.math.roundToInt

/** What to try next time: the values to fill in, and why. */
data class Suggestion(val values: SetValues, val reason: String)

/** Seconds a hold grows by when it went well. */
const val HOLD_STEP_SECONDS = 5

/** From this long, a weighted hold gets heavier instead of longer. */
const val HOLD_LONG_SECONDS = 45

/**
 * The smallest sensible jump for a weight: 1 kg or 2.5 lb for light weights (dumbbells, small
 * muscles), 2.5 kg or 5 lb from 20 kg (45 lb) up. Machine levels go one level up.
 */
fun weightIncrementKg(weightKg: Double, units: UnitSystem): Double = when (units) {
    UnitSystem.METRIC -> if (weightKg < 20.0) 1.0 else 2.5
    UnitSystem.IMPERIAL -> units.weightToKg(if (units.weightFromKg(weightKg) < 45.0) 2.5 else 5.0)
    UnitSystem.LEVELS -> 1.0
}

/**
 * The next step from the last session ("double progression"): when every working set reached the
 * planned reps (or last time's best), add the smallest weight jump; when not, keep the weight and
 * aim for one more rep. Bodyweight sets add a rep, holds add time, and long weighted holds add
 * weight. A tendon that hurt ([pain]) holds the load (yellow) or steps it back (red).
 * Null when there is nothing to go on, or for cardio, sports and intervals.
 */
fun progressionSuggestion(
    type: ExerciseType,
    plan: ExercisePlan,
    previous: HistorySession?,
    units: UnitSystem,
    pain: PainLight? = null,
): Suggestion? {
    val working = previous?.sets.orEmpty().map { it.values }.filter { !it.isDropSet }
    if (working.isEmpty()) return null
    val side = working.first().side
    return when (type) {
        ExerciseType.WEIGHT_REPS -> weightSuggestion(working, plan, units, pain)
        ExerciseType.REPS -> repsSuggestion(working, plan, pain)
        ExerciseType.TIME, ExerciseType.TIME_WEIGHT -> holdSuggestion(working, type, units, pain)
        else -> null
    }?.let { it.copy(values = it.values.copy(side = side)) }
}

private fun weightSuggestion(working: List<SetValues>, plan: ExercisePlan, units: UnitSystem, pain: PainLight?): Suggestion? {
    val top = working.filter { it.weightKg != null && it.reps != null }.maxByOrNull { it.weightKg!! } ?: return null
    val weight = top.weightKg!!
    val atTop = working.filter { it.weightKg == weight && it.reps != null }
    val target = plan.reps ?: atTop.maxOf { it.reps!! }
    val lowest = atTop.minOf { it.reps!! }
    val label = formatWeight(weight, units)
    return when {
        pain == PainLight.RED -> {
            val lighter = roundToPlates(weight * 0.9, units)
            Suggestion(SetValues(lighter, target), "Your tendon check says step back: about 10% lighter ($label → ${formatWeight(lighter, units)}).")
        }
        pain == PainLight.YELLOW -> Suggestion(SetValues(weight, target), "Hold the load while the tendon settles: $label × $target again.")
        lowest >= target && (plan.sets == null || atTop.size >= plan.sets) -> {
            val next = weight + weightIncrementKg(weight, units)
            Suggestion(SetValues(next, target), "Every set reached $target reps at $label last time: add a little weight.")
        }
        else -> {
            val aim = minOf(lowest + 1, target)
            Suggestion(SetValues(weight, aim), "Stay at $label and aim for $aim reps in every set, then add weight.")
        }
    }
}

private fun repsSuggestion(working: List<SetValues>, plan: ExercisePlan, pain: PainLight?): Suggestion? {
    val reps = working.mapNotNull { it.reps }
    if (reps.isEmpty()) return null
    val best = reps.max()
    val lowest = reps.min()
    return when {
        pain == PainLight.RED -> Suggestion(SetValues(reps = (best * 0.8).roundToInt().coerceAtLeast(1)), "Your tendon check says step back: fewer reps for now.")
        pain == PainLight.YELLOW -> Suggestion(SetValues(reps = best), "Hold the load while the tendon settles: $best reps again.")
        plan.reps != null && lowest < plan.reps -> Suggestion(SetValues(reps = lowest + 1), "Aim for ${lowest + 1} reps, on the way to ${plan.reps}.")
        else -> Suggestion(SetValues(reps = best + 1), "Last time's best was $best: try one more rep.")
    }
}

private fun holdSuggestion(working: List<SetValues>, type: ExerciseType, units: UnitSystem, pain: PainLight?): Suggestion? {
    val held = working.filter { it.durationSeconds != null }.maxByOrNull { it.durationSeconds!! } ?: return null
    val seconds = held.durationSeconds!!
    val weight = held.weightKg?.takeIf { type == ExerciseType.TIME_WEIGHT && it > 0 }
    return when {
        pain == PainLight.RED -> Suggestion(
            held.copy(durationSeconds = seconds, weightKg = weight?.let { roundToPlates(it * 0.9, units) }, rpe = null, note = ""),
            "Your tendon check says step back: a lighter hold for now.",
        )
        pain == PainLight.YELLOW -> Suggestion(
            SetValues(weightKg = weight, durationSeconds = seconds),
            "Hold the load while the tendon settles: the same hold again.",
        )
        type == ExerciseType.TIME_WEIGHT && seconds >= HOLD_LONG_SECONDS -> {
            val base = weight ?: 0.0
            val next = base + weightIncrementKg(base, units)
            Suggestion(
                SetValues(weightKg = next, durationSeconds = seconds),
                "You held ${formatDuration(seconds)}: keep the time and add weight (${formatWeight(next, units)}).",
            )
        }
        else -> Suggestion(
            SetValues(weightKg = weight, durationSeconds = seconds + HOLD_STEP_SECONDS),
            "You held ${formatDuration(seconds)}: try $HOLD_STEP_SECONDS seconds longer.",
        )
    }
}

private fun formatWeight(kg: Double, units: UnitSystem) = "${formatNumber(units.weightFromKg(kg))} ${units.weightUnit}"
