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

// ─── Neutrals (warm-tinted, never pure grey) ───────────────────────────────
val Linen              = Color(0xFFFDFAF7)  // app background
val Shell              = Color(0xFFF7F1EC)  // recessed surface
val Card               = Color(0xFFFFFFFF)  // raised surface
val Ink                = Color(0xFF33272A)  // primary text — warm near-black
val InkMuted           = Color(0xFF7A6A6E)  // secondary text
val InkFaint           = Color(0xFFA89A9D)  // tertiary text / placeholders
val Hairline           = Color(0xFFEDE4DE)  // borders, dividers

// ─── Dark theme neutrals ───────────────────────────────────────────────────
val InkDarkBg          = Color(0xFF1A1416)
val InkDarkSurface     = Color(0xFF241C1F)
val InkDarkCard        = Color(0xFF2E2427)
val InkDarkHairline    = Color(0xFF3D3134)
val OnDark             = Color(0xFFF5EDEA)
val OnDarkMuted        = Color(0xFFB5A5A8)

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
val GradientDawn      = listOf(Color(0xFFFDF0EC), Color(0xFFF9E4E6))  // hero bg
val GradientBloom     = listOf(Color(0xFFE89AA2), Color(0xFFD97A84))  // primary CTA
val GradientGold      = listOf(Color(0xFFE8BC63), Color(0xFFD9A441))  // rewards
val GradientSage      = listOf(Color(0xFF9DBCA1), Color(0xFF7A9E7E))  // wellness
val GradientDusk      = listOf(Color(0xFFB5A6C9), Color(0xFF9B8FB5))  // sleep
