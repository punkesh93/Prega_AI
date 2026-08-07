package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import java.time.LocalDate

/**
 * Prega AI — gentle step tracking.
 *
 * Reads the hardware step counter (Android's cumulative-since-boot sensor)
 * while the app is open, and turns it into today's count by remembering a
 * per-day baseline in SharedPreferences. No background service, no location,
 * no third-party SDK — the phone already counts; we only read.
 *
 * The daily goal is deliberately soft: 4,000 steps, in line with common
 * guidance that gentle regular walking is one of the best things in an
 * uncomplicated pregnancy — and reaching it awards Bloom Points ONCE per
 * day, so the garden grows from walks too. It is a celebration, not a
 * demand: no red numbers, no "you failed" states, and the tile simply
 * shows an invitation when permission hasn't been granted.
 *
 * Honest limitation, by design: steps count while the phone is carried and
 * may lag until the app is opened. Good enough for a gentle nudge; wearable
 * integration is the roadmap item for precision.
 */
private const val STEP_GOAL = 4000
private const val PREFS = "prega_steps"

@Composable
fun StepsTile(
    onGoalReached: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    var stepsToday by remember { mutableStateOf(readCachedSteps(context)) }

    if (granted) {
        DisposableEffect(Unit) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val raw = event.values.firstOrNull()?.toInt() ?: return
                    stepsToday = stepsFromRaw(context, raw)
                    maybeAward(context, stepsToday, onGoalReached)
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
            }
            if (sensor != null) {
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose { sm.unregisterListener(listener) }
        }
    }

    val progress by animateFloatAsState(
        targetValue = (stepsToday / STEP_GOAL.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "stepsProgress",
    )

    PregaCard(
        onClick = {
            if (!granted) permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        },
        containerColor = PregaTheme.colors.successSoft,
        border = false,
        contentPadding = PaddingValues(Space.md),
        modifier = modifier.heightIn(min = 112.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👟", fontSize = 22.sp)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "Steps",
                    style = MaterialTheme.typography.titleMedium,
                    color = PregaTheme.colors.ink,
                )
            }
            Spacer(Modifier.weight(1f))
            if (!granted) {
                Text(
                    "Tap to count today's walk",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkMuted,
                )
            } else {
                Text(
                    "$stepsToday",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PregaTheme.colors.ink,
                )
                Spacer(Modifier.height(Space.xs))
                PregaProgressBar(progress = progress, height = 5.dp)
                Spacer(Modifier.height(Space.xxs))
                Text(
                    if (stepsToday >= STEP_GOAL) "Gentle goal reached 🌸"
                    else "gentle goal $STEP_GOAL",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkMuted,
                )
            }
        }
    }
}

private fun hasPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED

/**
 * TYPE_STEP_COUNTER is cumulative since boot; today's count is raw minus a
 * baseline captured on the first reading each day (reset when raw < baseline,
 * i.e. the phone rebooted).
 */
private fun stepsFromRaw(context: Context, raw: Int): Int {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val today = LocalDate.now().toString()
    val savedDay = prefs.getString("day", null)
    var baseline = prefs.getInt("baseline", -1)
    if (savedDay != today || baseline < 0 || raw < baseline) {
        baseline = raw
        prefs.edit {
            putString("day", today)
            putInt("baseline", baseline)
        }
    }
    val steps = raw - baseline
    prefs.edit { putInt("cached", steps) }
    return steps
}

private fun readCachedSteps(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return if (prefs.getString("day", null) == LocalDate.now().toString())
        prefs.getInt("cached", 0) else 0
}

/** Awards the goal exactly once per day, tracked locally. */
private fun maybeAward(context: Context, steps: Int, onGoalReached: () -> Unit) {
    if (steps < STEP_GOAL) return
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val today = LocalDate.now().toString()
    if (prefs.getString("awarded", null) != today) {
        prefs.edit { putString("awarded", today) }
        onGoalReached()
    }
}
