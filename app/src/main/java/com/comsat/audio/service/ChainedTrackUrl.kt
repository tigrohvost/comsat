package com.comsat.audio.service

import java.net.URI

/** Resolves relative, root-relative and absolute track URLs from a station API. */
internal fun resolveChainedTrackUrl(apiBase: String, trackPath: String): String? {
    val path = trackPath.trim()
    if (path.isEmpty()) return null

    return runCatching {
        val base = URI(apiBase.trim().trimEnd('/') + "/")
        val resolved = base.resolve(path)
        resolved.takeIf {
            it.scheme.equals("http", ignoreCase = true) ||
                it.scheme.equals("https", ignoreCase = true)
        }?.takeIf { !it.host.isNullOrBlank() }
            ?.toASCIIString()
    }.getOrNull()
}
