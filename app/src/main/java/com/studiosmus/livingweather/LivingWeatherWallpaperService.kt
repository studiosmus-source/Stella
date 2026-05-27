package com.studiosmus.livingweather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LivingWeatherWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine() {

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val thread = HandlerThread("WallpaperRender").also { it.start() }
        private val handler = Handler(thread.looper)

        private var surfaceW = 1080
        private var surfaceH = 1920
        private var visible = false
        private var lastFrameMs = 0L

        private var bgBitmap: Bitmap? = null
        private var weatherData: WeatherData? = null

        private var rain: RainSystem? = null
        private var snow: SnowSystem? = null

        private val drawRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val dt = if (lastFrameMs == 0L) 0.016f
                         else ((now - lastFrameMs) / 1000f).coerceAtMost(0.1f)
                lastFrameMs = now
                drawFrame(dt)
                if (visible) handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastFrameMs = 0L
                handler.post(drawRunnable)
                fetchWeather()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            loadBackground()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            surfaceW = w
            surfaceH = h
            loadBackground()
            rain = RainSystem(w, h)
            snow = SnowSystem(w, h)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(drawRunnable)
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            thread.quitSafely()
            scope.cancel()
        }

        private fun loadBackground() {
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val path = prefs.getString(KEY_BG, null) ?: return
            val raw = BitmapFactory.decodeFile(path) ?: return
            bgBitmap = scaleCrop(raw, surfaceW, surfaceH)
        }

        private fun fetchWeather() {
            scope.launch {
                val data = WeatherFetcher.fetch(this@LivingWeatherWallpaperService) ?: return@launch
                weatherData = data
                val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                WeatherWidgetProvider.saveWeather(prefs, WEATHER_ID, data)
            }
        }

        private fun drawFrame(dt: Float) {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                renderFrame(canvas, dt)
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        private fun renderFrame(canvas: Canvas, dt: Float) {
            val w = surfaceW
            val h = surfaceH
            val weather = weatherData
            val condition = weather?.condition ?: WeatherCondition.CLEAR_DAY
            val timeOfDay = weather?.timeOfDay ?: TimeOfDay.AFTERNOON
            val temp = weather?.temperatureCelsius ?: 0.0

            // 1. Background
            val bg = bgBitmap
            if (bg != null) canvas.drawBitmap(bg, 0f, 0f, null)
            else canvas.drawColor(Color.rgb(20, 20, 40))

            // 2. Time-of-day overlay
            ParticleSystem.drawTimeOverlay(canvas, timeOfDay, w, h)

            // 3. Animated weather effects
            when (condition) {
                WeatherCondition.DRIZZLE -> {
                    rain?.update(dt, 0.5f); rain?.draw(canvas, 0.3f)
                }
                WeatherCondition.RAIN -> {
                    rain?.update(dt); rain?.draw(canvas, 0.7f)
                }
                WeatherCondition.HEAVY_RAIN -> {
                    ParticleSystem.drawDarkOverlay(canvas, w, h, 80)
                    rain?.update(dt, 1.5f); rain?.draw(canvas, 1.0f)
                }
                WeatherCondition.SNOW -> {
                    snow?.update(dt); snow?.draw(canvas, 0.6f)
                }
                WeatherCondition.HEAVY_SNOW -> {
                    ParticleSystem.drawWhiteHaze(canvas, w, h, 45)
                    snow?.update(dt); snow?.draw(canvas, 1.0f)
                }
                WeatherCondition.THUNDERSTORM -> {
                    ParticleSystem.drawDarkOverlay(canvas, w, h, 100)
                    rain?.update(dt, 2f); rain?.draw(canvas, 1.0f)
                    // lightning flash ~every 5 seconds
                    if (System.currentTimeMillis() % 5000 < 80) {
                        canvas.drawColor(Color.argb(55, 255, 255, 190))
                    }
                }
                WeatherCondition.FOG -> ParticleSystem.drawFog(canvas, w, h)
                WeatherCondition.OVERCAST -> ParticleSystem.drawDarkOverlay(canvas, w, h, 55)
                WeatherCondition.PARTLY_CLOUDY_DAY,
                WeatherCondition.PARTLY_CLOUDY_NIGHT -> ParticleSystem.drawDarkOverlay(canvas, w, h, 22)
                WeatherCondition.CLEAR_DAY -> ParticleSystem.drawSunGlow(canvas, w, h)
                WeatherCondition.CLEAR_NIGHT ->
                    ParticleSystem.drawNightOverlay(canvas, w, h, System.currentTimeMillis() / 3_600_000)
            }

            // 4. Temperature + condition text
            if (weather != null) {
                ParticleSystem.drawInfoOverlay(canvas, temp, condition, w, h)
            }
        }

        private fun scaleCrop(src: Bitmap, tw: Int, th: Int): Bitmap {
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

    companion object {
        const val PREFS = "LivingWeatherPrefs"
        const val KEY_BG = "live_bg"
        const val WEATHER_ID = -1
        private const val FRAME_MS = 42L // ~24 fps
    }
}
