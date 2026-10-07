package com.kkfittracking.model

import java.time.DayOfWeek

enum class UnitSystem(
    val label: String,
    val weightUnit: String,
    val distanceUnit: String,
    /** Step used by the +/- buttons on the weight field, in this unit system's weight unit. */
    val weightStep: Double,
) {
    METRIC("Metric (kg, km)", "kg", "km", 2.5),
    IMPERIAL("Imperial (lb, mi)", "lb", "mi", 5.0),

    /**
     * For one exercise only: a machine's own levels (pin or stack numbers), which are neither kg nor
     * lb. The number is kept as it is; distances stay metric.
     */
    LEVELS("Machine levels", "lvl", "km", 1.0),
    ;

    /** A choice for the whole app (levels are only for single machines). */
    val isAppWide: Boolean get() = this != LEVELS

    private val imperial: Boolean get() = this == IMPERIAL

    fun weightFromKg(kg: Double): Double = if (imperial) kg / KG_PER_LB else kg

    fun weightToKg(value: Double): Double = if (imperial) value * KG_PER_LB else value

    fun distanceFromMeters(meters: Double): Double =
        if (imperial) meters / METERS_PER_MILE else meters / METERS_PER_KM

    fun distanceToMeters(value: Double): Double =
        if (imperial) value * METERS_PER_MILE else value * METERS_PER_KM

    val lengthUnit: String get() = if (imperial) "in" else "cm"

    fun lengthFromCm(cm: Double): Double = if (imperial) cm / CM_PER_INCH else cm

    fun lengthToCm(value: Double): Double = if (imperial) value * CM_PER_INCH else value

    /** Jump height or distance, stored in meters, shown in cm or inches. */
    fun heightFromMeters(meters: Double): Double = lengthFromCm(meters * 100)

    fun heightToMeters(value: Double): Double = lengthToCm(value) / 100

    companion object {
        const val KG_PER_LB = 0.45359237
        const val METERS_PER_KM = 1000.0
        const val METERS_PER_MILE = 1609.344
        const val CM_PER_INCH = 2.54
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class Settings(
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val restTimerSeconds: Int = 90,
    val autoStartRestTimer: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Workouts per week that keep a streak going. */
    val weeklyGoal: Int = 3,
    /** Show the drop set option when logging weighted sets. */
    val dropSetsEnabled: Boolean = true,
    /** How much lighter each drop set is, in percent. */
    val dropSetPercent: Int = 20,
    /** After saving a set in a superset, open the next exercise of the superset. */
    val supersetAutoAdvance: Boolean = true,
    /** Default seconds to get from one superset exercise to the next. */
    val supersetTransitionSeconds: Int = 15,
    /** The library's sections (category ids) in the user's order; empty for the default order. */
    val sectionOrder: List<String> = emptyList(),
    /** The training styles (their names) in the user's order; empty for the default order. */
    val styleOrder: List<String> = emptyList(),
    /** Finished workouts go to Health Connect (Samsung Health, Google Fit…) when it is connected. */
    val sendWorkoutsToHealth: Boolean = true,
    /** The estimated calories go along; off, as a watch that counts calories all day would count them twice. */
    val sendCaloriesToHealth: Boolean = false,
    /** A backup saved to Downloads/FitTraKKing by itself. */
    val autoBackup: AutoBackup = AutoBackup.WEEKLY,
    /** Days with a training reminder; empty for none. */
    val reminderDays: Set<DayOfWeek> = emptySet(),
    /** When the reminder comes, in minutes after midnight. */
    val reminderMinute: Int = 17 * 60,
    /** Suggest the next weight, reps or time from the last session. */
    val progressionHints: Boolean = true,
    /** Suggest warm-up sets before the first heavy set of a lift. */
    val warmUpSets: Boolean = true,
    /** The barbell's weight, for the plate calculator and warm-ups. */
    val barKg: Double = 20.0,
)
