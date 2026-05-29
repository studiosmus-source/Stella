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
import java.util.Calendar

class StellaWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine() {

        private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val thread  = HandlerThread("WallpaperRender").also { it.start() }
        private val handler = Handler(thread.looper)

        private var surfaceW = 1080
        private var surfaceH = 1920
        private var visible  = false
        private var lastFrameMs = 0L

        private var bgBitmap:    Bitmap?      = null
        private var weatherData: WeatherData? = null
        private var horizonFrac: Float        = 0.40f
        private var receiverRegistered        = false

        // ── Debug overrides (set from MainActivity secret panel) ─────────────
        @Volatile private var debugCondition: WeatherCondition? = null
        @Volatile private var debugTimeOfDay: TimeOfDay?        = null

        private val bgReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_BG_CHANGED    -> handler.post { reloadBackground() }
                    ACTION_DEBUG_CHANGED -> handler.post { reloadDebugOverrides() }
                }
            }
        }

        // ── Animated particle systems ────────────────────────────────────────
        private var rain:       RainSystem?      = null
        private var snow:       SnowSystem?      = null
        private var fog:        FogSystem?       = null
        private var lightning:  LightningSystem? = null
        private var clouds:     CloudSystem?     = null
        private var glassDrops: GlassDropSystem? = null
        private var hail:       HailSystem?      = null
        private var celestial:  CelestialSystem? = null

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
                val filter = IntentFilter().apply {
                    addAction(ACTION_BG_CHANGED)
                    addAction(ACTION_DEBUG_CHANGED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    registerReceiver(bgReceiver, filter, RECEIVER_NOT_EXPORTED)
                else
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    registerReceiver(bgReceiver, filter)
                receiverRegistered = true
            }
            reloadBackground()
            reloadDebugOverrides()
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
            val bmp = WeatherRenderer.loadBackground(path, surfaceW, surfaceH)
            bgBitmap = bmp
            if (bmp != null) horizonFrac = HorizonDetector.detect(bmp)
        }

        private fun reloadDebugOverrides() {
            val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            debugCondition = p.getString(KEY_DEBUG_COND, null)
                ?.let { runCatching { WeatherCondition.valueOf(it) }.getOrNull() }
            debugTimeOfDay = p.getString(KEY_DEBUG_TOFD, null)
                ?.let { runCatching { TimeOfDay.valueOf(it) }.getOrNull() }
        }

        private fun initParticles() {
            val w = surfaceW; val h = surfaceH
            rain       = RainSystem(w, h)
            snow       = SnowSystem(w, h)
            fog        = FogSystem(w, h)
            lightning  = LightningSystem(w, h)
            clouds     = CloudSystem(w, h)
            glassDrops = GlassDropSystem(w, h)
            hail       = HailSystem(w, h)
            celestial  = CelestialSystem(w, h)
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
            val w   = surfaceW; val h = surfaceH
            val hz  = horizonFrac
            val cal = Calendar.getInstance()
            val hour     = cal.get(Calendar.HOUR_OF_DAY)
            val sunriseH = weatherData?.sunriseHour ?: 6
            val sunsetH  = weatherData?.sunsetHour  ?: 20

            // Debug overrides take priority over fetched weather
            val timeOfDay = debugTimeOfDay
                ?: weatherData?.timeOfDay
                ?: TimeOfDay.fromHour(hour, sunriseH, sunsetH)

            val condition = debugCondition
                ?: weatherData?.condition
                ?: if (timeOfDay == TimeOfDay.NIGHT || timeOfDay == TimeOfDay.DUSK)
                       WeatherCondition.CLEAR_NIGHT else WeatherCondition.CLEAR_DAY

            // ── 1. Background + atmospheric color grading ────────────────────
            val bg = bgBitmap
            if (bg != null)
                WeatherRenderer.drawBackground(canvas, null, condition, timeOfDay, w, h, preloaded = bg)
            else
                canvas.drawColor(Color.rgb(20, 20, 40))

            // ── 2. Time-of-day sky gradient overlay ──────────────────────────
            ParticleSystem.drawTimeOverlay(canvas, timeOfDay, w, h)

            // ── 3. Sun or Moon (fixed top-right HUD, texture pre-rendered) ──────
            if (timeOfDay != TimeOfDay.NIGHT)
                celestial?.drawSun(canvas, timeOfDay)
            else if (condition == WeatherCondition.CLEAR_NIGHT)
                celestial?.drawMoon(canvas)

            // ── 4. Stars above horizon only ───────────────────────────────────
            if (condition == WeatherCondition.CLEAR_NIGHT)
                ParticleSystem.drawNightOverlay(canvas, w, h, System.currentTimeMillis() / 3_600_000, hz)

            // ── 5. Clouds (capped at horizon) ─────────────────────────────────
            val (cloudDensity, stormLevel) = cloudParams(condition)
            if (cloudDensity > 0f) {
                clouds?.update(dt)
                clouds?.draw(canvas, cloudDensity, stormLevel, hz)
            }

            // ── 6. Weather particle effects ───────────────────────────────────
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
                    // Pioggia violentissima + fulmini frequenti e doppi
                    rain?.update(dt, 2.4f); rain?.draw(canvas, 1.0f)
                    lightning?.let { it.maybeStrike(1200, 3200, bolts = 2); it.draw(canvas) }
                }
                WeatherCondition.HAIL -> {
                    // Pioggia intensa + grandine grossa + fulmini
                    rain?.update(dt, 1.9f); rain?.draw(canvas, 1.0f)
                    hail?.update(dt, 1.4f); hail?.draw(canvas, 1.0f)
                    lightning?.let { it.maybeStrike(2000, 5000); it.draw(canvas) }
                }
                WeatherCondition.FOG -> {
                    fog?.update(dt); fog?.draw(canvas, 1.0f)
                }
                WeatherCondition.CLEAR_DAY -> ParticleSystem.drawSunGlow(canvas, w, h)
                else -> {}
            }

            // ── 7. Rain on glass (foreground) ─────────────────────────────────
            glassDrops?.let { gd ->
                gd.setIntensity(glassIntensity(condition))
                gd.update(dt)
                gd.draw(canvas)
            }

            // ── 8. Sky info HUD (temperature + condition label) ────────────────
            ParticleSystem.drawSkyInfoHUD(canvas, w, h,
                weatherData?.temperatureCelsius, condition)
        }

        private fun cloudParams(cond: WeatherCondition): Pair<Float, Float> = when (cond) {
            WeatherCondition.CLEAR_DAY                                               -> 0.12f to 0.0f
            WeatherCondition.CLEAR_NIGHT                                             -> 0.05f to 0.0f
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> 0.55f to 0.0f
            WeatherCondition.OVERCAST                                                -> 0.92f to 0.25f
            WeatherCondition.DRIZZLE                                                 -> 0.72f to 0.35f
            WeatherCondition.RAIN                                                    -> 0.88f to 0.55f
            WeatherCondition.HEAVY_RAIN                                              -> 1.00f to 0.72f
            WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW                       -> 0.80f to 0.05f
            WeatherCondition.THUNDERSTORM                                            -> 1.00f to 1.00f
            WeatherCondition.HAIL                                                    -> 1.00f to 0.90f
            WeatherCondition.FOG                                                     -> 0.30f to 0.10f
        }

        private fun glassIntensity(cond: WeatherCondition): Float = when (cond) {
            WeatherCondition.DRIZZLE      -> 0.35f
            WeatherCondition.RAIN         -> 0.75f
            WeatherCondition.HEAVY_RAIN   -> 1.00f
            WeatherCondition.THUNDERSTORM -> 1.00f
            WeatherCondition.HAIL         -> 0.65f
            else                          -> 0.00f
        }
    }

    companion object {
        const val PREFS              = "StellaPrefs"
        const val KEY_BG             = "live_bg"
        const val KEY_DEBUG_COND     = "debug_cond"
        const val KEY_DEBUG_TOFD     = "debug_tofd"
        const val ACTION_BG_CHANGED    = "com.studiosmus.stella.ACTION_BG_CHANGED"
        const val ACTION_DEBUG_CHANGED = "com.studiosmus.stella.ACTION_DEBUG_CHANGED"
        private const val FRAME_MS = 16L
    }
}
