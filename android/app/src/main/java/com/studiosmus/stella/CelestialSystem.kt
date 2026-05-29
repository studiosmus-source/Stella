package com.studiosmus.stella

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Renders the sun and moon in a fixed top-right HUD position.
 *
 * Moon surface is pre-generated on a background thread as a 512×512 Bitmap
 * using fBm value noise (same technique as CloudSystem):
 *   - Low-frequency noise → maria (dark basaltic plains) vs. highlands
 *   - High-frequency noise overlay → surface roughness / micro-texture
 *   - Limb darkening → darker at the rim (physically accurate)
 *   - Procedural craters with bowl (dark), raised rim (bright), ejecta halo
 *
 * The phase clip uses Path.op() so the terminator shadow is mathematically exact.
 */
class CelestialSystem(private val w: Int, private val h: Int) {

    companion object {
        const val HUD_X_FRAC = 0.80f
        const val HUD_Y_FRAC = 0.22f
        const val HUD_R_FRAC = 0.038f
    }

    @Volatile private var moonBitmap: Bitmap? = null
    @Volatile private var ready       = false

    private val paint       = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        CoroutineScope(Dispatchers.Default).launch {
            moonBitmap = buildMoonTexture(512)
            ready = true
        }
    }

    // ─── Sun ─────────────────────────────────────────────────────────────────

    private data class SunStyle(val disk: Int, val bloom: Int, val scatter: Int)

    private fun sunStyle(t: TimeOfDay) = when (t) {
        TimeOfDay.DAWN        -> SunStyle(
            Color.argb(255, 255, 210, 120), Color.argb(115, 255, 150,  50), Color.argb(62,  255,  90,  10))
        TimeOfDay.MORNING     -> SunStyle(
            Color.argb(255, 255, 255, 245), Color.argb(100, 255, 230, 110), Color.argb(40,  255, 200,  55))
        TimeOfDay.AFTERNOON   -> SunStyle(
            Color.argb(255, 255, 255, 255), Color.argb( 85, 255, 255, 220), Color.argb(28,  255, 255, 200))
        TimeOfDay.GOLDEN_HOUR -> SunStyle(
            Color.argb(255, 255, 195,  70), Color.argb(130, 255, 130,  10), Color.argb(72,  235,  80,   0))
        TimeOfDay.DUSK        -> SunStyle(
            Color.argb(235, 240,  90,  30), Color.argb(115, 200,  50,  10), Color.argb(65,  160,  25,   0))
        TimeOfDay.NIGHT       -> SunStyle(0, 0, 0)
    }

    fun drawSun(canvas: Canvas, timeOfDay: TimeOfDay) {
        val s = sunStyle(timeOfDay)
        if (Color.alpha(s.disk) == 0) return

        val cx = w * HUD_X_FRAC
        val cy = h * HUD_Y_FRAC
        val r  = h * HUD_R_FRAC

        // Wide atmospheric scatter from the sun's direction
        paint.shader = RadialGradient(cx, cy, r * 22f,
            s.scatter, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Close bloom
        paint.shader = RadialGradient(cx, cy, r * 6f,
            s.bloom, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(cx - r * 7f, cy - r * 7f, cx + r * 7f, cy + r * 7f, paint)
        paint.shader = null

        // Overexposed disk: white-hot centre fading to disk colour, no hard edge
        paint.shader = RadialGradient(cx, cy, r * 1.8f,
            intArrayOf(
                Color.argb(255, 255, 255, 255),
                Color.argb(255, 255, 255, 255),
                s.disk,
                Color.TRANSPARENT),
            floatArrayOf(0f, 0.42f, 0.72f, 1f),
            Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.8f, paint)
        paint.shader = null
    }

    // ─── Moon ────────────────────────────────────────────────────────────────

    fun drawMoon(canvas: Canvas) {
        val cx = w * HUD_X_FRAC
        val cy = h * HUD_Y_FRAC
        val r  = h * HUD_R_FRAC

        val phase = moonPhase()
        val lit   = ((1.0 - cos(phase * 2 * PI)) / 2.0).toFloat()

        // Cold blue-silver sky glow
        val glowA = (20 + lit * 75).toInt()
        paint.shader = RadialGradient(cx, cy, r * 9f,
            Color.argb(glowA, 185, 205, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(cx - r * 10f, cy - r * 10f, cx + r * 10f, cy + r * 10f, paint)
        paint.shader = null

        // Dark disk base (real lunar albedo ~12%)
        paint.shader = RadialGradient(cx, cy, r,
            Color.argb(215, 38, 42, 55), Color.argb(235, 22, 26, 40), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        // Earthshine: faint blue on the unlit side (visible on crescent moons)
        if (lit < 0.7f) {
            paint.color = Color.argb(((1f - lit) * 28).toInt(), 80, 110, 180)
            canvas.drawCircle(cx, cy, r, paint)
        }

        // Lit surface
        val litPath = moonLitPath(cx, cy, r, phase)
        val tex = if (ready) moonBitmap else null

        if (tex != null) {
            // Pre-rendered noise texture: maria, highlands, craters, limb darkening
            canvas.save()
            canvas.clipPath(litPath)
            canvas.drawBitmap(tex, null, RectF(cx - r, cy - r, cx + r, cy + r), bitmapPaint)
            canvas.restore()
        } else {
            // Fallback while texture loads (first few frames only)
            paint.shader = RadialGradient(cx, cy, r,
                intArrayOf(Color.argb(245, 210, 215, 222),
                           Color.argb(230, 185, 192, 205),
                           Color.argb(210, 155, 165, 180)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawPath(litPath, paint)
            paint.shader = null
        }

        // Thin bright rim on the sunlit limb
        if (lit > 0.04f) {
            canvas.save()
            canvas.clipPath(litPath)
            paint.color = Color.argb((lit * 85).toInt(), 238, 245, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.06f
            canvas.drawCircle(cx, cy, r * 0.97f, paint)
            paint.style = Paint.Style.FILL
            canvas.restore()
        }
    }

    // ─── Phase helpers ────────────────────────────────────────────────────────

    private fun moonPhase(): Float {
        val synodicSec = 2551443.0
        val ageS = System.currentTimeMillis() / 1000.0 - 947182440.0
        return ((ageS % synodicSec + synodicSec) % synodicSec / synodicSec).toFloat()
    }

    private fun moonLitPath(cx: Float, cy: Float, r: Float, phase: Float): Path {
        val waxing = phase <= 0.5f
        val semi = Path().apply {
            moveTo(cx, cy - r)
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r),
                270f, if (waxing) -180f else 180f)
            close()
        }
        val terminatorRx = r * cos(phase * 2 * PI.toFloat())
        val absRx = abs(terminatorRx)
        val dark = Path(semi)
        if (absRx > 0.5f) {
            val term = Path().apply {
                addOval(RectF(cx - absRx, cy - r, cx + absRx, cy + r), Path.Direction.CW)
            }
            dark.op(term, if (terminatorRx > 0f) Path.Op.UNION else Path.Op.DIFFERENCE)
        }
        return Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            op(dark, Path.Op.DIFFERENCE)
        }
    }

    // ─── Moon texture (background thread) ────────────────────────────────────

    private fun buildMoonTexture(size: Int): Bitmap {
        val pixels = IntArray(size * size)
        val fc = size / 2f
        val fr = size / 2f

        for (y in 0 until size) {
            for (x in 0 until size) {
                val nx = (x - fc) / fr     // normalised [-1, 1]
                val ny = (y - fc) / fr
                val dist2 = nx * nx + ny * ny
                if (dist2 > 1f) { pixels[y * size + x] = 0; continue }
                val dist = sqrt(dist2)

                // Maria (dark basaltic plains) vs. highlands (lighter grey)
                val mariaN = fbm(nx * 1.3f + 5.1f, ny * 1.3f + 3.7f, 4)
                val baseGray = if (mariaN < 0.46f)
                    lerp(72f, 118f, mariaN / 0.46f)
                else
                    lerp(138f, 205f, (mariaN - 0.46f) / 0.54f)

                // Fine surface roughness
                val rough = (fbm(nx * 11f + 1.2f, ny * 11f + 2.4f, 3) - 0.5f) * 18f

                // Physical limb darkening
                val limb = 1f - dist * 0.18f

                val g = ((baseGray + rough) * limb).toInt().coerceIn(55, 228)

                // Anti-aliased circular edge
                val alpha = if (dist < 0.96f) 255
                            else ((1f - (dist - 0.96f) / 0.04f) * 255).toInt().coerceIn(0, 255)

                pixels[y * size + x] = Color.argb(alpha, g, g, (g + 5).coerceAtMost(255))
            }
        }

        addCraters(pixels, size, fr)

        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    private fun addCraters(pixels: IntArray, size: Int, fr: Float) {
        // (normX, normY, normRadius) — positions consistent with real moon geography
        val craterDefs = arrayOf(
            floatArrayOf( 0.08f, -0.12f, 0.19f),   // Tycho (large, south)
            floatArrayOf(-0.40f,  0.22f, 0.13f),   // Copernicus-like
            floatArrayOf( 0.45f,  0.30f, 0.11f),
            floatArrayOf(-0.18f, -0.48f, 0.09f),
            floatArrayOf( 0.30f, -0.42f, 0.08f),
            floatArrayOf(-0.52f, -0.20f, 0.10f),
            floatArrayOf( 0.15f,  0.45f, 0.08f),
            floatArrayOf(-0.28f,  0.06f, 0.06f),
            floatArrayOf( 0.40f, -0.08f, 0.06f),
            floatArrayOf(-0.10f,  0.28f, 0.05f),
            floatArrayOf( 0.22f,  0.18f, 0.04f),
            floatArrayOf(-0.35f, -0.35f, 0.05f),
        )
        val fc = fr
        for (c in craterDefs) {
            val cx  = (c[0] * fr + fc).toInt()
            val cy  = (c[1] * fr + fc).toInt()
            val cr  = (c[2] * fr).toInt().coerceAtLeast(2)
            val rim    = (cr * 0.15f).toInt().coerceAtLeast(1)
            val ejecta = (cr * 0.30f).toInt().coerceAtLeast(1)

            for (dy in -(cr + ejecta)..(cr + ejecta)) {
                for (dx in -(cr + ejecta)..(cr + ejecta)) {
                    val px = cx + dx; val py = cy + dy
                    if (px < 0 || py < 0 || px >= size || py >= size) continue
                    val existing = pixels[py * size + px]
                    if (Color.alpha(existing) == 0) continue
                    val d = sqrt((dx * dx + dy * dy).toFloat())
                    val g = Color.red(existing)
                    val newG = when {
                        d < cr - rim   -> lerp(g * 0.68f, g * 0.88f,
                                               d / (cr - rim).toFloat()).toInt()
                        d < cr + rim   -> lerp(g * 1.28f, g * 1.10f,
                                               (d - (cr - rim)) / (rim * 2f)).toInt()
                        d < cr + ejecta -> {
                            val f = 1f - (d - cr - rim) / ejecta.toFloat()
                            (g * (1f + f * 0.12f)).toInt()
                        }
                        else           -> g
                    }.coerceIn(30, 245)
                    pixels[py * size + px] =
                        Color.argb(Color.alpha(existing), newG, newG, (newG + 5).coerceAtMost(255))
                }
            }
        }
    }

    // ─── Noise helpers ────────────────────────────────────────────────────────

    private fun hash(ix: Int, iy: Int): Float {
        var h = ix * 374761393 xor iy * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF
    }

    private fun valueNoise(x: Float, y: Float): Float {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        val fx = x - ix;           val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        return lerp(lerp(hash(ix, iy),   hash(ix + 1, iy),   ux),
                    lerp(hash(ix, iy+1), hash(ix + 1, iy+1), ux), uy)
    }

    private fun fbm(x: Float, y: Float, octaves: Int): Float {
        var v = 0f; var amp = 0.5f; var freq = 1f
        repeat(octaves) {
            v += valueNoise(x * freq, y * freq) * amp
            amp *= 0.5f; freq *= 2f
        }
        return v
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
}
