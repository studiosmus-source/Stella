package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object ParticleSystem {

    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.8f }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    fun drawWeatherEffect(
        canvas: Canvas,
        condition: WeatherCondition,
        timeOfDay: TimeOfDay,
        seed: Long,
        width: Int,
        height: Int
    ) {
        drawTimeOverlay(canvas, timeOfDay, width, height)
        when (condition) {
            WeatherCondition.RAIN -> drawRain(canvas, width, height, 0.6f, seed)
            WeatherCondition.HEAVY_RAIN -> {
                darkenCanvas(canvas, width, height, 80)
                drawRain(canvas, width, height, 1.0f, seed)
            }
            WeatherCondition.DRIZZLE -> drawRain(canvas, width, height, 0.3f, seed)
            WeatherCondition.SNOW -> drawSnow(canvas, width, height, 0.5f, seed)
            WeatherCondition.HEAVY_SNOW -> {
                drawWhiteHaze(canvas, width, height, 60)
                drawSnow(canvas, width, height, 1.0f, seed)
            }
            WeatherCondition.THUNDERSTORM -> {
                darkenCanvas(canvas, width, height, 100)
                drawRain(canvas, width, height, 1.0f, seed)
                drawLightning(canvas, width, height, seed)
            }
            WeatherCondition.FOG -> drawFog(canvas, width, height)
            WeatherCondition.OVERCAST -> darkenCanvas(canvas, width, height, 50)
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT ->
                darkenCanvas(canvas, width, height, 20)
            WeatherCondition.CLEAR_DAY -> drawSunGlow(canvas, width, height)
            WeatherCondition.CLEAR_NIGHT -> drawStars(canvas, width, height, seed)
        }
    }

    fun drawInfo(canvas: Canvas, temp: Double, condition: WeatherCondition, width: Int, height: Int) {
        textPaint.textSize = height * 0.12f
        val tempStr = "${temp.toInt()}°"
        canvas.drawText(tempStr, width * 0.08f, height * 0.22f, textPaint)

        textPaint.textSize = height * 0.07f
        canvas.drawText(condition.label, width * 0.08f, height * 0.36f, textPaint)
    }

    private val WeatherCondition.label get() = when (this) {
        WeatherCondition.CLEAR_DAY -> "Sereno"
        WeatherCondition.CLEAR_NIGHT -> "Sereno"
        WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> "Parzialmente nuvoloso"
        WeatherCondition.OVERCAST -> "Nuvoloso"
        WeatherCondition.FOG -> "Nebbia"
        WeatherCondition.DRIZZLE -> "Pioggerella"
        WeatherCondition.RAIN -> "Pioggia"
        WeatherCondition.HEAVY_RAIN -> "Pioggia intensa"
        WeatherCondition.SNOW -> "Neve"
        WeatherCondition.HEAVY_SNOW -> "Neve intensa"
        WeatherCondition.THUNDERSTORM -> "Temporale"
    }

    private fun drawTimeOverlay(canvas: Canvas, timeOfDay: TimeOfDay, width: Int, height: Int) {
        val (color, alpha) = when (timeOfDay) {
            TimeOfDay.DAWN -> Pair(Color.rgb(255, 140, 60), 70)
            TimeOfDay.MORNING -> Pair(Color.rgb(255, 220, 120), 25)
            TimeOfDay.AFTERNOON -> return
            TimeOfDay.GOLDEN_HOUR -> Pair(Color.rgb(255, 120, 20), 80)
            TimeOfDay.DUSK -> Pair(Color.rgb(120, 60, 160), 85)
            TimeOfDay.NIGHT -> Pair(Color.rgb(10, 15, 50), 130)
        }
        overlayPaint.color = color
        overlayPaint.alpha = alpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
    }

    private fun drawRain(canvas: Canvas, width: Int, height: Int, intensity: Float, seed: Long) {
        val rng = Random(seed)
        val angle = Math.toRadians(20.0)
        val dropCount = (width * intensity * 0.4f).toInt()
        val dropLen = height * 0.07f

        repeat(dropCount) {
            val x = rng.nextFloat() * (width + dropLen)
            val y = rng.nextFloat() * height
            val alpha = (rng.nextFloat() * 100 + 80).toInt()
            rainPaint.color = Color.argb(alpha, 174, 214, 241)
            canvas.drawLine(
                x, y,
                x + (dropLen * sin(angle)).toFloat(),
                y + (dropLen * cos(angle)).toFloat(),
                rainPaint
            )
        }
    }

    private fun drawSnow(canvas: Canvas, width: Int, height: Int, intensity: Float, seed: Long) {
        val rng = Random(seed)
        val flakeCount = (width * height / 3000 * intensity).toInt()

        repeat(flakeCount) {
            val x = rng.nextFloat() * width
            val y = rng.nextFloat() * height
            val radius = rng.nextFloat() * 5f + 1.5f
            val alpha = (rng.nextFloat() * 150 + 100).toInt()
            snowPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(x, y, radius, snowPaint)
        }
    }

    private fun darkenCanvas(canvas: Canvas, width: Int, height: Int, alpha: Int) {
        overlayPaint.color = Color.argb(alpha, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
    }

    private fun drawWhiteHaze(canvas: Canvas, width: Int, height: Int, alpha: Int) {
        overlayPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
    }

    private fun drawFog(canvas: Canvas, width: Int, height: Int) {
        val gradient = LinearGradient(
            0f, height * 0.3f, 0f, height.toFloat(),
            Color.argb(180, 200, 200, 210),
            Color.argb(40, 200, 200, 210),
            Shader.TileMode.CLAMP
        )
        overlayPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        overlayPaint.shader = null
        drawWhiteHaze(canvas, width, height, 60)
    }

    private fun drawSunGlow(canvas: Canvas, width: Int, height: Int) {
        val gradient = LinearGradient(
            0f, 0f, 0f, height * 0.4f,
            Color.argb(50, 255, 220, 100),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        overlayPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.5f, overlayPaint)
        overlayPaint.shader = null
    }

    private fun drawStars(canvas: Canvas, width: Int, height: Int, seed: Long) {
        val rng = Random(seed / 3600)
        val starCount = (width * height / 4000)
        snowPaint.color = Color.WHITE
        repeat(starCount) {
            val x = rng.nextFloat() * width
            val y = rng.nextFloat() * height * 0.7f
            val r = rng.nextFloat() * 1.5f + 0.5f
            snowPaint.alpha = (rng.nextFloat() * 180 + 75).toInt()
            canvas.drawCircle(x, y, r, snowPaint)
        }
    }

    private fun drawLightning(canvas: Canvas, width: Int, height: Int, seed: Long) {
        if (seed % 5 != 0L) return
        val rng = Random(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 180)
            strokeWidth = 3f
        }
        val startX = rng.nextFloat() * width
        var x = startX
        var y = 0f
        while (y < height * 0.6f) {
            val nx = x + (rng.nextFloat() - 0.5f) * 40f
            val ny = y + rng.nextFloat() * 40f + 20f
            canvas.drawLine(x, y, nx, ny, paint)
            x = nx; y = ny
        }
    }
}
