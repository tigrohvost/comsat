package com.comsat.audio.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Validates the bundled res/raw/airports.json: the app trusts it blindly at
// runtime (Airport.init throws on an empty feed list), so catch a bad
// regeneration here rather than on first launch.
class AirportCatalogTest {

    private val airports by lazy {
        val file = listOf("src/main/res/raw/airports.json", "app/src/main/res/raw/airports.json")
            .map(::File)
            .first { it.exists() }
        parseAirportCatalog(file.readText())
    }

    @Test
    fun `catalog is non-trivial`() {
        assertTrue("expected a real catalog, got ${airports.size}", airports.size >= 30)
    }

    @Test
    fun `icao codes are unique four-letter uppercase`() {
        val codes = airports.map { it.icao }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { assertTrue(it, Regex("[A-Z]{4}").matches(it)) }
    }

    @Test
    fun `every airport has feeds with unique well-formed mounts and labels`() {
        airports.forEach { a ->
            assertTrue("${a.icao} has no feeds", a.feeds.isNotEmpty())
            val mounts = a.feeds.map { it.mount }
            assertEquals("${a.icao} has duplicate mounts", mounts.size, mounts.toSet().size)
            a.feeds.forEach { f ->
                assertTrue("${a.icao} mount '${f.mount}'", Regex("[a-z0-9_\\-]+").matches(f.mount))
                assertTrue("${a.icao} feed ${f.mount} has empty label", f.label.isNotBlank())
            }
        }
    }

    @Test
    fun `metadata is filled in and coordinates are on the globe`() {
        airports.forEach { a ->
            assertTrue(a.icao, a.name.isNotBlank() && a.city.isNotBlank())
            assertTrue(a.icao, Regex("[A-Z]{2}").matches(a.country))
            assertTrue(a.icao, a.region.isNotBlank())
            assertTrue("${a.icao} lat ${a.lat}", a.lat in -90f..90f)
            assertTrue("${a.icao} lon ${a.lon}", a.lon in -180f..180f)
        }
    }

    @Test
    fun `default feed and stream url derive from the first feed`() {
        val jfk = airports.first { it.icao == "KJFK" }
        assertEquals(jfk.feeds.first(), jfk.feed)
        assertEquals("https://d.liveatc.net/${jfk.feeds.first().mount}", jfk.streamUrl)
        assertTrue(!jfk.isOnline)
        val probed = jfk.copy(activeFeed = jfk.feeds[1])
        assertTrue(probed.isOnline)
        assertEquals(jfk.feeds[1].streamUrl, probed.streamUrl)
    }
}
