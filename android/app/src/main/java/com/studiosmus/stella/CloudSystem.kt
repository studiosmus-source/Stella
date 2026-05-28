package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class CloudSystem(private val w: Int, private val h: Int) {

    private class Cloud(
        var cx: Float,
        val cy: Float,
        val circles: FloatArray,   // [dx, dy, r, dx, dy, r ...]
        val speed: Float,
        val baseAlpha: Int,
        val layer: Int
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clouds = mutableListOf<Cloud>()

    init {
        repeat(7) { addCloud(0, null) }
        repeat(5) { addCloud(1, null) }
        repeat(3) { addCloud(2, null) }
    }

    private fun addCloud(layer: Int, forcedX: Float?) {
        val rng = Random
        val baseR: Float; val cy: Float; val speed: Float; val alpha: Int
        when (layer) {
            0 -> { baseR = w * (0.06f + rng.nextFloat() * 0.09f)
                   cy = h * (0.03f + rng.nextFloat() * 0.10f)
                   speed = 18f + rng.nextFloat() * 18f; alpha = 30 + (rng.nextFloat() * 30).toInt() }
            1 -> { baseR = w * (0.12f + rng.nextFloat() * 0.16f)
                   cy = h * (0.04f + rng.nextFloat() * 0.15f)
                   speed = 7f + rng.nextFloat() * 9f; alpha = 65 + (rng.nextFloat() * 50).toInt() }
            else -> { baseR = w * (0.20f + rng.nextFloat() * 0.22f)
                      cy = h * (0.04f + rng.nextFloat() * 0.18f)
                      speed = 3f + rng.nextFloat() * 4f; alpha = 100 + (rng.nextFloat() * 60).toInt() }
        }

        // Row of overlapping circles → natural cloud bump shape
        val n = 4 + rng.nextInt(4)
        val circles = FloatArray(n * 3)
        var x = 0f
        for (i in 0 until n) {
            val r = baseR * (0.45f + rng.nextFloat() * 0.55f)
            circles[i * 3]     = x
            circles[i * 3 + 1] = rng.nextFloat() * r * 0.25f - r * 0.1f
            circles[i * 3 + 2] = r
            x += r * (0.75f + rng.nextFloat() * 0.4f)
        }
        // Center horizontally
        val shift = x / 2f
        for (i in 0 until n) circles[i * 3] -= shift

        val cx = forcedX ?: ((-x) + rng.nextFloat() * (w + x))
        clouds.add(Cloud(cx, cy, circles, speed, alpha, layer))
    }

    fun update(dt: Float) {
        for (c in clouds) {
            c.cx += c.speed * dt
            val halfW = (c.circles.filterIndexed { i, _ -> i % 3 == 2 }.maxOrNull() ?: 200f) * 2f
            if (c.cx - halfW > w) c.cx = -halfW
        }
    }

    fun draw(canvas: Canvas, density: Float, stormLevel: Float = 0f) {
        if (density <= 0f) return
        val n = clouds.size
        for (i in 0 until n) {
            val c = clouds[i]
            val alpha = (c.baseAlpha * density).toInt().coerceIn(0, 255)
            if (alpha < 4) continue
            val grey = (238 - stormLevel * 170).toInt().coerceIn(18, 238)
            paint.color = Color.argb(alpha, grey, grey + 1, (grey + 10).coerceAtMost(255))
            val nc = c.circles.size / 3
            for (j in 0 until nc) {
                canvas.drawCircle(
                    c.cx + c.circles[j * 3],
                    c.cy + c.circles[j * 3 + 1],
                    c.circles[j * 3 + 2],
                    paint
                )
            }
        }
    }
}
