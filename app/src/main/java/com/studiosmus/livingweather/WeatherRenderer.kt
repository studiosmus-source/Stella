package com.studiosmus.livingweather

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color

object WeatherRenderer {

    const val WIDGET_W = 480
    const val WIDGET_H = 240

    fun render(
        backgroundPath: String?,
        weatherData: WeatherData?,
        seed: Long = System.currentTimeMillis() / 60_000
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDGET_W, WIDGET_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Background image (or fallback dark colour)
        if (backgroundPath != null) {
            val raw = BitmapFactory.decodeFile(backgroundPath)
            if (raw != null) {
                canvas.drawBitmap(scaleCrop(raw, WIDGET_W, WIDGET_H), 0f, 0f, null)
            } else {
                canvas.drawColor(Color.rgb(30, 30, 50))
            }
        } else {
            canvas.drawColor(Color.rgb(30, 30, 50))
        }

        // 2. Weather + time-of-day effects
        if (weatherData != null) {
            ParticleSystem.drawWeatherEffect(
                canvas,
                weatherData.condition,
                weatherData.timeOfDay,
                seed,
                WIDGET_W, WIDGET_H
            )
            ParticleSystem.drawInfoOverlay(
                canvas,
                weatherData.temperatureCelsius,
                weatherData.condition,
                WIDGET_W, WIDGET_H
            )
        }

        return bmp
    }

    private fun scaleCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = targetW.toFloat() / targetH
        val (scaledW, scaledH) = if (srcRatio > dstRatio) {
            Pair((targetH * srcRatio).toInt(), targetH)
        } else {
            Pair(targetW, (targetW / srcRatio).toInt())
        }
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = ((scaledW - targetW) / 2).coerceAtLeast(0)
        val y = ((scaledH - targetH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(scaled, x, y, targetW, targetH)
    }
}
