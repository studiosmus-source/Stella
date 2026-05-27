package com.studiosmus.livingweather

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF

object WeatherRenderer {

    const val WIDGET_W = 480
    const val WIDGET_H = 240

    // For widget (static render)
    fun render(
        backgroundPath: String?,
        weatherData: WeatherData?,
        seed: Long = System.currentTimeMillis() / 60_000
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDGET_W, WIDGET_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(canvas, backgroundPath, weatherData?.condition, WIDGET_W, WIDGET_H)
        if (weatherData != null) {
            ParticleSystem.drawWeatherEffect(
                canvas, weatherData.condition, weatherData.timeOfDay,
                seed, WIDGET_W, WIDGET_H
            )
            ParticleSystem.drawInfoOverlay(
                canvas, weatherData.temperatureCelsius,
                weatherData.condition, WIDGET_W, WIDGET_H
            )
        }
        return bmp
    }

    // Load and scale background for live wallpaper
    fun loadBackground(path: String, w: Int, h: Int): Bitmap? {
        val raw = BitmapFactory.decodeFile(path) ?: return null
        return scaleCrop(raw, w, h)
    }

    // Draw background with atmospheric color grading
    fun drawBackground(
        canvas: Canvas,
        path: String?,
        condition: WeatherCondition?,
        w: Int, h: Int,
        preloaded: Bitmap? = null
    ) {
        val src = preloaded
            ?: path?.let { BitmapFactory.decodeFile(it) }
            ?: run { canvas.drawColor(Color.rgb(20, 20, 40)); return }

        val bg = if (preloaded != null) src else scaleCrop(src, w, h)
        val paint = Paint()
        paint.colorFilter = atmosphericFilter(condition)
        canvas.drawBitmap(bg, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
    }

    // ColorMatrix that shifts image colours to match the weather atmosphere
    fun atmosphericFilter(condition: WeatherCondition?): ColorMatrixColorFilter? {
        val m = FloatArray(20)
        when (condition) {
            WeatherCondition.DRIZZLE -> setMatrix(m,
                rs = 0.88f, gs = 0.90f, bs = 0.97f,
                rt = -6f,  gt = -4f,  bt = 8f)

            WeatherCondition.RAIN -> setMatrix(m,
                rs = 0.78f, gs = 0.82f, bs = 0.95f,
                rt = -12f, gt = -8f,  bt = 12f)

            WeatherCondition.HEAVY_RAIN -> setMatrix(m,
                rs = 0.65f, gs = 0.70f, bs = 0.88f,
                rt = -20f, gt = -14f, bt = 15f)

            WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW -> setMatrix(m,
                rs = 0.88f, gs = 0.90f, bs = 0.98f,
                rt = 18f,  gt = 18f,  bt = 25f)

            WeatherCondition.THUNDERSTORM -> setMatrix(m,
                rs = 0.55f, gs = 0.60f, bs = 0.72f,
                rt = -25f, gt = -18f, bt = -5f)

            WeatherCondition.FOG -> setMatrix(m,
                rs = 0.68f, gs = 0.70f, bs = 0.72f,
                rt = 45f,  gt = 45f,  bt = 48f)

            WeatherCondition.OVERCAST -> setMatrix(m,
                rs = 0.80f, gs = 0.82f, bs = 0.85f,
                rt = -5f,  gt = -3f,  bt = 0f)

            WeatherCondition.CLEAR_NIGHT -> setMatrix(m,
                rs = 0.28f, gs = 0.32f, bs = 0.55f,
                rt = -8f,  gt = -5f,  bt = 12f)

            WeatherCondition.CLEAR_DAY -> return null // no filter

            else -> return null
        }
        val cm = ColorMatrix(m)
        return ColorMatrixColorFilter(cm)
    }

    private fun setMatrix(
        m: FloatArray,
        rs: Float, gs: Float, bs: Float,
        rt: Float, gt: Float, bt: Float
    ) {
        m[0] = rs;  m[6] = gs;  m[12] = bs; m[18] = 1f
        m[4] = rt;  m[9] = gt;  m[14] = bt; m[19] = 0f
    }

    fun scaleCrop(src: Bitmap, tw: Int, th: Int): Bitmap {
        val srcR = src.width.toFloat() / src.height
        val dstR = tw.toFloat() / th
        val (sw, sh) = if (srcR > dstR) Pair((th * srcR).toInt(), th)
                       else Pair(tw, (tw / srcR).toInt())
        val m = Matrix()
        m.setScale(sw.toFloat() / src.width, sh.toFloat() / src.height)
        val scaled = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val ox = ((sw - tw) / 2).coerceAtLeast(0)
        val oy = ((sh - th) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(scaled, ox, oy, tw, th)
    }
}
