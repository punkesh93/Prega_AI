package com.example.labor

import com.example.data.ContractionEntity
import com.example.ui.labor.buildSummaryText
import com.example.ui.labor.computeStats
import com.example.ui.labor.elapsedSec
import com.example.ui.labor.fmtClock
import com.example.ui.labor.fmtSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contraction calculation layer, hammered per the integration rules:
 * 0/1/2/10 contractions, deleted (missing) entries, edited durations, very
 * long gaps, midnight crossing, and killed-process reconstruction — all as
 * pure-function tests with no Android in sight. Every number both UIs show
 * comes from these functions, so these tests ARE the timer's correctness.
 */
class ContractionMathTest {

    private fun c(id: Int, startSec: Long, durSec: Int, storedInterval: Int = 0) =
        ContractionEntity(
            id = id,
            startTime = startSec * 1000,
            durationSeconds = durSec,
            intervalSeconds = storedInterval,
            sessionId = 1L,
        )

    // ── 0 / 1 / 2 / 10 ────────────────────────────────────────────────────

    @Test
    fun `empty session has zeroed stats and no last-ended`() {
        val s = computeStats(emptyList(), nowMs = 1_000_000)
        assertEquals(0, s.completed.size)
        assertEquals(0, s.avgDurationSec)
        assertEquals(0, s.avgIntervalSec)
        assertNull(s.lastEndedAgoSec)
    }

    @Test
    fun `single in-progress contraction is excluded from completed stats`() {
        val s = computeStats(listOf(c(1, startSec = 100, durSec = -1)), nowMs = 160_000)
        assertEquals(0, s.completed.size)
        assertNull(s.lastEndedAgoSec)
    }

    @Test
    fun `one completed contraction - duration counted, interval zero`() {
        val s = computeStats(listOf(c(1, 100, 60)), nowMs = 200_000)
        assertEquals(1, s.completed.size)
        assertEquals(60, s.avgDurationSec)
        assertEquals(0, s.avgIntervalSec)          // no pair yet
        assertEquals(0, s.completed[0].intervalSec)
        assertEquals(40, s.lastEndedAgoSec)        // ended at 160s, now 200s
    }

    @Test
    fun `two contractions - interval is start-to-start`() {
        val s = computeStats(listOf(c(1, 100, 60), c(2, 400, 50)), nowMs = 500_000)
        assertEquals(300, s.completed[1].intervalSec)
        assertEquals(300, s.avgIntervalSec)
        assertEquals(55, s.avgDurationSec)
    }

    @Test
    fun `ten contractions - averages across all, list order stable`() {
        val list = (0 until 10).map { i -> c(i + 1, startSec = i * 300L, durSec = 60 + i) }
        val s = computeStats(list.shuffled(), nowMs = 4_000_000) // input order must not matter
        assertEquals(10, s.completed.size)
        assertEquals((60..69).average().toInt(), s.avgDurationSec)
        assertEquals(300, s.avgIntervalSec)
        assertEquals((1..10).toList(), s.completed.map { it.entry.id }) // sorted by start
    }

    // ── The review's sharpest case: deletion must not poison intervals ───

    @Test
    fun `deleting a middle contraction - intervals derived from survivors, stored column ignored`() {
        // Original spacing 100->400->700; middle one deleted. Stored
        // intervalSeconds on the third row still says 300 (stale) — derived
        // math must bridge the survivors to 600 instead.
        val survivors = listOf(
            c(1, 100, 60, storedInterval = 0),
            c(3, 700, 55, storedInterval = 300), // stale stored value
        )
        val s = computeStats(survivors, nowMs = 800_000)
        assertEquals(600, s.completed[1].intervalSec)
        assertEquals(600, s.avgIntervalSec)
    }

    @Test
    fun `edited duration feeds averages and last-ended`() {
        // She long-pressed and corrected 60s -> 45s.
        val s = computeStats(listOf(c(1, 100, 45)), nowMs = 200_000)
        assertEquals(45, s.avgDurationSec)
        assertEquals(55, s.lastEndedAgoSec) // ended 145s, now 200s
    }

    // ── Long gaps, midnight, timezone ─────────────────────────────────────

    @Test
    fun `very long gap counts honestly rather than being capped`() {
        val s = computeStats(listOf(c(1, 0, 60), c(2, 7_200, 60)), nowMs = 8_000_000)
        assertEquals(7_200, s.completed[1].intervalSec) // two hours apart
    }

    @Test
    fun `crossing midnight is a non-event because everything is epoch millis`() {
        // 23:59:30 UTC -> 00:00:30 UTC across a day boundary.
        val beforeMidnight = 1_699_999_170L
        val afterMidnight = 1_699_999_230L
        val s = computeStats(
            listOf(c(1, beforeMidnight, 20), c(2, afterMidnight, 20)),
            nowMs = (afterMidnight + 100) * 1000,
        )
        assertEquals(60, s.completed[1].intervalSec)
        // Timezone changes are equally irrelevant: no local dates exist in
        // the math layer at all — rendering alone localizes.
    }

    // ── Killed process: reconstruction is just... calling the function ───

    @Test
    fun `active contraction elapsed reconstructs purely from its timestamp`() {
        val active = c(9, startSec = 1_000, durSec = -1)
        assertEquals(272, elapsedSec(active, nowMs = 1_272_000))
        assertEquals(0, elapsedSec(active, nowMs = 999_000)) // clock skew clamps, never negative
    }

    @Test
    fun `both surfaces get identical numbers because there is one function`() {
        val list = listOf(c(1, 100, 60), c(2, 400, 50))
        val calm = computeStats(list, 500_000)
        val labor = computeStats(list, 500_000)
        assertEquals(calm, labor)
    }

    // ── Formatting + summary ─────────────────────────────────────────────

    @Test
    fun `formatting is stable and locale-pinned`() {
        assertEquals("1:05", fmtClock(65))
        assertEquals("0:09", fmtClock(9))
        assertEquals("45s", fmtSpan(45))
        assertEquals("5m 04s", fmtSpan(304))
    }

    @Test
    fun `summary text carries counts, averages, and the care-team line`() {
        val s = computeStats(listOf(c(1, 100, 60), c(2, 400, 50)), 500_000)
        val text = buildSummaryText(s, sessionStartLabel = "8:00 PM", timeLabel = { "t" })
        assertTrue(text.contains("Contractions: 2"))
        assertTrue(text.contains("Average interval: 5m 00s"))
        assertTrue(text.contains("worth discussing with the care team"))
    }
}
