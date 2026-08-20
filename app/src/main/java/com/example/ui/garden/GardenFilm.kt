package com.example.ui.garden

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Prega AI — the garden film.
 *
 * Eight seconds of HER garden, 720x1280 (WhatsApp-status native), rendered
 * on the phone with zero new dependencies: every Android ships an H.264
 * encoder (MediaCodec) and an MP4 writer (MediaMuxer). Each frame is the
 * exact drawGardenScene the live screen uses — swaying blooms, breathing
 * petals, her butterflies, up to two of her visitors — under a slow, proud
 * push-in, with falling petals and a serif title card that rises line by
 * line:
 *
 *     {Her name}'s Bloom Garden
 *     Week {w} - {n} blooms - {d} days of showing up
 *     "Grown one gentle day at a time."
 *     It never wilts. It only ever grows.
 *
 * The soundtrack (res/raw/garden_film_audio.m4a — breeze, two footfalls as
 * she walks in, her hum, one bird) is pre-encoded AAC, so it is COPIED into
 * the MP4 sample-by-sample via MediaExtractor: no audio codec is ever
 * spun up, which removes the entire class of device-specific audio-encode
 * failures.
 *
 * Video frames go in as flexible YUV through getInputImage(), which makes
 * the encoder's row/pixel strides ITS problem, not ours — the historic
 * source of green-tinted or crashing exports on odd devices.
 *
 * Everything returns null on any failure; the caller falls back to the PNG
 * share. A share button must never be a dead tap.
 */
object GardenFilm {
    private const val W = 720
    private const val H = 1280
    private const val FPS = 24
    private const val SECONDS = 8f
    private const val FRAMES = (FPS * SECONDS).toInt() // 192

    suspend fun create(
        context: Context,
        name: String,
        week: Int,
        flowers: Int,
        butterflies: Int,
        goldBlooms: Int,
        daysActive: Int,
        visitors: List<Visitor>,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.Default) {
        val outDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(outDir, "my_garden_film.mp4")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var extractor: MediaExtractor? = null
        try {
            // ── Audio track, straight from the shipped AAC ──
            val ext = MediaExtractor()
            extractor = ext
            context.resources.openRawResourceFd(R.raw.garden_film_audio).use { afd ->
                ext.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            var audioFormat: MediaFormat? = null
            for (i in 0 until ext.trackCount) {
                val f = ext.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    ext.selectTrack(i)
                    audioFormat = f
                    break
                }
            }

            // ── Encoder ──
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = enc
            enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()

            val mux = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val audioTrack = audioFormat?.let { mux.addTrack(it) } ?: -1
            var videoTrack = -1
            var muxerStarted = false

            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val sceneBitmap = Bitmap.createBitmap(W, SCENE_H, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(W * H)
            val drawScope = CanvasDrawScope()
            val info = MediaCodec.BufferInfo()
            var framesQueued = 0

            fun drain(endOfStream: Boolean) {
                var spins = 0
                while (true) {
                    val outIdx = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrack = mux.addTrack(enc.outputFormat)
                            mux.start()
                            muxerStarted = true
                        }
                        outIdx >= 0 -> {
                            val buf = enc.getOutputBuffer(outIdx) ?: return
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                info.size = 0 // format already delivered via outputFormat
                            }
                            if (info.size > 0 && muxerStarted) {
                                mux.writeSampleData(videoTrack, buf, info)
                            }
                            enc.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                        else -> {
                            if (!endOfStream) return // TRY_AGAIN while feeding
                            if (++spins > 600) return // ~6 s: give up, fallback
                        }
                    }
                }
            }

            // ── Frames ──
            while (framesQueued < FRAMES) {
                val inIdx = enc.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val t = framesQueued / FPS.toFloat()
                    renderFrame(
                        drawScope, bitmap, sceneBitmap, t,
                        name, week, flowers, butterflies, goldBlooms, daysActive, visitors,
                    )
                    val image = enc.getInputImage(inIdx) ?: return@withContext null
                    bitmap.getPixels(pixels, 0, W, 0, 0, W, H)
                    fillYuvFromArgb(pixels, image.planes)
                    enc.queueInputBuffer(
                        inIdx, 0, W * H * 3 / 2,
                        framesQueued * 1_000_000L / FPS, 0,
                    )
                    framesQueued++
                    onProgress(0.9f * framesQueued / FRAMES)
                }
                drain(endOfStream = false)
            }
            // EOS via an empty input buffer.
            var eosIdx: Int
            do { eosIdx = enc.dequeueInputBuffer(10_000) } while (eosIdx < 0)
            enc.queueInputBuffer(
                eosIdx, 0, 0,
                FRAMES * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            drain(endOfStream = true)
            if (!muxerStarted) return@withContext null

            // ── Copy the soundtrack ──
            if (audioTrack >= 0) {
                val buf = ByteBuffer.allocate(256 * 1024)
                val aInfo = MediaCodec.BufferInfo()
                while (true) {
                    val size = ext.readSampleData(buf, 0)
                    if (size < 0) break
                    val pts = ext.sampleTime
                    if (pts > (SECONDS * 1_000_000L + 60_000L)) break
                    aInfo.set(
                        0, size, pts,
                        if (ext.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                            MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
                    )
                    mux.writeSampleData(audioTrack, buf, aInfo)
                    ext.advance()
                }
            }
            onProgress(1f)
            outFile
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }
            runCatching { muxer?.stop() }; runCatching { muxer?.release() }
            runCatching { extractor?.release() }
        }
    }

    // The scene keeps the live garden's 4:3 proportions (its layout is in
    // fractions of canvas HEIGHT — on the full 9:16 frame the flowers grew
    // into giants, caught by the render preview). It draws into its own
    // bitmap and sits at the frame's bottom; the sky above is the title's.
    private const val SCENE_H = (W * 3) / 4 // 540

    /** One film frame: cream sky, the shared garden scene, the title card. */
    private fun renderFrame(
        drawScope: CanvasDrawScope,
        bitmap: Bitmap,
        sceneBitmap: Bitmap,
        t: Float,
        name: String,
        week: Int,
        flowers: Int,
        butterflies: Int,
        goldBlooms: Int,
        daysActive: Int,
        visitors: List<Visitor>,
    ) {
        drawScope.draw(
            density = Density(2f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(sceneBitmap.asImageBitmap()),
            size = Size(W.toFloat(), SCENE_H.toFloat()),
        ) {
            drawRect(Color(0xFFFAF8F1))
            // The proud push-in: 1.00 -> 1.07 over the film, pivot low so
            // the camera leans INTO her flowers, never off them.
            val cam = 1f + 0.07f * easeInOut(t / SECONDS)
            scale(cam, cam, pivot = androidx.compose.ui.geometry.Offset(W * 0.5f, SCENE_H * 0.85f)) {
                drawGardenScene(
                    t = 1.1f + t * 1.05f, // the live garden's own gentle pace
                    flowers = flowers,
                    butterflies = butterflies,
                    goldBlooms = goldBlooms,
                    visitors = visitors.take(2),
                    petalFall = (t / SECONDS * 1.4f) % 1f,
                )
            }
        }
        val g0 = android.graphics.Canvas(bitmap)
        g0.drawColor(android.graphics.Color.rgb(250, 248, 241))
        g0.drawBitmap(sceneBitmap, 0f, (H - SCENE_H).toFloat(), null)

        // Title card in the sky, ink on cream — serif, like the brand.
        val g = g0
        val ink = android.graphics.Color.rgb(58, 52, 42)
        val muted = android.graphics.Color.rgb(99, 90, 73)

        fun textPaint(sizePx: Float, bold: Boolean, alpha: Int, italic: Boolean = false) =
            Paint().apply {
                color = ink
                this.alpha = alpha
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = sizePx
                typeface = Typeface.create(
                    Typeface.SERIF,
                    when {
                        bold && italic -> Typeface.BOLD_ITALIC
                        bold -> Typeface.BOLD
                        italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    },
                )
            }

        // Each line fades in and rises 18 px over 0.8 s at its own cue.
        fun line(text: String, cueSec: Float, y: Float, p: Paint) {
            val a = ((t - cueSec) / 0.8f).coerceIn(0f, 1f)
            if (a <= 0f) return
            val e = easeInOut(a)
            p.alpha = (p.alpha * e).toInt()
            g.drawText(text, W / 2f, y + 18f * (1f - e), p)
        }

        fun fit(p: Paint, text: String, maxW: Float): Paint {
            while (p.textSize > 20f && p.measureText(text) > maxW) p.textSize -= 2f
            return p
        }
        val title =
            if (name.isBlank()) "My Bloom Garden" else "$name's Bloom Garden"
        line(title, 0.9f, H * 0.155f, fit(textPaint(58f, bold = true, alpha = 255), title, W * 0.90f))
        val stats = buildString {
            if (week > 0) append("Week $week  \u00B7  ")
            append("$flowers bloom${if (flowers == 1) "" else "s"}")
            if (daysActive > 0) {
                append("  \u00B7  $daysActive day${if (daysActive == 1) "" else "s"} of showing up")
            }
        }
        line(stats, 2.4f, H * 0.205f, fit(Paint().apply {
            color = muted; isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 30f
        }, stats, W * 0.92f))
        line(
            "\u201CGrown one gentle day at a time.\u201D",
            4.4f, H * 0.265f,
            textPaint(34f, bold = false, alpha = 235, italic = true),
        )
        line(
            "It never wilts. It only ever grows.",
            6.2f, H * 0.315f,
            Paint().apply {
                color = muted; isAntiAlias = true
                textAlign = Paint.Align.CENTER; textSize = 27f
            },
        )
        // Quiet signature, present throughout the last half.
        val sigA = ((t - 4f) / 1f).coerceIn(0f, 1f)
        if (sigA > 0f) {
            g.drawText(
                "Prega AI", W / 2f, H * 0.955f,
                Paint().apply {
                    color = muted; alpha = (150 * sigA).toInt(); isAntiAlias = true
                    textAlign = Paint.Align.CENTER; textSize = 24f
                },
            )
        }
    }

    private fun easeInOut(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /**
     * ARGB -> YUV420 (BT.601), written through the flexible Image planes so
     * every device's stride layout is honoured. Chroma is averaged over each
     * 2x2 block — cheap and correct for illustration content.
     */
    private fun fillYuvFromArgb(argb: IntArray, planes: Array<android.media.Image.Plane>) {
        val yPlane = planes[0]; val uPlane = planes[1]; val vPlane = planes[2]
        val yBuf = yPlane.buffer; val uBuf = uPlane.buffer; val vBuf = vPlane.buffer
        val yRow = yPlane.rowStride; val yPix = yPlane.pixelStride
        val uRow = uPlane.rowStride; val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride; val vPix = vPlane.pixelStride

        var i = 0
        for (y in 0 until H) {
            var yPos = y * yRow
            for (x in 0 until W) {
                val c = argb[i++]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yy = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                yBuf.put(yPos, yy.coerceIn(0, 255).toByte())
                yPos += yPix
            }
        }
        for (cy in 0 until H / 2) {
            for (cx in 0 until W / 2) {
                // Average the 2x2 block.
                var r = 0; var g = 0; var b = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val c = argb[(cy * 2 + dy) * W + cx * 2 + dx]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                }
                r /= 4; g /= 4; b /= 4
                val u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                val v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                uBuf.put(cy * uRow + cx * uPix, u.coerceIn(0, 255).toByte())
                vBuf.put(cy * vRow + cx * vPix, v.coerceIn(0, 255).toByte())
            }
        }
    }

    /** Hands the finished film to the share sheet. */
    fun share(context: Context, file: File, flowers: Int, daysActive: Int) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.share", file,
        )
        val text = buildString {
            append("My self-care garden \u2014 $flowers flowers and counting \uD83C\uDF38\n")
            if (daysActive > 0) {
                append("$daysActive day${if (daysActive == 1) "" else "s"} in bloom\n")
            }
            append("\nGrown with Prega AI")
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                android.content.Intent.createChooser(intent, "Share your garden film")
            )
        }
        com.example.stats.AppStats.log(com.example.stats.StatEvent.GardenShared)
    }
}
