package com.plantcure.ai.data.repository

import android.content.Context
import com.plantcure.ai.BuildConfig
import com.plantcure.ai.domain.model.ShopResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for finding nearby agricultural shops.
 * Uses Google Places Nearby Search API.
 */
@Singleton
class ShopsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Find nearby agricultural shops using Google Places Nearby Search.
     * Searches for pesticide shops, krishi kendra, fertilizer shops, agri stores.
     */
    suspend fun findNearbyShops(lat: Double, lon: Double, radiusMeters: Int = 5000): List<ShopResult> {
        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("your_")) {
            return emptyList()
        }

        return try {
            val keyword = "pesticide shop|krishi kendra|fertilizer shop|agri store"
            val encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8")
            val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=$lat,$lon" +
                "&radius=$radiusMeters" +
                "&keyword=$encodedKeyword" +
                "&key=$apiKey"

            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                parsePlacesResponse(json, lat, lon)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parsePlacesResponse(json: String, userLat: Double, userLon: Double): List<ShopResult> {
        return try {
            val gson = com.google.gson.Gson()
            val response = gson.fromJson(json, PlacesResponse::class.java)

            response.results?.map { place ->
                val placeLat = place.geometry?.location?.lat ?: 0.0
                val placeLon = place.geometry?.location?.lng ?: 0.0
                val distKm = haversineDistanceKm(userLat, userLon, placeLat, placeLon).toFloat()

                ShopResult(
                    placeId = place.place_id ?: "",
                    name = place.name ?: "Agricultural Shop",
                    address = place.vicinity ?: "Unknown location",
                    latitude = placeLat,
                    longitude = placeLon,
                    rating = place.rating?.toFloat() ?: 0f,
                    userRatingsTotal = place.user_ratings_total ?: 0,
                    phoneNumber = null, // Not available in Nearby Search
                    isOpen = place.opening_hours?.open_now,
                    distanceKm = distKm
                )
            }?.sortedBy { it.distanceKm } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    // POJOs for Google Places Nearby Search response
    private data class PlacesResponse(val results: List<PlaceResult>?, val status: String?)
    private data class PlaceResult(
        val place_id: String?,
        val name: String?,
        val vicinity: String?,
        val geometry: PlaceGeometry?,
        val rating: Double?,
        val user_ratings_total: Int?,
        val opening_hours: PlaceOpeningHours?
    )
    private data class PlaceGeometry(val location: PlaceLocation?)
    private data class PlaceLocation(val lat: Double?, val lng: Double?)
    private data class PlaceOpeningHours(val open_now: Boolean?)
}
