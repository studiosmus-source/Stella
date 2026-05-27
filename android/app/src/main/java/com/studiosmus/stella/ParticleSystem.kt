package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object ParticleSystem {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawWeatherEffect(
        canvas: Canvas,
        condition: WeatherCondition,
        timeOfDay: TimeOfDay,
        seed: Long,
        w: Int,
        h: Int
    ) {
        drawTimeOverlay(canvas, timeOfDay, w, h)
        when (condition) {
            WeatherCondition.CLEAR_DAY -> drawSunGlow(canvas, w, h)
            WeatherCondition.CLEAR_NIGHT -> drawNightOverlay(canvas, w, h, seed)
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT ->
                drawDarkOverlay(canvas, w, h, 25)
            WeatherCondition.OVERCAST -> drawDarkOverlay(canvas, w, h, 60)
            WeatherCondition.FOG -> drawFog(canvas, w, h)
            WeatherCondition.DRIZZLE -> drawRain(canvas, w, h, 0.3f, seed)
            WeatherCondition.RAIN -> drawRain(canvas, w, h, 0.65f, seed)
            WeatherCondition.HEAVY_RAIN -> {
                drawDarkOverlay(canvas, w, h, 90)
                drawRain(canvas, w, h, 1.0f, seed)
            }
            WeatherCondition.SNOW -> drawSnow(canvas, w, h, 0.5f, seed)
            WeatherCondition.HEAVY_SNOW -> {
                drawWhiteHaze(canvas, w, h, 55)
                drawSnow(canvas, w, h, 1.0f, seed)
            }
            WeatherCondition.THUNDERSTORM -> {
                drawDarkOverlay(canvas, w, h, 110)
                drawRain(canvas, w, h, 1.0f, seed)
                drawLightning(canvas, w, h, seed)
            }
        }
    }

    fun drawInfoOverlay(canvas: Canvas, temp: Double, condition: WeatherCondition, w: Int, h: Int) {
        val tempSize = (h * 0.22f).coerceAtMost(110f)
        val condSize = (h * 0.08f).coerceAtMost(44f)
        val stripH = (h * 0.28f).coerceAtMost(340f)

        paint.shader = LinearGradient(
            0f, h - stripH, 0f, h.toFloat(),
            Color.argb(160, 0, 0, 0), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h - stripH, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        paint.color = Color.WHITE
        paint.setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
        paint.textSize = tempSize
        paint.isFakeBoldText = true
        canvas.drawText("${temp.toInt()}°", w * 0.06f, h - stripH + tempSize * 1.15f, paint)

        paint.textSize = condSize
        paint.isFakeBoldText = false
        canvas.drawText(condition.label, w * 0.06f, h - condSize * 0.4f, paint)
        paint.clearShadowLayer()
    }

    internal fun drawTimeOverlay(canvas: Canvas, timeOfDay: TimeOfDay, w: Int, h: Int) {
        val (color, alpha) = when (timeOfDay) {
            TimeOfDay.DAWN -> Pair(Color.rgb(255, 140, 60), 75)
            TimeOfDay.MORNING -> Pair(Color.rgb(255, 225, 130), 28)
            TimeOfDay.AFTERNOON -> return
            TimeOfDay.GOLDEN_HOUR -> Pair(Color.rgb(255, 110, 20), 85)
            TimeOfDay.DUSK -> Pair(Color.rgb(110, 50, 150), 90)
            TimeOfDay.NIGHT -> Pair(Color.rgb(8, 12, 45), 135)
        }
        paint.color = color
        paint.alpha = alpha
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.alpha = 255
    }

    internal fun drawSunGlow(canvas: Canvas, w: Int, h: Int) {
        paint.shader = RadialGradient(
            w * 0.75f, h * 0.15f, h * 0.5f,
            Color.argb(60, 255, 235, 100), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    internal fun drawNightOverlay(canvas: Canvas, w: Int, h: Int, seed: Long) {
        val rng = Random(seed / 60)
        val count = w * h / 3500
        paint.color = Color.WHITE
        repeat(count) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h * 0.75f
            val r = rng.nextFloat() * 1.6f + 0.4f
            paint.alpha = (rng.nextFloat() * 180 + 70).toInt()
            canvas.drawCircle(x, y, r, paint)
        }
        paint.alpha = 255
    }

    internal fun drawWhiteHaze(canvas: Canvas, w: Int, h: Int, alpha: Int) {
        paint.color = Color.WHITE
        paint.alpha = alpha
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.alpha = 255
    }

    internal fun drawDarkOverlay(canvas: Canvas, w: Int, h: Int, alpha: Int) {
        paint.color = Color.BLACK
        paint.alpha = alpha
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.alpha = 255
    }

    private fun drawRain(canvas: Canvas, w: Int, h: Int, intensity: Float, seed: Long) {
        val rng = Random(seed)
        val angle = Math.toRadians(22.0)
        val dropLen = h * 0.075f
        val count = (w * intensity * 0.45f).toInt()

        paint.strokeWidth = 1.6f
        repeat(count) {
            val x = rng.nextFloat() * (w + dropLen)
            val y = rng.nextFloat() * h
            val alpha = (rng.nextFloat() * 110 + 75).toInt()
            paint.color = Color.argb(alpha, 170, 215, 245)
            canvas.drawLine(
                x, y,
                x + (dropLen * sin(angle)).toFloat(),
                y + (dropLen * cos(angle)).toFloat(),
                paint
            )
        }
    }

    private fun drawSnow(canvas: Canvas, w: Int, h: Int, intensity: Float, seed: Long) {
        val rng = Random(seed)
        val count = (w * h / 2800 * intensity).toInt()
        repeat(count) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h
            val r = rng.nextFloat() * 5.5f + 1.5f
            val alpha = (rng.nextFloat() * 155 + 100).toInt()
            paint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(x, y, r, paint)
        }
    }

    private fun drawLightning(canvas: Canvas, w: Int, h: Int, seed: Long) {
        if (seed % 6 != 0L) return
        val rng = Random(seed)
        paint.color = Color.argb(210, 255, 255, 190)
        paint.strokeWidth = 3.5f
        paint.color = Color.WHITE
        paint.alpha = 35
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.alpha = 255
        paint.color = Color.argb(210, 255, 255, 190)

        var x = w * 0.3f + rng.nextFloat() * w * 0.4f
        var y = 0f
        while (y < h * 0.65f) {
            val nx = x + (rng.nextFloat() - 0.5f) * 50f
            val ny = y + rng.nextFloat() * 45f + 20f
            canvas.drawLine(x, y, nx, ny, paint)
            x = nx; y = ny
        }
    }

    private fun drawFog(canvas: Canvas, w: Int, h: Int) {
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.argb(170, 210, 210, 220),
            Color.argb(50, 210, 210, 220),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }
}
