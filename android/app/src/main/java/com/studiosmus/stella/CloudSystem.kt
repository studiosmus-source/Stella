package com.studiosmus.stella

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

class CloudSystem(private val w: Int, private val h: Int) {

    private class Cloud(
        var cx: Float,
        val cy: Float,
        val bmp: Bitmap,
        val halfW: Float,
        val halfH: Float,
        val speed: Float,
        val layer: Int
    )

    private val drawPaint = Paint()
    private val clouds = mutableListOf<Cloud>()

    init {
        repeat(5) { addCloud(0, null) }
        repeat(4) { addCloud(1, null) }
        repeat(3) { addCloud(2, null) }
    }

    private fun addCloud(layer: Int, forcedX: Float?) {
        val rng = Random
        val bmp = buildBitmap(layer, rng)
        val cy = when (layer) {
            0 -> h * (0.03f + rng.nextFloat() * 0.10f)
            1 -> h * (0.04f + rng.nextFloat() * 0.17f)
            else -> h * (0.05f + rng.nextFloat() * 0.20f)
        }
        val speed = when (layer) {
            0 -> 16f + rng.nextFloat() * 22f
            1 -> 6f  + rng.nextFloat() * 9f
            else -> 2f  + rng.nextFloat() * 4f
        }
        val cx = forcedX ?: ((-bmp.width * 0.5f) + rng.nextFloat() * (w + bmp.width))
        clouds.add(Cloud(cx, cy, bmp, bmp.width / 2f, bmp.height * 0.6f, speed, layer))
    }

    // ─── Pre-render a cloud to a Bitmap using RadialGradient circles ─────────

    private fun buildBitmap(layer: Int, rng: Random): Bitmap {
        val maxR = when (layer) {
            0 -> w * 0.13f          // cirrus: wide, very flat
            1 -> w * 0.22f          // cumulus: puffy
            else -> w * 0.28f       // stratus/storm: wide, thick
        }

        // Build puff list  [cx, cy, r]
        val puffs = mutableListOf<FloatArray>()

        if (layer == 0) {
            // Cirrus: horizontal streak of small wispy puffs
            val n = 4 + rng.nextInt(4)
            var x = 0f
            repeat(n) {
                val r = maxR * (0.12f + rng.nextFloat() * 0.20f)
                puffs.add(floatArrayOf(x, rng.nextFloat() * r * 0.25f, r))
                x += r * (1.1f + rng.nextFloat() * 0.5f)
            }
        } else {
            // Cumulus / stratus: row of main puffs + top bumps
            val n = 4 + rng.nextInt(4)
            var x = 0f
            val mainRow = mutableListOf<FloatArray>()
            repeat(n) {
                val r = maxR * (0.50f + rng.nextFloat() * 0.55f)
                val p = floatArrayOf(x, 0f, r)
                mainRow.add(p); puffs.add(p)
                x += r * (0.72f + rng.nextFloat() * 0.38f)
            }
            // Top bumps
            repeat((n * 0.6).toInt()) {
                val parent = mainRow[rng.nextInt(mainRow.size)]
                val r = parent[2] * (0.25f + rng.nextFloat() * 0.35f)
                puffs.add(floatArrayOf(
                    parent[0] + (rng.nextFloat() - 0.5f) * parent[2] * 1.1f,
                    parent[1] - parent[2] * (0.40f + rng.nextFloat() * 0.30f) - r * 0.2f,
                    r
                ))
            }
        }

        // Bounds
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE
        var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (p in puffs) {
            x0 = minOf(x0, p[0] - p[2]); x1 = maxOf(x1, p[0] + p[2])
            y0 = minOf(y0, p[1] - p[2]); y1 = maxOf(y1, p[1] + p[2])
        }
        val pad = 8f
        val bmpW = ((x1 - x0) + pad * 2).toInt().coerceIn(2, 2048)
        val bmpH = if (layer == 0) {
            // Cirrus: very thin — just a fraction of the circle height
            ((y1 - y0) * 0.28f + pad * 2).toInt().coerceIn(2, 200)
        } else {
            ((y1 - y0) + pad * 2).toInt().coerceIn(2, 1024)
        }

        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)   // software canvas — RadialGradient always works here
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Layer-specific colours
        val shadowA: Int; val shadowGrey: Int
        val mainA: Int; val mainR: Int; val mainG: Int; val mainB: Int
        when (layer) {
            0 -> { shadowA = 40; shadowGrey = 230; mainA = 70;  mainR = 255; mainG = 255; mainB = 255 }
            1 -> { shadowA = 85; shadowGrey = 175; mainA = 200; mainR = 250; mainG = 252; mainB = 255 }
            else -> { shadowA = 140; shadowGrey = 105; mainA = 175; mainR = 148; mainG = 152; mainB = 168 }
        }

        for (pass in 0..1) {
            for (puff in puffs) {
                val bx = puff[0] - x0 + pad
                // Shadow pass: shift down slightly; main pass: at actual Y
                val by = puff[1] - y0 + pad + (if (pass == 0) puff[2] * 0.13f else 0f)
                val r = puff[2]

                if (layer == 0) {
                    // Squash vertically to mimic wispy cirrus
                    c.save()
                    c.scale(1f, 0.24f, bx, by)
                    p.shader = RadialGradient(bx, by, r,
                        if (pass == 0) Color.argb(shadowA, shadowGrey, shadowGrey, shadowGrey + 5)
                        else           Color.argb(mainA, mainR, mainG, mainB),
                        Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    c.drawCircle(bx, by, r, p)
                    c.restore()
                } else {
                    p.shader = RadialGradient(bx, by, r,
                        if (pass == 0) Color.argb(shadowA, shadowGrey, shadowGrey, shadowGrey + 8)
                        else           Color.argb(mainA, mainR, mainG, mainB),
                        Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    c.drawCircle(bx, by, r, p)
                }
            }
        }
        p.shader = null
        return bmp
    }

    // ─── Update & draw ────────────────────────────────────────────────────────

    fun update(dt: Float) {
        for (cloud in clouds) {
            cloud.cx += cloud.speed * dt
            if (cloud.cx - cloud.halfW > w) cloud.cx = -cloud.halfW
        }
    }

    fun draw(canvas: Canvas, density: Float, stormLevel: Float = 0f) {
        if (density <= 0f) return

        val cf = if (stormLevel > 0.05f) {
            val d = 1f - stormLevel * 0.52f
            ColorMatrixColorFilter(ColorMatrix().apply { setScale(d, d, d * 0.85f, 1f) })
        } else null

        for (cloud in clouds.sortedBy { it.layer }) {
            drawPaint.alpha = (density * 255).toInt().coerceIn(0, 255)
            drawPaint.colorFilter = cf
            canvas.drawBitmap(
                cloud.bmp,
                cloud.cx - cloud.halfW,
                cloud.cy - cloud.halfH,
                drawPaint
            )
        }
        drawPaint.colorFilter = null
    }
}
