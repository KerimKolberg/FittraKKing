package com.kkfittracking.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TendonPainTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private var next = 0

    private fun rate(date: LocalDate, moment: PainMoment, score: Int, tendon: Tendon = Tendon.PATELLAR) =
        PainEntry("p${next++}", date, tendon, moment, score)

    @Test
    fun lowPainThatSettlesOvernightIsGreen() {
        val entries = listOf(
            rate(monday, PainMoment.MORNING, 1),
            rate(monday, PainMoment.BEFORE, 1),
            rate(monday, PainMoment.AFTER, 2),
            rate(monday.plusDays(1), PainMoment.MORNING, 1),
        )
        val check = painCheck(Tendon.PATELLAR, entries)!!
        assertEquals(PainLight.GREEN, check.light)
        assertEquals(1, check.baseline)
        assertEquals(1, check.nextMorning)
    }

    @Test
    fun pianAboveFiveOrAWorseMorningIsRed() {
        assertEquals(
            PainLight.RED,
            painCheck(Tendon.PATELLAR, listOf(rate(monday, PainMoment.AFTER, 6)))!!.light,
        )
        // Before 1, the next morning 3: clearly worse than usual.
        val worse = listOf(rate(monday, PainMoment.BEFORE, 1), rate(monday, PainMoment.AFTER, 3), rate(monday.plusDays(1), PainMoment.MORNING, 3))
        assertEquals(PainLight.RED, painCheck(Tendon.PATELLAR, worse)!!.light)
    }

    @Test
    fun moderatePainOrAMissingMorningHolds() {
        val moderate = listOf(rate(monday, PainMoment.AFTER, 4), rate(monday.plusDays(1), PainMoment.MORNING, 1))
        assertEquals(PainLight.YELLOW, painCheck(Tendon.PATELLAR, moderate)!!.light)
        val waiting = painCheck(Tendon.PATELLAR, listOf(rate(monday, PainMoment.AFTER, 1)))!!
        assertEquals(PainLight.YELLOW, waiting.light)
        assertTrue(waiting.waitingForMorning)
        // A slightly worse morning (by one) also holds.
        val slightly = listOf(rate(monday, PainMoment.BEFORE, 1), rate(monday.plusDays(1), PainMoment.MORNING, 2))
        assertEquals(PainLight.YELLOW, painCheck(Tendon.PATELLAR, slightly)!!.light)
    }

    @Test
    fun theLatestSessionCountsAndOnlyItsTendon() {
        val entries = listOf(
            rate(monday, PainMoment.AFTER, 7),
            rate(monday.plusDays(2), PainMoment.AFTER, 1),
            rate(monday.plusDays(3), PainMoment.MORNING, 1),
            rate(monday.plusDays(2), PainMoment.AFTER, 6, Tendon.ACHILLES),
        )
        assertEquals(PainLight.GREEN, painCheck(Tendon.PATELLAR, entries)!!.light)
        assertEquals(PainLight.RED, painCheck(Tendon.ACHILLES, entries)!!.light)
        assertNull(painCheck(Tendon.ROTATOR_CUFF, entries))
        assertEquals(listOf(Tendon.PATELLAR, Tendon.ACHILLES), painChecks(entries).map { it.tendon }.sortedBy { it.ordinal })
    }

    @Test
    fun theMorningAfterAsksAboutYesterdaysTendons() {
        val entries = listOf(
            rate(monday, PainMoment.AFTER, 2),
            rate(monday, PainMoment.AFTER, 2, Tendon.ACHILLES),
            rate(monday.plusDays(1), PainMoment.MORNING, 1, Tendon.ACHILLES),
        )
        assertEquals(listOf(Tendon.PATELLAR), tendonsForMorningCheck(entries, monday.plusDays(1)))
        assertEquals(emptyList<Tendon>(), tendonsForMorningCheck(entries, monday.plusDays(2)))
        assertEquals(PainMoment.BEFORE, suggestedMoment(loggedToday = false))
        assertEquals(PainMoment.AFTER, suggestedMoment(loggedToday = true))
    }
}
