package com.example.ui.garden

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.data.ProgressEntity
import com.example.ui.components.PregaButton
import com.example.ui.components.PregaCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Prega AI — the Bloom Garden.
 *
 * The game. Every day she shows up and cares for herself — logs, quests,
 * streaks — one more flower blooms in a garden that is visibly, animatedly
 * HERS. The next flower always stands as a bud, so tomorrow has a face.
 *
 * Engagement model, chosen deliberately for this audience:
 *  - The garden GROWS from real self-care and never regresses. Missed days
 *    pause it; nothing wilts, nothing dies, nothing resets. A pregnant
 *    woman opening the app after a hard week must find her garden exactly
 *    as she left it — a record of care, not a judgment. This is the
 *    Forest/Finch loop ("I want to see it grow") without the loss-aversion
 *    hook ("it dies if I leave"), which is the one mechanic this audience
 *    should never be given.
 *  - Streaks add life, not stakes: butterflies appear while a streak is
 *    alive (one per 3 days, up to 4) and simply rest again when it isn't.
 *  - Badge count seeds special gold blooms.
 *
 * Everything is drawn — stems sway on individual phase offsets, petals
 * breathe, butterflies fly figure-eights — so it renders crisp at any size
 * and inherits the theme in both modes.
 */
@Composable
fun GardenScreen(
    progress: ProgressEntity,
    badgeCount: Int,
    modifier: Modifier = Modifier,
) {
    // Growth: one flower per 40 points, capped at a full bed of 18.
    val flowers = (progress.points / 40).coerceIn(0, 18)
    val butterflies = (progress.currentStreak / 3).coerceIn(0, 4)
    val goldBlooms = badgeCount.coerceAtMost(3)

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
            .padding(bottom = Space.navClearance),
    ) {
        Spacer(Modifier.height(Space.lg))
        SectionHeader(title = "Your garden", overline = "Grows every day you show up")
        Spacer(Modifier.height(Space.md))

        PregaCard(contentPadding = PaddingValues(0.dp), border = false) {
            GardenCanvas(
                flowers = flowers,
                butterflies = butterflies,
                goldBlooms = goldBlooms,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
        }

        Spacer(Modifier.height(Space.md))

        val shareContext = androidx.compose.ui.platform.LocalContext.current
        PregaButton(
            text = "Share my garden",
            onClick = {
                shareGarden(shareContext, flowers, butterflies, goldBlooms, progress.currentStreak)
            },
        )

        Spacer(Modifier.height(Space.md))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GardenStat("${flowers}", "blooms")
            GardenStat("${(progress.points % 40) * 100 / 40}%", "next bud")
            GardenStat("$butterflies", if (butterflies == 1) "butterfly" else "butterflies")
        }

        Spacer(Modifier.height(Space.lg))

        PregaCard(containerColor = PregaTheme.colors.sageSoft, border = false) {
            Text(
                "Every point you earn — water logged, quests done, days you simply " +
                    "open the app — grows this garden. It never wilts and never " +
                    "resets. It only ever grows.",
                style = MaterialTheme.typography.bodyMedium,
                color = PregaTheme.colors.ink,
            )
        }
        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun GardenStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = PregaTheme.colors.ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = PregaTheme.colors.inkMuted)
    }
}

// ─── The living canvas ─────────────────────────────────────────────────────

private data class Bloom(
    val xFrac: Float,
    val height: Float,
    val baseInset: Float,
    val color: Int,      // 0..3 pastel index, 9 = gold badge bloom
    val phase: Float,
)

@Composable
private fun GardenCanvas(
    flowers: Int,
    butterflies: Int,
    goldBlooms: Int,
    modifier: Modifier = Modifier,
) {
    // Deterministic layout: the same garden every time she opens it. Seeded
    // positions, front-to-back, so new flowers join without rearranging hers.
    val blooms = remember(flowers, goldBlooms) {
        val rnd = Random(42)
        List(18) { i ->
            Bloom(
                xFrac = 0.06f + (i % 9) * 0.105f + rnd.nextFloat() * 0.03f,
                height = 0.22f + rnd.nextFloat() * 0.22f + if (i % 3 == 0) 0.08f else 0f,
                baseInset = rnd.nextFloat() * 0.10f,
                color = if (i < goldBlooms) 9 else i % 4,
                phase = rnd.nextFloat() * 6.28f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "garden")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "gardenClock",
    )

    val sageLight = Color(0xFFE9EDDA)
    val sageDeep = Color(0xFFD6E0C1)
    val stem = Color(0xFF7A8A50)
    val leaf = Color(0xFF8C9E60)
    val petals = listOf(
        Color(0xFFD87A84), // rose
        Color(0xFFD9A441), // gold
        Color(0xFFC5BADE), // lavender
        Color(0xFFE29478), // terracotta
    )
    val badgeGold = Color(0xFFE3B23C)
    val centre = Color(0xFFF7E6C4)
    val budGreen = Color(0xFFB2C084)

    Canvas(modifier) {
        // Sky: a barely-there vertical wash so the scene has air, not a void.
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color(0x00FFFFFF),
                1f to sageLight.copy(alpha = 0.35f),
            ),
        )
        // Far mound: a third depth plane behind the two existing ones.
        drawOval(
            color = sageLight.copy(alpha = 0.55f),
            topLeft = Offset(size.width * 0.28f, size.height * 0.55f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.9f, size.height * 0.5f),
        )
        // Ground: two overlapping sage mounds.
        drawOval(
            color = sageLight,
            topLeft = Offset(-size.width * 0.2f, size.height * 0.62f),
            size = androidx.compose.ui.geometry.Size(size.width * 1.4f, size.height * 0.8f),
        )
        drawOval(
            color = sageDeep,
            topLeft = Offset(-size.width * 0.3f, size.height * 0.78f),
            size = androidx.compose.ui.geometry.Size(size.width * 1.6f, size.height * 0.9f),
        )

        // Flowers that have bloomed, plus the next one as a bud.
        blooms.forEachIndexed { i, b ->
            val baseY = size.height * (0.86f - b.baseInset)
            val x = size.width * b.xFrac
            val h = size.height * b.height
            when {
                i < flowers -> drawFlower(
                    x, baseY, h,
                    sway = sin(t + b.phase) * 0.05f,
                    petalColor = if (b.color == 9) badgeGold else petals[b.color],
                    breathe = 1f + 0.03f * sin(t * 2f + b.phase),
                    stem = stem, leaf = leaf, centre = centre,
                )
                i == flowers -> drawBud(x, baseY, h * 0.6f, stem, budGreen)
                // Beyond the bud: open soil, quietly waiting.
            }
        }

        // Butterflies fly slow figure-eights while a streak is alive.
        repeat(butterflies) { b ->
            val cx = size.width * (0.25f + b * 0.18f)
            val cy = size.height * 0.24f
            val bx = cx + sin(t + b * 1.7f) * size.width * 0.10f
            val by = cy + sin(2f * (t + b * 1.7f)) * size.height * 0.05f
            drawButterfly(bx, by, flap = sin(t * 6f + b) * 0.4f + 0.8f)
        }
    }
}

private fun DrawScope.drawFlower(
    x: Float, baseY: Float, h: Float,
    sway: Float, petalColor: Color, breathe: Float,
    stem: Color, leaf: Color, centre: Color,
) {
    val topX = x + sway * h
    val topY = baseY - h
    drawPath(
        Path().apply {
            moveTo(x, baseY)
            cubicTo(x - h * 0.05f, baseY - h * 0.4f, topX - h * 0.1f, baseY - h * 0.7f, topX, topY)
        },
        color = stem,
        style = Stroke(width = (h * 0.045f).coerceAtLeast(3f), cap = StrokeCap.Round),
    )
    // Leaf halfway up.
    drawOval(
        color = leaf,
        topLeft = Offset(x + h * 0.02f, baseY - h * 0.48f),
        size = androidx.compose.ui.geometry.Size(h * 0.18f, h * 0.09f),
    )
    // Six oval petals, rotated outward, with a deeper under-tone first so
    // each petal has depth — the difference between clipart and illustration.
    val r = h * 0.10f * breathe
    val deep = petalColor.copy(
        red = petalColor.red * 0.82f,
        green = petalColor.green * 0.82f,
        blue = petalColor.blue * 0.82f,
    )
    for (layer in 0..1) {
        val col = if (layer == 0) deep else petalColor
        val spread = if (layer == 0) 1.12f else 1f
        for (p in 0 until 6) {
            val a = p / 6f * 6.2832f + (if (layer == 0) 0.26f else 0f)
            val cxp = topX + cos(a) * r * spread
            val cyp = topY + sin(a) * r * spread
            rotate(
                degrees = a * 57.2958f,
                pivot = Offset(cxp, cyp),
            ) {
                drawOval(
                    color = col,
                    topLeft = Offset(cxp - h * 0.095f * breathe, cyp - h * 0.055f * breathe),
                    size = androidx.compose.ui.geometry.Size(
                        h * 0.19f * breathe, h * 0.11f * breathe,
                    ),
                )
            }
        }
    }
    drawCircle(color = centre, radius = h * 0.052f, center = Offset(topX, topY))
    drawCircle(
        color = centre.copy(alpha = 0.5f),
        radius = h * 0.075f,
        center = Offset(topX, topY),
    )
}

private fun DrawScope.drawBud(x: Float, baseY: Float, h: Float, stem: Color, bud: Color) {
    drawPath(
        Path().apply {
            moveTo(x, baseY)
            cubicTo(x - 3f, baseY - h * 0.5f, x + 3f, baseY - h * 0.8f, x, baseY - h)
        },
        color = stem,
        style = Stroke(width = 3f, cap = StrokeCap.Round),
    )
    drawOval(
        color = bud,
        topLeft = Offset(x - h * 0.09f, baseY - h - h * 0.12f),
        size = androidx.compose.ui.geometry.Size(h * 0.18f, h * 0.22f),
    )
}

private fun DrawScope.drawButterfly(x: Float, y: Float, flap: Float) {
    val rose = Color(0xFFD87A84)
    val terra = Color(0xFFE29478)
    val body = Color(0xFF5A503C)
    val wx = 8f * flap
    drawOval(rose, topLeft = Offset(x - 9f - wx * 0.4f, y - 9f), size = androidx.compose.ui.geometry.Size(7f + wx, 9f))
    drawOval(rose, topLeft = Offset(x + 2f - wx * 0.6f + wx, y - 9f), size = androidx.compose.ui.geometry.Size(7f + wx, 9f))
    drawOval(terra, topLeft = Offset(x - 7f - wx * 0.3f, y + 1f), size = androidx.compose.ui.geometry.Size(5f + wx * 0.7f, 6f))
    drawOval(terra, topLeft = Offset(x + 2f + wx * 0.3f, y + 1f), size = androidx.compose.ui.geometry.Size(5f + wx * 0.7f, 6f))
    drawLine(body, Offset(x, y - 7f), Offset(x, y + 8f), strokeWidth = 2.5f, cap = StrokeCap.Round)
}

// ─── Sharing ───────────────────────────────────────────────────────────────

/**
 * Renders her garden — the same drawing code as the live screen, one fixed
 * frame — to a PNG and opens the share sheet. The image carries a small
 * stamp line; her garden is the content, the app is the signature.
 */
private fun shareGarden(
    context: android.content.Context,
    flowers: Int,
    butterflies: Int,
    goldBlooms: Int,
    streak: Int,
) {
    val w = 1080
    val h = 810
    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap.asImageBitmap())

    val blooms = run {
        val rnd = Random(42)
        List(18) { i ->
            Bloom(
                xFrac = 0.06f + (i % 9) * 0.105f + rnd.nextFloat() * 0.03f,
                height = 0.22f + rnd.nextFloat() * 0.22f + if (i % 3 == 0) 0.08f else 0f,
                baseInset = rnd.nextFloat() * 0.10f,
                color = if (i < goldBlooms) 9 else i % 4,
                phase = rnd.nextFloat() * 6.28f,
            )
        }
    }

    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        density = androidx.compose.ui.unit.Density(2.5f),
        layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        canvas = canvas,
        size = androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()),
    ) {
        // Cream backdrop for a standalone image.
        drawRect(Color(0xFFFAF8F1))
        val sageLight = Color(0xFFE9EDDA)
        val sageDeep = Color(0xFFD6E0C1)
        val stem = Color(0xFF7A8A50)
        val leaf = Color(0xFF8C9E60)
        val petals = listOf(
            Color(0xFFD87A84), Color(0xFFD9A441), Color(0xFFC5BADE), Color(0xFFE29478),
        )
        drawOval(
            color = sageLight,
            topLeft = Offset(-size.width * 0.2f, size.height * 0.62f),
            size = androidx.compose.ui.geometry.Size(size.width * 1.4f, size.height * 0.8f),
        )
        drawOval(
            color = sageDeep,
            topLeft = Offset(-size.width * 0.3f, size.height * 0.78f),
            size = androidx.compose.ui.geometry.Size(size.width * 1.6f, size.height * 0.9f),
        )
        blooms.forEachIndexed { i, b ->
            val baseY = size.height * (0.86f - b.baseInset)
            val x = size.width * b.xFrac
            val hh = size.height * b.height
            when {
                i < flowers -> drawFlower(
                    x, baseY, hh,
                    sway = sin(b.phase) * 0.04f,
                    petalColor = if (b.color == 9) Color(0xFFE3B23C) else petals[b.color],
                    breathe = 1f,
                    stem = stem, leaf = leaf, centre = Color(0xFFF7E6C4),
                )
                i == flowers -> drawBud(x, baseY, hh * 0.6f, stem, Color(0xFFB2C084))
            }
        }
        repeat(butterflies) { b ->
            drawButterfly(
                size.width * (0.25f + b * 0.18f),
                size.height * (0.20f + (b % 2) * 0.08f),
                flap = 0.9f,
            )
        }
    }

    // Title + stamp drawn INTO the image, so the share stands alone on any
    // feed without its accompanying text: serif title top, quiet footer band.
    run {
        val g = android.graphics.Canvas(bitmap)
        val ink = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(58, 52, 42)
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD
            )
            textSize = 64f
        }
        g.drawText("My Bloom Garden", 48f, 96f, ink)
        val sub = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(99, 90, 73)
            isAntiAlias = true
            textSize = 34f
        }
        val subtitle = buildString {
            append("$flowers flower${if (flowers == 1) "" else "s"}")
            if (streak > 0) append("  ·  $streak-day streak")
        }
        g.drawText(subtitle, 50f, 150f, sub)
        val foot = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(180, 99, 90, 73)
            isAntiAlias = true
            textSize = 30f
        }
        g.drawText("Grown with Prega AI", 48f, h - 36f, foot)
    }

    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
    val file = java.io.File(dir, "my_garden.png")
    java.io.FileOutputStream(file).use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }

    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.share", file,
    )
    val text = buildString {
        append("My self-care garden — $flowers flowers and counting 🌸\n")
        if (streak > 0) append("$streak-day streak\n")
        append("\nGrown with Prega AI")
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(intent, "Share your garden")
        )
    }
}
