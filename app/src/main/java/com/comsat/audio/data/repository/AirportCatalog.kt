package com.comsat.audio.data.repository

import android.content.Context
import com.comsat.audio.R
import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.AtcFeed
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Curated airport list with LiveATC feeds, bundled as res/raw/airports.json so
// a feed rename is a data update, not a code change. Regenerate the file with
// tools/liveatc_feeds.py; feed order in the file is the probe/playback order.
@Singleton
class AirportCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val airports: List<Airport> by lazy {
        context.resources.openRawResource(R.raw.airports)
            .bufferedReader()
            .use { parseAirportCatalog(it.readText()) }
    }

    fun find(icao: String): Airport? = airports.find { it.icao == icao }
}

// Pure parser so the bundled file can be validated in a plain unit test.
fun parseAirportCatalog(json: String): List<Airport> =
    Gson().fromJson(json, CatalogDto::class.java).airports.map { a ->
        Airport(
            icao = a.icao,
            name = a.name,
            city = a.city,
            country = a.country,
            region = a.region,
            feeds = a.feeds.map { AtcFeed(mount = it.mount, label = it.label) },
            lat = a.lat,
            lon = a.lon
        )
    }

private class CatalogDto(
    @SerializedName("airports") val airports: List<AirportDto> = emptyList()
)

private class AirportDto(
    @SerializedName("icao") val icao: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("city") val city: String = "",
    @SerializedName("country") val country: String = "",
    @SerializedName("region") val region: String = "",
    @SerializedName("lat") val lat: Float = 0f,
    @SerializedName("lon") val lon: Float = 0f,
    @SerializedName("feeds") val feeds: List<FeedDto> = emptyList()
)

private class FeedDto(
    @SerializedName("mount") val mount: String = "",
    @SerializedName("label") val label: String = ""
)
