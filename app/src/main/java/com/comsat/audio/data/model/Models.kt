package com.comsat.audio.data.model

import com.google.gson.annotations.SerializedName

// ─── Airport / LiveATC ────────────────────────────────────────────────────────

data class Airport(
    val icao: String,
    val name: String,
    val city: String,
    val country: String,
    val region: String,
    val feedId: String,
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
