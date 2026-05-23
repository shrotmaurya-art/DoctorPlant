package com.plantcure.ai.data.repository

import com.plantcure.ai.BuildConfig
import com.plantcure.ai.data.remote.WeatherApiService
import com.plantcure.ai.domain.model.RiskLevel
import com.plantcure.ai.domain.model.WeatherData
import com.plantcure.ai.util.RiskCalculator
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for weather data from OpenWeatherMap API.
 * Computes disease risk level using RiskCalculator.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weatherApi: WeatherApiService
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private fun saveToCache(weatherData: WeatherData) {
        prefs.edit().apply {
            putString("data", gson.toJson(weatherData))
            putLong("timestamp", System.currentTimeMillis())
            apply()
        }
    }
    
    private fun loadFromCache(): WeatherData? {
        val json = prefs.getString("data", null) ?: return null
        val data = gson.fromJson(json, WeatherData::class.java)
        val timestamp = prefs.getLong("timestamp", 0L)
        val hoursAgo = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60)
        
        return if (hoursAgo > 0) {
            data.copy(riskMessage = "${data.riskMessage} (Cached $hoursAgo hours ago)")
        } else {
            data.copy(riskMessage = "${data.riskMessage} (Cached recently)")
        }
    }
    /**
     * Fetch current weather and compute disease risk.
     * Returns null if API key is missing or network fails.
     */
    suspend fun getWeather(lat: Double, lon: Double): WeatherData? {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("your_")) {
            return loadFromCache()
        }

        return try {
            android.util.Log.d("PlantCure_Weather", "Fetching weather for lat=$lat, lon=$lon")
            val response = weatherApi.getCurrentWeather(lat, lon, apiKey)
            android.util.Log.d("PlantCure_Weather", "Response code: ${response.code()}")
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    android.util.Log.e("PlantCure_Weather", "Response body is null!")
                    return loadFromCache()
                }
                val temp = body.main.temp
                val humidity = body.main.humidity
                val wind = body.wind.speed
                val rainMm = body.rain?.`1h` ?: 0f
                val description = body.weather.firstOrNull()?.description ?: "Clear"
                val conditionMain = body.weather.firstOrNull()?.main ?: "Clear"
                val city = body.name

                android.util.Log.d("PlantCure_Weather",
                    "Temp: $temp, Humidity: $humidity, Wind: $wind, " +
                    "Rain: $rainMm, Desc: $description, City: $city, Condition: $conditionMain")

                val riskLevel = RiskCalculator.calculateRisk(temp, humidity, rainMm)
                val riskMessage = RiskCalculator.getRiskMessage(riskLevel, temp, humidity)

                val data = WeatherData(
                    temperature = temp,
                    humidity = humidity,
                    windSpeed = wind,
                    description = description.replaceFirstChar { it.uppercase() },
                    condition = conditionMain,
                    cityName = city,
                    riskLevel = riskLevel,
                    riskMessage = riskMessage
                )
                saveToCache(data)
                data
            } else {
                android.util.Log.e("PlantCure_Weather",
                    "Error: ${response.code()} ${response.errorBody()?.string()}")
                loadFromCache()
            }
        } catch (e: Exception) {
            android.util.Log.e("PlantCure_Weather", "Exception: ${e.message}", e)
            loadFromCache()
        }
    }
}
