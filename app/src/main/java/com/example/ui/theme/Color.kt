package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Prega AI — "Bloom" Design System v2
 *
 * Design intent: warm, premium, calming. Nothing clinical, nothing childish.
 * The palette is built on a warm neutral base (Linen/Shell) so that colour is
 * used sparingly and meaningfully — accent colour signals *progress* and
 * *reward*, never decoration for its own sake.
 *
 * Naming: <Role><Tone>. Always prefer semantic tokens (below) in UI code.
 */

// ─── Core brand ────────────────────────────────────────────────────────────
/** Primary. A soft, dusty rose — warm and human, never "hospital pink". */
// ─── Reference scheme: olive green leads ───────────────────────────────────
// The reference app's identity colour — the "ME" badge, play button, mood
// ring — is a deep olive green. It is now THIS app's primary. Rose survives
// only as a soft card pastel (blush blocks) and the heart accent, exactly
// the role pink plays in the reference.
val DeepOlive          = Color(0xFF66713A)  // primary — buttons, selection
val DeepOliveDark      = Color(0xFF474F26)  // pressed / onContainer
val OliveWhisper       = Color(0xFFE9EDDA)  // primaryContainer — pale sage band
val BloomRose          = Color(0xFFD97A84)
val BloomRoseDeep      = Color(0xFFB85A67)
val BloomRoseSoft      = Color(0xFFF2C9CC)
val BloomRoseWhisper   = Color(0xFFFCEEEE)

/** Secondary. Terracotta — grounding, earthy warmth for accents & streaks. */
val Terracotta         = Color(0xFFC97B5A)
val TerracottaSoft     = Color(0xFFF5D9CC)

/** Tertiary. Sage — calm, "safe", used for wellness + completed states. */
val Sage               = Color(0xFF7A9E7E)
val SageDeep           = Color(0xFF54785C)
val SageSoft           = Color(0xFFD6E5D8)

/** Accent. Soft gold — reserved exclusively for rewards, badges, premium. */
val Gold               = Color(0xFFD9A441)
val GoldSoft           = Color(0xFFF7E6C4)

/** Accent. Lavender — used for rest, sleep, and mindfulness surfaces. */
val Lavender           = Color(0xFF9B8FB5)
val LavenderSoft       = Color(0xFFE6E1EF)

// ─── Neutrals ──────────────────────────────────────────────────────────────
// Warm cream, not near-white: the base is closer to unbleached paper. This is
// what makes the reference designs read as "calm" — colour sits ON warmth
// rather than on clinical white. Ink shifts to a deep warm olive-brown to
// match, so text feels inked rather than printed.
val Linen              = Color(0xFFFAF8F1)  // app background — reference near-white cream
val Shell              = Color(0xFFECF0DF)  // recessed surface — pale sage band, per reference
val Card               = Color(0xFFFFFDF8)  // raised surface — cream-white
val Ink                = Color(0xFF3A342A)  // primary text — warm olive-black
val InkMuted           = Color(0xFF7C7263)  // secondary text
val InkFaint           = Color(0xFFA79D8C)  // tertiary text / placeholders
val Hairline           = Color(0xFFE7DFCF)  // borders, dividers

// ─── Dark theme neutrals ───────────────────────────────────────────────────
// Same warmth philosophy as light: not near-black but a deep warm umber, like
// a candlelit room rather than an OLED void. On-dark text is warm cream so
// dark mode feels like the same calm app after sunset, not a different app.
val InkDarkBg          = Color(0xFF211B14)
val InkDarkSurface     = Color(0xFF2A231B)
val InkDarkCard        = Color(0xFF342C22)
val InkDarkHairline    = Color(0xFF443A2E)
val OnDark             = Color(0xFFF3ECDF)
val OnDarkMuted        = Color(0xFFBCB09D)

// ─── Semantic / status ─────────────────────────────────────────────────────
val Success            = Color(0xFF5B9E6A)
val SuccessSoft        = Color(0xFFDCEEE0)
val Warning            = Color(0xFFD9963F)
val WarningSoft        = Color(0xFFFBEBD2)
/** Used only for genuine medical red-flags. Deliberately restrained — we do
 *  not want to alarm, we want to be clearly heard. */
val Alert              = Color(0xFFC1584F)
val AlertSoft          = Color(0xFFF9E0DD)

// ─── Trimester identity ────────────────────────────────────────────────────
// Each trimester gets its own tint so the app visibly "grows" with her.
val Trimester1         = Color(0xFFA8C4A2)  // fresh green — beginnings
val Trimester2         = Color(0xFFD9A441)  // golden — the "glow" trimester
val Trimester3         = Color(0xFFD97A84)  // rose — nearing arrival

fun trimesterColor(trimester: Int): Color = when (trimester) {
    1 -> Trimester1
    2 -> Trimester2
    else -> Trimester3
}

// ─── Gradients (as colour stop lists — consumed by Brush helpers) ──────────
val GradientDawn      = listOf(Color(0xFFF9F4EA), Color(0xFFF6E7E2))  // hero bg
val GradientBloom     = listOf(Color(0xFFE89AA2), Color(0xFFD97A84))  // primary CTA
val GradientGold      = listOf(Color(0xFFE8BC63), Color(0xFFD9A441))  // rewards
val GradientSage      = listOf(Color(0xFF9DBCA1), Color(0xFF7A9E7E))  // wellness
val GradientDusk      = listOf(Color(0xFFB5A6C9), Color(0xFF9B8FB5))  // sleep
