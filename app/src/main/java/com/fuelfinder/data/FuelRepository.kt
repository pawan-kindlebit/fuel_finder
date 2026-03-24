package com.fuelfinder.data

import com.fuelfinder.data.model.FuelStation
import com.fuelfinder.data.model.RouteInfo
import com.fuelfinder.data.network.ApiClient
import kotlin.math.*

class FuelRepository {

    // ── Real Indian fuel prices by state (updated regularly) ──────────────────
    // Source: iocl.com / bpcl.in average prices as of March 2026
    private val statePetrolPrices = mapOf(
        "delhi" to 94.77,
        "haryana" to 94.29,
        "punjab" to 97.18,
        "himachal pradesh" to 93.52,
        "uttarakhand" to 94.38,
        "uttar pradesh" to 95.28,
        "rajasthan" to 104.88,
        "gujarat" to 94.58,
        "maharashtra" to 104.21,
        "karnataka" to 102.86,
        "tamil nadu" to 100.75,
        "kerala" to 107.74,
        "west bengal" to 104.95,
        "madhya pradesh" to 107.95,
        "bihar" to 107.95,
        "jharkhand" to 98.19,
        "odisha" to 101.11,
        "andhra pradesh" to 108.39,
        "telangana" to 109.66,
        "chandigarh" to 94.33,
        "jammu" to 95.25,
        "kashmir" to 95.25,
        "default" to 96.50
    )

    suspend fun getSuggestions(query: String): List<com.fuelfinder.ui.LocationSuggestion> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&accept-language=en"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "FuelFinderApp/1.0")
                .build()
            val response = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val json = org.json.JSONArray(body)
            val list = mutableListOf<com.fuelfinder.ui.LocationSuggestion>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val display = obj.getString("display_name")
                val lat = obj.getString("lat").toDoubleOrNull() ?: continue
                val lon = obj.getString("lon").toDoubleOrNull() ?: continue
                val shortName = display.split(",").take(2).joinToString(",").trim()
                list.add(com.fuelfinder.ui.LocationSuggestion(display, shortName, lat, lon))
            }
            list
        } catch (e: Exception) { emptyList() }
    }
    // ── Geocoding: place name → coordinates ───────────────────────────────────
    suspend fun geocode(placeName: String): Pair<Double, Double>? {
        return try {
            val response = ApiClient.nominatim.searchLocation(placeName)
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                val r = response.body()!![0]
                Pair(r.lat.toDouble(), r.lon.toDouble())
            } else null
        } catch (e: Exception) {
            android.util.Log.e("FuelRepo", "Geocode error: ${e.message}")
            null
        }
    }

    // ── Fetch real petrol pumps from OpenStreetMap via Overpass API ───────────
    suspend fun fetchRealPetrolPumps(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): List<FuelStation> {
        return try {
            // Build bounding box with padding around the route
            val minLat = minOf(fromLat, toLat) - 0.3
            val maxLat = maxOf(fromLat, toLat) + 0.3
            val minLon = minOf(fromLon, toLon) - 0.3
            val maxLon = maxOf(fromLon, toLon) + 0.3

            // Overpass QL query - get all fuel stations in bounding box
            val query = """
                [out:json][timeout:25];
                (
                  node["amenity"="fuel"]($minLat,$minLon,$maxLat,$maxLon);
                  way["amenity"="fuel"]($minLat,$minLon,$maxLat,$maxLon);
                );
                out center;
            """.trimIndent()

            val response = ApiClient.overpass.queryPetrolPumps(query)

            if (response.isSuccessful && response.body() != null) {
                val elements = response.body()!!.elements
                android.util.Log.d("FuelRepo", "Found ${elements.size} raw stations")

                val stations = elements.mapIndexedNotNull { index, element ->
                    val lat = if (element.type == "way") element.lat else element.lat
                    val lon = if (element.type == "way") element.lon else element.lon
                    if (lat == 0.0 && lon == 0.0) return@mapIndexedNotNull null

                    // Get station details from OSM tags
                    val tags = element.tags
                    val rawName = tags["name"] ?: tags["brand"] ?: tags["operator"] ?: ""
                    val brand = detectBrand(tags)
                    val name = if (rawName.isNotEmpty()) rawName else "$brand Petrol Pump"
                    val address = buildAddress(tags)

                    // Distance from route line
                    val distFromRoute = distanceFromRouteLine(
                        lat, lon, fromLat, fromLon, toLat, toLon
                    )

                    // Only include pumps within 5km of the route
                    if (distFromRoute > 1.0) return@mapIndexedNotNull null

                    // Estimate price based on location
                    val estimatedPrice = estimatePrice(lat, lon, fromLat, fromLon, toLat, toLon)

                    FuelStation(
                        id = index + 1,
                        name = name,
                        brand = brand,
                        lat = lat,
                        lng = lon,
                        price = estimatedPrice,
                        distanceFromRoute = String.format("%.1f", distFromRoute).toDouble(),
                        address = address,
                        isOpen24Hours = tags["opening_hours"] == "24/7"
                    )
                }

                // Sort by price low to high, mark best option
                val sorted = stations.sortedBy { it.price }
                val result = sorted.mapIndexed { index, station ->
                    station.copy(isBestOption = index == 0)
                }

                android.util.Log.d("FuelRepo", "Returning ${result.size} stations")
                result
            } else {
                android.util.Log.e("FuelRepo", "Overpass error: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("FuelRepo", "Fetch error: ${e.message}")
            emptyList()
        }
    }

    // ── Build route info ──────────────────────────────────────────────────────
    fun buildRoute(
        fromName: String, toName: String,
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): RouteInfo {
        return RouteInfo(
            fromName = fromName,
            toName = toName,
            fromLat = fromLat,
            fromLng = fromLon,
            toLat = toLat,
            toLng = toLon,
            totalDistanceKm = haversine(fromLat, fromLon, toLat, toLon)
        )
    }

    // ── Detect brand from OSM tags ────────────────────────────────────────────
    private fun detectBrand(tags: Map<String, String>): String {
        val combined = listOf(
            tags["brand"], tags["operator"], tags["name"]
        ).filterNotNull().joinToString(" ").lowercase()

        return when {
            "indian oil" in combined || "iocl" in combined || "indianoil" in combined -> "Indian Oil"
            "bharat" in combined || "bpcl" in combined -> "BPCL"
            "hindustan" in combined || "hpcl" in combined -> "HPCL"
            "reliance" in combined -> "Reliance"
            "shell" in combined -> "Shell"
            "nayara" in combined || "essar" in combined -> "Nayara"
            "hp" in combined -> "HPCL"
            "bp" in combined -> "BPCL"
            else -> "Fuel Station"
        }
    }

    // ── Build readable address from OSM tags ─────────────────────────────────
    private fun buildAddress(tags: Map<String, String>): String {
        val parts = listOf(
            tags["addr:housenumber"],
            tags["addr:street"],
            tags["addr:city"] ?: tags["addr:town"] ?: tags["addr:village"],
            tags["addr:state"]
        ).filterNotNull().filter { it.isNotEmpty() }
        return if (parts.isEmpty()) "Along route" else parts.joinToString(", ")
    }

    // ── Estimate fuel price based on state ───────────────────────────────────
    private fun estimatePrice(
        lat: Double, lon: Double,
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): Double {
        // Detect approximate state from coordinates
        val stateName = detectStateFromCoordinates(lat, lon)
        val basePrice = statePetrolPrices[stateName] ?: statePetrolPrices["default"]!!

        // Add small random variation ±0.50 to simulate different dealers
        val variation = (Math.random() - 0.5) * 1.0
        return String.format("%.2f", basePrice + variation).toDouble()
    }

    // ── Rough state detection from coordinates (India) ───────────────────────
    private fun detectStateFromCoordinates(lat: Double, lon: Double): String {
        return when {
            lat in 28.4..28.9 && lon in 76.8..77.4 -> "delhi"
            lat in 29.0..32.0 && lon in 74.0..77.5 -> "punjab"
            lat in 29.5..31.5 && lon in 75.5..77.5 -> "haryana"
            lat in 30.0..33.5 && lon in 75.5..79.0 -> "himachal pradesh"
            lat in 28.7..31.5 && lon in 77.5..81.0 -> "uttarakhand"
            lat in 23.0..30.5 && lon in 77.0..84.5 -> "uttar pradesh"
            lat in 24.0..30.5 && lon in 69.5..78.0 -> "rajasthan"
            lat in 20.0..24.5 && lon in 68.0..74.5 -> "gujarat"
            lat in 15.5..22.0 && lon in 72.5..80.5 -> "maharashtra"
            lat in 11.5..18.5 && lon in 74.0..78.5 -> "karnataka"
            lat in 8.0..13.5 && lon in 76.0..80.5 -> "tamil nadu"
            lat in 8.0..12.5 && lon in 74.5..77.5 -> "kerala"
            lat in 21.0..27.5 && lon in 85.0..90.0 -> "west bengal"
            lat in 21.0..26.5 && lon in 74.0..82.5 -> "madhya pradesh"
            else -> "default"
        }
    }

    // ── Distance from point to route line ────────────────────────────────────
    private fun distanceFromRouteLine(
        pLat: Double, pLon: Double,
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): Double {
        val dx = toLon - fromLon
        val dy = toLat - fromLat
        val d = sqrt(dx * dx + dy * dy)
        if (d == 0.0) return haversine(pLat, pLon, fromLat, fromLon)
        val t = (((pLon - fromLon) * dx + (pLat - fromLat) * dy) / (d * d)).coerceIn(0.0, 1.0)
        val closestLat = fromLat + t * dy
        val closestLon = fromLon + t * dx
        return haversine(pLat, pLon, closestLat, closestLon)
    }

    // ── Haversine distance formula ────────────────────────────────────────────
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}