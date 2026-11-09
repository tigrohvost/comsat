package com.comsat.audio.service

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** One logical selection, retained across lost responses, retries and pauses. */
internal class ChainedTrackSelection(
    val current: String?,
    private val nonce: String = UUID.randomUUID().toString()
) {
    var requestId: String? = null
        private set

    fun synchronize(serverTimeMillis: Long?, httpDate: String?): Boolean {
        if (requestId != null) return true
        // The station's id TTL uses its clock, not the phone's adjustable clock.
        val issuedAt = serverTimeMillis?.takeIf { it in 1_000_000_000_000..9_999_999_999_999 }
            ?: runCatching {
                ZonedDateTime.parse(httpDate, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli()
            }.getOrNull()?.takeIf { it in 1_000_000_000_000..9_999_999_999_999 }
            ?: return false
        requestId = "$nonce:$issuedAt:0"
        return true
    }

    fun url(apiBase: String): String =
        (apiBase.trimEnd('/') + if (requestId == null) "/listener-status" else "/next-track")
            .toHttpUrl().newBuilder().apply {
                requestId?.let { id ->
                    addQueryParameter("request_id", id)
                    current?.let { addQueryParameter("current", it) }
                }
            }.build().toString()
}
