package com.comsat.audio.data.model

import com.google.gson.Gson
import java.util.UUID

data class SavedMix(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val airportIcao: String? = null,
    val station: SomaStation? = null,
    val atcVolume: Float = 0.8f,
    val somaVolume: Float = 0.5f
)

fun SomaStation.isRestorable(): Boolean = runCatching {
    listOf(description, genre, imageUrl).forEach { requireNotNull(it) }
    id.isNotBlank() && title.isNotBlank() && network.isNotBlank() &&
        (nextTrackApiBase?.startsWith("https://") == true ||
            streamUrl.startsWith("https://") || streamUrl.startsWith("http://"))
}.getOrDefault(false)

/** Corrupt or incomplete saved records must not prevent the app from starting. */
object MixStorage {
    private val gson = Gson()
    fun station(json: String?): SomaStation? = runCatching {
        gson.fromJson(json, SomaStation::class.java)?.takeIf { it.isRestorable() }
    }.getOrNull()

    fun mixes(json: String?): List<SavedMix> = runCatching {
        gson.fromJson(json, Array<SavedMix>::class.java)?.filter { mix ->
            runCatching {
                mix.id.isNotBlank() && mix.name.isNotBlank() &&
                    mix.atcVolume.isFinite() && mix.somaVolume.isFinite() &&
                    mix.atcVolume in 0f..1f && mix.somaVolume in 0f..1f &&
                    (mix.station == null || mix.station.isRestorable()) &&
                    (mix.airportIcao != null || mix.station != null)
            }.getOrDefault(false)
        }?.distinctBy { it.id }.orEmpty()
    }.getOrDefault(emptyList())

    fun encode(value: Any): String = gson.toJson(value)
}
