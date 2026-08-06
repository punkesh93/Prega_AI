package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset

/**
 * Prega AI — Motion language.
 *
 * Principle: motion should feel like *breathing*, not like software. Nothing
 * snaps, nothing bounces aggressively. Durations sit slightly longer than
 * Material defaults because the app's job is to slow the user down, not speed
 * her up. Reward moments are the single exception — they are allowed to be
 * playful, because that is the payoff.
 */
object Motion {

    // ─── Easing ───────────────────────────────────────────────────────────
    /** Default. Gentle acceleration, long settle. Feels like an exhale. */
    val EaseBreath: Easing = CubicBezierEasing(0.32f, 0.0f, 0.16f, 1.0f)
    /** For elements entering the screen. */
    val EaseEnter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    /** For elements leaving. Quicker — don't make her wait to dismiss. */
    val EaseExit: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // ─── Durations (ms) ───────────────────────────────────────────────────
    const val Instant = 100
    const val Quick = 180
    const val Standard = 300
    const val Gentle = 450
    const val Slow = 700
    /** Ambient loops: the belly-glow pulse, floating petals, breathing rings. */
    const val Ambient = 4000

    // ─── Springs ──────────────────────────────────────────────────────────
    /** Default spring for size/position. Soft, no visible overshoot. */
    fun <T> soft(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Reward spring. Allowed a little bounce — this is the celebration. */
    fun <T> playful(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium,
    )

    /** For the kick-counter tap: immediate, tactile, snappy. */
    fun <T> tactile(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessHigh,
    )

    // ─── Standard transitions ─────────────────────────────────────────────
    /** Cards and sections easing up into place on screen entry. */
    fun riseIn(delayMillis: Int = 0): EnterTransition =
        fadeIn(tween(Gentle, delayMillis, EaseEnter)) +
            slideInVertically(tween(Gentle, delayMillis, EaseEnter)) { it / 8 }

    fun riseOut(): ExitTransition =
        fadeOut(tween(Quick, easing = EaseExit)) +
            slideOutVertically(tween(Quick, easing = EaseExit)) { it / 8 }

    /** Dialogs, badges, and reward reveals. */
    val popIn: EnterTransition =
        fadeIn(tween(Standard, easing = EaseEnter)) +
            scaleIn(initialScale = 0.88f, animationSpec = tween(Standard, easing = EaseEnter))

    val popOut: ExitTransition =
        fadeOut(tween(Quick, easing = EaseExit)) +
            scaleOut(targetScale = 0.92f, animationSpec = tween(Quick, easing = EaseExit))

    /** Simple crossfade for tab/content swaps. */
    fun <T> crossfade(): FiniteAnimationSpec<T> = tween(Standard, easing = EaseBreath)

    /**
     * Staggered entry delay. Use for lists of cards so the screen assembles
     * itself gracefully rather than appearing all at once.
     * Capped so long lists never feel slow.
     */
    fun stagger(index: Int, step: Int = 60, max: Int = 400): Int =
        (index * step).coerceAtMost(max)
}
