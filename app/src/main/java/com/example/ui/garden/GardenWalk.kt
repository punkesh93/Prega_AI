package com.example.ui.garden

import android.content.Context
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.Space
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Prega AI — a walk in her garden.
 *
 * First-person stroll through the blooms she has grown. This is the app's
 * "3D": a real perspective projection of a flower field onto the screen —
 * world coordinates in metres, an eye height, a focal length, fog with
 * distance — rendered with the SAME drawFlower / drawButterfly /
 * drawVisitor the flat garden uses, so it is unmistakably the same garden,
 * now walked through instead of looked at. No engine, no new dependency,
 * nothing that can fail to load: geometry and the drawing code we already
 * trust.
 *
 * Why hold-to-walk and not a joystick: one thumb, resting anywhere,
 * pressure-free — release and the garden simply breathes around you. The
 * camera bobs at an unhurried 1.7 steps/second with a whisper of roll, and
 * every second footfall lands a soft grass press in the ear that matches
 * the eye. A hum drifts alongside — a mother humming to herself on a slow
 * evening walk (synthesized like the rest of the ambience; original
 * melody, verified by spectrogram).
 *
 * Life along the path: her butterflies cross ahead at their own depths,
 * and every visitor she has earned stands at its own station down the
 * path — walking far enough means MEETING the ladybird, the bee, the
 * snail. The field density itself scales with her bloom count: a young
 * garden is an open meadow, a tended one closes into colour.
 */
private const val EYE_HEIGHT = 1.55f       // metres
private const val STEP_HZ = 1.7f           // unhurried steps per second
private const val WALK_SPEED = 1.05f       // m/s — a stroll, not a hike
private const val BOB_AMPLITUDE = 0.045f   // metres of vertical bob
private const val PATH_HALF = 0.85f        // metres of clear path each side
private const val FAR_CLIP = 26f           // metres of visible world
private const val ROW_GAP = 1.15f          // metres between flower rows

private const val PREFS = "prega_garden"
private const val KEY_SOUND = "sound_on"

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
    var camX by remember { mutableFloatStateOf(0f) }
    var targetX by remember { mutableFloatStateOf(0f) }
    var bobPhase by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableFloatStateOf(0f) }
    var pressing by remember { mutableStateOf(false) }
    var walked by remember { mutableFloatStateOf(0f) }
    var lastFrame by remember { mutableLongStateOf(0L) }
    var viewW by remember { mutableFloatStateOf(1f) }

    val soundOn = remember {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOUND, true)
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
                    if (pressing) {
                        camZ += WALK_SPEED * dt
                        walked += WALK_SPEED * dt
                        val before = bobPhase
                        bobPhase += (2f * PI.toFloat()) * STEP_HZ * dt
                        // A footfall is each half-cycle of the bob — the
                        // moment the body settles onto the next foot.
                        if (floor(before / PI.toFloat()) != floor(bobPhase / PI.toFloat())) {
                            footfall()
                        }
                    } else {
                        // Ease the bob back to standing so releasing never
                        // freezes the camera mid-lurch.
                        bobPhase += (2f * PI.toFloat()) * STEP_HZ * dt * 0.35f
                    }
                    camX += (targetX - camX) * (1f - kotlin.math.exp(-6f * dt))
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
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        val down = event.changes.any { it.pressed }
                        if (change != null) {
                            if (down && !pressing) {
                                pressing = true
                                lastX = change.position.x
                            } else if (down) {
                                // Drag to wander: full width sweeps ~2.4 m.
                                val worldPerPx = 2.4f / viewW.coerceAtLeast(1f)
                                targetX = (targetX + (change.position.x - lastX) * worldPerPx)
                                    .coerceIn(-2.1f, 2.1f)
                                lastX = change.position.x
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
            drawWalkFrame(
                clock = clock,
                camX = camX,
                camZ = camZ,
                bob = if (pressing) sin(bobPhase) * BOB_AMPLITUDE else 0f,
                roll = if (pressing) sin(bobPhase * 0.5f) * 0.45f else 0f,
                density = density,
                goldRatio = goldRatio,
                butterflies = butterflies,
                visitors = visitors,
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
                    "Hold to stroll \u00B7 drag to wander",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF5A503C),
                )
            }
        }
    }
}

// ─── The projected world ───────────────────────────────────────────────────

private data class WalkFlower(
    val x: Float,       // metres left/right of path centre
    val z: Float,       // metres along the path
    val h: Float,       // bloom height in metres
    val color: Int,     // palette index, 9 = gold
    val phase: Float,
)

/** Deterministic flowers for world row [row] — same field on every walk. */
private fun rowFlowers(row: Int, density: Float, goldRatio: Float): List<WalkFlower> {
    val rnd = Random(row * 7919 + 42)
    val out = ArrayList<WalkFlower>(3)
    repeat(3) {
        if (rnd.nextFloat() < density) {
            val side = if (rnd.nextBoolean()) 1f else -1f
            val x = side * (PATH_HALF + 0.25f + rnd.nextFloat() * 2.3f)
            out += WalkFlower(
                x = x,
                z = row * ROW_GAP + rnd.nextFloat() * 0.6f,
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

private fun DrawScope.drawWalkFrame(
    clock: Float,
    camX: Float,
    camZ: Float,
    bob: Float,
    roll: Float,
    density: Float,
    goldRatio: Float,
    butterflies: Int,
    visitors: List<Visitor>,
) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.40f
    // Honest projection, two focal lengths: X from width (field of view),
    // Y from height (so a bloom two metres ahead stands at her feet, not
    // squashed onto the horizon — the first render preview caught exactly
    // that with a fudge factor here).
    val focalX = w * 0.85f
    val focalY = h * 0.72f
    val eye = EYE_HEIGHT + bob
    val mist = Color(0xFFEFF2E4)

    fun projX(wx: Float, zRel: Float) = w / 2f + (wx - camX) * focalX / zRel
    fun groundY(zRel: Float) = horizon + eye * focalY / zRel
    fun fog(zRel: Float) = ((zRel - 2f) / (FAR_CLIP - 4f)).coerceIn(0f, 0.82f)

    rotate(degrees = roll, pivot = Offset(w / 2f, h * 0.6f)) {
        // Sky: dawn cream into sage, with a soft sun.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFFFDF7EA),
                0.45f to Color(0xFFF6F2E2),
                1f to GardenSageLight,
            ),
        )
        drawCircle(Color(0x33E3B23C), radius = w * 0.20f, center = Offset(w * 0.78f, h * 0.15f))
        drawCircle(Color(0x55E9C87A), radius = w * 0.10f, center = Offset(w * 0.78f, h * 0.15f))

        // Far mounds, parallaxed a whisper against her wandering.
        val par = camX * 0.06f * focalX / FAR_CLIP
        drawOval(
            color = GardenSageLight.copy(alpha = 0.9f),
            topLeft = Offset(-w * 0.3f - par, horizon - h * 0.05f),
            size = androidx.compose.ui.geometry.Size(w * 1.1f, h * 0.16f),
        )
        drawOval(
            color = mixToward(GardenSageDeep, mist, 0.45f),
            topLeft = Offset(w * 0.35f - par * 1.6f, horizon - h * 0.035f),
            size = androidx.compose.ui.geometry.Size(w * 0.95f, h * 0.13f),
        )

        // Ground: mist at the horizon deepening toward her feet.
        drawRect(
            brush = Brush.verticalGradient(
                0f to mist,
                0.45f to GardenSageLight,
                1f to GardenSageDeep,
                startY = horizon,
                endY = h,
            ),
            topLeft = Offset(0f, horizon),
            size = androidx.compose.ui.geometry.Size(w, h - horizon),
        )

        // The path she is on, converging to the horizon.
        val zNear = 0.8f
        val nearHalf = PATH_HALF * focalX / zNear
        val nearY = groundY(zNear).coerceAtMost(h + 40f)
        val vanish = Offset(projX(0f, FAR_CLIP), groundY(FAR_CLIP))
        drawPath(
            Path().apply {
                moveTo(projX(-PATH_HALF, zNear).coerceIn(-nearHalf, w + nearHalf), nearY)
                lineTo(projX(PATH_HALF, zNear).coerceIn(-nearHalf, w + nearHalf), nearY)
                lineTo(vanish.x + 6f, vanish.y)
                lineTo(vanish.x - 6f, vanish.y)
                close()
            },
            color = Color(0xFFEFE8D6),
        )

        // Flowers, far to near, fogged into the mist with distance. The
        // renderer is the flat garden's drawFlower — the same petals she
        // knows, now standing around her.
        val firstRow = floor(camZ / ROW_GAP).toInt()
        for (row in (firstRow + (FAR_CLIP / ROW_GAP).toInt()) downTo firstRow) {
            for (f in rowFlowers(row, density, goldRatio)) {
                val zRel = f.z - camZ
                if (zRel < 0.55f || zRel > FAR_CLIP) continue
                val sx = projX(f.x, zRel)
                if (sx < -w * 0.2f || sx > w * 1.2f) continue
                val sy = groundY(zRel)
                val sh = (f.h * focalY / zRel).coerceAtMost(h * 0.62f)
                val fogAmt = fog(zRel)
                val petal = if (f.color == 9) GardenBadgeGold else GardenPetals[f.color]
                drawFlower(
                    x = sx, baseY = sy, h = sh,
                    sway = sin(clock * 1.1f + f.phase) * 0.05f,
                    petalColor = mixToward(petal, mist, fogAmt),
                    breathe = 1f + 0.03f * sin(clock * 2f + f.phase),
                    stem = mixToward(GardenStem, mist, fogAmt),
                    leaf = mixToward(GardenLeaf, mist, fogAmt),
                    centre = mixToward(GardenCentre, mist, fogAmt),
                )
            }
        }

        // Her butterflies cross the path ahead, each at its own depth.
        repeat(butterflies.coerceIn(0, 4)) { b ->
            val zRel = 3.5f + b * 2.6f + sin(clock * 0.4f + b) * 0.8f
            val wx = sin(clock * (0.5f + b * 0.13f) + b * 2f) * 2.2f
            val sx = projX(wx, zRel)
            val sy = groundY(zRel) - (1.1f + 0.25f * sin(clock * 1.6f + b)) * focalY / zRel
            val s = (focalY / zRel * 0.012f).coerceIn(0.5f, 3f)
            withTransform({
                scale(s, s, pivot = Offset(sx, sy))
            }) {
                drawButterfly(sx, sy, flap = sin(clock * 7f + b) * 0.4f + 0.8f)
            }
        }

        // Visitor stations: every friend she has earned waits somewhere down
        // the path. Walking far enough is how you meet them.
        visitors.forEachIndexed { i, v ->
            val worldZ = 7f + i * 7f
            // The path loops every ~70 m so a long stroll re-meets everyone.
            val loop = 7f + visitors.size * 7f + 14f
            var zRel = (worldZ - camZ).mod(loop)
            if (zRel < 0.8f || zRel > 20f) return@forEachIndexed
            val side = if (i % 2 == 0) -1.35f else 1.35f
            val sx = projX(side, zRel)
            val sy = groundY(zRel)
            val s = (0.30f * focalY / zRel).coerceIn(6f, 90f)
            val fogAmt = fog(zRel)
            if (fogAmt < 0.7f) drawVisitor(v.id, sx, sy - s * 0.6f, s, clock + i)
        }
    }
}
