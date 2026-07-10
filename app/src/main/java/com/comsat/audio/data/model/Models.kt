package com.comsat.audio.data.model

import com.google.gson.annotations.SerializedName
import kotlin.math.roundToInt

// ─── Airport / LiveATC ────────────────────────────────────────────────────────

data class Airport(
    val icao: String,
    val name: String,
    val city: String,
    val country: String,
    val region: String,
    val feedId: String,
    val lat: Float = 0f,
    val lon: Float = 0f,
    val isOnline: Boolean = false
) {
    // d.liveatc.net is a Cloudflare-fronted dispatcher that 302-redirects to the active
    // Icecast server (e.g. https://s1-bos.liveatc.net/{feedId}?nocache=...).
    // Both OkHttp and ExoPlayer follow cross-protocol redirects automatically.
    val streamUrl: String get() = "http://d.liveatc.net/$feedId"
}

// ─── Soma.fm ──────────────────────────────────────────────────────────────────

data class SomaChannelsResponse(
    @SerializedName("channels") val channels: List<SomaChannelDto> = emptyList()
)

data class SomaChannelDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("genre") val genre: String = "",
    @SerializedName("playlists") val playlists: List<SomaPlaylistDto> = emptyList(),
    @SerializedName("image") val image: String = "",
    // API returns listeners as a quoted string e.g. "1091"
    @SerializedName("listeners") val listeners: String = "0"
) {
    // Direct Icecast MP3 stream — skip PLS download entirely
    fun directStreamUrl(): String = "https://ice1.somafm.com/$id-128-mp3"

    fun toStation() = SomaStation(
        id = id,
        title = title,
        description = description,
        genre = genre,
        streamUrl = directStreamUrl(),
        imageUrl = image,
        listeners = listeners.toIntOrNull() ?: 0
    )
}

data class SomaPlaylistDto(
    @SerializedName("url") val url: String = "",
    @SerializedName("format") val format: String = "",
    @SerializedName("quality") val quality: String = ""
)

data class SomaStation(
    val id: String,
    val title: String,
    val description: String,
    val genre: String,
    val streamUrl: String,
    val imageUrl: String,
    val listeners: Int = 0
)

// ─── Stream state ─────────────────────────────────────────────────────────────

enum class StreamStatus { IDLE, LOADING, PLAYING, BUFFERING, PAUSED, RECONNECTING, ERROR }

data class StreamState(
    val status: StreamStatus = StreamStatus.IDLE,
    val error: String? = null
) {
    val isActive get() = status == StreamStatus.PLAYING || status == StreamStatus.BUFFERING
}

// ─── ATIS / METAR ─────────────────────────────────────────────────────────────

// Magnus formula; METAR reports dewpoint but not humidity directly
fun relativeHumidity(tempC: Double, dewpointC: Double): Double {
    fun gamma(t: Double) = 17.625 * t / (243.04 + t)
    return 100.0 * kotlin.math.exp(gamma(dewpointC) - gamma(tempC))
}

data class AtisData(
    val tempC: Double,
    val dewpointC: Double,
    val qnhHpa: Int?,
    val windDirDeg: Int?,      // null with non-zero speed means variable (VRB)
    val windSpeedKt: Int?,
    val observedUtc: String?
) {
    val humidityPct: Int
        get() = relativeHumidity(tempC, dewpointC).roundToInt()

    val tempText: String get() = "${tempC.roundToInt()}°"
    val dewText: String get() = "${dewpointC.roundToInt()}°"
    val rhText: String get() = "$humidityPct%"
    val qnhText: String get() = qnhHpa?.let { "Q$it" } ?: "Q----"

    val windText: String
        get() = when {
            windSpeedKt == null -> "---"
            windSpeedKt == 0 -> "CALM"
            windDirDeg == null -> "VRB/%02dKT".format(windSpeedKt)
            else -> "%d°/%02dKT".format(windDirDeg, windSpeedKt)
        }
}

data class MetarDto(
    @SerializedName("temp") val temp: Double? = null,
    @SerializedName("dewp") val dewp: Double? = null,
    // JSON number for degrees, or the string "VRB" for variable wind
    @SerializedName("wdir") val wdir: String? = null,
    @SerializedName("wspd") val wspd: Double? = null,
    @SerializedName("altim") val altim: Double? = null,
    @SerializedName("reportTime") val reportTime: String? = null
) {
    fun toAtisData(): AtisData? {
        if (temp == null || dewp == null) return null
        return AtisData(
            tempC = temp,
            dewpointC = dewp,
            qnhHpa = altim?.roundToInt(),
            windDirDeg = wdir?.toDoubleOrNull()?.roundToInt(),
            windSpeedKt = wspd?.roundToInt(),
            observedUtc = reportTime
        )
    }
}
