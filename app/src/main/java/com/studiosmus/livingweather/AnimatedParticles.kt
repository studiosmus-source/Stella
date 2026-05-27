package com.studiosmus.livingweather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class RainSystem(private val w: Int, private val h: Int) {
    private val count = (w * 0.35f).toInt().coerceIn(60, 400)
    private val x = FloatArray(count) { Random.nextFloat() * (w + 100f) - 50f }
    private val y = FloatArray(count) { Random.nextFloat() * h }
    private val speed = FloatArray(count) { Random.nextFloat() * 500f + 350f }
    private val len = FloatArray(count) { Random.nextFloat() * 28f + 10f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.6f }

    private val sinA = sin(Math.toRadians(22.0)).toFloat()
    private val cosA = cos(Math.toRadians(22.0)).toFloat()

    fun update(dt: Float, speedMult: Float = 1f) {
        for (i in 0 until count) {
            val s = speed[i] * dt * speedMult
            y[i] += s * cosA
            x[i] += s * sinA
            if (y[i] > h + len[i]) {
                y[i] = -len[i]
                x[i] = Random.nextFloat() * (w + 50f) - 25f
            }
        }
    }

    fun draw(canvas: Canvas, intensity: Float) {
        val n = (count * intensity).toInt()
        for (i in 0 until n) {
            paint.color = Color.argb((90 + (i % 90)).coerceAtMost(200), 170, 215, 245)
            canvas.drawLine(x[i], y[i], x[i] + len[i] * sinA, y[i] + len[i] * cosA, paint)
        }
    }
}

class SnowSystem(private val w: Int, private val h: Int) {
    private val count = (w * h / 3500).coerceIn(40, 300)
    private val x = FloatArray(count) { Random.nextFloat() * w }
    private val y = FloatArray(count) { Random.nextFloat() * h }
    private val speed = FloatArray(count) { Random.nextFloat() * 65f + 30f }
    private val radius = FloatArray(count) { Random.nextFloat() * 4.5f + 1f }
    private val sway = FloatArray(count) { (Random.nextFloat() - 0.5f) * 18f }
    private var time = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun update(dt: Float) {
        time += dt
        for (i in 0 until count) {
            y[i] += speed[i] * dt
            x[i] += sin(time * 0.7f + i * 0.4f).toFloat() * sway[i] * dt
            if (y[i] > h + radius[i]) {
                y[i] = -radius[i]
                x[i] = Random.nextFloat() * w
            }
        }
    }

    fun draw(canvas: Canvas, intensity: Float) {
        val n = (count * intensity).toInt()
        for (i in 0 until n) {
            val alpha = (130 + (i % 100)).coerceAtMost(230)
            paint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(x[i], y[i], radius[i], paint)
        }
    }
}
