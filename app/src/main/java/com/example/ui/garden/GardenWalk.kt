package com.example.ui.garden

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.Space
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Prega AI — a walk in her garden.
 *
 * First-person stroll through the blooms she has grown. This is the app's
 * "3D": a real perspective projection of a flower meadow onto the screen —
 * world coordinates in metres, an eye height, two focal lengths, fog with
 * distance — rendered with the SAME drawFlower / drawButterfly /
 * drawVisitor the flat garden uses, so it is unmistakably the same garden,
 * now walked through instead of looked at. No engine, no new dependency,
 * nothing that can fail to load: geometry and the drawing code we already
 * trust.
 *
 * v2 — a place, not a corridor. The first walk projected a straight path
 * with a fixed forward gaze; you could only go on. Now the camera has a
 * position, a heading and a pitch, the meadow is an endless deterministic
 * field on every side, and the path winds through it. Three ways to be in
 * it, all of them one-thumb and pressure-free:
 *
 *  - Drag UP to stroll forward (down to step back), like scrolling; let
 *    go and momentum carries a few more steps then settles. Footfalls
 *    follow the metres actually walked, so a slow drag is a slow walk.
 *  - Drag SIDEWAYS to turn; a full width sweep is half a turn.
 *  - Turn the PHONE to look around — gyroscope yaw and pitch, so glancing
 *    left is a glance left and tilting up shows more sky. Tiny drift is
 *    accepted for the honesty of it; the toggle turns it off and the
 *    choice is remembered.
 *
 * Life along the path: her butterflies circle wherever she stands, and
 * every visitor she has earned waits at its own station down the path —
 * walking far enough means MEETING the ladybird, the bee, the snail; turn
 * round and the path is still there behind you. The field density scales
 * with her bloom count: a young garden is an open meadow, a tended one
 * closes into colour. Hum + breeze + birds loop; every second footfall
 * lands a soft grass press in the ear that matches the eye.
 */
private const val EYE_HEIGHT = 1.55f       // metres
private const val STEP_HZ = 1.7f           // unhurried steps per second
private const val WALK_SPEED = 1.05f       // m/s — a stroll, not a hike
private const val MAX_SPEED = 2.2f         // m/s — even a hurried drag stays a walk
private const val BOB_AMPLITUDE = 0.045f   // metres of vertical bob
private const val PATH_HALF = 0.85f        // metres of clear path each side
private const val FAR_CLIP = 22f           // metres of visible world
private const val NEAR_CLIP = 0.55f        // metres — closer than this is behind the eye
private const val CELL = 2f                // metres per meadow cell
private const val MAX_PITCH = 0.42f        // radians (~24°) of looking up/down
private const val STEPS_PER_METRE = STEP_HZ / WALK_SPEED

private const val PREFS = "prega_garden"
private const val KEY_SOUND = "sound_on"
private const val KEY_MOTION = "walk_motion_look"

/** Where the path runs: a slow meander so turning reveals it curving away. */
private fun pathX(z: Float): Float = 2.6f * sin(z / 9f) + 1.1f * sin(z / 3.7f + 1.3f)

@Composable
fun GardenWalk(
    flowers: Int,
    butterflies: Int,
    goldBlooms: Int,
    visitors: List<Visitor>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)

    // The walk is cream in BOTH themes, so in dark mode the light status
    // icons would vanish against the sky — the same trap Labor Mode hit in
    // reverse. Force dark icons for the walk; restore on leave.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val window = generateSequence(view.context) {
            (it as? android.content.ContextWrapper)?.baseContext
        }.filterIsInstance<android.app.Activity>().firstOrNull()?.window
        val controller = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view)
        }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }

    // ── Simulation state (frame-driven, not recomposition-driven) ──
    var camZ by remember { mutableFloatStateOf(0f) }
    var camX by remember { mutableFloatStateOf(pathX(0f)) }
    var heading by remember { mutableFloatStateOf(0f) }   // radians, 0 = +z
    var pitch by remember { mutableFloatStateOf(0f) }     // radians, + = looking up
    var speed by remember { mutableFloatStateOf(0f) }     // m/s along heading, signed
    var pendingMove by remember { mutableFloatStateOf(0f) }  // metres queued by drag
    var pendingTurn by remember { mutableFloatStateOf(0f) }  // radians queued by drag/gyro
    var pendingPitch by remember { mutableFloatStateOf(0f) }
    var lastDragAt by remember { mutableFloatStateOf(-1f) }  // clock time of last drag move
    var bobPhase by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableFloatStateOf(0f) }
    var pressing by remember { mutableStateOf(false) }
    var walked by remember { mutableFloatStateOf(0f) }
    var lastFrame by remember { mutableLongStateOf(0L) }
    var viewW by remember { mutableFloatStateOf(1f) }
    var viewH by remember { mutableFloatStateOf(1f) }

    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val soundOn = remember { prefs.getBoolean(KEY_SOUND, true) }
    var motionLook by remember { mutableStateOf(prefs.getBoolean(KEY_MOTION, true)) }

    // ── Look around by moving the phone: gyroscope y → heading, x → pitch.
    // Held upright in portrait the phone's y axis is world-up, so turning
    // the body is rotation about y; tilting the top toward her is rotation
    // about x. Integrated per event with a small dead-zone against drift.
    // Absent sensor → silently nothing; drag still does everything.
    DisposableEffect(motionLook) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyro = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        var lastTs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (lastTs != 0L) {
                    val dt = ((event.timestamp - lastTs) / 1e9f).coerceIn(0f, 0.1f)
                    val gx = event.values[0]
                    val gy = event.values[1]
                    if (kotlin.math.abs(gy) > 0.02f) pendingTurn -= gy * dt
                    if (kotlin.math.abs(gx) > 0.02f) pendingPitch -= gx * dt
                }
                lastTs = event.timestamp
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (motionLook && manager != null && gyro != null) {
            runCatching { manager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME) }
        }
        onDispose { runCatching { manager?.unregisterListener(listener) } }
    }

    // ── Sound: hum + breeze + birds as loops, footsteps as one-shots ──
    val stepPool = remember {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val stepIds = remember {
        if (soundOn) {
            runCatching {
                listOf(
                    stepPool.load(context, R.raw.step_soft_1, 1),
                    stepPool.load(context, R.raw.step_soft_2, 1),
                    stepPool.load(context, R.raw.step_soft_3, 1),
                )
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    var leftFoot by remember { mutableStateOf(false) }
    val stepRandom = remember { Random(System.nanoTime()) }
    fun footfall() {
        if (stepIds.isEmpty()) return
        val id = stepIds[stepRandom.nextInt(stepIds.size)]
        val rate = 0.94f + stepRandom.nextFloat() * 0.12f
        val base = 0.42f + stepRandom.nextFloat() * 0.12f
        // Feet alternate ears, just slightly — the body knows.
        val l = if (leftFoot) base else base * 0.72f
        val r = if (leftFoot) base * 0.72f else base
        leftFoot = !leftFoot
        runCatching { stepPool.play(id, l, r, 1, 0, rate) }
    }

    if (soundOn) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val players = mutableListOf<MediaPlayer>()
            fun make(res: Int, vol: Float) {
                try {
                    MediaPlayer.create(context, res)?.let {
                        it.isLooping = true
                        it.setVolume(vol, vol)
                        it.start()
                        players += it
                    }
                } catch (_: Exception) {
                    // Sound is a garnish, never a crash.
                }
            }
            make(R.raw.garden_hum, 0.50f)
            make(R.raw.garden_wind, 0.28f)
            make(R.raw.garden_birds, 0.32f)

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE ->
                        players.forEach { runCatching { it.pause() } }
                    Lifecycle.Event.ON_RESUME ->
                        players.forEach { runCatching { it.start() } }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                players.forEach { p ->
                    try { p.stop(); p.release() } catch (_: Exception) { }
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { stepPool.release() } } }

    // ── Frame clock ──
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val dt = ((now - lastFrame) / 1e9f).coerceAtMost(0.05f)
                    clock += dt

                    // Turning: drag and gyroscope both queue radians.
                    heading += pendingTurn
                    pendingTurn = 0f
                    pitch = (pitch + pendingPitch).coerceIn(-MAX_PITCH, MAX_PITCH)
                    pendingPitch = 0f
                    // Without the gyro nothing pitches the view on purpose;
                    // any residual drifts back to level.
                    if (!motionLook) pitch *= kotlin.math.exp(-2f * dt)

                    // Moving: while she drags, the queued metres ARE the
                    // motion and also teach the fling velocity; after release
                    // that velocity carries her a few steps and settles.
                    val step: Float
                    if (pendingMove != 0f) {
                        val cap = MAX_SPEED * dt * 3f
                        step = pendingMove.coerceIn(-cap, cap)
                        pendingMove = 0f
                        speed = (0.6f * speed + 0.4f * (step / dt)).coerceIn(-MAX_SPEED, MAX_SPEED)
                    } else if (pressing && clock - lastDragAt < 0.12f) {
                        // Finger paused mid-drag: hold position, keep the
                        // learned speed for a moment in case she continues.
                        step = 0f
                    } else {
                        val decay = if (pressing) 8f else 2.2f
                        speed *= kotlin.math.exp(-decay * dt)
                        if (kotlin.math.abs(speed) < 0.03f) speed = 0f
                        step = speed * dt
                    }
                    if (step != 0f) {
                        camX += sin(heading) * step
                        camZ += cos(heading) * step
                        walked += kotlin.math.abs(step)
                        val before = bobPhase
                        bobPhase += (2f * PI.toFloat()) * STEPS_PER_METRE * kotlin.math.abs(step)
                        // A footfall is each half-cycle of the bob — the
                        // moment the body settles onto the next foot.
                        if (floor(before / PI.toFloat()) != floor(bobPhase / PI.toFloat())) {
                            footfall()
                        }
                    } else {
                        // Ease the bob back to standing so stopping never
                        // freezes the camera mid-lurch.
                        val rest = kotlin.math.round(bobPhase / PI.toFloat()) * PI.toFloat()
                        bobPhase += (rest - bobPhase) * (1f - kotlin.math.exp(-6f * dt))
                    }
                }
                lastFrame = now
            }
        }
    }

    // ── The world, seeded once — endless rows generated on demand ──
    val density = 0.30f + 0.65f * (flowers.coerceIn(0, 18) / 18f)
    val goldRatio = if (flowers > 0) goldBlooms.toFloat() / flowers else 0f

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(30f)
            .background(Color(0xFFFDFBF4))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var lastX = 0f
                    var lastY = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        val down = event.changes.any { it.pressed }
                        if (change != null) {
                            if (down && !pressing) {
                                pressing = true
                                lastX = change.position.x
                                lastY = change.position.y
                            } else if (down) {
                                val dx = change.position.x - lastX
                                val dy = change.position.y - lastY
                                // Sideways: a full width sweep is half a turn.
                                pendingTurn += dx * (PI.toFloat() / viewW.coerceAtLeast(1f))
                                // Up: a full height drag is ~5 m of path.
                                pendingMove += -dy * (5f / viewH.coerceAtLeast(1f))
                                if (dy != 0f) lastDragAt = clock
                                lastX = change.position.x
                                lastY = change.position.y
                            }
                            if (!down) pressing = false
                            // Consume everything: the tab pager underneath
                            // must never page while she is mid-garden.
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            viewW = size.width
            viewH = size.height
            val moving = kotlin.math.abs(speed) > 0.05f
            drawWalkFrame(
                clock = clock,
                camX = camX,
                camZ = camZ,
                heading = heading,
                pitch = pitch,
                bob = if (moving) sin(bobPhase) * BOB_AMPLITUDE else 0f,
                roll = if (moving) sin(bobPhase * 0.5f) * 0.45f else 0f,
                density = density,
                goldRatio = goldRatio,
                butterflies = butterflies,
                visitors = visitors,
            )
        }

        // Motion-look toggle — the only other chrome. Off is remembered.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(Space.gutter)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xCCFAF8F1))
                .clickable {
                    motionLook = !motionLook
                    prefs.edit { putBoolean(KEY_MOTION, motionLook) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (motionLook) Icons.Rounded.ScreenRotation else Icons.Rounded.ScreenLockRotation,
                contentDescription = if (motionLook) "Stop looking around by moving the phone"
                else "Look around by moving the phone",
                tint = Color(0xFF3A342A),
            )
        }

        // Back — the only chrome that competes with the sky.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(Space.gutter)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xCCFAF8F1))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Leave the garden walk",
                tint = Color(0xFF3A342A),
            )
        }

        // Hint until she has taken her first few steps, then out of the way.
        if (walked < 2f) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.xxl)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB3FAF8F1))
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (motionLook) "Drag up to stroll \u00B7 sideways to turn \u00B7 move your phone to look"
                    else "Drag up to stroll \u00B7 sideways to turn",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF5A503C),
                )
            }
        }
    }
}

// ─── The projected world ───────────────────────────────────────────────────

private data class WalkFlower(
    val x: Float,       // world metres
    val z: Float,       // world metres
    val h: Float,       // bloom height in metres
    val color: Int,     // palette index, 9 = gold
    val phase: Float,
)

/**
 * Deterministic flowers for meadow cell [cx],[cz] — same field on every
 * walk, in every direction, with the path kept clear. Up to four blooms
 * per 2 m cell at full density.
 */
private fun cellFlowers(cx: Int, cz: Int, density: Float, goldRatio: Float): List<WalkFlower> {
    val rnd = Random((cx * 73856093) xor (cz * 19349663) xor 42)
    val out = ArrayList<WalkFlower>(4)
    repeat(4) {
        if (rnd.nextFloat() < density) {
            val x = cx * CELL + rnd.nextFloat() * CELL
            val z = cz * CELL + rnd.nextFloat() * CELL
            if (kotlin.math.abs(x - pathX(z)) < PATH_HALF + 0.22f) return@repeat
            out += WalkFlower(
                x = x,
                z = z,
                h = 0.42f + rnd.nextFloat() * 0.34f,
                color = if (rnd.nextFloat() < goldRatio) 9 else rnd.nextInt(4),
                phase = rnd.nextFloat() * 6.28f,
            )
        }
    }
    return out
}

private fun mixToward(c: Color, toward: Color, amount: Float): Color = Color(
    red = c.red + (toward.red - c.red) * amount,
    green = c.green + (toward.green - c.green) * amount,
    blue = c.blue + (toward.blue - c.blue) * amount,
    alpha = c.alpha,
)

private class Projected(val sx: Float, val sy: Float, val depth: Float)

/** Signed angle from the camera heading to a world bearing, in (-π, π]. */
private fun relBearing(bearing: Float, heading: Float): Float =
    ((bearing - heading + PI.toFloat()).mod(2f * PI.toFloat())) - PI.toFloat()

private fun DrawScope.drawWalkFrame(
    clock: Float,
    camX: Float,
    camZ: Float,
    heading: Float,
    pitch: Float,
    bob: Float,
    roll: Float,
    density: Float,
    goldRatio: Float,
    butterflies: Int,
    visitors: List<Visitor>,
) {
    val w = size.width
    val h = size.height
    // Honest projection, two focal lengths: X from width (field of view),
    // Y from height (so a bloom two metres ahead stands at her feet, not
    // squashed onto the horizon — the first render preview caught exactly
    // that with a fudge factor here).
    val focalX = w * 0.85f
    val focalY = h * 0.72f
    // Looking up moves the horizon DOWN the screen (more sky), looking
    // down moves it up. A shifted horizon is a good approximation of a
    // pitched camera within the ±24° we allow.
    val horizon = h * 0.40f + kotlin.math.tan(pitch) * focalY
    val eye = EYE_HEIGHT + bob
    val mist = Color(0xFFEFF2E4)
    val sinH = sin(heading)
    val cosH = cos(heading)

    // World → camera: forward along the heading, right perpendicular to it.
    fun project(wx: Float, wz: Float): Projected? {
        val dx = wx - camX
        val dz = wz - camZ
        val fwd = dx * sinH + dz * cosH
        if (fwd < NEAR_CLIP) return null
        val right = dx * cosH - dz * sinH
        return Projected(
            sx = w / 2f + right * focalX / fwd,
            sy = horizon + eye * focalY / fwd,
            depth = fwd,
        )
    }
    fun fog(depth: Float) = ((depth - 2f) / (FAR_CLIP - 4f)).coerceIn(0f, 0.82f)

    rotate(degrees = roll, pivot = Offset(w / 2f, h * 0.6f)) {
        // Sky: dawn cream into sage, hung from the horizon so pitch moves it.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFFFDF7EA),
                0.55f to Color(0xFFF6F2E2),
                1f to GardenSageLight,
                startY = horizon - h * 0.9f,
                endY = horizon,
            ),
            topLeft = Offset(0f, -h),
            size = androidx.compose.ui.geometry.Size(w, horizon + h),
        )
        // The sun keeps a fixed bearing in the world (a little right of the
        // way she first faced), so turning carries it across the sky and
        // out of view — the single strongest cue that she is really turning.
        run {
            val rel = relBearing(0.4f, heading)
            if (kotlin.math.abs(rel) < 1.2f) {
                val sx = w / 2f + kotlin.math.tan(rel) * focalX
                val sy = horizon - h * 0.25f
                drawCircle(Color(0x33E3B23C), radius = w * 0.20f, center = Offset(sx, sy))
                drawCircle(Color(0x55E9C87A), radius = w * 0.10f, center = Offset(sx, sy))
            }
        }

        // Far mounds at fixed bearings all the way round, so there is
        // always a horizon and it always moves the right way.
        val mounds = listOf(
            Triple(0.15f, 1.1f, 0.16f), Triple(1.35f, 0.95f, 0.13f), Triple(2.55f, 1.2f, 0.15f),
            Triple(3.8f, 0.9f, 0.12f), Triple(4.9f, 1.05f, 0.14f), Triple(-0.95f, 0.85f, 0.12f),
        )
        mounds.forEachIndexed { i, (bearing, wf, hf) ->
            val rel = relBearing(bearing, heading)
            if (kotlin.math.abs(rel) < 1.3f) {
                val cx = w / 2f + kotlin.math.tan(rel) * focalX
                val mw = w * wf
                val mh = h * hf
                drawOval(
                    color = if (i % 2 == 0) GardenSageLight.copy(alpha = 0.9f)
                    else mixToward(GardenSageDeep, mist, 0.45f),
                    topLeft = Offset(cx - mw / 2f, horizon - mh * 0.3f),
                    size = androidx.compose.ui.geometry.Size(mw, mh),
                )
            }
        }

        // Ground: mist at the horizon deepening toward her feet.
        drawRect(
            brush = Brush.verticalGradient(
                0f to mist,
                0.45f to GardenSageLight,
                1f to GardenSageDeep,
                startY = horizon,
                endY = h + h * 0.4f,
            ),
            topLeft = Offset(0f, horizon),
            size = androidx.compose.ui.geometry.Size(w, h - horizon + h),
        )

        // The path, one metre at a time, far to near, wherever it winds —
        // ahead, beside, or behind her once she turns round.
        val z0 = floor(camZ).toInt() - FAR_CLIP.toInt()
        for (zi in (z0 + 2 * FAR_CLIP.toInt()) downTo z0) {
            val za = zi.toFloat()
            val zb = za + 1f
            val a = pathX(za)
            val b = pathX(zb)
            val p1 = project(a - PATH_HALF, za) ?: continue
            val p2 = project(a + PATH_HALF, za) ?: continue
            val p3 = project(b + PATH_HALF, zb) ?: continue
            val p4 = project(b - PATH_HALF, zb) ?: continue
            if (p1.depth > FAR_CLIP && p4.depth > FAR_CLIP) continue
            val f = fog(minOf(p1.depth, p4.depth))
            drawPath(
                Path().apply {
                    moveTo(p1.sx, p1.sy); lineTo(p2.sx, p2.sy)
                    lineTo(p3.sx, p3.sy); lineTo(p4.sx, p4.sy); close()
                },
                color = mixToward(Color(0xFFEFE8D6), mist, f),
            )
        }

        // Flowers on every side: gather the visible ones from the cells
        // around her, sort far to near, draw with the flat garden's
        // drawFlower — the same petals she knows, now standing around her.
        val cells = (FAR_CLIP / CELL).toInt() + 1
        val ccx = floor(camX / CELL).toInt()
        val ccz = floor(camZ / CELL).toInt()
        val visible = ArrayList<Pair<Projected, WalkFlower>>(256)
        val halfFov = (w / 2f) / focalX
        for (cx in ccx - cells..ccx + cells) {
            for (cz in ccz - cells..ccz + cells) {
                // Cheap cull on the cell centre before touching its flowers.
                val mdx = (cx + 0.5f) * CELL - camX
                val mdz = (cz + 0.5f) * CELL - camZ
                val mfwd = mdx * sinH + mdz * cosH
                if (mfwd < -CELL) continue
                val mright = kotlin.math.abs(mdx * cosH - mdz * sinH)
                if (mright > (mfwd + CELL * 1.5f) * halfFov * 1.4f + CELL) continue
                for (f in cellFlowers(cx, cz, density, goldRatio)) {
                    val p = project(f.x, f.z) ?: continue
                    if (p.depth > FAR_CLIP) continue
                    if (p.sx < -w * 0.2f || p.sx > w * 1.2f) continue
                    // Beyond 12 m only every other bloom: the eye can't
                    // tell, the frame budget can.
                    if (p.depth > 12f && (f.phase * 10f).toInt() % 2 == 0) continue
                    visible += p to f
                }
            }
        }
        visible.sortByDescending { it.first.depth }
        val budget = if (visible.size > 230) visible.subList(visible.size - 230, visible.size) else visible
        for ((p, f) in budget) {
            val sh = (f.h * focalY / p.depth).coerceAtMost(h * 0.62f)
            val fogAmt = fog(p.depth)
            val petal = if (f.color == 9) GardenBadgeGold else GardenPetals[f.color]
            drawFlower(
                x = p.sx, baseY = p.sy, h = sh,
                sway = sin(clock * 1.1f + f.phase) * 0.05f,
                petalColor = mixToward(petal, mist, fogAmt),
                breathe = 1f + 0.03f * sin(clock * 2f + f.phase),
                stem = mixToward(GardenStem, mist, fogAmt),
                leaf = mixToward(GardenLeaf, mist, fogAmt),
                centre = mixToward(GardenCentre, mist, fogAmt),
            )
        }

        // Her butterflies circle wherever she stands, each on its own
        // orbit — turn and you find them, they don't follow the gaze.
        repeat(butterflies.coerceIn(0, 4)) { b ->
            val ang = clock * (0.25f + b * 0.07f) + b * 1.7f
            val r = 3f + b * 1.4f + sin(clock * 0.4f + b) * 0.8f
            val p = project(camX + sin(ang) * r, camZ + cos(ang) * r) ?: return@repeat
            if (p.depth > FAR_CLIP) return@repeat
            val sy = p.sy - (1.1f + 0.25f * sin(clock * 1.6f + b)) * focalY / p.depth
            val s = (focalY / p.depth * 0.012f).coerceIn(0.5f, 3f)
            withTransform({
                scale(s, s, pivot = Offset(p.sx, sy))
            }) {
                drawButterfly(p.sx, sy, flap = sin(clock * 7f + b) * 0.4f + 0.8f)
            }
        }

        // Visitor stations: every friend she has earned waits somewhere
        // along the path, and the stations repeat every loop so a long
        // stroll — in either direction — re-meets everyone.
        if (visitors.isNotEmpty()) {
            val loop = 7f + visitors.size * 7f + 14f
            val k0 = floor((camZ - 7f) / loop).toInt()
            visitors.forEachIndexed { i, v ->
                val side = if (i % 2 == 0) -1.35f else 1.35f
                for (k in k0 - 1..k0 + 1) {
                    val wz = 7f + i * 7f + k * loop
                    val p = project(pathX(wz) + side, wz) ?: continue
                    if (p.depth < 0.8f || p.depth > 20f) continue
                    val s = (0.30f * focalY / p.depth).coerceIn(6f, 90f)
                    val fogAmt = fog(p.depth)
                    if (fogAmt < 0.7f) drawVisitor(v.id, p.sx, p.sy - s * 0.6f, s, clock + i)
                }
            }
        }
    }
}
