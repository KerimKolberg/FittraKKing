package com.kkfittracking.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * What the user plans for an exercise: how many sets of how many reps, the rest between sets,
 * drop sets, and the interval timings for HIIT. Everything is optional. Stored as JSON on the
 * exercise, so new fields need a default.
 */
@Serializable
data class ExercisePlan(
    /** Normal sets planned. With drop sets on, 1 means the whole exercise is one drop set. */
    val sets: Int? = null,
    val reps: Int? = null,
    /** Target weight in kg. */
    val weightKg: Double? = null,
    /** Rest between sets; null uses the default from the settings. A superset's timing replaces it. */
    val restSeconds: Int? = null,
    /** After the last planned set, lower the weight [drops] times without resting. */
    val dropSets: Boolean = false,
    val drops: Int = DEFAULT_DROPS,
    /** Reps for each drop; null means as many as the set before. */
    val dropReps: Int? = null,
    /** Lower the weight by a percentage, or else by a fixed amount. */
    val dropByPercent: Boolean = true,
    /** Null uses the default percentage from the settings. */
    val dropPercent: Int? = null,
    /** The fixed amount in kg; null uses 5 kg (10 lb). */
    val dropAmountKg: Double? = null,
    /** HIIT: seconds of high intensity, seconds of low intensity, and how many rounds. */
    val highSeconds: Int? = null,
    val lowSeconds: Int? = null,
    val rounds: Int? = null,
) {
    val isEmpty: Boolean get() = this == ExercisePlan()

    /** One set followed by its drops: the whole exercise is a drop set. */
    val isWholeExerciseDropSet: Boolean get() = dropSets && (sets ?: 1) == 1

    val intervalHighSeconds: Int get() = highSeconds ?: DEFAULT_HIGH_SECONDS
    val intervalLowSeconds: Int get() = lowSeconds ?: DEFAULT_LOW_SECONDS
    val intervalRounds: Int get() = rounds ?: DEFAULT_ROUNDS

    /** The same plan without the set plan and drop sets, keeping the interval timings. */
    fun withoutSetPlan(): ExercisePlan = ExercisePlan(highSeconds = highSeconds, lowSeconds = lowSeconds, rounds = rounds)

    /** Stored text; empty for an empty plan. */
    fun toJson(): String = if (isEmpty) "" else json.encodeToString(serializer(), this)

    companion object {
        const val DEFAULT_DROPS = 2
        const val MAX_DROPS = 5
        const val MAX_SETS = 20
        const val DEFAULT_HIGH_SECONDS = 30
        const val DEFAULT_LOW_SECONDS = 30
        const val DEFAULT_ROUNDS = 10

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /** Reads a stored plan. Empty or damaged text gives an empty plan rather than an error. */
        fun fromJson(text: String): ExercisePlan =
            if (text.isBlank()) {
                ExercisePlan()
            } else {
                try {
                    json.decodeFromString(serializer(), text)
                } catch (e: IllegalArgumentException) {
                    // SerializationException is an IllegalArgumentException.
                    ExercisePlan()
                }
            }
    }
}

/** Rounds a weight (kg) to what plates and dumbbells allow: 0.5 kg or 1 lb. Never below zero. */
fun roundToPlates(kg: Double, units: UnitSystem): Double {
    val step = if (units == UnitSystem.METRIC) 0.5 else 1.0 // lb and machine levels go in whole steps
    val rounded = ((units.weightFromKg(kg) / step).roundToInt() * step).coerceAtLeast(0.0)
    return units.weightToKg(rounded)
}

/** The fixed drop used when none was chosen: 5 kg, or 10 lb. Returns kg. */
fun defaultDropAmountKg(units: UnitSystem): Double = units.weightToKg(
    when (units) {
        UnitSystem.METRIC -> 5.0
        UnitSystem.IMPERIAL -> 10.0
        UnitSystem.LEVELS -> 1.0
    },
)

/** The weight of the drop after a set of [previousKg], rounded to real plates. */
fun ExercisePlan.nextDropKg(previousKg: Double, defaultPercent: Int, units: UnitSystem): Double =
    if (dropByPercent) {
        dropSetWeightKg(previousKg, dropPercent ?: defaultPercent, units)
    } else {
        roundToPlates(previousKg - (dropAmountKg ?: defaultDropAmountKg(units)), units)
    }

/** The weights from [startKg] through each planned drop, e.g. 100, 80, 64. */
fun ExercisePlan.dropWeights(startKg: Double, defaultPercent: Int, units: UnitSystem): List<Double> =
    generateSequence(startKg) { nextDropKg(it, defaultPercent, units) }.take(drops.coerceAtLeast(0) + 1).toList()

/** How much each drop takes off: "−20%" or "−5 kg". */
fun ExercisePlan.dropStepLabel(defaultPercent: Int, units: UnitSystem): String =
    if (dropByPercent) {
        "−${dropPercent ?: defaultPercent}%"
    } else {
        "−${formatNumber(units.weightFromKg(dropAmountKg ?: defaultDropAmountKg(units)))} ${units.weightUnit}"
    }

/** "100 → 80 → 64 kg". */
fun formatWeightSteps(kgs: List<Double>, units: UnitSystem): String =
    kgs.joinToString(" → ") { formatNumber(units.weightFromKg(it)) } + " " + units.weightUnit

/** A one-line summary such as "3 × 8 · 80 kg · rest 2:00 · drop set on the last set: 2 drops, −20% each". */
fun ExercisePlan.summary(type: ExerciseType, defaultPercent: Int, units: UnitSystem): String? {
    val parts = mutableListOf<String>()
    val sets = sets
    val reps = reps?.takeIf { type.usesReps }
    when {
        sets != null && reps != null -> parts += "$sets × $reps"
        sets != null -> parts += if (sets == 1) "1 set" else "$sets sets"
        reps != null -> parts += "$reps reps"
    }
    weightKg?.takeIf { type.usesWeight }?.let { parts += "${formatNumber(units.weightFromKg(it))} ${units.weightUnit}" }
    restSeconds?.let { parts += "rest ${formatDuration(it)}" }
    if (dropSets && type == ExerciseType.WEIGHT_REPS) {
        val where = if (isWholeExerciseDropSet) "the whole exercise is a drop set" else "drop set on the last set"
        val count = if (drops == 1) "1 drop" else "$drops drops"
        parts += "$where: $count, ${dropStepLabel(defaultPercent, units)} each"
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

/** What one exercise does in a superset, when it differs from the superset's plan. */
data class SupersetMember(
    val exerciseId: String,
    /** It joins only the last this many rounds; null joins every round. */
    val rounds: Int? = null,
    /** A drop set on its last round (true), none (false), or as the superset says (null). */
    val dropSet: Boolean? = null,
)

/** The exercises of a superset and its plan, when the exercise being logged is in one. */
data class SupersetContext(
    val memberIds: List<String>,
    val transitionSeconds: Int?,
    val roundRestSeconds: Int?,
    /** Rounds planned; null keeps going round after round. */
    val rounds: Int? = null,
    /** Every exercise ends its last round with a drop set (unless it says otherwise). */
    val dropOnLastRound: Boolean = false,
    val members: List<SupersetMember> = emptyList(),
) {
    fun member(exerciseId: String): SupersetMember = members.firstOrNull { it.exerciseId == exerciseId } ?: SupersetMember(exerciseId)

    /** How many rounds an exercise does (the last ones), when rounds are planned. */
    fun roundsOf(exerciseId: String): Int? = rounds?.let { planned -> (member(exerciseId).rounds ?: planned).coerceIn(1, planned) }

    /** Whether an exercise takes part in [round] (counted from 1). */
    fun joins(exerciseId: String, round: Int): Boolean {
        val planned = rounds ?: return true
        return round > planned - (roundsOf(exerciseId) ?: planned)
    }

    /** Whether an exercise ends its last round with a drop set. */
    fun dropsOnLastRound(exerciseId: String, plan: ExercisePlan): Boolean =
        member(exerciseId).dropSet ?: (dropOnLastRound || plan.dropSets)

    /**
     * The exercise's plan as the superset shapes it: its sets are its rounds, and its drop sets
     * follow the superset. Without planned rounds, its own plan.
     */
    fun planFor(exerciseId: String, plan: ExercisePlan): ExercisePlan {
        val sets = roundsOf(exerciseId) ?: return plan
        return plan.copy(sets = sets, dropSets = dropsOnLastRound(exerciseId, plan))
    }
}

/** What comes after saving a set. */
sealed interface NextStep {
    /** Straight into a drop set with no rest. [number] counts from 1 up to [of]. */
    data class DropSet(val number: Int, val of: Int, val weightKg: Double, val reps: Int?) : NextStep

    /** Walk to the next exercise of the superset. */
    data class Transition(val exerciseId: String, val seconds: Int) : NextStep

    /**
     * Rest before the next set. In a superset this is the rest after a round, and
     * [nextExerciseId] is the exercise that starts the next round.
     */
    data class Rest(val seconds: Int, val nextExerciseId: String?) : NextStep

    /** One side of a one-arm or one-leg set is done: now the [side] still to do, with no rest. */
    data class OtherSide(val side: Side) : NextStep

    /** A whole session was logged (sports, HIIT): there is nothing to rest for. */
    data object Done : NextStep
}

/**
 * The drop set due next, given today's sets in order: once the planned sets are done, the weight
 * drops [ExercisePlan.drops] times, each time from the set before. Null when none is due.
 */
fun pendingDrop(
    plan: ExercisePlan,
    type: ExerciseType,
    setsToday: List<SetEntry>,
    defaultPercent: Int,
    units: UnitSystem,
    perSide: Boolean = false,
): NextStep.DropSet? {
    if (!plan.dropSets || type != ExerciseType.WEIGHT_REPS) return null
    val last = setsToday.lastOrNull() ?: return null
    // Drops come once the set is done on both sides.
    if (setsToday.sideDue(perSide) != null) return null
    val normalSets = setsToday.fullSets(perSide)
    val dropsDone = setsToday.takeLastWhile { it.values.isDropSet }.size
    if (normalSets != (plan.sets ?: 1) || dropsDone >= plan.drops) return null
    val weight = last.values.weightKg ?: return null
    return NextStep.DropSet(
        number = dropsDone + 1,
        of = plan.drops,
        weightKg = plan.nextDropKg(weight, defaultPercent, units),
        reps = plan.dropReps ?: last.values.reps,
    )
}

/**
 * Decides what follows a saved set, in this order: a planned drop set (no rest), the walk to the
 * next exercise of a superset, the superset's rest after a round, or the exercise's own rest.
 * A superset's timing replaces the exercise's own rest. [setsToday] includes the saved set.
 */
fun nextStep(
    exerciseId: String,
    type: ExerciseType,
    plan: ExercisePlan,
    setsToday: List<SetEntry>,
    superset: SupersetContext?,
    settings: Settings,
    perSide: Boolean = false,
): NextStep {
    if (type.isSession) return NextStep.Done
    // One side done: the other side comes straight after, before any rest or the next exercise.
    setsToday.sideDue(perSide)?.let { return NextStep.OtherSide(it) }
    val planned = superset?.rounds
    if (superset != null && planned != null && superset.memberIds.size >= 2 && exerciseId in superset.memberIds) {
        return nextInPlannedRounds(exerciseId, type, plan, setsToday, superset, planned, settings, perSide)
    }
    pendingDrop(plan, type, setsToday, settings.dropSetPercent, settings.unitSystem, perSide)?.let { return it }
    val members = superset?.memberIds.orEmpty()
    val next = nextInSuperset(members, exerciseId)
    if (superset != null && next != null) {
        return if (isLastInSuperset(members, exerciseId)) {
            NextStep.Rest(superset.roundRestSeconds ?: settings.restTimerSeconds, nextExerciseId = next)
        } else {
            NextStep.Transition(next, superset.transitionSeconds ?: settings.supersetTransitionSeconds)
        }
    }
    return NextStep.Rest(plan.restSeconds ?: settings.restTimerSeconds, nextExerciseId = null)
}

/**
 * A superset with a set number of rounds: its drop sets on an exercise's last round, then the next
 * exercise taking part in this round, else the rest before the next round, and after the last round
 * the rest with nothing to go to.
 */
private fun nextInPlannedRounds(
    exerciseId: String,
    type: ExerciseType,
    plan: ExercisePlan,
    setsToday: List<SetEntry>,
    superset: SupersetContext,
    planned: Int,
    settings: Settings,
    perSide: Boolean,
): NextStep {
    val roundPlan = superset.planFor(exerciseId, plan)
    pendingDrop(roundPlan, type, setsToday, settings.dropSetPercent, settings.unitSystem, perSide)?.let { return it }
    val mine = roundPlan.sets ?: planned
    val round = (planned - mine + setsToday.fullSets(perSide)).coerceIn(1, planned)
    val order = superset.memberIds
    val rest = superset.roundRestSeconds ?: settings.restTimerSeconds
    order.drop(order.indexOf(exerciseId) + 1).firstOrNull { superset.joins(it, round) }?.let {
        return NextStep.Transition(it, superset.transitionSeconds ?: settings.supersetTransitionSeconds)
    }
    if (round >= planned) return NextStep.Rest(rest, nextExerciseId = null)
    return NextStep.Rest(rest, nextExerciseId = order.firstOrNull { superset.joins(it, round + 1) })
}

/**
 * "Round 2 of 3 · this exercise joins the last 2 · drop set on its last round", or "Superset done ✓";
 * null when the superset has no planned rounds.
 */
fun supersetProgress(
    superset: SupersetContext,
    exerciseId: String,
    plan: ExercisePlan,
    setsToday: List<SetEntry>,
    perSide: Boolean = false,
): String? {
    val planned = superset.rounds ?: return null
    val mine = superset.roundsOf(exerciseId) ?: planned
    val round = planned - mine + setsToday.fullSets(perSide) + 1
    if (round > planned) return "All $planned rounds done ✓"
    return listOfNotNull(
        "Round $round of $planned",
        "this exercise joins the last ${if (mine == 1) "round" else "$mine rounds"}".takeIf { mine < planned },
        "drop set on its last round".takeIf { superset.dropsOnLastRound(exerciseId, plan) },
    ).joinToString(" · ")
}

/** "Set 2 of 3", "Drop 1 of 2" or "3 of 3 sets done ✓"; null when no number of sets is planned. */
fun planProgress(plan: ExercisePlan, setsToday: List<SetEntry>, drop: NextStep.DropSet?, perSide: Boolean = false): String? {
    val planned = plan.sets ?: return null
    if (drop != null) return "Drop ${drop.number} of ${drop.of}, no rest"
    val done = setsToday.fullSets(perSide)
    return if (done < planned) "Set ${done + 1} of $planned" else "$planned of $planned sets done ✓"
}
