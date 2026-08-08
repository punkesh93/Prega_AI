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

    /**
     * One likely symptom: what it is, what gently helps, and why it happens.
     * Three per phase, never more — enough to feel understood and equipped,
     * not enough to feel diagnosed. Wording rules: "many women feel", the
     * "do" is always low-stakes and safe, the "why" is one plain sentence.
     * Anything that could be serious carries its escalation inline, and the
     * card that renders these always closes with the midwife line.
     */
    data class Symptom(val name: String, val doThis: String, val why: String)

    fun symptomsForWeek(week: Int): List<Symptom> = when {
        week <= 8 -> listOf(
            Symptom(
                "Queasiness", "Keep small bland snacks close — before getting up, and between meals.",
                "An empty stomach makes it worse; steady blood sugar softens it.",
            ),
            Symptom(
                "Deep tiredness", "Sleep when you can and lower the bar everywhere else.",
                "Progesterone has surged and your body is building the placenta — real work.",
            ),
            Symptom(
                "Tender breasts", "A soft, supportive bra — day and, if it helps, night.",
                "Hormones are preparing tissue months ahead of time.",
            ),
        )
        week <= 13 -> listOf(
            Symptom(
                "Strong smells, odd aversions", "Eat what stays down; nutrition perfection can wait a few weeks.",
                "Your sense of smell is genuinely heightened right now.",
            ),
            Symptom(
                "Constipation", "More water, more fibre, gentle walks.",
                "Progesterone slows digestion so more nutrients are absorbed — with this side effect.",
            ),
            Symptom(
                "Mild stretching twinges", "Change positions slowly; warmth helps. Sharp, one-sided or persistent pain is a midwife call, today.",
                "The uterus and its ligaments are growing fast.",
            ),
        )
        week <= 17 -> listOf(
            Symptom(
                "Round ligament twinges", "Stand and turn without rushing; avoid sudden twists.",
                "The ligaments holding your uterus stretch, and quick moves tug them.",
            ),
            Symptom(
                "A blocked nose", "Saline spray and a humid room are safe and often enough.",
                "Your blood volume is up by almost half, and nasal vessels swell with it.",
            ),
            Symptom(
                "Returning energy", "Use it gently — walks, prep, the things that were too much last month.",
                "Early-pregnancy hormone turbulence is settling.",
            ),
        )
        week <= 22 -> listOf(
            Symptom(
                "Lower back ache", "Check chair support, sleep with a pillow between the knees.",
                "Your centre of gravity is shifting forward and posture quietly compensates.",
            ),
            Symptom(
                "Night leg cramps", "Stretch calves before bed; flex the foot upward when one strikes.",
                "Circulation changes and mineral demands make calves twitchy at night.",
            ),
            Symptom(
                "Skin changes — a dark belly line, darker patches", "Nothing needed; sunscreen keeps patches from deepening.",
                "Pregnancy hormones boost pigment. It fades after.",
            ),
        )
        week <= 27 -> listOf(
            Symptom(
                "Puffy feet and ankles", "Feet up when you sit, and keep drinking water. Sudden swelling of face or hands is a midwife call, same day.",
                "You're carrying much more fluid, and gravity pools it low.",
            ),
            Symptom(
                "Heartburn", "Smaller meals, and don't lie down straight after eating.",
                "The valve above your stomach is relaxed by hormones while space shrinks.",
            ),
            Symptom(
                "Vivid, strange dreams", "Nothing to fix — tell someone, they're usually good stories.",
                "Hormones plus lighter, more broken sleep means more remembered dreams.",
            ),
        )
        week <= 31 -> listOf(
            Symptom(
                "Practice tightenings (Braxton Hicks)", "Change position, drink water, breathe through it. If they become regular, rhythmic or painful — call your midwife.",
                "The uterus rehearses. Irregular and painless is the signature of practice.",
            ),
            Symptom(
                "Breathlessness on stairs", "Slow down and pause; you're not unfit, you're compressed.",
                "The uterus now sits high enough to crowd your diaphragm.",
            ),
            Symptom(
                "Broken sleep", "Side-lying with pillow support, and naps without guilt.",
                "Comfort, bathroom trips and a busy mind all peak together in this stretch.",
            ),
        )
        week <= 35 -> listOf(
            Symptom(
                "Pelvic pressure", "Rest breaks off your feet; a support band helps some women.",
                "Baby is heavier and sitting lower every week.",
            ),
            Symptom(
                "Bathroom trips, constantly", "Keep drinking anyway — cutting water back doesn't help and costs you.",
                "Your bladder is sharing its space and losing the negotiation.",
            ),
            Symptom(
                "Tiredness returning", "Treat rest as part of the job now, not a break from it.",
                "You're carrying more weight and sleeping less deeply — of course.",
            ),
        )
        else -> listOf(
            Symptom(
                "Low, sudden zings of pressure", "Shift position; they pass. Regular, rhythmic tightening that builds is the call to make.",
                "Baby is settling head-down against nerves — a sign of engagement, not a problem.",
            ),
            Symptom(
                "Bursts of nesting energy", "Enjoy it — but pace it, and let others carry the heavy things.",
                "A well-documented late surge; it doesn't suspend the rules of lifting.",
            ),
            Symptom(
                "Feeling 'done'", "You're allowed. Say it out loud to someone who loves you.",
                "The last weeks are the heaviest in every sense. This is the normal shape of the end.",
            ),
        )
    }

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
