package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the failure mode reported on 13 Sep: the coach answered a question
 * about a 26-week scan with its own planning notes instead of an answer —
 * "The user is asking… I need to provide… I should ask what type of scan
 * they're scheduled for, or" — cut off mid-sentence. A reasoning model
 * (Nemotron 3 Ultra) whose chain of thought arrived inside the reply.
 *
 * The detector must catch that while never flagging real coach replies,
 * which are warm, first-person, and quite reasonably say things like "I
 * should mention" or "I want to be honest with you". A false positive
 * throws away a good answer, so every one of those is a test.
 */
class LeakedReasoningTest {

    @Test
    fun `the production bug is flagged`() {
        val bug = "The user is asking about what to expect at their next scan at 26 weeks " +
            "pregnant. I need to provide specific, week-appropriate information. At 26 weeks, " +
            "the next scan would typically be the anatomy scan (if not done yet) or a growth " +
            "scan. I should ask what type of scan they're scheduled for, or"
        assertTrue(bug.isLeakedReasoning())
    }

    @Test
    fun `third-person reference to her is enough on its own`() {
        assertTrue("The user wants reassurance about movement.".isLeakedReasoning())
        assertTrue("This person is asking a medical question.".isLeakedReasoning())
    }

    @Test
    fun `two planning phrases are enough`() {
        val t = "Okay, so she's at 26 weeks. Let me think about what matters here."
        assertTrue(t.isLeakedReasoning())
    }

    @Test
    fun `real coach answers are never flagged`() {
        val real = listOf(
            "At 26 weeks, most scans are growth scans — the sonographer measures your baby's " +
                "head, tummy and thigh bone and checks the fluid around them. Your midwife will " +
                "tell you which type yours is; it's worth asking at your next appointment.",
            "I want to be honest with you: I can't tell you whether that's normal, and I " +
                "shouldn't guess. Reduced movement is always worth a call to your maternity " +
                "unit, day or night. They would much rather hear from you.",
            "Week 26 is a big one. Your baby is about the length of a spring onion now, and " +
                "their eyes are beginning to open. You might be feeling stronger kicks in the " +
                "evening — many babies are liveliest once you finally sit down.",
            // Uses one planning phrase legitimately. One is not two.
            "I should mention that this is your midwife's call, not mine.",
        )
        real.forEach { assertFalse(it, it.isLeakedReasoning()) }
    }

    @Test
    fun `blank text is not a leak`() {
        assertFalse("".isLeakedReasoning())
        assertFalse("   ".isLeakedReasoning())
    }

    @Test
    fun `a tagged thinking block is removed`() {
        assertEquals("Hello there.", "<think>plan plan</think>Hello there.".withoutReasoning())
        assertEquals("Real answer.", "<Thinking>\nx\n</Thinking>\nReal answer.".withoutReasoning())
    }

    @Test
    fun `an unclosed thinking block leaves nothing`() {
        // The answer never arrived; empty is a retryable failure upstream,
        // which is the honest outcome.
        assertEquals("", "<think>plan that never ends".withoutReasoning())
    }

    @Test
    fun `ordinary replies pass through untouched`() {
        val t = "Your baby can hear you now — read to them if you like."
        assertEquals(t, t.withoutReasoning())
    }
}
