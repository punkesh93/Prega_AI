package com.example.ui.theme

/**
 * TEMPORARY — v1 → v2 colour bridge.
 *
 * PregnancyApp.kt is a single 4,800-line file that hard-codes the old palette
 * throughout. Rather than blast every call site in one unreviewable commit,
 * the old names are aliased onto their nearest Bloom equivalent so the app
 * keeps building and running while screens are rebuilt one at a time.
 *
 * The visual difference is immediate — the harsh #E64A19 orange is gone and
 * everything shifts to the softer rose palette — but the structural rewrite
 * lands screen by screen.
 *
 * DELETE THIS FILE once no references remain:
 *     grep -rn "PrimaryCoral\|TextDeepBrown\|BackgroundCream" app/src/
 */

@Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("BloomRose"))
val PrimaryCoral = BloomRose

@Deprecated("Use MaterialTheme.colorScheme.primaryContainer", ReplaceWith("BloomRoseSoft"))
val PrimaryCoralLight = BloomRoseSoft

@Deprecated("Use PregaTheme.colors.terracotta", ReplaceWith("BloomRoseDeep"))
val WarmRose = BloomRoseDeep

@Deprecated("Use PregaTheme.colors.sage", ReplaceWith("Sage"))
val SageGreen = Sage

@Deprecated("Use PregaTheme.colors.sageSoft", ReplaceWith("SageSoft"))
val SageGreenLight = SageSoft

@Deprecated("Use MaterialTheme.colorScheme.background", ReplaceWith("Linen"))
val BackgroundCream = Linen

@Deprecated("Use MaterialTheme.colorScheme.surfaceVariant", ReplaceWith("Shell"))
val SurfacePeachLight = Shell

@Deprecated("Use MaterialTheme.colorScheme.onSurface", ReplaceWith("Ink"))
val TextDeepBrown = Ink

@Deprecated("Use PregaTheme.colors.inkMuted", ReplaceWith("InkMuted"))
val TextMutedBrown = InkMuted
