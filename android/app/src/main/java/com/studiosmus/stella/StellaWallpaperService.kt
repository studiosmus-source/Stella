package com.studiosmus.stella

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StellaWallpaperService : WallpaperService() {

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
        private var receiverRegistered = false

        private val bgReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.post { reloadBackground() }
            }
        }

        private var rain: RainSystem? = null
        private var snow: SnowSystem? = null
        private var fog: FogSystem? = null
        private var lightning: LightningSystem? = null

        private val drawRunnable = object : Runnable {
            override fun run() {
                val start = System.currentTimeMillis()
                val dt = if (lastFrameMs == 0L) 0.016f
                         else ((start - lastFrameMs) / 1000f).coerceAtMost(0.05f)
                lastFrameMs = start
                drawFrame(dt)
                if (visible) {
                    val renderMs = System.currentTimeMillis() - start
                    handler.postDelayed(this, (FRAME_MS - renderMs).coerceAtLeast(0))
                }
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
            if (!receiverRegistered) {
                val filter = IntentFilter(ACTION_BG_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(bgReceiver, filter, RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    registerReceiver(bgReceiver, filter)
                }
                receiverRegistered = true
            }
            reloadBackground()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            surfaceW = w; surfaceH = h
            reloadBackground()
            initParticles()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(drawRunnable)
        }

        override fun onDestroy() {
            if (receiverRegistered) {
                unregisterReceiver(bgReceiver)
                receiverRegistered = false
            }
            handler.removeCallbacks(drawRunnable)
            thread.quitSafely()
            scope.cancel()
        }

        private fun reloadBackground() {
            val path = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_BG, null) ?: return
            bgBitmap = WeatherRenderer.loadBackground(path, surfaceW, surfaceH)
        }

        private fun initParticles() {
            val w = surfaceW; val h = surfaceH
            rain = RainSystem(w, h)
            snow = SnowSystem(w, h)
            fog = FogSystem(w, h)
            lightning = LightningSystem(w, h)
        }

        private fun fetchWeather() {
            scope.launch {
                val data = WeatherFetcher.fetch(this@StellaWallpaperService) ?: return@launch
                weatherData = data
            }
        }

        private fun drawFrame(dt: Float) {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    holder.lockHardwareCanvas() else holder.lockCanvas()
                canvas ?: return
                renderFrame(canvas, dt)
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        private fun renderFrame(canvas: Canvas, dt: Float) {
            val w = surfaceW; val h = surfaceH
            val weather = weatherData
            val timeOfDay = weather?.timeOfDay ?: run {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                TimeOfDay.fromHour(hour, 6, 20)
            }
            val condition = weather?.condition
                ?: if (timeOfDay == TimeOfDay.NIGHT || timeOfDay == TimeOfDay.DUSK) WeatherCondition.CLEAR_NIGHT
                   else WeatherCondition.CLEAR_DAY

            val bg = bgBitmap
            if (bg != null) {
                WeatherRenderer.drawBackground(canvas, null, condition, timeOfDay, w, h, preloaded = bg)
            } else {
                canvas.drawColor(Color.rgb(20, 20, 40))
            }

            ParticleSystem.drawTimeOverlay(canvas, timeOfDay, w, h)

            when (condition) {
                WeatherCondition.DRIZZLE -> {
                    rain?.update(dt, 0.6f); rain?.draw(canvas, 0.35f)
                }
                WeatherCondition.RAIN -> {
                    rain?.update(dt); rain?.draw(canvas, 0.75f)
                }
                WeatherCondition.HEAVY_RAIN -> {
                    rain?.update(dt, 1.4f); rain?.draw(canvas, 1.0f)
                }
                WeatherCondition.SNOW -> {
                    snow?.update(dt); snow?.draw(canvas, 0.65f)
                }
                WeatherCondition.HEAVY_SNOW -> {
                    ParticleSystem.drawWhiteHaze(canvas, w, h, 40)
                    snow?.update(dt); snow?.draw(canvas, 1.0f)
                }
                WeatherCondition.THUNDERSTORM -> {
                    rain?.update(dt, 1.8f); rain?.draw(canvas, 1.0f)
                    lightning?.let { if (it.maybeStrike()) Unit; it.draw(canvas) }
                }
                WeatherCondition.FOG -> {
                    fog?.update(dt); fog?.draw(canvas, 1.0f)
                }
                WeatherCondition.OVERCAST -> {}
                WeatherCondition.PARTLY_CLOUDY_DAY,
                WeatherCondition.PARTLY_CLOUDY_NIGHT -> {}
                WeatherCondition.CLEAR_DAY -> ParticleSystem.drawSunGlow(canvas, w, h)
                WeatherCondition.CLEAR_NIGHT ->
                    ParticleSystem.drawNightOverlay(canvas, w, h, System.currentTimeMillis() / 3_600_000)
            }
        }
    }

    companion object {
        const val PREFS = "StellaPrefs"
        const val KEY_BG = "live_bg"
        const val ACTION_BG_CHANGED = "com.studiosmus.stella.ACTION_BG_CHANGED"
        private const val FRAME_MS = 16L
    }
}
