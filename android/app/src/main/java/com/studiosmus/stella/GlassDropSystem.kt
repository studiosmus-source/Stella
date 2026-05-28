package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.random.Random

class GlassDropSystem(private val w: Int, private val h: Int) {

    private val dropPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var intensity = 0f
    private var nextSpawn = 0f

    private class StaticDrop(val x: Float, val y: Float, val r: Float, var life: Float = 1f)
    private class RunningDrop(
        var x: Float, var y: Float, val r: Float,
        var vy: Float,
        val trail: MutableList<Float> = mutableListOf(),  // x0,y0,x1,y1...
        var life: Float = 1f
    )

    private val staticDrops  = mutableListOf<StaticDrop>()
    private val runningDrops = mutableListOf<RunningDrop>()
    private val trailPath    = Path()

    fun setIntensity(v: Float) { intensity = v }

    fun update(dt: Float) {
        // Spawn
        if (intensity > 0f) {
            nextSpawn -= dt
            if (nextSpawn <= 0f) {
                spawn()
                nextSpawn = (0.25f + Random.nextFloat() * 0.7f) / intensity
            }
        }

        // Static drops fade
        val fadeRate = if (intensity > 0f) 0.03f else 0.12f
        for (d in staticDrops) d.life -= dt * fadeRate
        staticDrops.removeAll { it.life <= 0f }

        // Running drops
        for (d in runningDrops) {
            // Record trail every ~6px
            val lastY = if (d.trail.size >= 2) d.trail[d.trail.size - 1] else Float.MAX_VALUE
            if (kotlin.math.abs(d.y - lastY) > 6f) {
                d.trail.add(d.x); d.trail.add(d.y)
                if (d.trail.size > 40) { d.trail.removeAt(0); d.trail.removeAt(0) }
            }
            d.vy += 260f * dt
            d.y  += d.vy * dt
            d.x  += (Random.nextFloat() - 0.5f) * 12f * dt
            d.x   = d.x.coerceIn(d.r, w - d.r)
            if (d.y > h + d.r) d.life = 0f
            if (intensity == 0f) d.life -= dt * 0.25f
        }
        runningDrops.removeAll { it.life <= 0f }
    }

    private fun spawn() {
        val rng = Random
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h * 0.65f
        if (rng.nextFloat() < 0.65f) {
            staticDrops.add(StaticDrop(x, y, 1.8f + rng.nextFloat() * 4.5f))
        } else {
            runningDrops.add(RunningDrop(x, y, 4f + rng.nextFloat() * 7f,
                70f + rng.nextFloat() * 100f))
        }
    }

    fun draw(canvas: Canvas) {
        // Static drops
        for (d in staticDrops) {
            val a = (d.life * 65).toInt().coerceIn(0, 65)
            dropPaint.color = Color.argb(a, 200, 222, 245)
            canvas.drawCircle(d.x, d.y, d.r, dropPaint)
            dropPaint.color = Color.argb(a / 2, 255, 255, 255)
            canvas.drawCircle(d.x - d.r * 0.22f, d.y - d.r * 0.28f, d.r * 0.32f, dropPaint)
        }

        // Running drops + trails
        for (d in runningDrops) {
            val a = (d.life * 85).toInt().coerceIn(0, 85)

            // Trail
            if (d.trail.size >= 4) {
                trailPath.reset()
                trailPath.moveTo(d.trail[0], d.trail[1])
                var i = 2; while (i < d.trail.size - 1) {
                    trailPath.lineTo(d.trail[i], d.trail[i + 1]); i += 2
                }
                trailPaint.color = Color.argb(a / 4, 180, 210, 240)
                trailPaint.strokeWidth = d.r * 0.55f
                canvas.drawPath(trailPath, trailPaint)
            }

            // Drop body
            dropPaint.color = Color.argb(a, 185, 215, 245)
            canvas.drawCircle(d.x, d.y, d.r, dropPaint)
            // Specular highlight
            dropPaint.color = Color.argb(a / 2, 255, 255, 255)
            canvas.drawCircle(d.x - d.r * 0.28f, d.y - d.r * 0.28f, d.r * 0.30f, dropPaint)
        }
    }
}
