package com.studiosmus.livingweather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class RainSystem(private val w: Int, private val h: Int) {
    private val count = (w * 0.5f).toInt().coerceIn(100, 600)
    private val x = FloatArray(count) { Random.nextFloat() * (w + 120f) - 60f }
    private val y = FloatArray(count) { Random.nextFloat() * h }
    private val speed = FloatArray(count) { Random.nextFloat() * 900f + 700f }  // 700–1600 px/s
    private val len = FloatArray(count) { Random.nextFloat() * 50f + 22f }       // 22–72 px

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.8f }
    private val sinA = sin(Math.toRadians(22.0)).toFloat()
    private val cosA = cos(Math.toRadians(22.0)).toFloat()

    fun update(dt: Float, speedMult: Float = 1f) {
        for (i in 0 until count) {
            val s = speed[i] * dt * speedMult
            y[i] += s * cosA
            x[i] += s * sinA
            if (y[i] > h + len[i]) {
                y[i] = -len[i] - Random.nextFloat() * 40f
                x[i] = Random.nextFloat() * (w + 80f) - 40f
            }
        }
    }

    fun draw(canvas: Canvas, intensity: Float) {
        val n = (count * intensity).toInt()
        for (i in 0 until n) {
            val alpha = (140 + ((y[i] * 0.05f).toInt() and 0x3F)).coerceIn(140, 210)
            paint.color = Color.argb(alpha, 170, 215, 245)
            canvas.drawLine(x[i], y[i], x[i] + len[i] * sinA, y[i] + len[i] * cosA, paint)
        }
    }
}

class SnowSystem(private val w: Int, private val h: Int) {
    private val count = (w * h / 3000).coerceIn(50, 350)
    private val x = FloatArray(count) { Random.nextFloat() * w }
    private val y = FloatArray(count) { Random.nextFloat() * h }
    private val speed = FloatArray(count) { Random.nextFloat() * 90f + 50f }
    private val radius = FloatArray(count) { Random.nextFloat() * 5f + 1.5f }
    private val sway = FloatArray(count) { (Random.nextFloat() - 0.5f) * 22f }
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
            val alpha = (130 + ((y[i] * 0.04f).toInt() and 0x5F)).coerceIn(130, 230)
            paint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(x[i], y[i], radius[i], paint)
        }
    }
}
