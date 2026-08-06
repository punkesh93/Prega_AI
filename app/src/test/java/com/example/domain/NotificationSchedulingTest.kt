package com.example.domain

import com.example.notifications.NotificationScheduler
import com.example.notifications.PregaNotificationWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Quiet hours are the part that must not be got wrong. A notification at 3am
 * to someone who finally got to sleep is the fastest uninstall in the app.
 */
class NotificationSchedulingTest {

    private fun quiet(now: String) =
        PregaNotificationWorker.inQuietHours("21:30", "08:00", LocalTime.parse(now))

    @Test
    fun `late evening is inside the quiet window`() {
        assertTrue(quiet("22:00"))
        assertTrue(quiet("21:30"))
    }

    @Test
    fun `the small hours are inside the quiet window`() {
        assertTrue(quiet("03:00"))
        assertTrue(quiet("00:01"))
    }

    @Test
    fun `early morning before the window ends is still quiet`() {
        assertTrue(quiet("07:59"))
    }

    @Test
    fun `the window ends exactly at the end time`() {
        assertFalse(quiet("08:00"))
    }

    @Test
    fun `daytime is not quiet`() {
        assertFalse(quiet("09:00"))
        assertFalse(quiet("14:00"))
        assertFalse(quiet("21:29"))
    }

    /** Some users work nights and set a daytime quiet window. */
    @Test
    fun `a same-day window that does not cross midnight works`() {
        fun q(now: String) =
            PregaNotificationWorker.inQuietHours("09:00", "17:00", LocalTime.parse(now))
        assertTrue(q("12:00"))
        assertFalse(q("08:00"))
        assertFalse(q("18:00"))
        assertFalse(q("02:00"))
    }

    @Test
    fun `malformed quiet hours never suppress notifications silently`() {
        assertFalse(PregaNotificationWorker.inQuietHours("", "08:00", LocalTime.NOON))
        assertFalse(PregaNotificationWorker.inQuietHours("nonsense", "x", LocalTime.NOON))
    }

    @Test
    fun `a time later today schedules for today`() {
        val now = LocalDateTime.of(2026, 5, 20, 6, 0)
        val delay = NotificationScheduler.delayUntil(LocalTime.of(8, 30), now)
        assertEquals(150, delay.toMinutes())
    }

    @Test
    fun `a time already passed schedules for tomorrow`() {
        val now = LocalDateTime.of(2026, 5, 20, 9, 0)
        val delay = NotificationScheduler.delayUntil(LocalTime.of(8, 30), now)
        assertEquals(23 * 60 + 30, delay.toMinutes())
    }

    @Test
    fun `scheduling exactly at the target time waits a full day`() {
        val now = LocalDateTime.of(2026, 5, 20, 8, 30)
        val delay = NotificationScheduler.delayUntil(LocalTime.of(8, 30), now)
        assertEquals(24 * 60, delay.toMinutes())
    }
}
