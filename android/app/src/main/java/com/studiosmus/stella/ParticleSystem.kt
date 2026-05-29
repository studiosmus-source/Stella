package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

object ParticleSystem {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ─── Widget static render ─────────────────────────────────────────────────

    fun drawWeatherEffect(
        canvas: Canvas, condition: WeatherCondition, timeOfDay: TimeOfDay,
        seed: Long, w: Int, h: Int
    ) {
        drawTimeOverlay(canvas, timeOfDay, w, h)
        when (condition) {
            WeatherCondition.CLEAR_DAY           -> drawSunGlow(canvas, w, h)
            WeatherCondition.CLEAR_NIGHT         -> drawNightOverlay(canvas, w, h, seed)
            WeatherCondition.PARTLY_CLOUDY_DAY,
            WeatherCondition.PARTLY_CLOUDY_NIGHT -> drawDarkOverlay(canvas, w, h, 25)
            WeatherCondition.OVERCAST            -> drawDarkOverlay(canvas, w, h, 60)
            WeatherCondition.FOG                 -> drawFog(canvas, w, h)
            WeatherCondition.DRIZZLE             -> drawRain(canvas, w, h, 0.3f, seed)
            WeatherCondition.RAIN                -> drawRain(canvas, w, h, 0.65f, seed)
            WeatherCondition.HEAVY_RAIN          -> { drawDarkOverlay(canvas, w, h, 90); drawRain(canvas, w, h, 1.0f, seed) }
            WeatherCondition.SNOW                -> drawSnow(canvas, w, h, 0.5f, seed)
            WeatherCondition.HEAVY_SNOW          -> { drawWhiteHaze(canvas, w, h, 55); drawSnow(canvas, w, h, 1.0f, seed) }
            WeatherCondition.THUNDERSTORM        -> {
                drawDarkOverlay(canvas, w, h, 110)
                drawRain(canvas, w, h, 1.0f, seed)
                drawLightning(canvas, w, h, seed)
            }
            WeatherCondition.HAIL                -> {
                drawDarkOverlay(canvas, w, h, 95)
                drawRain(canvas, w, h, 0.7f, seed)
                drawHail(canvas, w, h, seed)
                drawLightning(canvas, w, h, seed)
            }
        }
    }

    fun drawInfoOverlay(canvas: Canvas, temp: Double, condition: WeatherCondition, w: Int, h: Int) {
        val tempSize = (h * 0.22f).coerceAtMost(110f)
        val condSize = (h * 0.08f).coerceAtMost(44f)
        val stripH   = (h * 0.28f).coerceAtMost(340f)

        paint.shader = LinearGradient(0f, h - stripH, 0f, h.toFloat(),
            Color.argb(160, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - stripH, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        paint.color = Color.WHITE
        paint.setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
        paint.textSize = tempSize; paint.isFakeBoldText = true
        canvas.drawText("${temp.toInt()}°", w * 0.06f, h - stripH + tempSize * 1.15f, paint)
        paint.textSize = condSize; paint.isFakeBoldText = false
        canvas.drawText(condition.label, w * 0.06f, h - condSize * 0.4f, paint)
        paint.clearShadowLayer()
    }

    // ─── Time-of-day overlays ─────────────────────────────────────────────────

    /**
     * Multi-layer sky gradient + horizon glow based on time of day.
     * Called every frame by the live wallpaper service.
     */
    internal fun drawTimeOverlay(canvas: Canvas, timeOfDay: TimeOfDay, w: Int, h: Int) {
        when (timeOfDay) {
            TimeOfDay.DAWN        -> drawDawnOverlay(canvas, w, h)
            TimeOfDay.MORNING     -> drawSolidOverlay(canvas, w, h, Color.rgb(255, 230, 140), 22)
            TimeOfDay.AFTERNOON   -> { /* no tint - bright daylight */ }
            TimeOfDay.GOLDEN_HOUR -> drawGoldenHourOverlay(canvas, w, h)
            TimeOfDay.DUSK        -> drawDuskOverlay(canvas, w, h)
            TimeOfDay.NIGHT       -> drawNightTintOverlay(canvas, w, h)
        }
    }

    private fun drawDawnOverlay(canvas: Canvas, w: Int, h: Int) {
        // Warm orange-pink horizon gradient
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.argb(0, 255, 100, 30),
            Color.argb(90, 255, 120, 40),
            Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        // Slight blue-dark at top (pre-sunrise sky)
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.35f,
            Color.argb(80, 20, 30, 80), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.35f, paint)
        paint.shader = null
    }

    private fun drawGoldenHourOverlay(canvas: Canvas, w: Int, h: Int) {
        // Rich warm amber spreading from bottom
        paint.shader = LinearGradient(0f, h * 0.3f, 0f, h.toFloat(),
            Color.TRANSPARENT,
            Color.argb(100, 255, 100, 10),
            Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        // Warm tint on the whole scene
        drawSolidOverlay(canvas, w, h, Color.rgb(255, 90, 0), 40)
    }

    private fun drawDuskOverlay(canvas: Canvas, w: Int, h: Int) {
        // Purple-blue at top, orange-red at bottom
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.argb(100, 40, 0, 80),
            Color.argb(80, 200, 50, 10),
            Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    private fun drawNightTintOverlay(canvas: Canvas, w: Int, h: Int) {
        // Deep blue-black vignette
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.argb(150, 4, 6, 30),
            Color.argb(100, 8, 12, 45),
            Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    // ─── Fixed HUD sun/moon (upper-right sky, colour depends on time of day) ────

    // Positioned clearly in the sky area, well below the status bar.
    private val HUD_X_FRAC = 0.80f   // fraction of w for the disk centre
    private val HUD_Y_FRAC = 0.22f   // fraction of h  (moved down from top)
    private val HUD_R_FRAC = 0.055f  // disk radius as fraction of h (larger)

    private data class SunStyle(
        val diskCenter: Int, val diskEdge: Int,
        val halo: Int, val atmosphere: Int,
        val rayAlpha: Int, val nRays: Int
    )

    private fun sunStyle(t: TimeOfDay) = when (t) {
        TimeOfDay.DAWN        -> SunStyle(
            Color.argb(255, 255, 180,  80), Color.argb(230, 255,  90,  20),
            Color.argb(90,  255, 110,  30), Color.argb(40,  255,  70,   0), 50, 6)
        TimeOfDay.MORNING     -> SunStyle(
            Color.argb(255, 255, 255, 230), Color.argb(235, 255, 220,  90),
            Color.argb(85,  255, 200,  70), Color.argb(35,  255, 180,  40), 70, 8)
        TimeOfDay.AFTERNOON   -> SunStyle(
            Color.argb(255, 255, 255, 255), Color.argb(245, 255, 252, 210),
            Color.argb(80,  255, 250, 180), Color.argb(28,  255, 248, 160), 80, 8)
        TimeOfDay.GOLDEN_HOUR -> SunStyle(
            Color.argb(255, 255, 210,  80), Color.argb(235, 255, 120,  10),
            Color.argb(95,  255, 100,   0), Color.argb(45,  220,  70,   0), 60, 8)
        TimeOfDay.DUSK        -> SunStyle(
            Color.argb(230, 240, 100,  30), Color.argb(210, 200,  40,  10),
            Color.argb(90,  180,  30,   0), Color.argb(42,  140,  15,   0), 42, 6)
        TimeOfDay.NIGHT       -> SunStyle(0, 0, 0, 0, 0, 0)
    }

    internal fun drawSun(canvas: Canvas, w: Int, h: Int, timeOfDay: TimeOfDay) {
        val style = sunStyle(timeOfDay)
        if (Color.alpha(style.diskCenter) == 0) return

        val cx = w * HUD_X_FRAC
        val cy = h * HUD_Y_FRAC
        val r  = h * HUD_R_FRAC

        // Wide atmospheric diffusion — bleeds across a large area of sky
        paint.shader = RadialGradient(cx, cy, r * 20f,
            style.atmosphere, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Corona halo
        paint.shader = RadialGradient(cx, cy, r * 5.5f,
            style.halo, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(cx - r * 6f, cy - r * 6f, cx + r * 6f, cy + r * 6f, paint)
        paint.shader = null

        // Corona rays — alternating long/short for a natural starburst
        if (style.rayAlpha > 0) {
            paint.strokeCap = Paint.Cap.ROUND
            paint.style = Paint.Style.STROKE
            val total = style.nRays * 2
            for (i in 0 until total) {
                val angle  = i * (PI / total).toFloat()
                val isLong = i % 2 == 0
                val len    = r * (if (isLong) 2.6f else 1.4f)
                val alpha  = if (isLong) style.rayAlpha else style.rayAlpha / 2
                paint.color      = Color.argb(alpha, 255, 252, 210)
                paint.strokeWidth = h * (if (isLong) 0.0024f else 0.0014f)
                val ix = cos(angle); val iy = sin(angle)
                canvas.drawLine(cx + ix * r * 1.3f, cy + iy * r * 1.3f,
                                cx + ix * (r + len), cy + iy * (r + len), paint)
            }
            paint.style    = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.BUTT
        }

        // Disk with limb-darkening (brighter white centre → warm tinted rim)
        paint.shader = RadialGradient(cx, cy, r,
            intArrayOf(style.diskCenter, style.diskCenter, style.diskEdge),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    // ─── Fixed HUD moon with real astronomical phase ──────────────────────────

    internal fun drawMoon(canvas: Canvas, w: Int, h: Int) {
        val cx = w * HUD_X_FRAC
        val cy = h * HUD_Y_FRAC
        val r  = h * HUD_R_FRAC

        val phase = moonPhase()
        val lit   = ((1.0 - cos(phase * 2 * PI)) / 2.0).toFloat()   // 0=new, 1=full

        // Atmospheric glow — scales with how much of the disk is illuminated
        val glowA = (22 + lit * 70).toInt()
        paint.shader = RadialGradient(cx, cy, r * 8f,
            Color.argb(glowA, 195, 215, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(cx - r * 9f, cy - r * 9f, cx + r * 9f, cy + r * 9f, paint)
        paint.shader = null

        // Unlit disk — earthshine: very faint cold blue (visible on new/crescent moon)
        paint.shader = RadialGradient(cx, cy, r,
            Color.argb(60, 45, 60, 100), Color.argb(90, 28, 38, 75),
            Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        // Lit portion — realistic grayish-white lunar surface
        val litPath = moonLitPath(cx, cy, r, phase)
        paint.shader = RadialGradient(cx, cy, r,
            intArrayOf(
                Color.argb(245, 255, 255, 252),
                Color.argb(230, 235, 238, 245),
                Color.argb(210, 210, 218, 232)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP)
        canvas.drawPath(litPath, paint)
        paint.shader = null

        // Crater detail (visible only when enough surface is lit)
        if (lit > 0.12f) {
            canvas.save()
            canvas.clipPath(litPath)
            val craterA = (lit * 38).toInt()
            listOf(
                Triple(-0.20f, -0.26f, 0.11f),
                Triple( 0.30f,  0.14f, 0.09f),
                Triple(-0.07f,  0.34f, 0.10f),
                Triple( 0.12f, -0.40f, 0.07f),
                Triple(-0.38f,  0.20f, 0.06f)
            ).forEach { (dx, dy, rFrac) ->
                // Crater shadow
                paint.color = Color.argb(craterA, 148, 162, 188)
                canvas.drawCircle(cx + r * dx, cy + r * dy, r * rFrac, paint)
                // Crater rim highlight
                paint.color = Color.argb(craterA / 3, 225, 232, 248)
                canvas.drawCircle(
                    cx + r * (dx - rFrac * 0.35f),
                    cy + r * (dy - rFrac * 0.35f),
                    r * rFrac * 0.65f, paint)
            }
            canvas.restore()
        }
    }

    /** Current moon phase [0..1]: 0=new, 0.25=first quarter, 0.5=full, 0.75=last quarter */
    private fun moonPhase(): Float {
        // Known new moon: 6 Jan 2000 18:14 UTC → Unix epoch seconds 947182440
        val synodicSec = 2551443.0
        val ageS = (System.currentTimeMillis() / 1000.0 - 947182440.0)
        return ((ageS % synodicSec + synodicSec) % synodicSec / synodicSec).toFloat()
    }

    /**
     * Returns the Path covering the illuminated portion of the moon.
     * phase [0..1]: 0=new, 0.5=full.
     *
     * Method:
     *   dark = one semicircle (the unlit side) +/− terminator ellipse.
     *   lit  = fullCircle DIFFERENCE dark.
     *
     *   terminatorRx = r·cos(phase·2π):
     *     > 0 → crescent/new (shadow extends toward lit side)
     *     = 0 → quarter (straight vertical terminator)
     *     < 0 → gibbous/full (shadow retreats, revealing more of the lit side)
     */
    private fun moonLitPath(cx: Float, cy: Float, r: Float, phase: Float): Path {
        val waxing = phase <= 0.5f

        // Dark semicircle: left half for waxing (right side lit), right half for waning
        val semi = Path().apply {
            moveTo(cx, cy - r)
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r),
                270f, if (waxing) -180f else 180f)   // -180 = counterclockwise → left; +180 = clockwise → right
            close()
        }

        val terminatorRx = r * cos(phase * 2 * PI.toFloat())
        val absRx = abs(terminatorRx)
        val dark  = Path(semi)
        if (absRx > 0.5f) {
            val terminator = Path().apply {
                addOval(RectF(cx - absRx, cy - r, cx + absRx, cy + r), Path.Direction.CW)
            }
            dark.op(terminator, if (terminatorRx > 0f) Path.Op.UNION else Path.Op.DIFFERENCE)
        }

        return Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            op(dark, Path.Op.DIFFERENCE)
        }
    }

    // ─── Sky info HUD (temperature + condition, below sun/moon disk) ─────────

    internal fun drawSkyInfoHUD(
        canvas: Canvas, w: Int, h: Int,
        temp: Double?, condition: WeatherCondition?
    ) {
        if (temp == null && condition == null) return
        val cx        = w * HUD_X_FRAC
        val cy        = h * HUD_Y_FRAC
        val r         = h * HUD_R_FRAC
        val diskBot   = cy + r
        val tempSize  = h * 0.038f
        val condSize  = h * 0.021f
        val pad       = h * 0.013f

        paint.shader  = null
        paint.setShadowLayer(4f, 0f, 2f, Color.argb(160, 0, 0, 0))
        paint.textAlign = Paint.Align.CENTER

        if (temp != null) {
            paint.color        = Color.WHITE
            paint.textSize     = tempSize
            paint.isFakeBoldText = true
            canvas.drawText("${temp.toInt()}°", cx, diskBot + pad + tempSize, paint)
            paint.isFakeBoldText = false
        }

        if (condition != null) {
            paint.color    = Color.argb(210, 255, 255, 255)
            paint.textSize = condSize
            val condBaseY  = diskBot + pad + tempSize + condSize * 1.25f
            canvas.drawText(condition.label, cx, condBaseY, paint)
        }

        paint.clearShadowLayer()
        paint.textAlign = Paint.Align.LEFT
    }

    // ─── Stars with twinkling ────────────────────────────────────────────────

    internal fun drawNightOverlay(canvas: Canvas, w: Int, h: Int, seed: Long, horizonFrac: Float = 0.40f) {
        val rng   = Random(seed / 60)
        val count = w * h / 3200
        val skyH  = h * horizonFrac.coerceAtMost(0.75f)  // stars only above horizon
        val twinkle = ((System.currentTimeMillis() % 4000L) / 4000f) * 2 * Math.PI.toFloat()
        repeat(count) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * skyH
            val r = rng.nextFloat() * 1.6f + 0.4f
            val baseA = rng.nextFloat() * 180 + 70
            val phase = rng.nextFloat() * Math.PI.toFloat() * 2
            val a = (baseA * (0.7f + 0.3f * sin(twinkle + phase))).toInt().coerceIn(0, 255)
            paint.color = Color.argb(a, 255, 255, 255)
            canvas.drawCircle(x, y, r, paint)
        }
    }

    // ─── Helpers (also used by live wallpaper service) ────────────────────────

    internal fun drawSunGlow(canvas: Canvas, w: Int, h: Int) {
        paint.shader = RadialGradient(w * 0.72f, h * 0.14f, h * 0.5f,
            Color.argb(55, 255, 240, 100), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    internal fun drawWhiteHaze(canvas: Canvas, w: Int, h: Int, alpha: Int) =
        drawSolidOverlay(canvas, w, h, Color.WHITE, alpha)

    internal fun drawDarkOverlay(canvas: Canvas, w: Int, h: Int, alpha: Int) =
        drawSolidOverlay(canvas, w, h, Color.BLACK, alpha)

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun drawRain(canvas: Canvas, w: Int, h: Int, intensity: Float, seed: Long) {
        val rng = Random(seed); val angle = Math.toRadians(22.0)
        val dropLen = h * 0.075f; val count = (w * intensity * 0.45f).toInt()
        paint.strokeWidth = 1.6f
        repeat(count) {
            val x = rng.nextFloat() * (w + dropLen); val y = rng.nextFloat() * h
            val a = (rng.nextFloat() * 110 + 75).toInt()
            paint.color = Color.argb(a, 170, 215, 245)
            canvas.drawLine(x, y, x + (dropLen * sin(angle)).toFloat(),
                y + (dropLen * cos(angle)).toFloat(), paint)
        }
    }

    private fun drawSnow(canvas: Canvas, w: Int, h: Int, intensity: Float, seed: Long) {
        val rng = Random(seed); val count = (w * h / 2800 * intensity).toInt()
        repeat(count) {
            val x = rng.nextFloat() * w; val y = rng.nextFloat() * h
            val r = rng.nextFloat() * 5.5f + 1.5f
            paint.color = Color.argb((rng.nextFloat() * 155 + 100).toInt(), 255, 255, 255)
            canvas.drawCircle(x, y, r, paint)
        }
    }

    private fun drawLightning(canvas: Canvas, w: Int, h: Int, seed: Long) {
        if (seed % 6 != 0L) return
        val rng = Random(seed)
        drawSolidOverlay(canvas, w, h, Color.WHITE, 32)
        paint.color = Color.argb(210, 255, 255, 190); paint.strokeWidth = 3.5f
        var x = w * 0.3f + rng.nextFloat() * w * 0.4f; var y = 0f
        while (y < h * 0.65f) {
            val nx = x + (rng.nextFloat() - 0.5f) * 50f
            val ny = y + rng.nextFloat() * 45f + 20f
            canvas.drawLine(x, y, nx, ny, paint); x = nx; y = ny
        }
    }

    private fun drawHail(canvas: Canvas, w: Int, h: Int, seed: Long) {
        val rng = Random(seed xor 0xA3C1F7L)
        val count = (w * h / 6000)
        val sinA = sin(Math.toRadians(10.0)).toFloat()
        repeat(count) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h
            val r = rng.nextFloat() * 4.5f + 1.5f
            val a = (rng.nextFloat() * 100 + 130).toInt()
            paint.color = Color.argb(a, 195, 215, 240)
            canvas.drawCircle(x + r * sinA, y, r, paint)
            // Specular
            if (r > 3.5f) {
                paint.color = Color.argb(a / 3, 255, 255, 255)
                canvas.drawCircle(x - r * 0.28f, y - r * 0.30f, r * 0.26f, paint)
            }
        }
    }

    private fun drawFog(canvas: Canvas, w: Int, h: Int) {
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.argb(170, 210, 210, 220), Color.argb(50, 210, 210, 220), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    private fun drawSolidOverlay(canvas: Canvas, w: Int, h: Int, color: Int, alpha: Int) {
        paint.color = color; paint.alpha = alpha
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.alpha = 255
    }
}
