package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryCoralLight,
    secondary = SageGreenLight,
    tertiary = Pink80,
    background = Color(0xFF261D1A),
    surface = Color(0xFF3E2C27),
    onPrimary = TextDeepBrown,
    onSecondary = TextDeepBrown,
    onBackground = BackgroundCream,
    onSurface = BackgroundCream
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryCoral,
    secondary = SageGreen,
    tertiary = WarmRose,
    background = BackgroundCream,
    surface = SurfacePeachLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextDeepBrown,
    onSurface = TextDeepBrown
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
