package com.studiosmus.livingweather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WeatherUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val data = WeatherFetcher.fetch(applicationContext) ?: return Result.retry()

        val prefs = applicationContext.getSharedPreferences(
            WeatherWidgetProvider.PREFS, Context.MODE_PRIVATE
        )
        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(
            ComponentName(applicationContext, WeatherWidgetProvider::class.java)
        )

        ids.forEach { id ->
            WeatherWidgetProvider.saveWeather(prefs, id, data)
            WeatherWidgetProvider.updateWidget(applicationContext, manager, id)
        }

        return Result.success()
    }
}
