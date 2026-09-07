package com.comsat.audio.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.statusStore by preferencesDataStore(name = "atc_status")

// Result of one LiveATC status probe: which mount answered (null = nothing
// streaming) and when. Persisted so a relaunch within the TTL costs LiveATC
// zero connections — dozens of stream requests per app start from one IP is
// exactly what gets an address rate-limited.
data class AtcStatusEntry(
    @SerializedName("mount") val mount: String?,
    @SerializedName("at") val checkedAt: Long
) {
    fun isFresh(now: Long, ttlMillis: Long = STATUS_TTL_MILLIS): Boolean =
        now - checkedAt in 0..ttlMillis
}

const val STATUS_TTL_MILLIS = 15 * 60 * 1000L

@Singleton
class AtcStatusCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, AtcStatusEntry>>() {}.type

    suspend fun load(): Map<String, AtcStatusEntry> {
        return decode(context.statusStore.data.first()[KEY])
    }

    private fun decode(raw: String?): Map<String, AtcStatusEntry> {
        if (raw == null) return emptyMap()
        return runCatching { gson.fromJson<Map<String, AtcStatusEntry>>(raw, mapType) }
            .getOrNull() ?: emptyMap()
    }

    suspend fun update(entries: Map<String, AtcStatusEntry>) {
        if (entries.isEmpty()) return
        // Read inside the transaction: selection and a catalog refresh can
        // finish together, and neither may overwrite the other's results.
        context.statusStore.edit { prefs ->
            prefs[KEY] = gson.toJson(decode(prefs[KEY]) + entries)
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("entries")
    }
}
