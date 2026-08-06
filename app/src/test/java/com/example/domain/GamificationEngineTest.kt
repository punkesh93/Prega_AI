package com.example.domain

import com.example.data.ProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * These tests exist mainly to protect the *kindness* guarantees of the
 * gamification design. The scoring maths is trivial; what matters is that a
 * user who has a bad week is never punished for it.
 */
class GamificationEngineTest {

    private val engine = GamificationEngine(repository = FakeRepository())
    private val today: LocalDate = LocalDate.of(2026, 5, 20)

    private fun progress(
        streak: Int = 0,
        last: String = "",
        freezes: Int = 2,
        longest: Int = 0,
        points: Int = 0,
    ) = ProgressEntity(
        points = points,
        currentStreak = streak,
        longestStreak = longest,
        lastActiveDate = last,
        streakFreezes = freezes,
        // Pinned to "today" so the weekly top-up doesn't fire and muddy the
        // freeze assertions. The replenishment test overrides this explicitly.
        lastFreezeGrantDate = "2026-05-20",
    )

    @Test
    fun `first ever day starts a streak of one`() {
        val out = engine.resolveStreak(progress(), today)
        assertEquals(1, out.progress.currentStreak)
        assertTrue(out.streakIncreased)
    }

    @Test
    fun `opening twice in one day does not double count`() {
        val out = engine.resolveStreak(progress(streak = 5, last = "2026-05-20"), today)
        assertEquals(5, out.progress.currentStreak)
        assertFalse(out.streakIncreased)
    }

    @Test
    fun `consecutive day increments the streak`() {
        val out = engine.resolveStreak(progress(streak = 5, last = "2026-05-19"), today)
        assertEquals(6, out.progress.currentStreak)
        assertTrue(out.streakIncreased)
    }

    @Test
    fun `longest streak is remembered`() {
        val out = engine.resolveStreak(progress(streak = 9, last = "2026-05-19", longest = 9), today)
        assertEquals(10, out.progress.longestStreak)
    }

    /**
     * The core promise: one rough day — which in the first trimester is simply
     * a normal day — must not destroy weeks of progress.
     */
    @Test
    fun `a single missed day is covered by a freeze and the streak survives`() {
        val out = engine.resolveStreak(
            progress(streak = 40, last = "2026-05-18", freezes = 2),
            today,
        )
        assertEquals(41, out.progress.currentStreak)
        assertTrue(out.freezeUsed)
        assertEquals(1, out.progress.streakFreezes)
    }

    @Test
    fun `streak only resets once freezes are exhausted`() {
        val out = engine.resolveStreak(
            progress(streak = 40, last = "2026-05-10", freezes = 1),
            today,
        )
        assertEquals(1, out.progress.currentStreak)
        assertFalse(out.freezeUsed)
    }

    /** A reset returns her to day one, never to zero. Today still counts. */
    @Test
    fun `a broken streak resets to one not zero`() {
        val out = engine.resolveStreak(progress(streak = 30, last = "2026-01-01", freezes = 0), today)
        assertEquals(1, out.progress.currentStreak)
    }

    /** Longest streak is a keepsake — losing a run must not erase the record. */
    @Test
    fun `breaking a streak preserves the longest streak record`() {
        val out = engine.resolveStreak(
            progress(streak = 30, last = "2026-01-01", freezes = 0, longest = 30),
            today,
        )
        assertEquals(30, out.progress.longestStreak)
    }

    @Test
    fun `freezes replenish weekly and cap at the maximum`() {
        val stale = progress(streak = 1, last = "2026-05-19", freezes = 0)
            .copy(lastFreezeGrantDate = "2026-01-01")
        val out = engine.resolveStreak(stale, today)
        assertEquals(GamificationEngine.MAX_FREEZES, out.progress.streakFreezes)
    }

    /** Device clock changes and timezone travel must not cost her anything. */
    @Test
    fun `a backwards clock does not reset the streak`() {
        val out = engine.resolveStreak(progress(streak = 12, last = "2026-06-01"), today)
        assertEquals(12, out.progress.currentStreak)
    }

    @Test
    fun `points never decrease`() {
        val out = engine.awardPoints(progress(points = 100), -50)
        assertEquals(100, out.progress.points)
        assertEquals(0, out.pointsAwarded)
    }

    @Test
    fun `crossing a threshold reports a level up`() {
        val out = engine.awardPoints(progress(points = 140), 20)
        assertTrue(out.levelledUp)
        assertEquals(2, out.progress.level)
    }

    @Test
    fun `level never drops below one`() {
        assertEquals(1, progress(points = 0).level)
    }

    @Test
    fun `level progress is bounded`() {
        assertEquals(1f, progress(points = 999_999).levelProgress, 0.001f)
        assertTrue(progress(points = 75).levelProgress in 0f..1f)
    }
}
