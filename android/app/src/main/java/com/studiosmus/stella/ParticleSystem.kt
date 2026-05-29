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

    // ─── Fixed HUD sun (top-right, colour depends on time of day) ────────────

    // Sun/moon share the same top-right anchor so they never overlap ground.
    private val HUD_X_FRAC = 0.84f   // fraction of w for the disk centre
    private val HUD_Y_FRAC = 0.09f   // fraction of h
    private val HUD_R_FRAC = 0.038f  // disk radius as fraction of h

    private data class SunStyle(
        val disk:   Int, val inner: Int, val outer: Int,
        val rayAlpha: Int, val nRays: Int
    )

    private fun sunStyle(t: TimeOfDay) = when (t) {
        TimeOfDay.DAWN        -> SunStyle(Color.argb(200, 255, 110,  40), Color.argb(75, 255,  90,  20), Color.argb(32, 255,  70,   0), 38, 6)
        TimeOfDay.MORNING     -> SunStyle(Color.argb(215, 255, 225, 120), Color.argb(70, 255, 200,  80), Color.argb(28, 255, 180,  50), 55, 8)
        TimeOfDay.AFTERNOON   -> SunStyle(Color.argb(225, 255, 252, 235), Color.argb(65, 255, 248, 210), Color.argb(22, 255, 250, 190), 65, 8)
        TimeOfDay.GOLDEN_HOUR -> SunStyle(Color.argb(210, 255, 145,  30), Color.argb(80, 255, 110,   0), Color.argb(38, 255,  80,   0), 45, 8)
        TimeOfDay.DUSK        -> SunStyle(Color.argb(185, 215,  55,  20), Color.argb(75, 190,  35,  10), Color.argb(36, 165,  20,   0), 32, 6)
        TimeOfDay.NIGHT       -> SunStyle(0, 0, 0, 0, 0)  // not drawn
    }

    internal fun drawSun(canvas: Canvas, w: Int, h: Int, timeOfDay: TimeOfDay) {
        val style = sunStyle(timeOfDay)
        if (Color.alpha(style.disk) == 0) return

        val cx = w * HUD_X_FRAC;  val cy = h * HUD_Y_FRAC
        val r  = h * HUD_R_FRAC

        // Outer atmospheric glow
        paint.shader = RadialGradient(cx, cy, r * 14f,
            style.outer, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Inner halo
        paint.shader = RadialGradient(cx, cy, r * 4.5f,
            style.inner, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(cx - r * 5f, cy - r * 5f, cx + r * 5f, cy + r * 5f, paint)
        paint.shader = null

        // Disk with radial gradient (bright centre → tinted edge)
        paint.shader = RadialGradient(cx, cy, r,
            Color.argb(255, 255, 255, 255), style.disk, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        // Corona rays
        if (style.rayAlpha > 0) {
            paint.color = Color.argb(style.rayAlpha, 255, 250, 200)
            paint.strokeWidth = h * 0.0018f
            paint.style = Paint.Style.STROKE
            val rayLen = r * 2.0f
            for (i in 0 until style.nRays) {
                val angle = i * (PI / style.nRays).toFloat()
                val ix = cos(angle); val iy = sin(angle)
                canvas.drawLine(cx + ix * r * 1.25f, cy + iy * r * 1.25f,
                                cx + ix * (r + rayLen), cy + iy * (r + rayLen), paint)
            }
            paint.style = Paint.Style.FILL
        }
    }

    // ─── Fixed HUD moon with real astronomical phase ──────────────────────────

    internal fun drawMoon(canvas: Canvas, w: Int, h: Int) {
        val cx = w * HUD_X_FRAC;  val cy = h * HUD_Y_FRAC
        val r  = h * HUD_R_FRAC

        // Current moon phase [0..1]: 0 = new, 0.5 = full
        val phase = moonPhase()

        // Glow scales with illumination (full moon = brightest)
        val lit    = ((1 - cos(phase * 2 * PI)) / 2).toFloat()  // 0=new, 1=full
        val glowA  = (15 + lit * 45).toInt()
        paint.shader = RadialGradient(cx, cy, r * 6f,
            Color.argb(glowA, 190, 210, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(cx - r * 7f, cy - r * 7f, cx + r * 7f, cy + r * 7f, paint)
        paint.shader = null

        // Dark base (barely-lit new-moon disk)
        paint.color = Color.argb(30, 50, 60, 100)
        canvas.drawCircle(cx, cy, r, paint)

        // Lit portion using mathematically correct phase path
        val litPath = moonLitPath(cx, cy, r, phase)
        paint.shader = RadialGradient(cx, cy, r,
            Color.argb(230, 255, 255, 255), Color.argb(200, 220, 230, 255),
            Shader.TileMode.CLAMP)
        canvas.drawPath(litPath, paint)
        paint.shader = null

        // Subtle crater texture on lit portion (3 dots, consistent seed)
        if (lit > 0.15f) {
            val craterA = (lit * 28).toInt()
            canvas.save()
            canvas.clipPath(litPath)
            listOf(
                Triple(-0.18f, -0.22f, 0.12f), Triple(0.25f,  0.10f, 0.08f),
                Triple(-0.05f,  0.30f, 0.10f)
            ).forEach { (dx, dy, rFrac) ->
                paint.color = Color.argb(craterA, 160, 175, 200)
                canvas.drawCircle(cx + r * dx, cy + r * dy, r * rFrac, paint)
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
