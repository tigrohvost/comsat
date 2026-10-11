package com.comsat.audio.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WeatherModelsTest {
    private val moscow = WeatherPlace("Moscow", 55.7522, 37.6156)
    private val airport = Airport(
        icao = "KJFK",
        name = "John F. Kennedy Intl",
        city = "New York",
        country = "US",
        region = "North America",
        feeds = listOf(AtcFeed("kjfk_twr", "Tower")),
        lat = 40.6398f,
        lon = -73.7789f
    )

    @Test
    fun wmoCodesMapToConditions() {
        assertEquals(WeatherCondition.CLEAR, weatherCondition(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, weatherCondition(2))
        assertEquals(WeatherCondition.CLOUDY, weatherCondition(3))
        assertEquals(WeatherCondition.FOG, weatherCondition(48))
        assertEquals(WeatherCondition.DRIZZLE, weatherCondition(55))
        assertEquals(WeatherCondition.RAIN, weatherCondition(63))
        assertEquals(WeatherCondition.RAIN, weatherCondition(81))
        assertEquals(WeatherCondition.SNOW, weatherCondition(75))
        assertEquals(WeatherCondition.SNOW, weatherCondition(86))
        assertEquals(WeatherCondition.THUNDERSTORM, weatherCondition(99))
        // Unknown future codes still render as a plain cloud.
        assertEquals(WeatherCondition.CLOUDY, weatherCondition(42))
    }

    @Test
    fun gsonParsesOpenMeteoCurrentWeather() {
        val json = """
            {"latitude":55.75,"longitude":37.625,"current_units":{"temperature_2m":"°C"},
             "current":{"time":"2026-10-11T13:30","interval":900,"temperature_2m":-3.6,
                        "weather_code":71,"is_day":0}}
        """.trimIndent()
        val report = Gson().fromJson(json, OpenMeteoForecastDto::class.java)
            .current!!.toReport(moscow, fetchedAtMillis = 1_000L)!!

        assertEquals(-3.6, report.tempC, 0.001)
        assertEquals(WeatherCondition.SNOW, report.condition)
        assertFalse(report.isDay)
        // Stored against the requested place, not Open-Meteo's grid cell.
        assertEquals(moscow.latitude, report.latitude, 0.0)
        assertEquals(moscow.longitude, report.longitude, 0.0)
        assertEquals(1_000L, report.fetchedAtMillis)
    }

    @Test
    fun incompleteCurrentWeatherIsDiscarded() {
        assertNull(OpenMeteoCurrentDto(temperature = null, weatherCode = 1).toReport(moscow, 0L))
        assertNull(OpenMeteoCurrentDto(temperature = 4.0, weatherCode = null).toReport(moscow, 0L))
        assertNull(Gson().fromJson("{}", OpenMeteoForecastDto::class.java).current)
    }

    @Test
    fun gsonParsesMetNorwayForecastAtTheCurrentHour() {
        val json = """
            {"type":"Feature","properties":{"meta":{"updated_at":"2026-10-11T13:19:56Z"},
             "timeseries":[
               {"time":"2026-10-11T16:00:00Z","data":{
                  "instant":{"details":{"air_temperature":10.5,"wind_speed":2.1}},
                  "next_1_hours":{"summary":{"symbol_code":"rain"}}}},
               {"time":"2026-10-11T17:00:00Z","data":{
                  "instant":{"details":{"air_temperature":-1.2}},
                  "next_1_hours":{"summary":{"symbol_code":"clearsky_night"}}}},
               {"time":"2026-10-21T00:00:00Z","data":{
                  "instant":{"details":{"air_temperature":3.6}}}}
             ]}}
        """.trimIndent()
        val forecast = Gson().fromJson(json, MetForecastDto::class.java)
        val at1650 = Instant.parse("2026-10-11T16:50:00Z").toEpochMilli()
        val at1710 = Instant.parse("2026-10-11T17:10:00Z").toEpochMilli()

        val now = forecast.toReport(moscow, at1650)!!
        assertEquals(10.5, now.tempC, 0.001)
        assertEquals(WeatherCondition.RAIN, now.condition)
        assertTrue(now.isDay)
        assertEquals(moscow.latitude, now.latitude, 0.0)
        assertEquals(at1650, now.fetchedAtMillis)

        val later = forecast.toReport(moscow, at1710)!!
        assertEquals(-1.2, later.tempC, 0.001)
        assertEquals(WeatherCondition.CLEAR, later.condition)
        assertFalse(later.isDay)

        // A clock behind the series still shows its first hour.
        assertEquals(10.5, forecast.toReport(moscow, 0L)!!.tempC, 0.001)
        // Steps past the hourly range carry no symbol for the next hour.
        assertNull(forecast.toReport(moscow, Instant.parse("2026-10-21T01:00:00Z").toEpochMilli()))
        assertNull(Gson().fromJson("{}", MetForecastDto::class.java).toReport(moscow, at1650))
    }

    @Test
    fun metSymbolsMapToConditions() {
        assertEquals(WeatherCondition.CLEAR, metCondition("clearsky_polartwilight"))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, metCondition("fair_night"))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, metCondition("partlycloudy_day"))
        assertEquals(WeatherCondition.CLOUDY, metCondition("cloudy"))
        assertEquals(WeatherCondition.FOG, metCondition("fog"))
        assertEquals(WeatherCondition.RAIN, metCondition("lightrainshowers_day"))
        assertEquals(WeatherCondition.SNOW, metCondition("heavysleet"))
        assertEquals(WeatherCondition.SNOW, metCondition("snowshowers_night"))
        // MET's own spelling, "lightssleet", still reads as thunder first.
        assertEquals(WeatherCondition.THUNDERSTORM, metCondition("lightssleetshowersandthunder_day"))
        assertEquals(WeatherCondition.CLOUDY, metCondition("somethingnew"))
    }

    @Test
    fun metCoordinatesHaveAtMostFourDecimals() {
        assertEquals("56.8587", metCoordinate(56.85871))
        assertEquals("-73.7789", metCoordinate(-73.77889))
        assertEquals("37.6000", metCoordinate(37.6))
    }

    @Test
    fun geocodingResultsBecomePlacesWithRegion() {
        val json = """
            {"results":[
              {"id":524901,"name":"Moscow","latitude":55.75222,"longitude":37.61556,
               "country":"Russia","admin1":"Moscow"},
              {"id":5601538,"name":"Moscow","latitude":46.73239,"longitude":-117.00017,
               "country":"United States","admin1":"Idaho"},
              {"id":1,"name":"Nowhere"}
            ]}
        """.trimIndent()
        val places = Gson().fromJson(json, GeocodingResponseDto::class.java)
            .results!!.mapNotNull { it.toPlace() }

        assertEquals(2, places.size)
        assertEquals("Russia", places[0].region)
        assertEquals("Idaho, United States", places[1].region)
        assertEquals(-117.00017, places[1].longitude, 0.0)
    }

    @Test
    fun noGeocodingMatchesOmitResults() {
        assertNull(Gson().fromJson("""{"generationtime_ms":0.5}""", GeocodingResponseDto::class.java).results)
    }

    @Test
    fun reportsExpireAndBelongToTheirPlace() {
        val report = WeatherReport(10.0, WeatherCondition.CLEAR, true, moscow.latitude, moscow.longitude, 10_000L)

        assertTrue(report.isUsable(moscow, nowMillis = 10_000L, maxAgeMillis = 5_000L))
        assertTrue(report.isUsable(moscow.copy(latitude = 55.78), nowMillis = 14_999L, maxAgeMillis = 5_000L))
        assertFalse(report.isUsable(moscow, nowMillis = 15_000L, maxAgeMillis = 5_000L))
        // A clock moved backwards cannot make a reading look fresh indefinitely.
        assertFalse(report.isUsable(moscow, nowMillis = 9_000L, maxAgeMillis = 5_000L))
        assertFalse(report.isUsable(WeatherPlace("Tver", 56.86, 35.9), nowMillis = 10_000L, maxAgeMillis = 5_000L))
    }

    @Test
    fun targetFollowsTheChosenSource() {
        val device = WeatherPlace("Khimki", 55.89, 37.44)
        val state = WeatherState(place = moscow, deviceLocation = device)

        assertNull(state.target(airport))
        assertEquals(device, state.copy(source = WeatherSource.CURRENT_LOCATION).target(airport))
        assertEquals(moscow, state.copy(source = WeatherSource.PLACE).target(airport))
        val airportPlace = state.copy(source = WeatherSource.AIRPORT).target(airport)!!
        assertEquals("New York", airportPlace.name)
        assertEquals(40.6398, airportPlace.latitude, 0.0001)
    }

    @Test
    fun airportSourceNeedsAnAirportWithCoordinates() {
        val state = WeatherState(source = WeatherSource.AIRPORT)

        assertNull(state.target(null))
        assertNull(state.target(airport.copy(lat = 0f, lon = 0f)))
        assertNull(WeatherState(source = WeatherSource.CURRENT_LOCATION).target(airport))
    }
}
