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
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.ui.graphics.Color
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
 * v3 — a Japanese stroll garden (see GardenWorld.kt for the world itself).
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
private const val STEP_HZ = 1.7f           // unhurried steps per second
private const val WALK_SPEED = 1.05f       // m/s — a stroll, not a hike
private const val MAX_SPEED = 2.2f         // m/s — even a hurried drag stays a walk
private const val BOB_AMPLITUDE = 0.045f   // metres of vertical bob
private const val MAX_PITCH = 0.42f        // radians (~24°) of looking up/down
private const val STEPS_PER_METRE = STEP_HZ / WALK_SPEED

private const val PREFS = "prega_garden"
private const val KEY_SOUND = "sound_on"
private const val KEY_MOTION = "walk_motion_look"
private const val KEY_ROOM = "walk_in_room"

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

    // ── "In my room": the live camera behind the drawn garden. Opt-in,
    // remembered, and the permission is asked only when she taps it — a
    // pregnancy app that promises nothing leaves the phone must never ask
    // for the camera unprompted. Preview use case only: no capture, no
    // analysis, no frame ever stored or sent. Without a camera, or with the
    // permission refused, the toggle simply does nothing visible.
    var inRoom by remember { mutableStateOf(prefs.getBoolean(KEY_ROOM, false)) }
    var cameraGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        inRoom = granted
        prefs.edit { putBoolean(KEY_ROOM, granted) }
    }
    val roomLive = inRoom && cameraGranted
    val previewView = remember {
        androidx.camera.view.PreviewView(context).apply {
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        }
    }
    val roomLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(roomLive) {
        if (!roomLive) return@DisposableEffect onDispose { }
        val future = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
        var provider: androidx.camera.lifecycle.ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                provider = future.get()
                val preview = androidx.camera.core.Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    roomLifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                )
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbindAll() } }
    }

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
            .background(if (roomLive) Color.Transparent else Color(0xFFFDFBF4))
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
        if (roomLive) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            viewW = size.width
            viewH = size.height
            val moving = kotlin.math.abs(speed) > 0.05f
            val cam = WalkCamera(
                x = camX, z = camZ, heading = heading, pitch = pitch,
                w = size.width, h = size.height,
                bob = if (moving) sin(bobPhase) * BOB_AMPLITUDE else 0f,
            )
            drawJapaneseWalk(
                cam = cam,
                clock = clock,
                roll = if (moving) sin(bobPhase * 0.5f) * 0.45f else 0f,
                density = density,
                goldRatio = goldRatio,
                butterflies = butterflies,
                visitors = visitors,
                passthrough = roomLive,
            )
        }

        // "In my room" toggle. Asks for the camera only on this tap.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = Space.gutter, end = Space.gutter + 50.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xCCFAF8F1))
                .clickable {
                    when {
                        inRoom -> {
                            inRoom = false
                            prefs.edit { putBoolean(KEY_ROOM, false) }
                        }
                        cameraGranted -> {
                            inRoom = true
                            prefs.edit { putBoolean(KEY_ROOM, true) }
                        }
                        else -> cameraPermission.launch(android.Manifest.permission.CAMERA)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (roomLive) Icons.Rounded.NoPhotography else Icons.Rounded.PhotoCamera,
                contentDescription = if (roomLive) "Back to the garden's own sky"
                else "Put the garden in my room (uses the camera)",
                tint = Color(0xFF3A342A),
            )
        }

        // Motion-look toggle. Off is remembered.
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
                    else "Drag up to stroll \u00B7 sideways to turn \u00B7 \uD83D\uDCF7 for your room",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF5A503C),
                )
            }
        }
    }
}
