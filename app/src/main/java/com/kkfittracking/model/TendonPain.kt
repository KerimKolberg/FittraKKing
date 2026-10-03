package com.kkfittracking.model

import java.time.LocalDate

/** When a tendon's pain was rated: around a session, or the morning after it. */
enum class PainMoment(val label: String) {
    BEFORE("Before training"),
    AFTER("After training"),
    MORNING("In the morning"),
}

/** A tendon's pain on a scale from 0 (none) to 10 (worst), at one [moment] of a day. */
data class PainEntry(
    val id: String,
    val date: LocalDate,
    val tendon: Tendon,
    val moment: PainMoment,
    val score: Int,
    val note: String = "",
)

/** What the pain says about the load, after the pain-monitoring model physios use for tendons. */
enum class PainLight(val label: String, val advice: String) {
    GREEN("Good to go", "Pain stayed low and settled by the next morning: you can add a little load next time."),
    YELLOW("Hold", "Acceptable, but keep the load as it is until pain stays at 2 or less and settles overnight."),
    RED("Step back", "Too much: go back to lighter work or holds for a while. If it stays high, see a physio."),
}

/**
 * How a tendon took its latest session: the pain before, after and the next morning, the light
 * and why. The rules: pain up to 5/10 during and after training is acceptable, as long as it is
 * back to the usual level by the next morning; a morning that is worse than usual means the load
 * was too much.
 */
data class PainCheck(
    val tendon: Tendon,
    val day: LocalDate,
    val before: Int?,
    val after: Int?,
    val nextMorning: Int?,
    /** The usual level: that morning's pain, before the session, or the last morning before. */
    val baseline: Int?,
    val light: PainLight,
    val reason: String,
) {
    /** The next morning has not been rated yet. */
    val waitingForMorning: Boolean get() = nextMorning == null
}

/** Pain this high or higher during or after training means stepping back. */
const val PAIN_LIMIT = 5

fun painCheck(tendon: Tendon, entries: List<PainEntry>): PainCheck? {
    val own = entries.filter { it.tendon == tendon }
    val day = own.filter { it.moment != PainMoment.MORNING }.maxOfOrNull { it.date } ?: return null
    fun on(date: LocalDate, moment: PainMoment) = own.filter { it.date == date && it.moment == moment }.maxOfOrNull { it.score }
    val before = on(day, PainMoment.BEFORE)
    val after = on(day, PainMoment.AFTER)
    val nextMorning = on(day.plusDays(1), PainMoment.MORNING)
    val baseline = on(day, PainMoment.MORNING) ?: before
        ?: own.filter { it.moment == PainMoment.MORNING && it.date < day }.maxByOrNull { it.date }?.score
    val during = listOfNotNull(before, after).maxOrNull() ?: 0
    val (light, reason) = when {
        during > PAIN_LIMIT -> PainLight.RED to "Pain went above $PAIN_LIMIT/10 ($during)."
        nextMorning != null && nextMorning > PAIN_LIMIT -> PainLight.RED to "The next morning was $nextMorning/10."
        nextMorning != null && baseline != null && nextMorning > baseline + 1 ->
            PainLight.RED to "The next morning ($nextMorning) was clearly worse than usual ($baseline)."
        nextMorning != null && baseline != null && nextMorning > baseline ->
            PainLight.YELLOW to "The next morning ($nextMorning) was a little worse than usual ($baseline)."
        during > 2 -> PainLight.YELLOW to "Pain reached $during/10: acceptable, but not yet low."
        nextMorning == null -> PainLight.YELLOW to "Rate it tomorrow morning to know how the tendon took it."
        else -> PainLight.GREEN to "Pain stayed at $during/10 or less and was back to usual the next morning."
    }
    return PainCheck(tendon, day, before, after, nextMorning, baseline, light, reason)
}

/** Every tendon with ratings, the most recently trained first. */
fun painChecks(entries: List<PainEntry>): List<PainCheck> =
    entries.map { it.tendon }.distinct().mapNotNull { painCheck(it, entries) }.sortedByDescending { it.day }

/**
 * The tendons to ask about this morning: rated around a session yesterday and not yet rated this
 * morning.
 */
fun tendonsForMorningCheck(entries: List<PainEntry>, today: LocalDate): List<Tendon> {
    val yesterday = today.minusDays(1)
    val trained = entries.filter { it.date == yesterday && it.moment != PainMoment.MORNING }.map { it.tendon }.toSet()
    val rated = entries.filter { it.date == today && it.moment == PainMoment.MORNING }.map { it.tendon }.toSet()
    return Tendon.entries.filter { it in trained && it !in rated }
}

/** The moment that fits now: before the first set of the day, after it, or the morning after. */
fun suggestedMoment(loggedToday: Boolean): PainMoment = if (loggedToday) PainMoment.AFTER else PainMoment.BEFORE
