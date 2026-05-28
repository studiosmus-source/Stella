package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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

    // ─── Sun positioned by hour ───────────────────────────────────────────────

    /**
     * Draw sun with its actual arc position based on time of day.
     * sunFraction 0→1 = sunrise→sunset.
     */
    internal fun drawSunArc(
        canvas: Canvas, w: Int, h: Int,
        hour: Int, sunriseHour: Int, sunsetHour: Int
    ) {
        val fraction = ((hour - sunriseHour).toFloat() /
            (sunsetHour - sunriseHour).coerceAtLeast(1)).coerceIn(0f, 1f)

        val sunX = w * (0.08f + fraction * 0.84f)
        // Parabola: peaks at center-top, touches horizon at edges
        val sunY = h * (0.03f + 0.28f * (1f - 4f * (fraction - 0.5f).pow(2)))

        // Far outer glow (warm)
        paint.shader = RadialGradient(sunX, sunY, h * 0.55f,
            Color.argb(35, 255, 240, 120), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Inner glow
        paint.shader = RadialGradient(sunX, sunY, h * 0.18f,
            Color.argb(80, 255, 230, 100), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Sun disk
        paint.color = Color.argb(210, 255, 255, 210)
        val diskR = h * 0.038f
        canvas.drawCircle(sunX, sunY, diskR, paint)

        // Corona rays (8 spokes)
        paint.color = Color.argb(50, 255, 250, 180)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        val rayLen = diskR * 2.2f
        for (i in 0..7) {
            val angle = i * Math.PI.toFloat() / 4f
            canvas.drawLine(
                sunX + cos(angle) * diskR * 1.3f, sunY + sin(angle) * diskR * 1.3f,
                sunX + cos(angle) * (diskR + rayLen), sunY + sin(angle) * (diskR + rayLen),
                paint
            )
        }
        paint.style = Paint.Style.FILL

        // Horizon warmth when sun is low (< 20% or > 80% of day)
        val lowness = (1f - 2f * kotlin.math.abs(fraction - 0.5f)).coerceIn(0f, 1f)
        val horizonAlpha = ((1f - lowness) * 60f).toInt().coerceIn(0, 60)
        if (horizonAlpha > 5) {
            paint.shader = LinearGradient(0f, h * 0.6f, 0f, h.toFloat(),
                Color.TRANSPARENT, Color.argb(horizonAlpha, 255, 90, 10), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
        }
    }

    // ─── Moon ────────────────────────────────────────────────────────────────

    internal fun drawMoon(canvas: Canvas, w: Int, h: Int) {
        val moonX = w * 0.72f; val moonY = h * 0.12f
        val r = h * 0.032f

        // Glow
        paint.shader = RadialGradient(moonX, moonY, r * 5.5f,
            Color.argb(28, 190, 210, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Moon disk
        paint.color = Color.argb(220, 238, 244, 255)
        canvas.drawCircle(moonX, moonY, r, paint)

        // Crescent shadow to give 3/4 lit appearance
        paint.color = Color.argb(90, 5, 8, 40)
        canvas.drawCircle(moonX + r * 0.35f, moonY, r, paint)
    }

    // ─── Stars with twinkling ────────────────────────────────────────────────

    internal fun drawNightOverlay(canvas: Canvas, w: Int, h: Int, seed: Long) {
        val rng = Random(seed / 60)
        val count = w * h / 3200
        val twinkle = ((System.currentTimeMillis() % 4000L) / 4000f) * 2 * Math.PI.toFloat()
        repeat(count) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h * 0.75f
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
