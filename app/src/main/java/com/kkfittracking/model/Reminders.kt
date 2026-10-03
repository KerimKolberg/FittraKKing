package com.kkfittracking.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** The next training reminder after [now], on one of [days] at [minuteOfDay]; null when no day is chosen. */
fun nextReminder(now: LocalDateTime, days: Set<DayOfWeek>, minuteOfDay: Int): LocalDateTime? {
    if (days.isEmpty()) return null
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    return (0L..7L)
        .map { now.toLocalDate().plusDays(it).atTime(time) }
        .first { it.isAfter(now) && it.dayOfWeek in days }
}

/** "17:30" for a minute of the day. */
fun formatMinuteOfDay(minuteOfDay: Int): String = "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
