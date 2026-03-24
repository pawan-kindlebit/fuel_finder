package com.fuelfinder.data.model

data class FuelStation(
    val id: Int,
    val name: String,
    val brand: String,
    val lat: Double,
    val lng: Double,
    val price: Double,          // estimated price in INR
    val distanceFromRoute: Double,
    val isBestOption: Boolean = false,
    val address: String = "",
    val isOpen24Hours: Boolean = true
)

data class RouteInfo(
    val fromName: String,
    val toName: String,
    val fromLat: Double,
    val fromLng: Double,
    val toLat: Double,
    val toLng: Double,
    val totalDistanceKm: Double
)

// Nominatim geocoding response
data class NominatimResult(
    val place_id: Long = 0,
    val display_name: String = "",
    val lat: String = "0",
    val lon: String = "0"
)

// Overpass API response models
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

data class OverpassElement(
    val id: Long = 0,
    val type: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val tags: Map<String, String> = emptyMap()
)
