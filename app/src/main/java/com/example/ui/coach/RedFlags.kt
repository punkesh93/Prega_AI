package com.example.ui.coach

/**
 * Prega AI — local red-flag detection.
 *
 * A client-side safety net that runs *before* the question is sent anywhere.
 *
 * Why this exists even though the system prompt already covers it: the model
 * might be unreachable, the API key might be missing, the response might be
 * slow, or the model might simply soften its answer. None of those are
 * acceptable failure modes when someone has just typed "I'm bleeding". This
 * check needs no network, no key, and no inference — it fires instantly and
 * cannot be talked out of it.
 *
 * It deliberately over-triggers. A false positive costs her a banner she can
 * ignore; a false negative could cost far more. It never blocks her question —
 * the banner appears alongside the normal answer.
 */
object RedFlags

/** Symptom clusters that warrant contacting a maternity unit without delay. */
private val PATTERNS: List<Regex> = listOf(
    // Bleeding
    """\b(bleed|bleeding|blood|haemorrhag|hemorrhag|spotting heavy|heavy spotting)\b""",
    // Pain
    """\b(severe|sharp|intense|unbearable|constant)\s+(pain|cramp|cramping|ache)""",
    """\bpain\b.{0,20}\b(severe|sharp|unbearable|won'?t stop)""",
    // Pre-eclampsia signals
    """\b(blurred vision|blurry vision|seeing spots|vision changes|flashing lights)\b""",
    """\b(swollen|swelling|puffy)\b.{0,25}\b(face|hands|suddenly|sudden)\b""",
    // Same symptom, reversed word order: "my hands are suddenly swollen".
    """\b(face|hands|feet|ankles)\b.{0,25}\b(swollen|swelling|puffy)\b""",
    """\bsudden\b.{0,20}\b(swell|swelling|puffiness)\b""",
    """\b(bad|severe|persistent|terrible|worst)\s+headache\b""",
    // Fluid / membranes
    """\b(waters?\s+(broke|breaking)|leaking fluid|fluid leaking|gush of fluid)\b""",
    // Movement — the single most important one
    // "move" and "moves" included: people write "haven't felt the baby move",
    // not "reduced fetal movement".
    """\b(reduced|less|no|fewer|decreased|not feeling|haven'?t felt|hasn'?t felt)\b.{0,30}\b(movements?|kicks?|moving|moves?)\b""",
    """\b(baby|bean|she|he|they)\b.{0,20}\b(not|hasn'?t|haven'?t|stopped)\b.{0,20}\b(mov|kick)""",
    // Infection / systemic
    """\b(fever|temperature over|high temperature|chills and)\b""",
    // Other emergencies
    """\b(fainted|passed out|can'?t breathe|chest pain|seizure|convulsion)\b""",
    """\b(contractions?)\b.{0,30}\b(regular|every \d+ minutes?|close together)\b""",
).map { Regex(it, setOf(RegexOption.IGNORE_CASE)) }

/**
 * True when the text contains language suggesting an urgent obstetric symptom.
 *
 * Intentionally simple and greedy. This is a smoke alarm, not a diagnosis.
 */
fun containsRedFlag(text: String): Boolean {
    if (text.isBlank()) return false
    val normalised = text.lowercase().replace("’", "'")
    return PATTERNS.any { it.containsMatchIn(normalised) }
}

/**
 * The banner copy. Deliberately calm and specific — panic makes people freeze,
 * and vagueness makes them wait to see if it improves.
 */
const val RED_FLAG_MESSAGE: String =
    "Please call your midwife, maternity unit or emergency number now rather than " +
        "waiting to see if it settles. They would always rather you called and it " +
        "turned out to be nothing. I can't assess symptoms and you shouldn't wait " +
        "on my answer."
