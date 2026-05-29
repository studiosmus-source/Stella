package com.studiosmus.stella

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Estimates the sky/ground horizon in a background photo.
 *
 * Algorithm: scale to 80×80, derive a sky reference from the top 15% of rows,
 * then scan each column from top until the first pixel whose squared RGB distance
 * from the sky baseline exceeds a threshold. The median over all columns is the
 * horizon fraction [0..1] (0 = top of image, 1 = bottom).
 *
 * Falls back to 0.40 when too few columns hit a clear transition (e.g. indoor
 * photos, solid-colour backgrounds, or pure-sky shots).
 */
object HorizonDetector {

    fun detect(bitmap: Bitmap): Float {
        val W = 80; val H = 80
        val small = Bitmap.createScaledBitmap(bitmap, W, H, false)

        // Sky reference: average colour of the top 15% of rows
        var sr = 0; var sg = 0; var sb = 0; var n = 0
        for (x in 0 until W) for (y in 0 until (H * 0.15f).toInt()) {
            val p = small.getPixel(x, y)
            sr += Color.red(p); sg += Color.green(p); sb += Color.blue(p); n++
        }
        val skyR = sr / n; val skyG = sg / n; val skyB = sb / n

        // Per-column horizon detection
        val threshold = 55 * 55     // squared Euclidean RGB distance
        val minRow    = (H * 0.10f).toInt()
        val maxRow    = (H * 0.88f).toInt()
        val hits      = mutableListOf<Float>()

        for (x in 0 until W) {
            for (y in minRow until maxRow) {
                val p  = small.getPixel(x, y)
                val dr = Color.red(p) - skyR
                val dg = Color.green(p) - skyG
                val db = Color.blue(p) - skyB
                if (dr * dr + dg * dg + db * db > threshold) {
                    hits.add(y.toFloat() / H)
                    break
                }
            }
        }

        small.recycle()

        // Need at least 25% of columns to have a hit for a credible detection
        if (hits.size < W / 4) return 0.40f
        hits.sort()
        return hits[hits.size / 2].coerceIn(0.12f, 0.82f)
    }
}
