package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─── Shape scale ───────────────────────────────────────────────────────────
// Generous, soft radii throughout. Nothing in this app has a sharp corner.
val PregaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

// ─── Spacing scale ─────────────────────────────────────────────────────────
/** 4pt grid. Use these instead of raw dp so density stays consistent. */
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** Standard horizontal page gutter. */
    val gutter: Dp = 20.dp
    /** Bottom padding on scrollables so content clears the nav bar. */
    val navClearance: Dp = 108.dp
    /** Minimum touch target — never go below this on interactive elements. */
    val touchTarget: Dp = 48.dp
}

// ─── Elevation ─────────────────────────────────────────────────────────────
/** Deliberately shallow. Depth comes from colour and spacing, not shadow. */
object Elevate {
    val flat: Dp = 0.dp
    val card: Dp = 1.dp
    val raised: Dp = 3.dp
    val floating: Dp = 8.dp
    val dialog: Dp = 16.dp
}

// ─── Extended theme colours ────────────────────────────────────────────────
/**
 * Material's ColorScheme can't express everything the brand needs (gradients,
 * reward gold, trimester tints). Those live here and are reached via
 * `PregaTheme.colors`.
 */
data class PregaColors(
    val gold: Color,
    val goldSoft: Color,
    val sage: Color,
    val sageSoft: Color,
    val lavender: Color,
    val lavenderSoft: Color,
    val terracotta: Color,
    val terracottaSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val alert: Color,
    val alertSoft: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val hairline: Color,
    val cardSurface: Color,
    val recessed: Color,
    val gradientDawn: List<Color>,
    val gradientBloom: List<Color>,
    val gradientGold: List<Color>,
    val gradientSage: List<Color>,
    val gradientDusk: List<Color>,
) {
    val bloomBrush: Brush get() = Brush.horizontalGradient(gradientBloom)
    val goldBrush: Brush get() = Brush.horizontalGradient(gradientGold)
    val dawnBrush: Brush get() = Brush.verticalGradient(gradientDawn)
    val sageBrush: Brush get() = Brush.horizontalGradient(gradientSage)
    val duskBrush: Brush get() = Brush.horizontalGradient(gradientDusk)
}

private val LightPregaColors = PregaColors(
    gold = Gold, goldSoft = GoldSoft,
    sage = Sage, sageSoft = SageSoft,
    lavender = Lavender, lavenderSoft = LavenderSoft,
    terracotta = Terracotta, terracottaSoft = TerracottaSoft,
    success = Success, successSoft = SuccessSoft,
    warning = Warning, warningSoft = WarningSoft,
    alert = Alert, alertSoft = AlertSoft,
    ink = Ink, inkMuted = InkMuted, inkFaint = InkFaint,
    hairline = Hairline, cardSurface = Card, recessed = Shell,
    gradientDawn = GradientDawn, gradientBloom = GradientBloom,
    gradientGold = GradientGold, gradientSage = GradientSage,
    gradientDusk = GradientDusk,
)

private val DarkPregaColors = LightPregaColors.copy(
    goldSoft = Color(0xFF4A3B21),
    sageSoft = Color(0xFF2A3A2D),
    lavenderSoft = Color(0xFF332E3D),
    terracottaSoft = Color(0xFF43302A),
    successSoft = Color(0xFF233A29),
    warningSoft = Color(0xFF43351F),
    alertSoft = Color(0xFF432624),
    ink = OnDark, inkMuted = OnDarkMuted, inkFaint = Color(0xFF7D6E71),
    hairline = InkDarkHairline, cardSurface = InkDarkCard, recessed = InkDarkSurface,
    gradientDawn = listOf(Color(0xFF241C1F), Color(0xFF1A1416)),
)

private val LocalPregaColors = staticCompositionLocalOf { LightPregaColors }

/** Accessor for the extended palette: `PregaTheme.colors.gold`. */
object PregaTheme {
    val colors: PregaColors
        @Composable @ReadOnlyComposable get() = LocalPregaColors.current
}

// ─── Material colour schemes ───────────────────────────────────────────────
private val LightScheme = lightColorScheme(
    primary = BloomRose,
    onPrimary = Color.White,
    primaryContainer = BloomRoseWhisper,
    onPrimaryContainer = BloomRoseDeep,
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = SageSoft,
    onSecondaryContainer = SageDeep,
    tertiary = Terracotta,
    onTertiary = Color.White,
    tertiaryContainer = TerracottaSoft,
    onTertiaryContainer = Color(0xFF7A4128),
    background = Linen,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Shell,
    onSurfaceVariant = InkMuted,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Alert,
    onError = Color.White,
    errorContainer = AlertSoft,
    onErrorContainer = Color(0xFF7A2E27),
)

private val DarkScheme = darkColorScheme(
    primary = BloomRoseSoft,
    onPrimary = Color(0xFF4A1F25),
    primaryContainer = Color(0xFF5C2B32),
    onPrimaryContainer = BloomRoseSoft,
    secondary = Color(0xFFA8C4AC),
    onSecondary = Color(0xFF1F3324),
    secondaryContainer = Color(0xFF314736),
    onSecondaryContainer = SageSoft,
    tertiary = Color(0xFFE0A78D),
    onTertiary = Color(0xFF4A2417),
    background = InkDarkBg,
    onBackground = OnDark,
    surface = InkDarkCard,
    onSurface = OnDark,
    surfaceVariant = InkDarkSurface,
    onSurfaceVariant = OnDarkMuted,
    outline = InkDarkHairline,
    outlineVariant = InkDarkHairline,
    error = Color(0xFFE59A93),
    onError = Color(0xFF5C1F1A),
)

/**
 * Root theme.
 *
 * Note: dynamic colour is intentionally **off**. Prega's palette is a core part
 * of the product's identity and its calming intent; letting the system wallpaper
 * recolour a pregnancy app produces jarring, occasionally alarming results.
 */
@Composable
fun PregaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extended = if (darkTheme) DarkPregaColors else LightPregaColors

    CompositionLocalProvider(LocalPregaColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = PregaShapes,
            content = content,
        )
    }
}

/** Kept so existing call sites keep compiling during the v2 migration. */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) = PregaTheme(darkTheme = darkTheme, content = content)
