package com.studiosmus.stella

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Realistic cloud layers using Value Noise + domain warping (fBm).
 * Each layer is pre-generated as a wide seamless-scroll Bitmap on a background
 * thread, then drawn as a scrolling strip every frame — zero per-frame allocation.
 *
 * Three layers (cirrus / cumulus / stratus) scroll at different speeds to
 * give natural depth parallax.
 */
class CloudSystem(private val w: Int, private val h: Int) {

    private class CloudLayer(
        val yFrac: Float,       // screen-Y start (fraction of h)
        val hFrac: Float,       // rendered height (fraction of h)
        val speed: Float,       // px/sec horizontal scroll
        val noiseScale: Float,  // noise X frequency
        val octaves: Int,
        val threshold: Float,   // cloud density threshold [0..1]
        val topAlpha: Int,      // max alpha at cloud top (lit)
        val botAlpha: Int,      // max alpha at cloud bottom (shadow)
        var texture: Bitmap? = null,
        var offset: Float = 0f
    )

    private val drawPaint = Paint().apply { isFilterBitmap = true }

    private val layers = listOf(
        // Cirrus: high, fast, wispy, very transparent
        CloudLayer(0.02f, 0.08f, speed=26f, noiseScale=3.5f, octaves=4, threshold=0.46f, topAlpha=70,  botAlpha=40),
        // Cumulus: mid, medium speed, puffy white
        CloudLayer(0.04f, 0.22f, speed=10f, noiseScale=1.9f, octaves=5, threshold=0.37f, topAlpha=230, botAlpha=150),
        // Stratus: low, slow, wide grey bands
        CloudLayer(0.06f, 0.30f, speed= 3f, noiseScale=1.3f, octaves=6, threshold=0.30f, topAlpha=205, botAlpha=130)
    )

    @Volatile private var ready = false

    init {
        CoroutineScope(Dispatchers.Default).launch {
            for (layer in layers) {
                val texH = (h * layer.hFrac).toInt().coerceIn(40, 512)
                layer.texture = buildTexture(TEX_W, texH, layer)
            }
            ready = true
        }
    }

    // ─── Update / Draw ────────────────────────────────────────────────────────

    fun update(dt: Float) {
        if (!ready) return
        for (l in layers) l.offset = (l.offset + l.speed * dt) % TEX_W
    }

    fun draw(canvas: Canvas, density: Float, stormLevel: Float = 0f) {
        if (!ready || density <= 0f) return

        val cf = if (stormLevel > 0.05f) {
            val d = 1f - stormLevel * 0.55f
            ColorMatrixColorFilter(ColorMatrix().apply { setScale(d, d, d * 0.78f, 1f) })
        } else null
        drawPaint.colorFilter = cf

        for (layer in layers) {
            val tex = layer.texture ?: continue
            val alpha = (density * 255).toInt().coerceIn(0, 255)
            drawPaint.alpha = alpha

            val srcX = layer.offset.toInt().coerceIn(0, TEX_W - 1)
            val dstTop = h * layer.yFrac
            val dstBot = dstTop + h * layer.hFrac

            // First segment: [srcX .. TEX_W]
            val seg1W = (TEX_W - srcX).coerceAtMost(w)
            canvas.drawBitmap(tex,
                Rect(srcX, 0, srcX + seg1W, tex.height),
                RectF(0f, dstTop, seg1W.toFloat(), dstBot), drawPaint)

            // Second segment (wrap-around): [0 .. remainder]
            if (seg1W < w) {
                val seg2W = (w - seg1W).coerceAtMost(TEX_W)
                canvas.drawBitmap(tex,
                    Rect(0, 0, seg2W, tex.height),
                    RectF(seg1W.toFloat(), dstTop, w.toFloat(), dstBot), drawPaint)
            }
        }
        drawPaint.colorFilter = null
    }

    // ─── Texture generation (background thread) ───────────────────────────────

    private fun buildTexture(tw: Int, th: Int, layer: CloudLayer): Bitmap {
        val pixels = IntArray(tw * th)

        for (y in 0 until th) {
            val yFrac = y.toFloat() / th

            // Fade at top and bottom edges → no hard cuts
            val edgeFade = smoothstep(0f, 0.14f, yFrac) * smoothstep(1f, 0.86f, yFrac)

            // Physical lighting: cloud tops are white (lit by sun), bottoms gray
            val litFrac = 1f - yFrac * 0.32f

            for (x in 0 until tw) {
                val nx = x.toFloat() / tw * layer.noiseScale
                val ny = yFrac * layer.noiseScale * 0.45f

                // ── Domain warping ──────────────────────────────────────────
                // Warp input coordinates with low-freq noise → billowy turbulence.
                // This is the key trick: without warping, fBm looks blobby;
                // with warping, it looks like real cumulus cloud structure.
                val wx = fbm(nx + 1.70f, ny + 9.20f, 2) * 0.45f
                val wy = fbm(nx + 8.30f, ny + 2.80f, 2) * 0.28f
                val n  = fbm(nx + wx, ny + wy, layer.octaves)

                // Soft threshold → cloud density
                val density = smoothstep(layer.threshold, layer.threshold + 0.38f, n) * edgeFade

                val whiteVal = (255 * litFrac).toInt().coerceIn(185, 255)
                val maxA = (layer.topAlpha * (1f - yFrac) + layer.botAlpha * yFrac).toInt()
                pixels[y * tw + x] = Color.argb(
                    (density * maxA).toInt().coerceIn(0, 255),
                    whiteVal, whiteVal, whiteVal
                )
            }
        }

        return Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, tw, 0, 0, tw, th)
        }
    }

    // ─── Value Noise + fBm ────────────────────────────────────────────────────

    /** Hash two integers to a pseudo-random float in [0, 1]. */
    private fun hash(ix: Int, iy: Int): Float {
        var h = ix * 374761393 xor iy * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF
    }

    /** Smooth bilinear value noise. */
    private fun valueNoise(x: Float, y: Float): Float {
        val ix = floor(x).toInt();  val iy = floor(y).toInt()
        val fx = x - ix;            val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)   // smoothstep interpolation
        val uy = fy * fy * (3f - 2f * fy)
        return lerp(
            lerp(hash(ix,   iy  ), hash(ix + 1, iy  ), ux),
            lerp(hash(ix,   iy+1), hash(ix + 1, iy+1), ux),
            uy
        )
    }

    /** Fractal Brownian Motion: sum of noise at increasing frequencies. */
    private fun fbm(x: Float, y: Float, octaves: Int): Float {
        var value = 0f; var amp = 0.5f; var freq = 1f
        repeat(octaves) {
            value += valueNoise(x * freq, y * freq) * amp
            amp  *= 0.5f; freq *= 2f
        }
        return value
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    companion object {
        private const val TEX_W = 2048   // wide enough for ~100s before seamless repeat
    }
}
