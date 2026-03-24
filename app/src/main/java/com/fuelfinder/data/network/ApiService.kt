package com.fuelfinder.data.network

import com.fuelfinder.data.model.NominatimResult
import com.fuelfinder.data.model.OverpassResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Nominatim free geocoding API
interface NominatimApi {
    @GET("search")
    suspend fun searchLocation(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1,
        @Query("accept-language") lang: String = "en"
    ): Response<List<NominatimResult>>
}

// Overpass API for real petrol pump data from OpenStreetMap
interface OverpassApi {
    @GET("api/interpreter")
    suspend fun queryPetrolPumps(
        @Query("data") query: String
    ): Response<OverpassResponse>
}

object ApiClient {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Add User-Agent header required by Nominatim
            val request = chain.request().newBuilder()
                .header("User-Agent", "FuelFinderApp/1.0")
                .build()
            chain.proceed(request)
        }
        .build()

    val nominatim: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApi::class.java)
    }

    val overpass: OverpassApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://overpass-api.de/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OverpassApi::class.java)
    }
}
