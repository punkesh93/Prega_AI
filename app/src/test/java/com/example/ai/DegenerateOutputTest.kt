package com.example.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against the model repetition-loop failure mode seen in production:
 * a daily insight that collapsed into "practices iter practices iter..."
 * dozens of times on the Today card. The detector must catch loops like
 * that while never flagging legitimate prose — which naturally repeats
 * words ("you", "baby", "water") and even whole phrases for rhetorical
 * effect, just not the same phrase back-to-back five times.
 */
class DegenerateOutputTest {

    @Test
    fun `the production bug is flagged`() {
        val bug = "Test tube baby is likely starting to practice breathing. " +
            "You might notice small Base shifts in your belly as they inhale " +
            "and practices exhale tiny " + "practices iter ".repeat(40)
        assertTrue(bug.isDegenerate())
    }

    @Test
    fun `single word loop is flagged`() {
        assertTrue("baby ".repeat(20).isDegenerate())
    }

    @Test
    fun `two word loop is flagged`() {
        assertTrue(
            "kick counts kick counts kick counts kick counts kick counts okay"
                .isDegenerate()
        )
    }

    @Test
    fun `three word loop repeated five times is flagged`() {
        assertTrue(
            ("she kicks softly ".repeat(5) + "done").isDegenerate()
        )
    }

    @Test
    fun `normal insight prose is clean`() {
        assertFalse(
            ("Your baby is about the size of a mango this week. Their lungs " +
                "are practicing tiny breathing movements, and you might start " +
                "to feel gentle flutters as they stretch and turn.").isDegenerate()
        )
    }

    @Test
    fun `affirmation with natural repetition is clean`() {
        assertFalse(
            ("You are doing enough. Every day you show up for yourself and " +
                "your baby, and that quiet steadiness matters more than you " +
                "know.").isDegenerate()
        )
    }

    @Test
    fun `rhetorical anaphora is clean`() {
        assertFalse(
            ("You are strong. You are ready. You are growing a whole person. " +
                "You are allowed to rest.").isDegenerate()
        )
    }

    @Test
    fun `repetitive but legitimate instructions are clean`() {
        assertFalse(
            ("Drink water in the morning. Drink water at lunch. Drink water " +
                "in the afternoon. Drink water before bed. Small sips count " +
                "too.").isDegenerate()
        )
    }

    @Test
    fun `numbered quest list is clean`() {
        assertFalse(
            ("1. Drink 8 glasses of water today\n" +
                "2. Take a 10-minute walk after dinner\n" +
                "3. Write one line in your journal before bed").isDegenerate()
        )
    }

    @Test
    fun `short fallback strings are never flagged`() {
        assertFalse("Week 24, held onto.".isDegenerate())
        assertFalse("".isDegenerate())
        assertFalse("Rest today.".isDegenerate())
    }

    @Test
    fun `four consecutive repeats stay under the threshold`() {
        assertFalse(
            ("more water more water more water more water and a walk today " +
                "keeps things moving nicely").isDegenerate()
        )
    }
}
