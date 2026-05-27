package com.studiosmus.stella

import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object WeatherFetcher {

    suspend fun fetch(context: Context): WeatherData? = withContext(Dispatchers.IO) {
        val location = getLastKnownLocation(context) ?: return@withContext null
        fetchFromApi(location.latitude, location.longitude)
    }

    suspend fun fetchByCoords(lat: Double, lon: Double): WeatherData? =
        withContext(Dispatchers.IO) { fetchFromApi(lat, lon) }

    private fun getLastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers.mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }.maxByOrNull { it.accuracy }
        } catch (_: Exception) { null }
    }

    private fun fetchFromApi(lat: Double, lon: Double): WeatherData? {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=sunrise,sunset" +
                "&timezone=auto&forecast_days=1"

            val json = JSONObject(URL(url).readText())
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")

            val sunriseStr = daily.getJSONArray("sunrise").getString(0)
            val sunsetStr = daily.getJSONArray("sunset").getString(0)

            WeatherData(
                temperatureCelsius = current.getDouble("temperature_2m"),
                weatherCode = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
                sunriseHour = sunriseStr.substringAfter("T").substringBefore(":").toIntOrNull() ?: 6,
                sunsetHour = sunsetStr.substringAfter("T").substringBefore(":").toIntOrNull() ?: 20
            )
        } catch (_: Exception) { null }
    }
}
