package com.studiosmus.livingweather

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WeatherWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_VISUAL = "com.studiosmus.livingweather.ACTION_UPDATE_VISUAL"
        const val PREFS = "LivingWeatherPrefs"
        const val KEY_BG = "bg_"
        const val KEY_CODE = "code_"
        const val KEY_TEMP = "temp_"
        const val KEY_IS_DAY = "isday_"
        const val KEY_SUNRISE = "sunrise_"
        const val KEY_SUNSET = "sunset_"

        // Called from ConfigActivity and WorkManager after data is ready
        fun updateWidget(ctx: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val bgPath = prefs.getString(KEY_BG + widgetId, null)
            val weather = loadWeather(prefs, widgetId)

            val seed = System.currentTimeMillis() / 60_000
            val bitmap = WeatherRenderer.render(bgPath, weather, seed)

            val views = RemoteViews(ctx.packageName, R.layout.widget_weather)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            manager.updateAppWidget(widgetId, views)
        }

        fun saveWeather(prefs: SharedPreferences, widgetId: Int, data: WeatherData) {
            prefs.edit()
                .putInt(KEY_CODE + widgetId, data.weatherCode)
                .putFloat(KEY_TEMP + widgetId, data.temperatureCelsius.toFloat())
                .putBoolean(KEY_IS_DAY + widgetId, data.isDay)
                .putInt(KEY_SUNRISE + widgetId, data.sunriseHour)
                .putInt(KEY_SUNSET + widgetId, data.sunsetHour)
                .apply()
        }

        fun loadWeather(prefs: SharedPreferences, widgetId: Int): WeatherData? {
            val code = prefs.getInt(KEY_CODE + widgetId, -1)
            if (code == -1) return null
            return WeatherData(
                temperatureCelsius = prefs.getFloat(KEY_TEMP + widgetId, 20f).toDouble(),
                weatherCode = code,
                isDay = prefs.getBoolean(KEY_IS_DAY + widgetId, true),
                sunriseHour = prefs.getInt(KEY_SUNRISE + widgetId, 6),
                sunsetHour = prefs.getInt(KEY_SUNSET + widgetId, 20)
            )
        }

        fun scheduleVisualUpdates(ctx: Context, widgetId: Int) {
            val intent = Intent(ctx, WeatherWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_VISUAL
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = System.currentTimeMillis() + 60_000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
            } else {
                am.set(AlarmManager.RTC, next, pi)
            }
        }

        fun scheduleWeatherFetch(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "lw_weather_fetch",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelVisualUpdates(ctx: Context, widgetId: Int) {
            val intent = Intent(ctx, WeatherWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_VISUAL
            }
            val pi = PendingIntent.getBroadcast(
                ctx, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
            scheduleVisualUpdates(context, id)
        }
        scheduleWeatherFetch(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_VISUAL) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            val manager = AppWidgetManager.getInstance(context)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateWidget(context, manager, widgetId)
                scheduleVisualUpdates(context, widgetId)
            } else {
                // fallback: update all widgets
                val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                ids.forEach { id ->
                    updateWidget(context, manager, id)
                    scheduleVisualUpdates(context, id)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        appWidgetIds.forEach { id ->
            cancelVisualUpdates(context, id)
            prefs.edit()
                .remove(KEY_BG + id)
                .remove(KEY_CODE + id)
                .remove(KEY_TEMP + id)
                .remove(KEY_IS_DAY + id)
                .remove(KEY_SUNRISE + id)
                .remove(KEY_SUNSET + id)
                .apply()
        }
    }
}
