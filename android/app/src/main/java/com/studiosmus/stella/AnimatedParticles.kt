package com.studiosmus.stella

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

// ─── fBm flow field with domain warping ──────────────────────────────────────
// Shared infrastructure for all particle systems. A 28×14 grid of (vx,vy)
// vectors is built from Value Noise + fBm + domain warp and advanced each
// frame at a slow time rate. All systems bilinearly interpolate from the grid,
// giving spatially coherent gusts without per-particle noise eval overhead.

private class FlowField(private val w: Int, private val h: Int, private val seed: Float = 0f) {
    private val C = 28; private val R = 14
    private val vx = FloatArray(C * R)
    private val vy = FloatArray(C * R)
    private var t  = seed * 3.7f

    fun advance(dt: Float) {
        t += dt * 0.09f
        for (r in 0 until R) for (c in 0 until C) {
            val nx = c.toFloat() / C * 3.8f + t * 0.55f
            val ny = r.toFloat() / R * 3.8f + t * 0.20f + seed
            val wx = fbm(nx + 1.70f, ny + 9.20f, 3) * 0.50f
            val wy = fbm(nx + 8.30f, ny + 2.80f, 3) * 0.30f
            val i  = r * C + c
            vx[i]  = fbm(nx + wx,       ny + wy,       3) * 2f - 1f   // [-1, 1]
            vy[i]  = (fbm(nx + wx + 4f, ny + wy + 2f, 2) * 2f - 1f) * 0.20f
        }
    }

    fun vx(x: Float, y: Float) = bil(vx, x / w, y / h)
    fun vy(x: Float, y: Float) = bil(vy, x / w, y / h)

    private fun bil(f: FloatArray, u: Float, v: Float): Float {
        val cu = (u * (C - 1)).coerceIn(0f, C - 1.001f)
        val cv = (v * (R - 1)).coerceIn(0f, R - 1.001f)
        val ic = cu.toInt(); val ir = cv.toInt()
        val fu = cu - ic; val fv = cv - ir
        val i  = ir * C + ic
        return (f[i] * (1 - fu) + f[i + 1] * fu) * (1 - fv) +
               (f[i + C] * (1 - fu) + f[i + C + 1] * fu) * fv
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 xor b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF
    }
    private fun vn(x: Float, y: Float): Float {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        val fx = x - ix; val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx); val uy = fy * fy * (3f - 2f * fy)
        return (hash(ix, iy) * (1 - ux) + hash(ix + 1, iy) * ux) * (1 - uy) +
               (hash(ix, iy + 1) * (1 - ux) + hash(ix + 1, iy + 1) * ux) * uy
    }
    private fun fbm(x: Float, y: Float, oct: Int): Float {
        var v = 0f; var a = 0.5f; var f = 1f
        repeat(oct) { v += vn(x * f, y * f) * a; a *= 0.5f; f *= 2f }
        return v
    }
}

// ─── Rain: 3 depth layers, fall angle warped by fBm flow field ───────────────

class RainSystem(private val w: Int, private val h: Int) {
    private val field = FlowField(w, h, seed = 0f)

    private inner class Layer(
        count: Int, speedMin: Float, speedMax: Float,
        lenMin: Float, lenMax: Float,
        val stroke: Float, val alphaMin: Int, val alphaMax: Int,
        val windInfluence: Float
    ) {
        val x  = FloatArray(count) { Random.nextFloat() * (w + 120f) - 60f }
        val y  = FloatArray(count) { Random.nextFloat() * h }
        val sp = FloatArray(count) { Random.nextFloat() * (speedMax - speedMin) + speedMin }
        val ln = FloatArray(count) { Random.nextFloat() * (lenMax - lenMin) + lenMin }
        // Per-drop effective x-direction; smoothly tracks the fBm wind field
        val dx = FloatArray(count) { BASE_SIN }
        val n  = count
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = stroke }

        fun update(dt: Float, mult: Float) {
            for (i in 0 until n) {
                val wind   = field.vx(x[i], y[i]) * windInfluence
                val target = BASE_SIN + wind
                val k      = (dt * 3.5f).coerceIn(0f, 1f)
                dx[i] += (target - dx[i]) * k          // 1st-order low-pass
                y[i]  += sp[i] * dt * mult * BASE_COS
                x[i]  += sp[i] * dt * mult * dx[i]
                if (y[i] > h + ln[i]) {
                    y[i] = -ln[i] - Random.nextFloat() * 60f
                    x[i] = Random.nextFloat() * (w + 100f) - 50f
                    dx[i] = BASE_SIN
                }
            }
        }

        fun draw(canvas: Canvas, intensity: Float) {
            val draw = (n * intensity).toInt()
            for (i in 0 until draw) {
                val a = (alphaMin + ((y[i] * 0.04f).toInt() and (alphaMax - alphaMin)))
                    .coerceIn(alphaMin, alphaMax)
                paint.color = Color.argb(a, 190, 220, 255)
                // Trail points opposite to direction of travel
                canvas.drawLine(x[i], y[i],
                    x[i] - dx[i] * ln[i],
                    y[i] - BASE_COS * ln[i],
                    paint)
            }
        }
    }

    companion object {
        private val BASE_SIN = sin(Math.toRadians(20.0)).toFloat()
        private val BASE_COS = cos(Math.toRadians(20.0)).toFloat()
    }

    private val far  = Layer(w / 6, 300f,  500f,  8f, 16f, 1.0f, 40,  80,  0.10f)
    private val mid  = Layer(w / 4, 600f,  900f, 22f, 42f, 1.6f, 100, 155, 0.20f)
    private val near = Layer(w / 5, 1000f, 1600f, 45f, 80f, 2.2f, 160, 220, 0.32f)

    fun update(dt: Float, speedMult: Float = 1f) {
        field.advance(dt)
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

// ─── Snow: 3 depth layers, drift fully driven by fBm flow field ──────────────

class SnowSystem(private val w: Int, private val h: Int) {
    private val field = FlowField(w, h, seed = 17.3f)

    private inner class SnowLayer(
        count: Int, speedMin: Float, speedMax: Float,
        rMin: Float, rMax: Float,
        val alphaMin: Int, val alphaMax: Int,
        val windScale: Float
    ) {
        val x  = FloatArray(count) { Random.nextFloat() * w }
        val y  = FloatArray(count) { Random.nextFloat() * h }
        val sp = FloatArray(count) { Random.nextFloat() * (speedMax - speedMin) + speedMin }
        val r  = FloatArray(count) { Random.nextFloat() * (rMax - rMin) + rMin }
        // Smoothed wind velocity per flake
        val vx = FloatArray(count) { 0f }
        val vy = FloatArray(count) { 0f }
        val n  = count
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun update(dt: Float) {
            for (i in 0 until n) {
                val tx = field.vx(x[i], y[i]) * windScale
                val ty = field.vy(x[i], y[i]) * windScale * 0.40f
                val k  = (dt * 2.0f).coerceIn(0f, 1f)
                vx[i] += (tx - vx[i]) * k
                vy[i] += (ty - vy[i]) * k
                y[i]  += (sp[i] + vy[i]) * dt
                x[i]  += vx[i] * dt
                // Wrap horizontally so flakes blown off-screen reappear on the other side
                if (x[i] < -r[i])       x[i] = w + r[i]
                if (x[i] > w + r[i])    x[i] = -r[i]
                if (y[i] > h + r[i]) { y[i] = -r[i]; x[i] = Random.nextFloat() * w }
            }
        }

        fun draw(canvas: Canvas, intensity: Float) {
            val draw = (n * intensity).toInt().coerceAtMost(n)
            for (i in 0 until draw) {
                val a = (alphaMin + ((y[i] * 0.03f).toInt() and (alphaMax - alphaMin)))
                    .coerceIn(alphaMin, alphaMax)
                paint.color = Color.argb(a, 240, 245, 255)
                canvas.drawCircle(x[i], y[i], r[i], paint)
            }
        }
    }

    private val far  = SnowLayer(w * h / 40000, 25f, 50f, 1f, 2.5f, 50,  100, 28f)
    private val mid  = SnowLayer(w * h / 20000, 45f, 80f, 2f, 4f,   110, 175, 50f)
    private val near = SnowLayer(w * h / 30000, 70f, 120f, 4f, 7f,  160, 230, 75f)

    fun update(dt: Float) {
        field.advance(dt)
        far.update(dt)
        mid.update(dt)
        near.update(dt)
    }

    fun draw(canvas: Canvas, intensity: Float) {
        far.draw(canvas, intensity)
        mid.draw(canvas, intensity)
        near.draw(canvas, intensity * 0.6f)
    }
}

// ─── Fog: scrolling layers with fBm-modulated opacity ────────────────────────

class FogSystem(private val w: Int, private val h: Int) {
    private val field = FlowField(w, h, seed = 33.7f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t     = 0f

    fun update(dt: Float) {
        t += dt
        field.advance(dt)
    }

    fun draw(canvas: Canvas, density: Float) {
        for (i in 0..2) {
            val speed  = (0.38f + i * 0.14f) * 65f     // px/s per layer
            val offset = t * speed % w
            val yFrac  = 0.18f + i * 0.27f
            val baseA  = (density * (52 + i * 22)).toInt()
            val strips = 24
            val sw     = w.toFloat() / strips

            // Extra strip on each side avoids gaps during scrolling
            for (s in -1..strips) {
                val texX = s.toFloat() / strips * w
                val sx   = texX - offset
                // Noise coordinate tied to texture space → pattern moves with fog
                val cx   = ((texX + t * speed * 0.5f) % w + w) % w
                val nv   = (field.vx(cx, h * (yFrac + 0.15f)) * 0.5f + 0.5f).coerceIn(0.12f, 1f)
                val a    = (baseA * nv).toInt().coerceIn(8, 130)
                paint.color = Color.argb(a, 210, 215, 225)
                canvas.drawRect(sx, h * yFrac, sx + sw + 1f, h * (yFrac + 0.38f), paint)
            }
        }
    }
}

// ─── Hail: icy pellets, angle + speed lightly warped by fBm ─────────────────

class HailSystem(private val w: Int, private val h: Int) {
    private val field = FlowField(w, h, seed = 52.1f)

    private inner class HailLayer(
        count: Int, speedMin: Float, speedMax: Float,
        rMin: Float, rMax: Float,
        val alphaMin: Int, val alphaMax: Int,
        windDeg: Double,
        val windInfluence: Float
    ) {
        val x      = FloatArray(count) { Random.nextFloat() * w }
        val y      = FloatArray(count) { Random.nextFloat() * h }
        val speed  = FloatArray(count) { Random.nextFloat() * (speedMax - speedMin) + speedMin }
        val radius = FloatArray(count) { Random.nextFloat() * (rMax - rMin) + rMin }
        val alpha  = IntArray(count)   { alphaMin + Random.nextInt((alphaMax - alphaMin).coerceAtLeast(1)) }
        val dx     = FloatArray(count) { sin(Math.toRadians(windDeg)).toFloat() }
        val n      = count
        val baseDx = sin(Math.toRadians(windDeg)).toFloat()
        val baseDy = cos(Math.toRadians(windDeg)).toFloat()
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
        val shine  = Paint(Paint.ANTI_ALIAS_FLAG)

        fun update(dt: Float, mult: Float) {
            for (i in 0 until n) {
                val wind   = field.vx(x[i], y[i]) * windInfluence
                val target = baseDx + wind
                dx[i] += (target - dx[i]) * (dt * 3f).coerceIn(0f, 1f)
                y[i]  += speed[i] * dt * mult * baseDy
                x[i]  += speed[i] * dt * mult * dx[i]
                if (y[i] > h + radius[i]) {
                    y[i] = -radius[i]
                    x[i] = Random.nextFloat() * w
                }
            }
        }

        fun draw(canvas: Canvas, intensity: Float) {
            val draw = (n * intensity).toInt().coerceAtMost(n)
            for (i in 0 until draw) {
                paint.color = Color.argb(alpha[i], 195, 215, 240)
                canvas.drawCircle(x[i], y[i], radius[i], paint)
                if (radius[i] > 3.5f) {
                    shine.color = Color.argb(alpha[i] / 3, 255, 255, 255)
                    canvas.drawCircle(x[i] - radius[i] * 0.30f, y[i] - radius[i] * 0.32f,
                        radius[i] * 0.26f, shine)
                }
            }
        }
    }

    private val far  = HailLayer(w /  9,  650f,  1100f, 2f,   4.5f,  75, 135, 12.0, 0.06f)
    private val mid  = HailLayer(w /  6, 1150f,  1800f, 4f,   7.5f, 145, 205, 12.0, 0.10f)
    private val near = HailLayer(w /  9, 1600f,  2600f, 8f,  13f,   200, 255, 12.0, 0.14f)

    fun update(dt: Float, speedMult: Float = 1f) {
        field.advance(dt)
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
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3.5f }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 18f }
    private var lastBoltTime = 0L
    private var boltSeed     = 0L
    private var boltCount    = 1

    /** minMs/maxMs = intervallo tra i fulmini; bolts = quanti scoccare insieme */
    fun maybeStrike(minMs: Long = 3000, maxMs: Long = 8000, bolts: Int = 1): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastBoltTime > Random.nextLong(minMs, maxMs)) {
            lastBoltTime = now
            boltSeed     = now
            boltCount    = bolts
            return true
        }
        return false
    }

    fun draw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val age = now - lastBoltTime
        if (age > 450) return   // fulmine visibile più a lungo

        val alpha = (1f - age / 450f).coerceIn(0f, 1f)
        // Flash luminoso sullo schermo
        canvas.drawColor(Color.argb((75 * alpha).toInt(), 255, 255, 225))

        val rng = Random(boltSeed)
        repeat(boltCount) {
            drawBolt(canvas, rng, alpha,
                w * (0.15f + rng.nextFloat() * 0.70f), 0f,
                w * (0.25f + rng.nextFloat() * 0.50f), h * 0.70f, 5)
        }
    }

    private fun drawBolt(
        canvas: Canvas, rng: Random, alpha: Float,
        x1: Float, y1: Float, x2: Float, y2: Float, depth: Int
    ) {
        if (depth == 0) return
        val mx = (x1 + x2) / 2f + (rng.nextFloat() - 0.5f) * 95f
        val my = (y1 + y2) / 2f + (rng.nextFloat() - 0.5f) * 28f
        glowPaint.color = Color.argb((55 * alpha).toInt(), 210, 230, 255)
        canvas.drawLine(x1, y1, mx, my, glowPaint)
        canvas.drawLine(mx, my, x2, y2, glowPaint)
        boltPaint.color = Color.argb((245 * alpha).toInt(), 255, 255, 210)
        canvas.drawLine(x1, y1, mx, my, boltPaint)
        canvas.drawLine(mx, my, x2, y2, boltPaint)
        drawBolt(canvas, rng, alpha, x1, y1, mx, my, depth - 1)
        drawBolt(canvas, rng, alpha, mx, my, x2, y2, depth - 1)
        // Più rami secondari
        if (rng.nextFloat() > 0.42f && depth > 1) {
            val bx = mx + (rng.nextFloat() - 0.3f) * 160f
            val by = my + rng.nextFloat() * 200f
            boltPaint.color = Color.argb((150 * alpha).toInt(), 255, 255, 210)
            boltPaint.strokeWidth = 2f
            canvas.drawLine(mx, my, bx, by, boltPaint)
            boltPaint.strokeWidth = 3.5f
        }
    }
}
