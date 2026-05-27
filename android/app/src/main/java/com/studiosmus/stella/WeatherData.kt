package com.studiosmus.stella

data class WeatherData(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val sunriseHour: Int,
    val sunsetHour: Int
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode, isDay)
    val timeOfDay: TimeOfDay get() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return TimeOfDay.fromHour(hour, sunriseHour, sunsetHour)
    }
}

enum class WeatherCondition {
    CLEAR_DAY, CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY, PARTLY_CLOUDY_NIGHT,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN, HEAVY_RAIN,
    SNOW, HEAVY_SNOW,
    THUNDERSTORM;

    val label: String
        get() = when (this) {
            CLEAR_DAY, CLEAR_NIGHT -> "Sereno"
            PARTLY_CLOUDY_DAY, PARTLY_CLOUDY_NIGHT -> "Parz. nuvoloso"
            OVERCAST -> "Nuvoloso"
            FOG -> "Nebbia"
            DRIZZLE -> "Pioggerella"
            RAIN -> "Pioggia"
            HEAVY_RAIN -> "Pioggia intensa"
            SNOW -> "Neve"
            HEAVY_SNOW -> "Neve intensa"
            THUNDERSTORM -> "Temporale"
        }

    companion object {
        fun fromCode(code: Int, isDay: Boolean): WeatherCondition = when (code) {
            0 -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
            1, 2 -> if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 80, 81 -> RAIN
            65, 82 -> HEAVY_RAIN
            71, 73, 77, 85 -> SNOW
            75, 86 -> HEAVY_SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
        }
    }
}

enum class TimeOfDay {
    DAWN, MORNING, AFTERNOON, GOLDEN_HOUR, DUSK, NIGHT;

    companion object {
        fun fromHour(hour: Int, sunriseHour: Int, sunsetHour: Int): TimeOfDay {
            val dawnStart = sunriseHour - 1
            val goldenStart = sunsetHour - 2
            val duskEnd = sunsetHour + 1
            return when {
                hour in dawnStart until sunriseHour -> DAWN
                hour in sunriseHour until 12 -> MORNING
                hour in 12 until goldenStart -> AFTERNOON
                hour in goldenStart until sunsetHour -> GOLDEN_HOUR
                hour in sunsetHour until duskEnd -> DUSK
                else -> NIGHT
            }
        }
    }
}
