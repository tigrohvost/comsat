package com.comsat.audio.data.repository

import com.comsat.audio.data.model.Airport

// Static curated airport list with known LiveATC feed IDs (Icecast mount names).
//
// Mounts were taken from each airport's LiveATC feed page (https://www.liveatc.net/search/?icao=XXXX)
// on 2026-08-25, preferring the main Tower feed, then a combined Twr/App feed, then Approach.
// Airports with no LiveATC feed at all (most of Western Europe, the Gulf, Brazil, Argentina,
// Korea, South Africa) are deliberately absent: they would sit permanently offline.
// Coordinates are approximate (map display only).
val AIRPORT_CATALOG: List<Airport> = listOf(
    // North America — USA
    Airport("KJFK", "John F. Kennedy Intl", "New York", "US", "North America", "kjfk_twr", lat = 40.64f, lon = -73.78f),
    Airport("KLAX", "Los Angeles Intl", "Los Angeles", "US", "North America", "klax_twr", lat = 33.94f, lon = -118.41f),
    Airport("KORD", "O'Hare Intl", "Chicago", "US", "North America", "kord1n2_twr_n", lat = 41.98f, lon = -87.90f),
    Airport("KATL", "Hartsfield-Jackson Atlanta", "Atlanta", "US", "North America", "katl_twr", lat = 33.64f, lon = -84.43f),
    Airport("KSFO", "San Francisco Intl", "San Francisco", "US", "North America", "ksfo_twr", lat = 37.62f, lon = -122.38f),
    Airport("KBOS", "Logan Intl", "Boston", "US", "North America", "kbos1_twr", lat = 42.36f, lon = -71.01f),
    Airport("KSEA", "Seattle-Tacoma Intl", "Seattle", "US", "North America", "ksea3_twr_east", lat = 47.45f, lon = -122.31f),
    Airport("KDFW", "Dallas/Fort Worth Intl", "Dallas", "US", "North America", "kdfw1_twr1_e", lat = 32.90f, lon = -97.04f),
    Airport("KMIA", "Miami Intl", "Miami", "US", "North America", "kmia3_twr_1183", lat = 25.79f, lon = -80.29f),
    Airport("KLAS", "Harry Reid Intl", "Las Vegas", "US", "North America", "klas4_twr", lat = 36.08f, lon = -115.15f),
    Airport("KPHX", "Phoenix Sky Harbor", "Phoenix", "US", "North America", "kphx_twr_both", lat = 33.43f, lon = -112.01f),
    Airport("KEWR", "Newark Liberty Intl", "Newark", "US", "North America", "kewr_twr", lat = 40.69f, lon = -74.17f),
    Airport("KLGA", "LaGuardia", "New York", "US", "North America", "klga_twr", lat = 40.78f, lon = -73.87f),
    Airport("PHNL", "Daniel K. Inouye Intl", "Honolulu", "US", "North America", "phnl1_twr", lat = 21.32f, lon = -157.92f),
    // North America — Canada / Mexico
    Airport("CYYZ", "Toronto Pearson Intl", "Toronto", "CA", "North America", "cyyz1_twr_north", lat = 43.68f, lon = -79.63f),
    Airport("CYVR", "Vancouver Intl", "Vancouver", "CA", "North America", "cyvr1_twr", lat = 49.19f, lon = -123.18f),
    Airport("MMMX", "Benito Juárez Intl", "Mexico City", "MX", "North America", "mmmx1_twr", lat = 19.44f, lon = -99.07f),
    // Europe
    Airport("EIDW", "Dublin", "Dublin", "IE", "Europe", "eidw82", lat = 53.43f, lon = -6.27f),
    Airport("EHAM", "Amsterdam Schiphol", "Amsterdam", "NL", "Europe", "eham_app_118080", lat = 52.31f, lon = 4.76f),
    Airport("LSZH", "Zürich", "Zurich", "CH", "Europe", "lszh1_twr", lat = 47.46f, lon = 8.55f),
    Airport("EPWA", "Warsaw Chopin", "Warsaw", "PL", "Europe", "epwa_twr2", lat = 52.17f, lon = 20.97f),
    Airport("LHBP", "Budapest Ferenc Liszt", "Budapest", "HU", "Europe", "lhbp_twr_118715", lat = 47.44f, lon = 19.26f),
    Airport("ENGM", "Oslo Gardermoen", "Oslo", "NO", "Europe", "eneg2", lat = 60.19f, lon = 11.10f),
    Airport("ENBR", "Bergen Flesland", "Bergen", "NO", "Europe", "enbr4_twr", lat = 60.29f, lon = 5.22f),
    // Middle East
    Airport("LLBG", "Ben Gurion", "Tel Aviv", "IL", "Middle East", "llbg2", lat = 32.01f, lon = 34.89f),
    // Asia / Pacific
    Airport("RJTT", "Tokyo Haneda", "Tokyo", "JP", "Asia-Pacific", "rjtt_twr", lat = 35.55f, lon = 139.78f),
    Airport("RJAA", "Tokyo Narita", "Tokyo", "JP", "Asia-Pacific", "rjaa_twr", lat = 35.77f, lon = 140.39f),
    Airport("VHHH", "Hong Kong Intl", "Hong Kong", "HK", "Asia-Pacific", "vhhh9", lat = 22.31f, lon = 113.91f),
    Airport("RPLL", "Ninoy Aquino Intl", "Manila", "PH", "Asia-Pacific", "rpll", lat = 14.51f, lon = 121.02f),
    Airport("WSSS", "Singapore Changi", "Singapore", "SG", "Asia-Pacific", "wsss3", lat = 1.36f, lon = 103.99f),
    Airport("YSSY", "Sydney Kingsford Smith", "Sydney", "AU", "Asia-Pacific", "yssy1_twr", lat = -33.95f, lon = 151.18f),
    Airport("YMML", "Melbourne", "Melbourne", "AU", "Asia-Pacific", "ymml3", lat = -37.67f, lon = 144.84f),
    Airport("YPPH", "Perth", "Perth", "AU", "Asia-Pacific", "ypph_twr", lat = -31.94f, lon = 115.97f),
    // South America
    Airport("SPJC", "Jorge Chávez Intl", "Lima", "PE", "South America", "spjc1_app", lat = -12.02f, lon = -77.11f),
    Airport("SCEL", "Arturo Merino Benítez", "Santiago", "CL", "South America", "scel", lat = -33.39f, lon = -70.79f),
    // Africa
    Airport("HAAB", "Addis Ababa Bole", "Addis Ababa", "ET", "Africa", "haab2_twr", lat = 8.98f, lon = 38.80f),
)
