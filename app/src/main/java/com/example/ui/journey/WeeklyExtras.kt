package com.example.ui.journey

/**
 * Curated mother-side content for the Journey highlights: what she might be
 * feeling, and one gentle thing worth doing. WeekInfo covers the BABY; the
 * mockup-driven redesign showed the tab was missing HER.
 *
 * Written in bands (pregnancy changes by phase, not by exact week) to the
 * same standard as everything else: warm, factual, zero medical directives —
 * "many women notice", never "you have"; the tip is always something safe
 * and gentle (talk, walk, rest, note it for the midwife). No symptom here
 * is framed as a problem to fix, because the fix-it version of this list is
 * a doctor's job.
 */
object WeeklyExtras {

    data class Extras(val notice: String, val tip: String)

    fun forWeek(week: Int): Extras = when {
        week <= 8 -> Extras(
            "Tiredness and queasiness are very common right now — your body is doing enormous invisible work.",
            "Small frequent snacks and early nights count as achievements this month.",
        )
        week <= 13 -> Extras(
            "Many women find nausea starting to lift around now, and energy slowly returning.",
            "A short daily walk is one of the kindest habits to start this trimester.",
        )
        week <= 17 -> Extras(
            "The 'golden trimester' often begins — more energy, steadier appetite, and maybe the first flutters.",
            "Start noticing quiet moments; the first movements are easiest to feel lying still.",
        )
        week <= 22 -> Extras(
            "Movements become clearer week by week, and your bump is likely properly visible now.",
            "Talk, read or sing a little each day — hearing is developing and your voice is the favourite sound.",
        )
        week <= 27 -> Extras(
            "Stronger, more predictable kicks — and possibly some back ache as your centre of gravity shifts.",
            "Getting to know the daily pattern of movements now makes any change easy to spot later.",
        )
        week <= 31 -> Extras(
            "Braxton Hicks practice tightenings can appear, and sleep may take more arranging.",
            "A pillow between the knees when side-lying is a small change many women swear by.",
        )
        week <= 35 -> Extras(
            "Space is getting tight, so kicks turn into rolls and stretches — different, not less.",
            "Keep counting sessions going; pattern still matters more than strength.",
        )
        else -> Extras(
            "Pressure lower down as baby settles head-first, and bursts of nesting energy are common.",
            "Rest when the energy dips, and keep your bag and questions list ready — you're close.",
        )
    }
}
