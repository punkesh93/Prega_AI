package com.example.ui.garden

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Prega AI — the garden as a place: a Japanese stroll garden.
 *
 * Approved from the 13 Sep proposal (C). Everything here is drawn with
 * the same hand as the flat garden — ovals, circles, strokes, the flat
 * garden's own drawFlower — projected through the walk's camera. It is a
 * painted film, not a photograph, on purpose: the brand is line-art and
 * linen, and a photoreal garden next to a line-art logo is two products.
 *
 * The world repeats every WORLD_LOOP metres along a winding path so a
 * long walk keeps finding things, in either direction: a stone lantern
 * by the path, a koi pond with lily pads and irises at its edge, maples
 * in terracotta and rose, a bamboo grove, mossed rocks, a torii the path
 * passes under, azalea mounds thick with bloom, and the meadow flowers she
 * has grown. Three ranges of mist mountains sit at fixed bearings. Light
 * shafts, drifting petals and a soft vignette are screen-space.
 *
 * Budget: objects are gathered per frame into a depth-sorted list with a
 * cell-level frustum cull first; canopies and petal clusters draw fewer
 * circles the farther they are; beyond 12 m azaleas are mounds only. The
 * meadow flower budget is 230 blooms a frame.
 */

// ─── World constants ────────────────────────────────────────────────────────

internal const val WALK_EYE_HEIGHT = 1.55f   // metres
internal const val WALK_NEAR = 0.55f          // metres — closer than this is behind the eye
internal const val WALK_FAR = 24f             // metres of visible world
internal const val WALK_PATH_HALF = 0.85f     // metres of clear path each side
internal const val WORLD_LOOP = 60f           // metres before the garden repeats
private const val CELL = 2f                    // metres per meadow cell

/** Where the path runs: a slow meander so turning reveals it curving away. */
internal fun pathX(z: Float): Float = 2.6f * sin(z / 9f) + 1.1f * sin(z / 3.7f + 1.3f)

// ─── Palette — brand-adjacent, never saturated ─────────────────────────────

private val Mist = Color(0xFFEFF2E4)
private val SkyTop = Color(0xFFFDF7EA)
private val SkyMid = Color(0xFFF6F2E2)
private val Sand = Color(0xFFEFE8D6)
private val Stone = Color(0xFF9A9C90)
private val StoneLight = Color(0xFFBBBDB0)
private val StoneDark = Color(0xFF7E8076)
private val Lamp = Color(0xFFE9D6A8)
private val Vermilion = Color(0xFFB9584A)
private val Bark = Color(0xFF5E4A3A)
private val Moss = Color(0xFF7C9660)
private val MossLight = Color(0xFFA6B88C)
private val Water = Color(0xFFD6E0E2)
private val WaterLight = Color(0xFFECEEE6)
private val Bamboo = Color(0xFF96B060)
private val BambooNode = Color(0xFF6E8C46)
private val Koi = Color(0xFFE2784A)
private val Lily = Color(0xFF789660)
private val Iris = Color(0xFF7C60A8)
private val IrisSoft = Color(0xFFC5BADE)
internal val gardenMapleReds = listOf(Color(0xFFC4584A), Color(0xFFD8785A), Color(0xFFE59A62), Color(0xFFB85A67))
private val MapleGreens = listOf(Color(0xFF7EA562), Color(0xFF6C9455), Color(0xFF9AB86A))
private val AzaleaPinks = listOf(Color(0xFFD97A84), Color(0xFFE996A0), Color(0xFFF0B4BE))
private val MountainFar = Color(0xFFC4D0B6)
private val MountainMid = Color(0xFFB2C4A8)
private val MountainNear = Color(0xFF9EB496)

internal fun mixToward(c: Color, toward: Color, amount: Float): Color = Color(
    red = c.red + (toward.red - c.red) * amount,
    green = c.green + (toward.green - c.green) * amount,
    blue = c.blue + (toward.blue - c.blue) * amount,
    alpha = c.alpha,
)

// ─── Camera ────────────────────────────────────────────────────────────────

internal class Projected(val sx: Float, val sy: Float, val depth: Float)

/**
 * Honest perspective, two focal lengths: X from width (field of view), Y
 * from height (so a bloom two metres ahead stands at her feet, not
 * squashed onto the horizon). Pitch is a horizon shift — a fine
 * approximation within the ±24° the walk allows.
 */
internal class WalkCamera(
    val x: Float,
    val z: Float,
    val heading: Float,
    pitch: Float,
    val w: Float,
    val h: Float,
    bob: Float,
) {
    val sinH = sin(heading)
    val cosH = cos(heading)
    val focalX = w * 0.85f
    val focalY = h * 0.72f
    val horizon = h * 0.40f + tan(pitch) * focalY
    val eye = WALK_EYE_HEIGHT + bob

    fun fwd(wx: Float, wz: Float) = (wx - x) * sinH + (wz - z) * cosH
    fun right(wx: Float, wz: Float) = (wx - x) * cosH - (wz - z) * sinH
    fun sx(right: Float, fwd: Float) = w / 2f + right * focalX / fwd
    fun sy(fwd: Float, wy: Float) = horizon + (eye - wy) * focalY / fwd

    fun project(wx: Float, wz: Float, wy: Float = 0f): Projected? {
        val f = fwd(wx, wz)
        if (f < WALK_NEAR) return null
        return Projected(sx(right(wx, wz), f), sy(f, wy), f)
    }

    fun fog(depth: Float) = ((depth - 2f) / (WALK_FAR - 4f)).coerceIn(0f, 0.82f)

    /**
     * A ground polygon given as world x,z pairs, clipped against the near
     * plane in camera space (Sutherland–Hodgman on one plane) so the pond
     * and the path stay drawn while she stands beside or on them, instead
     * of vanishing the moment one corner passes behind the eye.
     */
    fun groundPolygon(xz: FloatArray): Path? {
        val n = xz.size / 2
        if (n < 3) return null
        val rs = FloatArray(n)
        val fs = FloatArray(n)
        var anyIn = false
        for (i in 0 until n) {
            rs[i] = right(xz[2 * i], xz[2 * i + 1])
            fs[i] = fwd(xz[2 * i], xz[2 * i + 1])
            if (fs[i] >= WALK_NEAR) anyIn = true
        }
        if (!anyIn) return null
        val path = Path()
        var started = false
        fun emit(r: Float, f: Float) {
            val px = sx(r, f)
            val py = sy(f, 0f)
            if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ri = rs[i]; val fi = fs[i]; val rj = rs[j]; val fj = fs[j]
            val inI = fi >= WALK_NEAR
            val inJ = fj >= WALK_NEAR
            if (inI) emit(ri, fi)
            if (inI != inJ) {
                val t = (WALK_NEAR - fi) / (fj - fi)
                emit(ri + (rj - ri) * t, WALK_NEAR)
            }
        }
        if (!started) return null
        path.close()
        return path
    }
}

private class Drawable(val depth: Float, val draw: DrawScope.() -> Unit)

// ─── World objects (positions relative to the path, repeated per loop) ─────

private class Station(val z: Float, val side: Float)

/** Where her earned visitors wait: by the lantern, the pond, the torii… */
private val VisitorStations = listOf(
    Station(5.2f, 2.7f), Station(8.2f, -1.5f), Station(20.6f, 1.6f),
    Station(28f, 1.4f), Station(36.6f, -2.7f), Station(47f, 1.5f),
)

private fun insidePond(x: Float, z: Float): Boolean {
    val zr = z.mod(WORLD_LOOP)
    val cx = pathX(9.5f) - 4.2f
    val dx = (x - cx) / 3.0f
    val dz = (zr - 9.5f) / 2.4f
    return dx * dx + dz * dz < 1.15f
}

// ─── Screen-space object drawers (px, py = ground point; sx/sy = px per metre) ──

internal fun DrawScope.drawLantern(px: Float, py: Float, sx: Float, sy: Float, fog: Float) {
    val g = mixToward(Stone, Mist, fog)
    val g2 = mixToward(StoneDark, Mist, fog)
    val lamp = mixToward(Lamp, Mist, fog)
    fun box(x0: Float, y0: Float, x1: Float, y1: Float, c: Color) =
        drawRect(c, Offset(px + x0 * sx, py - y1 * sy), Size((x1 - x0) * sx, (y1 - y0) * sy))
    box(-0.18f, 0f, 0.18f, 0.08f, g2)
    box(-0.07f, 0.08f, 0.07f, 0.62f, g)
    box(-0.27f, 0.62f, 0.27f, 0.72f, g2)
    box(-0.20f, 0.72f, 0.20f, 1.06f, lamp)
    drawPath(
        Path().apply {
            moveTo(px - 0.38f * sx, py - 1.06f * sy)
            lineTo(px + 0.38f * sx, py - 1.06f * sy)
            lineTo(px, py - 1.44f * sy)
            close()
        },
        g2,
    )
    box(-0.05f, 1.44f, 0.05f, 1.58f, g)
}

internal fun DrawScope.drawMaple(
    px: Float, py: Float, sx: Float, sy: Float, fog: Float,
    scale: Float, colors: List<Color>, seed: Int, t: Float,
) {
    val bark = mixToward(Bark, Mist, fog)
    val trunkW = (0.22f * sx).coerceAtLeast(2f)
    drawLine(bark, Offset(px, py), Offset(px + 0.15f * sx, py - 2.4f * scale * sy), trunkW)
    drawLine(
        bark, Offset(px + 0.15f * sx, py - 1.6f * scale * sy),
        Offset(px - 0.8f * sx, py - 2.3f * scale * sy), (trunkW * 0.55f).coerceAtLeast(1.5f),
    )
    val rnd = Random(seed)
    // Fewer circles when small: the eye can't tell, the frame budget can.
    val count = (12 + sx * 0.28f).toInt().coerceIn(12, 44)
    val sway = sin(t * 0.7f + seed) * 0.04f * sx
    repeat(count) {
        val ang = rnd.nextFloat() * 2f * PI.toFloat()
        val rad = kotlin.math.sqrt(rnd.nextFloat())
        val cx = px + 0.1f * sx + cos(ang) * rad * 1.7f * scale * sx + sway
        val cy = py - 2.6f * scale * sy + sin(ang) * rad * 1.1f * scale * sy
        val rr = (0.22f + rnd.nextFloat() * 0.3f) * scale * sx
        drawCircle(mixToward(colors[rnd.nextInt(colors.size)], Mist, fog), rr, Offset(cx, cy))
    }
}

internal fun DrawScope.drawBambooStalk(px: Float, py: Float, sx: Float, sy: Float, fog: Float, lean: Float) {
    val c = mixToward(Bamboo, Mist, fog)
    val node = mixToward(BambooNode, Mist, fog)
    val topX = px + lean * sx
    val topY = py - 3.2f * sy
    drawLine(c, Offset(px, py), Offset(topX, topY), (0.07f * sx).coerceAtLeast(1.5f))
    for (k in 1..6) {
        val f = k / 6.5f
        val nx = px + (topX - px) * f
        val ny = py + (topY - py) * f
        drawLine(node, Offset(nx - 0.06f * sx, ny), Offset(nx + 0.10f * sx, ny), 2f)
    }
    for (k in 0..2) {
        val f = 0.4f + k * 0.22f
        val lx = px + (topX - px) * f
        val ly = py + (topY - py) * f
        drawPath(
            Path().apply {
                moveTo(lx, ly)
                lineTo(lx + 0.5f * sx, ly - 0.25f * sy)
                lineTo(lx + 0.3f * sx, ly + 0.05f * sy)
                close()
            },
            c,
        )
    }
}

internal fun DrawScope.drawMossRock(px: Float, py: Float, sx: Float, sy: Float, fog: Float, rw: Float, rh: Float) {
    drawOval(mixToward(Stone, Mist, fog), Offset(px - rw * sx, py - rh * sy * 1.6f), Size(2f * rw * sx, rh * sy * 1.85f))
    drawOval(mixToward(StoneLight, Mist, fog), Offset(px - rw * sx * 0.8f, py - rh * sy * 1.5f), Size(rw * sx * 1.3f, rh * sy))
    drawOval(mixToward(Moss, Mist, fog), Offset(px - rw * sx * 0.9f, py - rh * sy * 0.6f), Size(1.8f * rw * sx, rh * sy * 0.8f))
}

internal fun DrawScope.drawAzalea(px: Float, py: Float, sx: Float, sy: Float, fog: Float, seed: Int, petals: Boolean) {
    val rnd = Random(seed)
    val r = (0.55f + rnd.nextFloat() * 0.5f)
    drawOval(mixToward(Moss, Mist, fog), Offset(px - r * sx, py - r * sy * 0.9f), Size(2f * r * sx, r * sy * 1.15f))
    if (!petals) return
    val pink = AzaleaPinks[rnd.nextInt(AzaleaPinks.size)]
    val count = (8 + sx * 0.12f).toInt().coerceIn(8, 22)
    repeat(count) {
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val rad = kotlin.math.sqrt(rnd.nextFloat())
        val cx = px + cos(a) * rad * r * sx * 0.9f
        val cy = py - r * sy * 0.35f + sin(a) * rad * r * sy * 0.55f
        val rr = r * sx * (0.09f + rnd.nextFloat() * 0.09f)
        drawCircle(mixToward(pink, Mist, fog), rr, Offset(cx, cy))
    }
}

internal fun DrawScope.drawIris(px: Float, py: Float, sx: Float, sy: Float, fog: Float, seed: Int, t: Float) {
    val rnd = Random(seed)
    val stem = mixToward(GardenStem, Mist, fog)
    val head = mixToward(Iris, Mist, fog)
    val soft = mixToward(IrisSoft, Mist, fog)
    repeat(5) { k ->
        val bx = px + (k - 2) * 0.12f * sx
        val hh = (0.7f + rnd.nextFloat() * 0.3f) * sy
        val swayX = sin(t * 1.3f + seed + k) * 0.03f * sx
        drawLine(stem, Offset(bx, py), Offset(bx + swayX, py - hh), (0.03f * sx).coerceAtLeast(1f))
        val rr = 0.11f * sx
        drawOval(head, Offset(bx + swayX - rr, py - hh - rr * 1.4f), Size(2f * rr, rr * 1.6f))
        drawOval(soft, Offset(bx + swayX - rr * 0.4f, py - hh - rr * 1.1f), Size(rr * 0.8f, rr * 0.7f))
    }
}

// ─── Flat-garden (diorama) versions ────────────────────────────────────────

/** Two ranges of mist mountains along the top of the flat garden's mounds. */
internal fun DrawScope.drawGardenMountains(t: Float) {
    val w = size.width
    val h = size.height
    val base = h * 0.62f
    for ((idx, layer) in listOf(MountainFar to 0.20f, MountainMid to 0.13f).withIndex()) {
        val (col, amp) = layer
        val path = Path()
        path.moveTo(-4f, base + 4f)
        val steps = 22
        for (i in 0..steps) {
            val x = -4f + (w + 8f) * i / steps
            val u = x / w * 6.3f
            val ridge = 0.5f + 0.5f * sin(u * 1.1f + idx * 1.7f) + 0.25f * sin(u * 2.7f + idx) + 0.1f * sin(u * 6f)
            path.lineTo(x, base + h * 0.02f * idx - h * amp * ridge)
        }
        path.lineTo(w + 4f, base + 4f)
        path.close()
        drawPath(path, col.copy(alpha = 0.55f + idx * 0.2f))
    }
    // A breath of mist lying on the far mound.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Mist.copy(alpha = 0f), 0.5f to Mist.copy(alpha = 0.7f), 1f to Mist.copy(alpha = 0f),
            startY = base - h * 0.03f, endY = base + h * 0.05f,
        ),
        topLeft = Offset(0f, base - h * 0.03f),
        size = Size(w, h * 0.08f),
    )
}

/** An oval pond in screen space with reflection rings, lily pads and a koi. */
internal fun DrawScope.drawGardenPond(cx: Float, cy: Float, rx: Float, ry: Float, t: Float) {
    for (ring in 0..3) {
        val f = ring / 3.5f
        drawOval(
            mixToward(Water, WaterLight, f),
            Offset(cx - rx * (1f - f * 0.35f), cy - ry * (1f - f * 0.35f)),
            Size(2f * rx * (1f - f * 0.35f), 2f * ry * (1f - f * 0.35f)),
        )
    }
    for ((px, py, pr) in listOf(Triple(-0.55f, -0.3f, 0.16f), Triple(0.35f, 0.35f, 0.14f), Triple(0.6f, -0.45f, 0.12f))) {
        val r = rx * pr
        drawOval(Lily, Offset(cx + rx * px - r, cy + ry * py - r * 0.35f), Size(2f * r, r * 0.7f))
    }
    val kx = cx + rx * (0.05f + sin(t * 0.5f) * 0.25f)
    val ky = cy + ry * 0.05f
    drawOval(Koi, Offset(kx - rx * 0.12f, ky - ry * 0.16f), Size(rx * 0.24f, ry * 0.32f))
    drawOval(Color(0xFFF6F0E8), Offset(kx - rx * 0.04f, ky - ry * 0.12f), Size(rx * 0.08f, ry * 0.16f))
}

// ─── The frame ─────────────────────────────────────────────────────────────

internal fun DrawScope.drawJapaneseWalk(
    cam: WalkCamera,
    clock: Float,
    roll: Float,
    density: Float,
    goldRatio: Float,
    butterflies: Int,
    visitors: List<Visitor>,
) {
    val w = cam.w
    val h = cam.h
    val horizon = cam.horizon

    rotate(degrees = roll, pivot = Offset(w / 2f, h * 0.6f)) {
        // ── Sky, hung from the horizon so pitch moves it ──
        drawRect(
            brush = Brush.verticalGradient(
                0f to SkyTop, 0.55f to SkyMid, 1f to GardenSageLight,
                startY = horizon - h * 0.9f, endY = horizon,
            ),
            topLeft = Offset(0f, -h),
            size = Size(w, horizon + h),
        )
        // The sun keeps a fixed bearing in the world; turning carries it
        // across the sky — the strongest cue that turning is real.
        val sunRel = relBearing(0.4f, cam.heading)
        var sunX = -1f
        var sunY = -1f
        if (abs(sunRel) < 1.2f) {
            sunX = w / 2f + tan(sunRel) * cam.focalX
            sunY = horizon - h * 0.25f
            drawCircle(Color(0x22E3B23C), w * 0.26f, Offset(sunX, sunY))
            drawCircle(Color(0x33E3B23C), w * 0.17f, Offset(sunX, sunY))
            drawCircle(Color(0x55E9C87A), w * 0.09f, Offset(sunX, sunY))
        }

        // ── Mist mountains: three ranges at fixed bearings, all the way round ──
        val steps = 28
        for ((layerIdx, layer) in listOf(
            Triple(0.13f, MountainFar, 0.55f),
            Triple(0.095f, MountainMid, 0.75f),
            Triple(0.06f, MountainNear, 0.9f),
        ).withIndex()) {
            val (amp, col, alpha) = layer
            val path = Path()
            path.moveTo(-8f, horizon + 4f)
            for (i in 0..steps) {
                val x = -8f + (w + 16f) * i / steps
                val bearing = cam.heading + atan((x - w / 2f) / cam.focalX)
                val ridge = 0.55f + 0.45f * sin(bearing * 2f + layerIdx * 1.7f) +
                    0.25f * sin(bearing * 5f + layerIdx) + 0.12f * sin(bearing * 11f)
                path.lineTo(x, horizon + h * 0.012f * layerIdx - h * amp * ridge)
            }
            path.lineTo(w + 8f, horizon + 4f)
            path.close()
            drawPath(path, col.copy(alpha = alpha))
        }

        // ── Ground ──
        drawRect(
            brush = Brush.verticalGradient(
                0f to Mist, 0.4f to GardenSageLight, 1f to GardenSageDeep,
                startY = horizon, endY = h + h * 0.4f,
            ),
            topLeft = Offset(0f, horizon),
            size = Size(w, h - horizon + h),
        )
        // Mist band lying on the horizon.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Mist.copy(alpha = 0f), 0.5f to Mist.copy(alpha = 0.85f), 1f to Mist.copy(alpha = 0f),
                startY = horizon - h * 0.03f, endY = horizon + h * 0.05f,
            ),
            topLeft = Offset(0f, horizon - h * 0.03f),
            size = Size(w, h * 0.08f),
        )

        // ── Gather the world ──
        val items = ArrayList<Drawable>(512)
        val camX = cam.x
        val camZ = cam.z

        // The path, one metre at a time, clipped so it stays under her feet.
        val z0 = floor(camZ).toInt() - WALK_FAR.toInt()
        for (zi in z0..(z0 + 2 * WALK_FAR.toInt())) {
            val za = zi.toFloat()
            val zb = za + 1f
            val a = pathX(za)
            val b = pathX(zb)
            val depth = cam.fwd(a, za + 0.5f)
            if (depth > WALK_FAR || depth < -1f) continue
            val poly = cam.groundPolygon(
                floatArrayOf(a - WALK_PATH_HALF, za, a + WALK_PATH_HALF, za, b + WALK_PATH_HALF, zb, b - WALK_PATH_HALF, zb)
            ) ?: continue
            val f = cam.fog(depth.coerceAtLeast(WALK_NEAR))
            items += Drawable(depth + 100f) { drawPath(poly, mixToward(Sand, Mist, f)) }
            // A stepping stone on each metre of path.
            val sz = za + 0.5f
            val stoneX = pathX(sz) + sin(sz * 1.7f) * 0.18f
            val p = cam.project(stoneX, sz)
            if (p != null && p.depth < WALK_FAR) {
                val s = cam.focalX / p.depth
                val ff = cam.fog(p.depth)
                items += Drawable(p.depth + 99f) {
                    drawOval(mixToward(StoneLight, Mist, ff), Offset(p.sx - 0.42f * s, p.sy - 0.13f * s), Size(0.84f * s, 0.26f * s))
                    drawOval(mixToward(Color(0xFFCDC9B8), Mist, ff), Offset(p.sx - 0.34f * s, p.sy - 0.11f * s), Size(0.7f * s, 0.16f * s))
                }
            }
        }

        // Loop objects: whichever loops are within range of her.
        val k0 = floor(camZ / WORLD_LOOP).toInt()
        for (k in k0 - 1..k0 + 1) {
            val base = k * WORLD_LOOP
            fun at(zRel: Float, side: Float): Pair<Float, Float> = (pathX(base + zRel) + side) to (base + zRel)

            // Pond with reflection rings, lily pads, koi.
            run {
                val (pcx, pcz) = at(9.5f, -4.2f)
                val depth = cam.fwd(pcx, pcz)
                if (depth > -4f && depth < WALK_FAR + 3f) {
                    val rings = ArrayList<Path>(4)
                    for (ring in 0..3) {
                        val rx = 3.0f - ring * 0.5f
                        val rz = 2.4f - ring * 0.4f
                        val pts = FloatArray(64)
                        for (i in 0 until 32) {
                            val a = i / 32f * 2f * PI.toFloat()
                            pts[2 * i] = pcx + rx * cos(a)
                            pts[2 * i + 1] = pcz + rz * sin(a)
                        }
                        cam.groundPolygon(pts)?.let { rings += it }
                    }
                    if (rings.isNotEmpty()) {
                        val f = cam.fog(depth.coerceAtLeast(WALK_NEAR))
                        items += Drawable(depth + 100.5f) {
                            rings.forEachIndexed { i, p ->
                                drawPath(p, mixToward(mixToward(Water, WaterLight, i / 3.5f), Mist, f))
                            }
                            for ((lx, lz, lr) in listOf(
                                Triple(1.4f, -0.9f, 0.36f), Triple(-0.8f, 0.7f, 0.3f),
                                Triple(0.6f, 1.3f, 0.28f), Triple(-1.6f, -0.5f, 0.32f),
                            )) {
                                val p = cam.project(pcx + lx, pcz + lz) ?: continue
                                val s = cam.focalX / p.depth
                                drawOval(mixToward(Lily, Mist, f), Offset(p.sx - lr * s, p.sy - lr * s * 0.32f), Size(2f * lr * s, lr * s * 0.64f))
                            }
                            for ((kx, kz) in listOf(0.2f to -0.1f, 1.0f to 0.6f, -1.0f to 0.2f)) {
                                val drift = sin(clock * 0.3f + kx) * 0.4f
                                val p = cam.project(pcx + kx + drift, pcz + kz) ?: continue
                                val s = cam.focalX / p.depth
                                drawOval(mixToward(Koi, Mist, f), Offset(p.sx - 0.22f * s, p.sy - 0.07f * s), Size(0.44f * s, 0.14f * s))
                                drawOval(mixToward(Color(0xFFF6F0E8), Mist, f), Offset(p.sx - 0.08f * s, p.sy - 0.05f * s), Size(0.14f * s, 0.07f * s))
                            }
                        }
                    }
                }
                // Irises at the pond's edge.
                for (i in 0 until 5) {
                    val a = 0.9f + i * 0.55f
                    val ix = pcx + 3.2f * cos(a)
                    val iz = pcz + 2.6f * sin(a)
                    val p = cam.project(ix, iz) ?: continue
                    if (p.depth > WALK_FAR) continue
                    val s = cam.focalX / p.depth
                    val sy = cam.focalY / p.depth
                    val f = cam.fog(p.depth)
                    items += Drawable(p.depth) { drawIris(p.sx, p.sy, s, sy, f, k * 31 + i, clock) }
                }
            }

            // Lanterns.
            for ((zr, side) in listOf(4.6f to 1.6f, 36f to -1.6f)) {
                val (lx, lz) = at(zr, side)
                val p = cam.project(lx, lz) ?: continue
                if (p.depth > WALK_FAR) continue
                val s = cam.focalX / p.depth
                val sy = cam.focalY / p.depth
                val f = cam.fog(p.depth)
                items += Drawable(p.depth) { drawLantern(p.sx, p.sy, s, sy, f) }
            }

            // Maples.
            for ((i, m) in listOf(
                Triple(11.5f, -6.0f, 1.4f), Triple(15f, 5.4f, 1.6f), Triple(7.2f, 4.4f, 0.9f),
                Triple(41f, -5.5f, 1.5f), Triple(47f, 6.0f, 1.3f), Triple(52f, -3.8f, 1.0f),
            ).withIndex()) {
                val (zr, side, scale) = m
                val (mx, mz) = at(zr, side)
                val p = cam.project(mx, mz) ?: continue
                if (p.depth > WALK_FAR + 2f) continue
                val s = cam.focalX / p.depth
                val sy = cam.focalY / p.depth
                val f = cam.fog(p.depth)
                val cols = if (i == 2 || i == 5) MapleGreens else gardenMapleReds
                items += Drawable(p.depth) { drawMaple(p.sx, p.sy, s, sy, f, scale, cols, k * 17 + i, clock) }
            }

            // Bamboo groves.
            for ((gi, g) in listOf(Triple(2.6f, 3.2f, 1f), Triple(55f, -3.2f, -1f)).withIndex()) {
                val (zr, side, dir) = g
                for (i in 0 until 12) {
                    val (bx, bz) = at(zr + (i / 4) * 0.6f + (i % 3) * 0.2f, side + dir * (i % 4) * 0.5f)
                    val p = cam.project(bx, bz) ?: continue
                    if (p.depth > WALK_FAR) continue
                    val s = cam.focalX / p.depth
                    val sy = cam.focalY / p.depth
                    val f = cam.fog(p.depth)
                    val lean = 0.05f + sin(clock * 0.5f + i + gi) * 0.03f
                    items += Drawable(p.depth) { drawBambooStalk(p.sx, p.sy, s, sy, f, lean) }
                }
            }

            // Mossed rocks.
            for (r in listOf(
                floatArrayOf(3.2f, -1.8f, 0.7f, 0.45f), floatArrayOf(9.5f, 1.7f, 0.9f, 0.5f),
                floatArrayOf(6.5f, -6.5f, 1.2f, 0.7f), floatArrayOf(13f, 2.6f, 0.8f, 0.5f),
                floatArrayOf(6.8f, -1.4f, 0.5f, 0.3f), floatArrayOf(33f, 2f, 0.9f, 0.5f),
                floatArrayOf(44f, -2.2f, 1.1f, 0.6f), floatArrayOf(52f, 1.8f, 0.6f, 0.35f),
            )) {
                val (rx, rz) = at(r[0], r[1])
                val p = cam.project(rx, rz) ?: continue
                if (p.depth > WALK_FAR) continue
                val s = cam.focalX / p.depth
                val sy = cam.focalY / p.depth
                val f = cam.fog(p.depth)
                items += Drawable(p.depth) { drawMossRock(p.sx, p.sy, s, sy, f, r[2], r[3]) }
            }

            // Torii: the path passes under it.
            run {
                val tz = base + 20f
                val tx = pathX(tz)
                val depth = cam.fwd(tx, tz)
                if (depth > WALK_NEAR && depth < WALK_FAR + 4f) {
                    val f = cam.fog(depth)
                    val c = mixToward(Vermilion, Mist, f)
                    val posts = listOf(-1.15f, 1.15f).mapNotNull { off ->
                        val a = cam.project(tx + off, tz, 0f) ?: return@mapNotNull null
                        val b = cam.project(tx + off, tz, 3.1f) ?: return@mapNotNull null
                        a to b
                    }
                    val beams = listOf(3.1f to 1.75f, 2.55f to 1.3f).mapNotNull { (wy, ext) ->
                        val a = cam.project(tx - ext, tz, wy) ?: return@mapNotNull null
                        val b = cam.project(tx + ext, tz, wy) ?: return@mapNotNull null
                        a to b
                    }
                    if (posts.size == 2) {
                        val wPx = (0.16f * cam.focalX / depth).coerceAtLeast(2f)
                        items += Drawable(depth) {
                            posts.forEach { (a, b) -> drawLine(c, Offset(a.sx, a.sy), Offset(b.sx, b.sy), wPx) }
                            beams.forEach { (a, b) -> drawLine(c, Offset(a.sx, a.sy), Offset(b.sx, b.sy), wPx * 1.1f) }
                        }
                    }
                }
            }
        }

        // Meadow: flowers and azalea mounds from the cells around her.
        val cells = (WALK_FAR / CELL).toInt() + 1
        val ccx = floor(camX / CELL).toInt()
        val ccz = floor(camZ / CELL).toInt()
        val halfFov = (w / 2f) / cam.focalX
        val flowerItems = ArrayList<Pair<Projected, WalkFlower>>(256)
        for (cx in ccx - cells..ccx + cells) {
            for (cz in ccz - cells..ccz + cells) {
                val mdx = (cx + 0.5f) * CELL - camX
                val mdz = (cz + 0.5f) * CELL - camZ
                val mfwd = mdx * cam.sinH + mdz * cam.cosH
                if (mfwd < -CELL) continue
                val mright = abs(mdx * cam.cosH - mdz * cam.sinH)
                if (mright > (mfwd + CELL * 1.5f) * halfFov * 1.4f + CELL) continue
                for (f in cellFlowers(cx, cz, density, goldRatio)) {
                    val p = cam.project(f.x, f.z) ?: continue
                    if (p.depth > WALK_FAR) continue
                    if (p.sx < -w * 0.2f || p.sx > w * 1.2f) continue
                    if (p.depth > 12f && (f.phase * 10f).toInt() % 2 == 0) continue
                    flowerItems += p to f
                }
                // One azalea per ~3 cells, never on the path or in the pond.
                val ar = Random(cx * 1619 xor cz * 31337 xor 7)
                if (ar.nextFloat() < 0.34f) {
                    val ax = cx * CELL + ar.nextFloat() * CELL
                    val az = cz * CELL + ar.nextFloat() * CELL
                    if (abs(ax - pathX(az)) > WALK_PATH_HALF + 1.2f && !insidePond(ax, az)) {
                        val p = cam.project(ax, az)
                        if (p != null && p.depth < WALK_FAR && p.sx > -w * 0.3f && p.sx < w * 1.3f) {
                            val s = cam.focalX / p.depth
                            val sy = cam.focalY / p.depth
                            val fg = cam.fog(p.depth)
                            val seed = cx * 7 + cz * 13
                            val petals = p.depth < 12f
                            items += Drawable(p.depth) { drawAzalea(p.sx, p.sy, s, sy, fg, seed, petals) }
                        }
                    }
                }
            }
        }
        flowerItems.sortByDescending { it.first.depth }
        val budget = if (flowerItems.size > 230) flowerItems.subList(flowerItems.size - 230, flowerItems.size) else flowerItems
        for ((p, f) in budget) {
            items += Drawable(p.depth) {
                val sh = (f.h * cam.focalY / p.depth).coerceAtMost(h * 0.62f)
                val fogAmt = cam.fog(p.depth)
                val petal = if (f.color == 9) GardenBadgeGold else GardenPetals[f.color]
                drawFlower(
                    x = p.sx, baseY = p.sy, h = sh,
                    sway = sin(clock * 1.1f + f.phase) * 0.05f,
                    petalColor = mixToward(petal, Mist, fogAmt),
                    breathe = 1f + 0.03f * sin(clock * 2f + f.phase),
                    stem = mixToward(GardenStem, Mist, fogAmt),
                    leaf = mixToward(GardenLeaf, Mist, fogAmt),
                    centre = mixToward(GardenCentre, Mist, fogAmt),
                )
            }
        }

        // Butterflies orbit wherever she stands.
        repeat(butterflies.coerceIn(0, 4)) { b ->
            val ang = clock * (0.25f + b * 0.07f) + b * 1.7f
            val r = 3f + b * 1.4f + sin(clock * 0.4f + b) * 0.8f
            val p = cam.project(camX + sin(ang) * r, camZ + cos(ang) * r) ?: return@repeat
            if (p.depth > WALK_FAR) return@repeat
            items += Drawable(p.depth - 0.5f) {
                val sy = p.sy - (1.1f + 0.25f * sin(clock * 1.6f + b)) * cam.focalY / p.depth
                val s = (cam.focalY / p.depth * 0.012f).coerceIn(0.5f, 3f)
                withTransform({ scale(s, s, pivot = Offset(p.sx, sy)) }) {
                    drawButterfly(p.sx, sy, flap = sin(clock * 7f + b) * 0.4f + 0.8f)
                }
            }
        }

        // Visitors wait at their stations, every loop, both directions.
        if (visitors.isNotEmpty()) {
            visitors.forEachIndexed { i, v ->
                val st = VisitorStations[i % VisitorStations.size]
                for (k in k0 - 1..k0 + 1) {
                    val wz = k * WORLD_LOOP + st.z
                    val p = cam.project(pathX(wz) + st.side, wz) ?: continue
                    if (p.depth < 0.8f || p.depth > 20f) continue
                    val s = (0.30f * cam.focalY / p.depth).coerceIn(6f, 90f)
                    val fogAmt = cam.fog(p.depth)
                    if (fogAmt < 0.7f) items += Drawable(p.depth) {
                        drawVisitor(v.id, p.sx, p.sy - s * 0.6f, s, clock + i)
                    }
                }
            }
        }

        // ── Draw far to near ──
        items.sortByDescending { it.depth }
        for (d in items) d.draw(this)

        // ── Light shafts from the sun, screen-space, barely there ──
        if (sunX > -1f) {
            for (i in 0 until 4) {
                val x0 = sunX - w * 0.12f + i * w * 0.09f
                drawPath(
                    Path().apply {
                        moveTo(x0, sunY - h * 0.3f); lineTo(x0 + w * 0.05f, sunY - h * 0.3f)
                        lineTo(x0 - w * 0.35f, h); lineTo(x0 - w * 0.5f, h); close()
                    },
                    Color(0x14FFF3D0),
                )
            }
        }

        // ── Drifting petals ──
        for (i in 0 until 26) {
            val seedX = ((i * 7919) % 1000) / 1000f
            val speed = 0.03f + ((i * 131) % 100) / 100f * 0.03f
            val prog = (clock * speed + seedX) % 1f
            val px = ((seedX * 1.7f + prog * 0.25f + sin(clock * 0.8f + i) * 0.04f) % 1f) * w
            val py = prog * h * 0.9f
            val s = 3f + ((i * 37) % 6)
            withTransform({ rotate(clock * 40f + i * 30f, pivot = Offset(px, py)) }) {
                drawOval(AzaleaPinks[i % 3].copy(alpha = 0.55f), Offset(px - s, py - s * 0.55f), Size(2f * s, s * 1.1f))
            }
        }

        // ── Vignette ──
        drawRect(
            brush = Brush.radialGradient(
                0.55f to Color.Transparent, 1f to Color(0x2A3A2E1C),
                center = Offset(w / 2f, h * 0.55f), radius = h * 0.75f,
            ),
        )
    }
}

/** Signed angle from the camera heading to a world bearing, in (-π, π]. */
internal fun relBearing(bearing: Float, heading: Float): Float =
    ((bearing - heading + PI.toFloat()).mod(2f * PI.toFloat())) - PI.toFloat()

// ─── Meadow flowers ────────────────────────────────────────────────────────

internal data class WalkFlower(
    val x: Float,       // world metres
    val z: Float,       // world metres
    val h: Float,       // bloom height in metres
    val color: Int,     // palette index, 9 = gold
    val phase: Float,
)

/**
 * Deterministic flowers for meadow cell [cx],[cz] — same field on every
 * walk, in every direction, with the path and the pond kept clear. Up to
 * five blooms per 2 m cell at full density.
 */
internal fun cellFlowers(cx: Int, cz: Int, density: Float, goldRatio: Float): List<WalkFlower> {
    val rnd = Random((cx * 73856093) xor (cz * 19349663) xor 42)
    val out = ArrayList<WalkFlower>(5)
    repeat(5) {
        if (rnd.nextFloat() < density) {
            val x = cx * CELL + rnd.nextFloat() * CELL
            val z = cz * CELL + rnd.nextFloat() * CELL
            if (abs(x - pathX(z)) < WALK_PATH_HALF + 0.22f) return@repeat
            if (insidePond(x, z)) return@repeat
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
