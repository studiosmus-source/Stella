package com.studiosmus.livingweather

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, WeatherWidgetProvider::class.java)
        )

        ids.forEach { id ->
            WeatherWidgetProvider.scheduleVisualUpdates(context, id)
        }
        WeatherWidgetProvider.scheduleWeatherFetch(context)
    }
}
