package com.kkfittracking.model

import kotlin.math.roundToInt

/** Sets the guide expects of an exercise that has no number of sets planned. */
const val DEFAULT_GUIDED_SETS = 3

/** The superset [exerciseId] is in on a day, with its plan; null when it is not in one. */
fun supersetContextOf(day: List<DayExercise>, exerciseId: String): SupersetContext? {
    val entry = day.firstOrNull { it.exerciseId == exerciseId } ?: return null
    val members = entry.supersetId?.let { id -> day.filter { it.supersetId == id } }.orEmpty()
    if (members.size < 2) return null
    return SupersetContext(
        memberIds = members.map { it.exerciseId },
        transitionSeconds = entry.transitionSeconds,
        roundRestSeconds = entry.roundRestSeconds,
        rounds = members.firstNotNullOfOrNull { it.supersetRounds },
        dropOnLastRound = members.any { it.supersetDropLast },
        members = members.map { SupersetMember(it.exerciseId, it.memberRounds, it.memberDropSet) },
    )
}

/** How far one exercise of a day got against its plan. */
data class ExerciseCompletion(
    val exerciseId: String,
    val name: String,
    val type: ExerciseType,
    val plannedSets: Int,
    val doneSets: Int,
    val plannedDrops: Int,
    val doneDrops: Int,
    /** No number of sets was planned, so [plannedSets] is the guide's default. */
    val setsGuessed: Boolean = false,
) {
    val planned: Int get() = plannedSets + plannedDrops
    val done: Int get() = doneSets.coerceAtMost(plannedSets) + doneDrops.coerceAtMost(plannedDrops)
    val isDone: Boolean get() = done >= planned
    val isStarted: Boolean get() = doneSets + doneDrops > 0

    /** "3 of 3 sets · 2 of 2 drops", or "Session done" for sports and HIIT. */
    val label: String
        get() {
            if (type.isSession) return if (doneSets > 0) "Session done" else "Session not done"
            val sets = "$doneSets of $plannedSets sets"
            return if (plannedDrops > 0) "$sets · $doneDrops of $plannedDrops drops" else sets
        }
}

/** A day's plan against what was logged: how much is done, what was done and what was not. */
data class DayCompletion(val exercises: List<ExerciseCompletion>) {
    val planned: Int get() = exercises.sumOf { it.planned }
    val done: Int get() = exercises.sumOf { it.done }

    /** 0 to 100. */
    val percent: Int get() = if (planned == 0) 0 else (done * 100.0 / planned).roundToInt()

    val finished: List<ExerciseCompletion> get() = exercises.filter { it.isDone }
    val partly: List<ExerciseCompletion> get() = exercises.filter { it.isStarted && !it.isDone }
    val notStarted: List<ExerciseCompletion> get() = exercises.filter { !it.isStarted }
}

/** What an exercise is planned to do today, as its superset shapes it. */
private data class Target(val sets: Int, val drops: Int, val guessed: Boolean, val plan: ExercisePlan)

private fun targetOf(exercise: DayExercise, superset: SupersetContext?): Target {
    if (exercise.exerciseType.isSession) return Target(1, 0, guessed = false, plan = ExercisePlan())
    val plan = superset?.planFor(exercise.exerciseId, exercise.plan) ?: exercise.plan
    // Drop sets without a number of sets make the whole exercise one drop set (see pendingDrop).
    val sets = plan.sets ?: if (plan.dropSets) 1 else DEFAULT_GUIDED_SETS
    val drops = if (plan.dropSets && exercise.exerciseType == ExerciseType.WEIGHT_REPS) plan.drops else 0
    return Target(sets, drops, guessed = plan.sets == null, plan = plan.copy(sets = sets))
}

/** Compares a day's plan (set plans, superset rounds and drop sets) with the sets logged. */
fun dayCompletion(day: List<DayExercise>): DayCompletion = DayCompletion(
    day.map { exercise ->
        val target = targetOf(exercise, supersetContextOf(day, exercise.exerciseId))
        val drops = exercise.sets.count { it.values.isDropSet }
        ExerciseCompletion(
            exerciseId = exercise.exerciseId,
            name = exercise.exerciseName,
            type = exercise.exerciseType,
            plannedSets = target.sets,
            // A left and a right set make one set of a one-sided exercise.
            doneSets = exercise.sets.fullSets(),
            plannedDrops = target.drops,
            doneDrops = drops,
            setsGuessed = target.guessed,
        )
    },
)

/** Where the guide sends the user next. */
data class GuideTarget(
    val exerciseId: String,
    val name: String,
    /** "Set 2 of 3", "Round 2 of 3" or "Drop 1 of 2". */
    val step: String,
    /** The exercise's place in the day, counted from 1, and the day's number of exercises. */
    val position: Int,
    val of: Int,
    /** The next set is a drop set. */
    val isDrop: Boolean = false,
    /** The side to do next, for one-sided exercises halfway through a set. */
    val side: Side? = null,
)

/**
 * The exercise to do now in a guided workout: the day's exercises in order, a superset round by
 * round (each exercise joining its rounds), drop sets right after the set they follow. Exercises
 * in [skipped] are passed over. Null when everything planned is done.
 */
fun guideTarget(day: List<DayExercise>, skipped: Set<String> = emptySet(), settings: Settings = Settings()): GuideTarget? {
    val completion = dayCompletion(day).exercises.associateBy { it.exerciseId }
    val position = { id: String -> day.indexOfFirst { it.exerciseId == id } + 1 }
    for (block in groupDay(day)) {
        val members = block.items.filter { it.exerciseId !in skipped && completion[it.exerciseId]?.isDone == false }
        if (members.isEmpty()) continue
        val superset = (block as? Block.Superset)?.let { supersetContextOf(day, block.items.first().exerciseId) }

        // The other side of a set that is halfway done comes first, with no rest.
        members.forEach { exercise ->
            val side = exercise.sets.sideDue() ?: return@forEach
            val done = completion.getValue(exercise.exerciseId)
            return GuideTarget(
                exercise.exerciseId, exercise.exerciseName, "Set ${done.doneSets + 1} of ${done.plannedSets} · ${side.label.lowercase()}",
                position(exercise.exerciseId), day.size, side = side,
            )
        }
        // A drop set that is due comes first: it follows its set with no rest.
        members.forEach { exercise ->
            val target = targetOf(exercise, superset)
            val drop = pendingDrop(target.plan, exercise.exerciseType, exercise.sets, settings.dropSetPercent, exercise.weightUnits ?: settings.unitSystem)
            if (drop != null) {
                return GuideTarget(
                    exercise.exerciseId, exercise.exerciseName, "Drop ${drop.number} of ${drop.of}",
                    position(exercise.exerciseId), day.size, isDrop = true,
                )
            }
        }
        if (superset == null) {
            val exercise = members.first()
            val done = completion.getValue(exercise.exerciseId)
            val step = if (exercise.exerciseType.isSession) "Session" else "Set ${done.doneSets + 1} of ${done.plannedSets}"
            return GuideTarget(exercise.exerciseId, exercise.exerciseName, step, position(exercise.exerciseId), day.size)
        }

        // Round by round: the lowest round still to do, and in it the first exercise in order.
        val order = block.items.map { it.exerciseId }
        val targets = block.items.associate { it.exerciseId to targetOf(it, superset).sets }
        val rounds = superset.rounds ?: targets.values.max()
        fun nextRound(exercise: DayExercise): Int {
            val mine = targets.getValue(exercise.exerciseId)
            val firstRound = if (superset.rounds != null) rounds - mine + 1 else 1
            return firstRound + completion.getValue(exercise.exerciseId).doneSets
        }
        val next = members.minWith(compareBy<DayExercise> { nextRound(it) }.thenBy { order.indexOf(it.exerciseId) })
        return GuideTarget(
            next.exerciseId, next.exerciseName, "Round ${nextRound(next).coerceAtMost(rounds)} of $rounds",
            position(next.exerciseId), day.size,
        )
    }
    return null
}

/**
 * The exercises coming up after [current], in the order the guide will reach them (the rest of a
 * superset round first, then the next exercises of the day), each named once: to get ready and set up
 * the equipment. Found by playing the plan forward, one set at a time.
 */
fun upcomingExercises(
    day: List<DayExercise>,
    current: GuideTarget,
    skipped: Set<String> = emptySet(),
    settings: Settings = Settings(),
    count: Int = 3,
): List<String> {
    val upcoming = mutableListOf<String>()
    var played = day
    var target = current
    repeat(MAX_PLAYED_SETS) { step ->
        if (upcoming.size >= count) return upcoming
        played = played.map { exercise ->
            if (exercise.exerciseId != target.exerciseId) return@map exercise
            val weight = exercise.sets.lastOrNull()?.values?.weightKg ?: 20.0
            exercise.copy(sets = exercise.sets + SetEntry("played-$step", SetValues(weight, 1, isDropSet = target.isDrop, side = target.side)))
        }
        target = guideTarget(played, skipped, settings) ?: return upcoming
        if (target.exerciseId != current.exerciseId && target.name !in upcoming) upcoming += target.name
    }
    return upcoming
}

private const val MAX_PLAYED_SETS = 300

/**
 * The values to suggest for the guide's next set: a due drop set's lighter weight (and its reps),
 * else today's last set of the exercise, else the plan's reps and weight over [lastSession]'s last
 * set. Empty when there is nothing to go on.
 */
fun guideSuggestion(day: List<DayExercise>, target: GuideTarget, settings: Settings, lastSession: List<SetEntry> = emptyList()): SetValues {
    val exercise = day.firstOrNull { it.exerciseId == target.exerciseId } ?: return SetValues()
    if (target.isDrop) {
        val plan = targetOf(exercise, supersetContextOf(day, exercise.exerciseId)).plan
        pendingDrop(plan, exercise.exerciseType, exercise.sets, settings.dropSetPercent, exercise.weightUnits ?: settings.unitSystem)?.let {
            return SetValues(weightKg = it.weightKg, reps = it.reps, isDropSet = true)
        }
    }
    exercise.sets.lastOrNull { !it.values.isDropSet }?.let { return it.values.copy(note = "") }
    val last = lastSession.lastOrNull { !it.values.isDropSet }?.values ?: SetValues()
    val plan = exercise.plan
    return SetValues(
        weightKg = plan.weightKg ?: last.weightKg,
        reps = plan.reps ?: last.reps,
        distanceMeters = last.distanceMeters,
        durationSeconds = last.durationSeconds,
        rpe = last.rpe,
    )
}

/**
 * A guided workout in progress on [epochDay]. Times are wall-clock milliseconds; time spent paused
 * does not count as training time.
 */
data class GuideSession(
    val epochDay: Long,
    val startedAtMillis: Long,
    /** When the current pause started; null while training. */
    val pausedAtMillis: Long? = null,
    /** Time spent in earlier pauses. */
    val pausedMillis: Long = 0,
    /** Exercises the user chose to leave out today. */
    val skipped: Set<String> = emptySet(),
) {
    val isPaused: Boolean get() = pausedAtMillis != null

    fun activeMillis(now: Long): Long = ((pausedAtMillis ?: now) - startedAtMillis - pausedMillis).coerceAtLeast(0)

    fun pause(now: Long): GuideSession = if (isPaused) this else copy(pausedAtMillis = now)

    fun resume(now: Long): GuideSession =
        pausedAtMillis?.let { copy(pausedAtMillis = null, pausedMillis = pausedMillis + (now - it).coerceAtLeast(0)) } ?: this

    fun skip(exerciseId: String): GuideSession = copy(skipped = skipped + exerciseId)
}

/** What a stopped guided workout did: shown once it ends. */
data class GuideSummary(
    val epochDay: Long,
    val activeMillis: Long,
    val completion: DayCompletion,
    val stoppedEarly: Boolean,
    /** Estimated calories of the day's workouts; null when nothing was logged. */
    val kcal: Int? = null,
    val bodyweightKnown: Boolean = false,
)

/** What moving a day's unfinished part to another day does: exercises to add there, entries to take off here. */
data class Postponement(
    val toAdd: List<PlannedExercise>,
    /** Exercises not started at all leave this day; partly done ones stay with the sets that were done. */
    val toRemove: List<String>,
)

/**
 * The unfinished part of a day, to do on another day: every exercise not done yet, with its
 * superset. A superset with planned rounds keeps only the rounds that are left.
 */
fun unfinishedPart(day: List<DayExercise>): Postponement {
    val completion = dayCompletion(day).exercises.associateBy { it.exerciseId }
    val left = day.filter { completion[it.exerciseId]?.isDone == false }
    fun roundsLeft(supersetId: String?, planned: Int?): Int? {
        if (supersetId == null || planned == null) return planned
        val members = left.filter { it.supersetId == supersetId }
        val superset = members.firstOrNull()?.let { supersetContextOf(day, it.exerciseId) } ?: return planned
        return members.maxOf { member ->
            (superset.roundsOf(member.exerciseId) ?: planned) - (completion[member.exerciseId]?.doneSets ?: 0)
        }.coerceIn(1, planned)
    }
    return Postponement(
        toAdd = left.map {
            PlannedExercise(
                exerciseId = it.exerciseId,
                supersetId = it.supersetId,
                transitionSeconds = it.transitionSeconds,
                roundRestSeconds = it.roundRestSeconds,
                supersetRounds = roundsLeft(it.supersetId, it.supersetRounds),
                supersetDropLast = it.supersetDropLast,
                memberRounds = it.memberRounds,
                memberDropSet = it.memberDropSet,
            )
        },
        toRemove = left.filter { it.sets.isEmpty() }.map { it.workoutExerciseId },
    )
}
