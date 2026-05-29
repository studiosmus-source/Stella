package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─── Rain: 3 depth layers ────────────────────────────────────────────────────

class RainSystem(private val w: Int, private val h: Int) {

    private inner class Layer(
        count: Int,
        speedMin: Float, speedMax: Float,
        lenMin: Float, lenMax: Float,
        val stroke: Float,
        val alphaMin: Int, val alphaMax: Int
    ) {
        val x = FloatArray(count) { Random.nextFloat() * (w + 120f) - 60f }
        val y = FloatArray(count) { Random.nextFloat() * h }
        val speed = FloatArray(count) { Random.nextFloat() * (speedMax - speedMin) + speedMin }
        val len = FloatArray(count) { Random.nextFloat() * (lenMax - lenMin) + lenMin }
        val count = count
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = stroke }

        fun update(dt: Float, mult: Float) {
            for (i in 0 until count) {
                y[i] += speed[i] * dt * mult * cosA
                x[i] += speed[i] * dt * mult * sinA
                if (y[i] > h + len[i]) {
                    y[i] = -len[i] - Random.nextFloat() * 60f
                    x[i] = Random.nextFloat() * (w + 100f) - 50f
                }
            }
        }

        fun draw(canvas: Canvas, intensity: Float) {
            val n = (count * intensity).toInt()
            for (i in 0 until n) {
                val alpha = alphaMin + ((y[i] * 0.04f).toInt() and (alphaMax - alphaMin))
                paint.color = Color.argb(alpha.coerceIn(alphaMin, alphaMax), 190, 220, 255)
                canvas.drawLine(x[i], y[i], x[i] + len[i] * sinA, y[i] + len[i] * cosA, paint)
            }
        }
    }

    private val sinA = sin(Math.toRadians(20.0)).toFloat()
    private val cosA = cos(Math.toRadians(20.0)).toFloat()

    private val far = Layer(w / 6, 300f, 500f, 8f, 16f, 1.0f, 40, 80)
    private val mid = Layer(w / 4, 600f, 900f, 22f, 42f, 1.6f, 100, 155)
    private val near = Layer(w / 5, 1000f, 1600f, 45f, 80f, 2.2f, 160, 220)

    fun update(dt: Float, speedMult: Float = 1f) {
        far.update(dt, speedMult * 0.35f)
        mid.update(dt, speedMult * 0.65f)
        near.update(dt, speedMult)
    }

    fun draw(canvas: Canvas, intensity: Float) {
        far.draw(canvas, intensity)
        mid.draw(canvas, intensity)
        near.draw(canvas, intensity * 0.75f)
    }
}

// ─── Snow: 3 depth layers ────────────────────────────────────────────────────

class SnowSystem(private val w: Int, private val h: Int) {

    private inner class SnowLayer(
        count: Int,
        speedMin: Float, speedMax: Float,
        rMin: Float, rMax: Float,
        val alphaMin: Int, val alphaMax: Int,
        val swayScale: Float
    ) {
        val x = FloatArray(count) { Random.nextFloat() * w }
        val y = FloatArray(count) { Random.nextFloat() * h }
        val speed = FloatArray(count) { Random.nextFloat() * (speedMax - speedMin) + speedMin }
        val radius = FloatArray(count) { Random.nextFloat() * (rMax - rMin) + rMin }
        val phase = FloatArray(count) { Random.nextFloat() * 6.28f }
        val count = count
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun update(dt: Float, time: Float) {
            for (i in 0 until count) {
                y[i] += speed[i] * dt
                x[i] += sin(time * 0.6f + phase[i]).toFloat() * swayScale * dt
                if (y[i] > h + radius[i]) {
                    y[i] = -radius[i]
                    x[i] = Random.nextFloat() * w
                }
            }
        }

        fun draw(canvas: Canvas, intensity: Float) {
            val n = (count * intensity).toInt()
            for (i in 0 until n) {
                val alpha = (alphaMin + ((y[i] * 0.03f).toInt() and (alphaMax - alphaMin)))
                    .coerceIn(alphaMin, alphaMax)
                paint.color = Color.argb(alpha, 240, 245, 255)
                canvas.drawCircle(x[i], y[i], radius[i], paint)
            }
        }
    }

    private var time = 0f
    private val far = SnowLayer(w * h / 40000, 25f, 50f, 1f, 2.5f, 50, 100, 8f)
    private val mid = SnowLayer(w * h / 20000, 45f, 80f, 2f, 4f, 110, 175, 14f)
    private val near = SnowLayer(w * h / 30000, 70f, 120f, 4f, 7f, 160, 230, 20f)

    fun update(dt: Float) {
        time += dt
        far.update(dt, time)
        mid.update(dt, time)
        near.update(dt, time)
    }

    fun draw(canvas: Canvas, intensity: Float) {
        far.draw(canvas, intensity)
        mid.draw(canvas, intensity)
        near.draw(canvas, intensity * 0.6f)
    }
}

// ─── Fog: animated drifting layers ───────────────────────────────────────────

class FogSystem(private val w: Int, private val h: Int) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var time = 0f

    fun update(dt: Float) { time += dt * 0.08f }

    fun draw(canvas: Canvas, density: Float) {
        for (i in 0..2) {
            val offset = (time * (0.4f + i * 0.15f) * w) % w
            val yFrac = 0.2f + i * 0.25f
            val alpha = (density * (55 + i * 20)).toInt().coerceIn(20, 120)
            paint.color = Color.argb(alpha, 210, 215, 225)
            canvas.drawRect(offset - w, h * yFrac, offset, h * (yFrac + 0.35f), paint)
            canvas.drawRect(offset, h * yFrac, offset + w, h * (yFrac + 0.35f), paint)
        }
    }
}

// ─── Hail: icy pellets, 3 depth layers ──────────────────────────────────────

class HailSystem(private val w: Int, private val h: Int) {

    private inner class HailLayer(
        count: Int,
        speedMin: Float, speedMax: Float,
        rMin: Float, rMax: Float,
        val alphaMin: Int, val alphaMax: Int,
        windDeg: Double
    ) {
        val x      = FloatArray(count) { Random.nextFloat() * w }
        val y      = FloatArray(count) { Random.nextFloat() * h }
        val speed  = FloatArray(count) { Random.nextFloat() * (speedMax - speedMin) + speedMin }
        val radius = FloatArray(count) { Random.nextFloat() * (rMax - rMin) + rMin }
        val alpha  = IntArray(count) { alphaMin + Random.nextInt((alphaMax - alphaMin).coerceAtLeast(1)) }
        val n      = count
        val sinA   = sin(Math.toRadians(windDeg)).toFloat()
        val cosA   = cos(Math.toRadians(windDeg)).toFloat()
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
        val shine  = Paint(Paint.ANTI_ALIAS_FLAG)

        fun update(dt: Float, mult: Float) {
            for (i in 0 until n) {
                y[i] += speed[i] * dt * mult * cosA
                x[i] += speed[i] * dt * mult * sinA
                if (y[i] > h + radius[i]) {
                    y[i] = -radius[i]
                    x[i] = Random.nextFloat() * w
                }
            }
        }

        fun draw(canvas: Canvas, intensity: Float) {
            val draw = (n * intensity).toInt().coerceAtMost(n)
            for (i in 0 until draw) {
                // Ice pellet body: cool blue-white
                paint.color = Color.argb(alpha[i], 195, 215, 240)
                canvas.drawCircle(x[i], y[i], radius[i], paint)
                // Specular highlight on larger pellets
                if (radius[i] > 3.5f) {
                    shine.color = Color.argb(alpha[i] / 3, 255, 255, 255)
                    canvas.drawCircle(
                        x[i] - radius[i] * 0.30f, y[i] - radius[i] * 0.32f,
                        radius[i] * 0.26f, shine
                    )
                }
            }
        }
    }

    // Near-vertical fall (10° wind), three depth layers
    private val far  = HailLayer(w / 11, 400f,  750f, 1.5f, 3f,  60, 115, 10.0)
    private val mid  = HailLayer(w /  8, 750f, 1200f, 3f,   5.5f, 130, 185, 10.0)
    private val near = HailLayer(w / 13, 1100f, 1700f, 5.5f, 9f, 185, 240, 10.0)

    fun update(dt: Float, speedMult: Float = 1f) {
        far.update(dt, speedMult * 0.38f)
        mid.update(dt, speedMult * 0.68f)
        near.update(dt, speedMult)
    }

    fun draw(canvas: Canvas, intensity: Float) {
        far.draw(canvas, intensity)
        mid.draw(canvas, intensity)
        near.draw(canvas, intensity * 0.72f)
    }
}

// ─── Lightning with branching glow ───────────────────────────────────────────

class LightningSystem(private val w: Int, private val h: Int) {
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 12f }
    private var lastBoltTime = 0L
    private var boltSeed = 0L

    fun maybeStrike(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastBoltTime > Random.nextLong(3000, 8000)) {
            lastBoltTime = now
            boltSeed = now
            return true
        }
        return false
    }

    fun draw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val age = now - lastBoltTime
        if (age > 300) return

        val alpha = (1f - age / 300f).coerceIn(0f, 1f)
        canvas.drawColor(Color.argb((40 * alpha).toInt(), 255, 255, 210))

        val rng = Random(boltSeed)
        drawBolt(canvas, rng, alpha,
            w * (0.2f + rng.nextFloat() * 0.6f), 0f,
            w * (0.3f + rng.nextFloat() * 0.4f), h * 0.6f,
            4)
    }

    private fun drawBolt(
        canvas: Canvas, rng: Random, alpha: Float,
        x1: Float, y1: Float, x2: Float, y2: Float,
        depth: Int
    ) {
        if (depth == 0) return
        val mx = (x1 + x2) / 2f + (rng.nextFloat() - 0.5f) * 80f
        val my = (y1 + y2) / 2f + (rng.nextFloat() - 0.5f) * 20f

        glowPaint.color = Color.argb((30 * alpha).toInt(), 200, 220, 255)
        canvas.drawLine(x1, y1, mx, my, glowPaint)
        canvas.drawLine(mx, my, x2, y2, glowPaint)

        boltPaint.color = Color.argb((220 * alpha).toInt(), 255, 255, 200)
        canvas.drawLine(x1, y1, mx, my, boltPaint)
        canvas.drawLine(mx, my, x2, y2, boltPaint)

        drawBolt(canvas, rng, alpha, x1, y1, mx, my, depth - 1)
        drawBolt(canvas, rng, alpha, mx, my, x2, y2, depth - 1)

        if (rng.nextFloat() > 0.55f && depth > 1) {
            val bx = mx + (rng.nextFloat() - 0.3f) * 120f
            val by = my + rng.nextFloat() * 150f
            boltPaint.color = Color.argb((120 * alpha).toInt(), 255, 255, 200)
            boltPaint.strokeWidth = 1.5f
            canvas.drawLine(mx, my, bx, by, boltPaint)
            boltPaint.strokeWidth = 3f
        }
    }
}
