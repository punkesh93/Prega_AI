package com.example.domain

import com.example.ui.coach.containsRedFlag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The highest-stakes logic in the app. A missed red flag is the worst thing
 * this product could do, so the detector is tested against realistic phrasing —
 * the way someone actually types at 3am, not clinical vocabulary.
 */
class RedFlagsTest {

    private fun flag(s: String) = containsRedFlag(s)

    @Test
    fun `bleeding is caught`() {
        assertTrue(flag("I'm bleeding a bit, is that normal"))
        assertTrue(flag("theres blood when I wipe"))
        assertTrue(flag("heavy spotting since this morning"))
    }

    @Test
    fun `reduced movement is caught in the way people actually phrase it`() {
        assertTrue(flag("I haven't felt the baby move today"))
        assertTrue(flag("reduced movement since yesterday"))
        assertTrue(flag("baby not moving as much"))
        assertTrue(flag("fewer kicks than usual"))
        assertTrue(flag("she hasn't moved much all day"))
    }

    @Test
    fun `pre-eclampsia signals are caught`() {
        assertTrue(flag("really bad headache that won't go away"))
        assertTrue(flag("my vision is blurry and I'm seeing spots"))
        assertTrue(flag("my face and hands are suddenly swollen"))
        assertTrue(flag("sudden swelling in my hands"))
    }

    @Test
    fun `severe pain is caught`() {
        assertTrue(flag("severe pain on one side"))
        assertTrue(flag("sharp cramping that won't stop"))
    }

    @Test
    fun `waters breaking is caught`() {
        assertTrue(flag("I think my waters broke"))
        assertTrue(flag("theres fluid leaking"))
    }

    @Test
    fun `other emergencies are caught`() {
        assertTrue(flag("I fainted this morning"))
        assertTrue(flag("I can't breathe properly"))
        assertTrue(flag("I have a fever"))
        assertTrue(flag("contractions every 5 minutes"))
    }

    @Test
    fun `curly apostrophes do not defeat detection`() {
        assertTrue(flag("I haven\u2019t felt the baby move"))
    }

    @Test
    fun `ordinary questions do not trigger the banner`() {
        assertFalse(flag("what can I eat for breakfast"))
        assertFalse(flag("is it normal to be tired in week 12"))
        assertFalse(flag("when will I start showing"))
        assertFalse(flag("can I drink coffee"))
        assertFalse(flag("what size is my baby this week"))
        assertFalse(flag("how do I sleep more comfortably"))
    }

    @Test
    fun `empty input is safe`() {
        assertFalse(flag(""))
        assertFalse(flag("   "))
    }

    /**
     * Documents the deliberate bias: this fires on any mention of movement
     * being reduced, including reassuring or hypothetical phrasing. A banner
     * she can ignore is a far cheaper error than a missed warning.
     */
    @Test
    fun `over-triggering is accepted on movement questions`() {
        assertTrue(flag("when should I worry about reduced movement?"))
    }
}
