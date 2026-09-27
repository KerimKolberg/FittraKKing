package com.kkfittracking.model

import java.time.LocalDate
import kotlin.math.roundToInt

/** The kind of a workout, as health apps (Health Connect, Samsung Health) know it. */
enum class ActivityKind(val label: String) {
    STRENGTH("Strength training"),
    RUNNING("Running"),
    WALKING("Walking"),
    HIKING("Hiking"),
    BIKING("Cycling"),
    ROWING("Rowing"),
    SWIMMING("Swimming"),
    ELLIPTICAL("Elliptical"),
    STAIRS("Stair climbing"),
    JUMP_ROPE("Jump rope"),
    HIIT("HIIT"),
    YOGA("Yoga"),
    STRETCHING("Stretching & mobility"),
    MEDITATION("Meditation & breathing"),
    DANCE("Dance"),
    TENNIS("Tennis"),
    TABLE_TENNIS("Table tennis"),
    VOLLEYBALL("Volleyball"),
    BADMINTON("Badminton"),
    SQUASH("Squash"),
    SOCCER("Football"),
    BASKETBALL("Basketball"),
    CLIMBING("Climbing"),
    MARTIAL_ARTS("Martial arts"),
    BOXING("Boxing"),
    SPORT("Sport"),
    OTHER("Workout"),
}

/** One workout as a health app sees it: one kind of activity from start to end, with its calories. */
data class WorkoutSession(
    val kind: ActivityKind,
    val startMillis: Long,
    val endMillis: Long,
    val title: String,
    /** The exercises and sets, one per line. */
    val notes: String,
    val kcal: Double,
    /** Measured by the watch while it recorded the activity, rather than estimated. */
    val kcalMeasured: Boolean,
    /** Stable for the same sets, so sending a day again replaces what was sent. */
    val key: String,
) {
    val minutes: Int get() = ((endMillis - startMillis) / 60_000).toInt()
}

/** A day's workouts and their calories. */
data class DayWorkouts(
    val sessions: List<WorkoutSession>,
    val bodyweightKg: Double,
    /** False when no bodyweight was logged, so a typical one was assumed. */
    val bodyweightKnown: Boolean,
) {
    val kcal: Int get() = sessions.sumOf { it.kcal }.roundToInt()
    val minutes: Int get() = sessions.sumOf { it.minutes }
}

/** Used for the calories when no bodyweight has been logged. */
const val TYPICAL_BODYWEIGHT_KG = 75.0

/** The last bodyweight logged on or before [date], in kg. */
fun bodyweightOn(date: LocalDate, measurements: List<BodyMeasurement>): Double? =
    measurements.filter { it.metric == BodyMetric.BODYWEIGHT && !it.date.isAfter(date) }.maxByOrNull { it.date }?.value

/** Minutes before a block's first logged set: the set itself and warming up to it. */
private const val LEAD_MINUTES = 2

/** A longer break between two logged sets starts a new workout (e.g. mobility in the morning, weights at night). */
private const val NEW_WORKOUT_AFTER_MINUTES = 45

/**
 * A day's workouts, from its logged sets: the gym work (weights, holds, stretches…) as one workout
 * per block of sets logged close together, and each run, ride, sports session or HIIT round as its
 * own. Times come from when the sets were logged; sets without a time end at [fallbackEndMillis].
 *
 * Calories are an estimate from MET values (how hard an activity is compared with sitting still):
 * kcal = MET × 3.5 × kg / 200 per minute. Expect ±20–30 %, a little more for weights. Calories the
 * watch measured while recording an activity (in the set's note) are used as they are.
 */
fun dayWorkouts(
    day: List<DayExercise>,
    bodyweightKg: Double?,
    units: UnitSystem,
    fallbackEndMillis: Long,
): DayWorkouts {
    val weight = bodyweightKg ?: TYPICAL_BODYWEIGHT_KG
    val activities = day.filter { isActivity(it) }
    val gym = day - activities.toSet()
    val sessions = gymWorkouts(gym, weight, fallbackEndMillis) + activities.flatMap { activitySessions(it, weight, units, fallbackEndMillis) }
    return DayWorkouts(sessions.sortedBy { it.startMillis }, weight, bodyweightKg != null)
}

private fun kcal(met: Double, weightKg: Double, minutes: Double): Double = met * 3.5 * weightKg / 200 * minutes

/** Runs, rides, sports and HIIT sessions: logged with a time or distance, each a workout of its own. */
private fun isActivity(exercise: DayExercise): Boolean {
    val type = exercise.exerciseType
    val style = exercise.styles.firstOrNull()
    return type.isSession || type.usesDistance ||
        (type == ExerciseType.TIME && (style == TrainingStyle.CARDIO || style == TrainingStyle.HIIT))
}

private fun gymWorkouts(gym: List<DayExercise>, weightKg: Double, fallbackEndMillis: Long): List<WorkoutSession> {
    val sets = gym.flatMap { exercise -> exercise.sets.map { exercise to it } }
        .sortedBy { it.second.loggedAtMillis ?: fallbackEndMillis }
    if (sets.isEmpty()) return emptyList()
    // Blocks of sets logged close together; a long break starts a new workout.
    val blocks = mutableListOf(mutableListOf(sets.first()))
    sets.zipWithNext().forEach { (before, after) ->
        val gap = (after.second.loggedAtMillis ?: fallbackEndMillis) - (before.second.loggedAtMillis ?: fallbackEndMillis)
        if (gap > NEW_WORKOUT_AFTER_MINUTES * 60_000L) blocks += mutableListOf(after) else blocks.last() += after
    }
    return blocks.map { block ->
        val end = block.last().second.loggedAtMillis ?: fallbackEndMillis
        val first = block.first().second.loggedAtMillis ?: fallbackEndMillis
        // Sets without times: about 2.5 minutes each, with the rest.
        val start = if (block.all { it.second.loggedAtMillis == null }) {
            end - (block.size * 150_000L)
        } else {
            first - LEAD_MINUTES * 60_000L
        }
        val minutes = (end - start) / 60_000.0
        // The block's time shared out by sets, each exercise at its own MET.
        val met = block.sumOf { (exercise, _) -> gymMet(exercise) } / block.size
        val exercises = block.map { it.first }.distinct()
        WorkoutSession(
            kind = gymKind(block.map { it.first }),
            startMillis = start,
            endMillis = end,
            title = gymKind(block.map { it.first }).label,
            notes = exercises.joinToString("\n") { exercise ->
                val mine = block.filter { it.first == exercise }.map { it.second }
                val full = mine.fullSets()
                val drops = mine.count { it.values.isDropSet }
                "${exercise.exerciseName}: " + (if (full == 1) "1 set" else "$full sets") + (if (drops > 0) " + $drops drops" else "")
            },
            kcal = kcal(met, weightKg, minutes),
            kcalMeasured = false,
            key = "kk-gym-${block.first().second.id}",
        )
    }
}

/** The kind of a gym block: strength, unless it was mostly yoga, stretching or breathing. */
private fun gymKind(exercises: List<DayExercise>): ActivityKind {
    val styles = exercises.groupingBy { it.styles.firstOrNull() ?: TrainingStyle.STRENGTH }.eachCount()
    val (main, count) = styles.maxBy { it.value }
    if (count * 2 <= exercises.size) return ActivityKind.STRENGTH
    return when (main) {
        TrainingStyle.YOGA -> ActivityKind.YOGA
        TrainingStyle.STRETCHING, TrainingStyle.MOBILITY -> ActivityKind.STRETCHING
        TrainingStyle.MEDITATION -> ActivityKind.MEDITATION
        else -> ActivityKind.STRENGTH
    }
}

/** How hard gym work is, rests included (Compendium of Physical Activities). */
private fun gymMet(exercise: DayExercise): Double = when (exercise.styles.firstOrNull() ?: TrainingStyle.STRENGTH) {
    TrainingStyle.STRENGTH, TrainingStyle.ECCENTRIC -> 3.5
    TrainingStyle.ISOMETRIC -> 3.0
    TrainingStyle.PLYOMETRIC -> 6.0
    TrainingStyle.MOBILITY, TrainingStyle.YOGA -> 2.5
    TrainingStyle.STRETCHING -> 2.3
    TrainingStyle.MEDITATION -> 1.3
    TrainingStyle.CARDIO -> 7.0
    TrainingStyle.HIIT -> 8.0
    TrainingStyle.SPORT -> 6.0
    TrainingStyle.DANCE -> 5.0
}

/** The kind of a run, ride or sports session, from its name and training style. */
fun activityKind(exercise: DayExercise): ActivityKind {
    val name = exercise.exerciseName.lowercase()
    fun has(vararg words: String) = words.any { it in name }
    val style = exercise.styles.firstOrNull()
    return when {
        has("sprint", "run", "jog") -> ActivityKind.RUNNING
        has("hik") -> ActivityKind.HIKING
        has("walk") -> ActivityKind.WALKING
        has("cycl", "bike", "spin") -> ActivityKind.BIKING
        has("rowing") -> ActivityKind.ROWING
        has("swim") -> ActivityKind.SWIMMING
        has("ellip") -> ActivityKind.ELLIPTICAL
        has("stair") -> ActivityKind.STAIRS
        has("rope") -> ActivityKind.JUMP_ROPE
        has("table tennis", "ping pong") -> ActivityKind.TABLE_TENNIS
        has("tennis") -> ActivityKind.TENNIS
        has("volleyball") -> ActivityKind.VOLLEYBALL
        has("badminton") -> ActivityKind.BADMINTON
        has("squash") -> ActivityKind.SQUASH
        has("football", "soccer") -> ActivityKind.SOCCER
        has("basketball") -> ActivityKind.BASKETBALL
        has("climb", "boulder") -> ActivityKind.CLIMBING
        has("kickbox", "martial", "judo", "karate", "bjj") -> ActivityKind.MARTIAL_ARTS
        has("boxing", "heavy bag") -> ActivityKind.BOXING
        exercise.exerciseType == ExerciseType.INTERVALS || style == TrainingStyle.HIIT ||
            has("hiit", "tabata", "interval") -> ActivityKind.HIIT
        style == TrainingStyle.DANCE -> ActivityKind.DANCE
        style == TrainingStyle.YOGA -> ActivityKind.YOGA
        style == TrainingStyle.MEDITATION || has("meditat", "breath") -> ActivityKind.MEDITATION
        style == TrainingStyle.SPORT -> ActivityKind.SPORT
        else -> ActivityKind.OTHER
    }
}

/** Minutes per km when a distance was logged without a time. */
private fun minutesPerKm(kind: ActivityKind): Double = when (kind) {
    ActivityKind.RUNNING -> 6.0
    ActivityKind.WALKING -> 12.0
    ActivityKind.HIKING -> 15.0
    ActivityKind.BIKING -> 3.0
    ActivityKind.ROWING -> 5.0
    ActivityKind.SWIMMING -> 25.0
    else -> 10.0
}

/** How hard an activity is (Compendium of Physical Activities); running and walking by their speed. */
private fun activityMet(kind: ActivityKind, name: String, kmPerHour: Double?): Double = when (kind) {
    ActivityKind.RUNNING -> kmPerHour?.let { (1.03 * it).coerceIn(6.0, 18.0) } ?: 9.8
    ActivityKind.WALKING -> kmPerHour?.let { (0.75 * it).coerceIn(2.5, 6.5) } ?: 3.5
    ActivityKind.HIKING -> 6.0
    ActivityKind.BIKING -> 7.5
    ActivityKind.ROWING -> 7.0
    ActivityKind.SWIMMING -> 7.0
    ActivityKind.ELLIPTICAL -> 5.0
    ActivityKind.STAIRS -> 9.0
    ActivityKind.JUMP_ROPE -> 11.0
    ActivityKind.HIIT -> 8.0
    ActivityKind.YOGA -> 2.5
    ActivityKind.STRETCHING -> 2.3
    ActivityKind.MEDITATION -> 1.3
    ActivityKind.DANCE -> 5.5
    ActivityKind.TENNIS -> 7.3
    ActivityKind.TABLE_TENNIS -> 4.0
    ActivityKind.VOLLEYBALL -> if ("beach" in name) 8.0 else 4.0
    ActivityKind.BADMINTON -> 5.5
    ActivityKind.SQUASH -> 7.3
    ActivityKind.SOCCER -> 7.0
    ActivityKind.BASKETBALL -> 6.5
    ActivityKind.CLIMBING -> 7.5
    ActivityKind.MARTIAL_ARTS -> 7.5
    ActivityKind.BOXING -> 7.8
    ActivityKind.SPORT -> 6.0
    ActivityKind.OTHER, ActivityKind.STRENGTH -> 5.0
}

/** "… 320 kcal …" in a set's note: what the watch measured while it recorded the activity. */
private val MEASURED_KCAL = Regex("""(\d+) kcal""")

private fun activitySessions(exercise: DayExercise, weightKg: Double, units: UnitSystem, fallbackEndMillis: Long): List<WorkoutSession> {
    val kind = activityKind(exercise)
    val name = exercise.exerciseName.lowercase()
    return exercise.sets.map { set ->
        val values = set.values
        val km = values.distanceMeters?.takeIf { exercise.exerciseType.usesDistance && it > 0 }?.div(1000)
        val minutes = values.durationSeconds?.takeIf { it > 0 }?.div(60.0)
            ?: km?.let { it * minutesPerKm(kind) }
            ?: 30.0
        val end = set.loggedAtMillis ?: fallbackEndMillis
        val start = end - (minutes * 60_000).toLong().coerceAtLeast(60_000)
        val speed = km?.let { it / (minutes / 60) }
        // Harder or easier than usual, when the effort (RPE 1–10) was logged: 5 is usual.
        val effort = values.rpe?.let { 0.7 + 0.06 * it } ?: 1.0
        val measured = MEASURED_KCAL.find(values.note)?.groupValues?.get(1)?.toDoubleOrNull()
        WorkoutSession(
            kind = kind,
            startMillis = start,
            endMillis = end,
            title = exercise.exerciseName,
            notes = formatSet(values, exercise.exerciseType, units),
            kcal = measured ?: kcal(activityMet(kind, name, speed) * effort, weightKg, minutes),
            kcalMeasured = measured != null,
            key = "kk-set-${set.id}",
        )
    }
}
