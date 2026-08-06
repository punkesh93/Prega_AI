package com.example.domain

import com.example.ui.onboarding.DateMode
import com.example.ui.onboarding.computeWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Getting the week wrong misdates her entire pregnancy — every milestone, every
 * piece of advice, the due date. It is the single highest-consequence
 * calculation in the app, so it is tested directly.
 */
class OnboardingDateTest {

    private val today: LocalDate = LocalDate.of(2026, 5, 20)

    @Test
    fun `last period ten weeks ago gives week ten`() {
        val r = computeWeek("2026-03-11", DateMode.LastPeriod, today)
        assertNotNull(r)
        assertEquals(10, r!!.week)
    }

    @Test
    fun `due date is 280 days after the last period`() {
        val r = computeWeek("2026-03-11", DateMode.LastPeriod, today)!!
        assertEquals(LocalDate.of(2026, 12, 16), r.dueDate)
    }

    /** Entering a due date must produce the same week as entering the LMP. */
    @Test
    fun `both entry modes agree`() {
        val viaLmp = computeWeek("2026-03-11", DateMode.LastPeriod, today)!!
        val viaDue = computeWeek("2026-12-16", DateMode.DueDate, today)!!
        assertEquals(viaLmp.week, viaDue.week)
        assertEquals(viaLmp.dueDate, viaDue.dueDate)
    }

    @Test
    fun `partial weeks report the day`() {
        // 10 weeks and 3 days before "today".
        val r = computeWeek("2026-03-08", DateMode.LastPeriod, today)!!
        assertEquals(10, r.week)
        assertEquals(3, r.dayOfWeek)
    }

    @Test
    fun `a future date is rejected rather than silently accepted`() {
        assertNull(computeWeek("2026-08-01", DateMode.LastPeriod, today))
    }

    @Test
    fun `an implausibly distant past date is rejected`() {
        assertNull(computeWeek("2024-01-01", DateMode.LastPeriod, today))
    }

    @Test
    fun `garbage input is rejected`() {
        assertNull(computeWeek("", DateMode.DueDate, today))
        assertNull(computeWeek("not-a-date", DateMode.DueDate, today))
        assertNull(computeWeek("2026-13-45", DateMode.DueDate, today))
    }

    @Test
    fun `days remaining counts down to the due date`() {
        val r = computeWeek("2026-12-16", DateMode.DueDate, today)!!
        assertEquals(210, r.daysRemaining)
    }

    @Test
    fun `an overdue pregnancy reports zero days remaining not a negative`() {
        val r = computeWeek("2026-05-01", DateMode.DueDate, today)!!
        assertEquals(0, r.daysRemaining)
    }
}
