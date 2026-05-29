package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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
        // All overlays use a directional radial from the sun/moon HUD position so the
        // scene looks lit FROM that corner, not from a flat vertical gradient.
        val sx = w * HUD_X_FRAC;  val sy = h * HUD_Y_FRAC
        when (timeOfDay) {
            TimeOfDay.DAWN -> {
                // Pre-sunrise: cool blue at top, warm blush radiating from sun corner
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    Color.argb(110, 15, 20, 70), Color.argb(0, 15, 20, 70), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = RadialGradient(sx, sy, w * 1.6f,
                    Color.argb(80, 255, 110, 40), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
            }
            TimeOfDay.MORNING -> {
                // Warm directional morning light from sun corner
                paint.shader = RadialGradient(sx, sy, w * 1.4f,
                    Color.argb(45, 255, 215, 80), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
            }
            TimeOfDay.AFTERNOON -> { /* clear bright daylight — sun handles it */ }
            TimeOfDay.GOLDEN_HOUR -> {
                // Strong warm directional from sun + complementary cool shadow in far corner
                paint.shader = RadialGradient(sx, sy, w * 1.6f,
                    Color.argb(85, 255, 120, 15), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                // Cool blue-purple opposite corner (lower left)
                paint.shader = RadialGradient(0f, h.toFloat(), w * 0.8f,
                    Color.argb(45, 50, 60, 140), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
            }
            TimeOfDay.DUSK -> {
                // Reddish sun corner + violet-indigo spreading across sky
                paint.shader = RadialGradient(sx, sy, w * 1.5f,
                    Color.argb(90, 200, 50, 10), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    Color.argb(80, 50, 10, 90), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
            }
            TimeOfDay.NIGHT -> {
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    Color.argb(160, 4, 6, 32), Color.argb(100, 8, 12, 48), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
            }
        }
    }

    // ─── Sky info HUD (temperature + condition, below sun/moon disk) ─────────

    internal fun drawSkyInfoHUD(
        canvas: Canvas, w: Int, h: Int,
        temp: Double?, condition: WeatherCondition?
    ) {
        if (temp == null && condition == null) return
        val cx       = w * CelestialSystem.HUD_X_FRAC
        val cy       = h * CelestialSystem.HUD_Y_FRAC
        val r        = h * CelestialSystem.HUD_R_FRAC
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
        val count = w * h / 7000   // meno stelle, cielo meno affollato
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
