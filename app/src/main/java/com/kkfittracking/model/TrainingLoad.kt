package com.kkfittracking.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A logged set with what it trained and when. */
private data class LoadSet(val date: LocalDate, val exercise: Exercise, val values: SetValues)

private fun loadSets(histories: Map<String, List<HistorySession>>, exercises: List<Exercise>, from: LocalDate, to: LocalDate) =
    exercises.flatMap { exercise ->
        histories[exercise.id].orEmpty()
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .flatMap { session -> session.sets.map { LoadSet(session.date, exercise, it.values) } }
    }

/**
 * Sets per muscle from [from] to [to]: a full set for the exercise's main muscle and half a set for
 * each other muscle it trains. Drop sets count as half a set too.
 */
fun setsPerMuscle(histories: Map<String, List<HistorySession>>, exercises: List<Exercise>, from: LocalDate, to: LocalDate): Map<Muscle, Double> {
    val totals = mutableMapOf<Muscle, Double>()
    loadSets(histories, exercises, from, to).forEach { set ->
        val weight = if (set.values.isDropSet) 0.5 else 1.0
        set.exercise.muscles.forEachIndexed { index, muscle ->
            if (muscle == Muscle.OTHER || muscle == Muscle.CARDIO || muscle == Muscle.MIND || muscle == Muscle.SPORT) return@forEachIndexed
            totals[muscle] = (totals[muscle] ?: 0.0) + if (index == 0) weight else weight / 2
        }
    }
    return totals
}

/** Sets that loaded each tendon from [from] to [to]. */
fun setsPerTendon(histories: Map<String, List<HistorySession>>, exercises: List<Exercise>, from: LocalDate, to: LocalDate): Map<Tendon, Int> =
    loadSets(histories, exercises, from, to)
        .filter { !it.values.isDropSet }
        .flatMap { set -> set.exercise.tendons }
        .groupingBy { it }.eachCount()

/** Rough weekly sets for growth and strength: under 4 is light, 10–20 is the usual range for a muscle. */
enum class VolumeLevel(val label: String) {
    NONE("Not trained"),
    LIGHT("Light"),
    MODERATE("Moderate"),
    HIGH("High"),
    VERY_HIGH("Very high"),
    ;

    companion object {
        fun of(sets: Double): VolumeLevel = when {
            sets <= 0.0 -> NONE
            sets < 4 -> LIGHT
            sets < 10 -> MODERATE
            sets <= 20 -> HIGH
            else -> VERY_HIGH
        }
    }
}

/** Effort × minutes, from the effort (RPE) logged, or a usual effort of 5 for sports and 6 for gym sets. */
fun setLoad(exercise: Exercise, values: SetValues): Double {
    val type = exercise.type
    val minutes = values.durationSeconds?.takeIf { it > 0 }?.div(60.0)
        ?: if (type.isSession || type.usesDistance) 30.0 else GYM_SET_MINUTES
    val effort = values.rpe ?: if (type.isSession || type.usesDistance || type == ExerciseType.INTERVALS) 5 else 6
    return minutes * effort
}

/** A gym set with its rest takes about this long. */
const val GYM_SET_MINUTES = 3.0

/**
 * This week's training load against the usual week of the last four: a ratio far above 1 is a
 * sudden jump, which often comes before overuse injuries in sports.
 */
data class LoadRatio(val thisWeek: Double, val usualWeek: Double) {
    /** Null until there are four weeks to compare with. */
    val ratio: Double? get() = if (usualWeek > 0) thisWeek / usualWeek else null

    val verdict: String
        get() {
            val r = ratio ?: return "Keep logging: after a few weeks this compares each week with your usual one."
            return when {
                r > 1.5 -> "A big jump (×${formatNumber(r)} your usual week): add load more gradually, tendons need time."
                r > 1.3 -> "More than usual (×${formatNumber(r)}): fine for a week, watch tendons and sleep."
                r >= 0.8 -> "Steady (×${formatNumber(r)} your usual week)."
                else -> "Lighter than usual (×${formatNumber(r)}): a recovery week, or time to build back up."
            }
        }
}

fun loadRatio(histories: Map<String, List<HistorySession>>, exercises: List<Exercise>, today: LocalDate): LoadRatio {
    val sets = loadSets(histories, exercises, today.minusDays(27), today)
    val first = histories.values.flatten().minOfOrNull { it.date }
    val weekStart = today.minusDays(6)
    val thisWeek = sets.filter { !it.date.isBefore(weekStart) }.sumOf { setLoad(it.exercise, it.values) }
    // Four weeks of history are needed for a fair "usual"; fewer weeks count as they are.
    val weeks = first?.let { ChronoUnit.DAYS.between(it, today).plus(1).coerceIn(1, 28) / 7.0 } ?: 0.0
    val usual = if (weeks >= 2) sets.sumOf { setLoad(it.exercise, it.values) } / weeks else 0.0
    return LoadRatio(thisWeek, usual)
}

/** Areas that are easy to forget, watched even if never trained. */
val OFTEN_FORGOTTEN: List<Muscle> = listOf(
    Muscle.NECK, Muscle.FEET, Muscle.SHINS, Muscle.ROTATOR_CUFF, Muscle.ADDUCTORS, Muscle.HIPS,
    Muscle.OBLIQUES, Muscle.LOWER_BACK, Muscle.REAR_DELTS, Muscle.FOREARMS,
)

/** A muscle not trained for a while: days since it last was, or null for never. */
data class ForgottenArea(val muscle: Muscle, val daysSince: Long?)

/**
 * The often forgotten areas, plus anything trained in the last three months, that had no set in
 * the last [days] days; longest ago (and never) first.
 */
fun forgottenAreas(histories: Map<String, List<HistorySession>>, exercises: List<Exercise>, today: LocalDate, days: Long = 14): List<ForgottenArea> {
    val last = mutableMapOf<Muscle, LocalDate>()
    loadSets(histories, exercises, LocalDate.MIN, today).forEach { set ->
        set.exercise.muscles.forEach { muscle ->
            if (last[muscle]?.isBefore(set.date) != false) last[muscle] = set.date
        }
    }
    val recent = last.filterValues { !it.isBefore(today.minusDays(90)) }.keys
    val watched = (OFTEN_FORGOTTEN + recent).distinct()
        .filter { it != Muscle.OTHER && it != Muscle.CARDIO && it != Muscle.MIND && it != Muscle.SPORT && it != Muscle.FULL_BODY }
    return watched.mapNotNull { muscle ->
        val date = last[muscle]
        val since = date?.let { ChronoUnit.DAYS.between(it, today) }
        if (since != null && since < days) null else ForgottenArea(muscle, since)
    }.sortedByDescending { it.daysSince ?: Long.MAX_VALUE }
}
