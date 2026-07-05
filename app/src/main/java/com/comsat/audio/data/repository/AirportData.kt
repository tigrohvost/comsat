package com.comsat.audio.data.repository

import com.comsat.audio.data.model.Airport

// Static curated airport list with known LiveATC feed IDs.
// Feed IDs sourced from liveatc.net — verify at https://www.liveatc.net/feedindex.php
// Coordinates are approximate (map display only).
val AIRPORT_CATALOG: List<Airport> = listOf(
    // North America — USA
    Airport("KJFK", "John F. Kennedy Intl", "New York", "US", "North America", "kjfk_app_n", lat = 40.64f, lon = -73.78f),
    Airport("KLAX", "Los Angeles Intl", "Los Angeles", "US", "North America", "klax5", lat = 33.94f, lon = -118.41f),
    Airport("KORD", "O'Hare Intl", "Chicago", "US", "North America", "kord5", lat = 41.98f, lon = -87.90f),
    Airport("KATL", "Hartsfield-Jackson Atlanta", "Atlanta", "US", "North America", "katl_app", lat = 33.64f, lon = -84.43f),
    Airport("KDEN", "Denver Intl", "Denver", "US", "North America", "kden_app", lat = 39.86f, lon = -104.67f),
    Airport("KSFO", "San Francisco Intl", "San Francisco", "US", "North America", "ksfo_app2_l", lat = 37.62f, lon = -122.38f),
    Airport("KBOS", "Logan Intl", "Boston", "US", "North America", "kbos_app_north", lat = 42.36f, lon = -71.01f),
    Airport("KSEA", "Seattle-Tacoma Intl", "Seattle", "US", "North America", "ksea_app", lat = 47.45f, lon = -122.31f),
    Airport("KDFW", "Dallas/Fort Worth Intl", "Dallas", "US", "North America", "kdfw_app", lat = 32.90f, lon = -97.04f),
    Airport("KMIA", "Miami Intl", "Miami", "US", "North America", "kmia_twr", lat = 25.79f, lon = -80.29f),
    Airport("KLAS", "Harry Reid Intl", "Las Vegas", "US", "North America", "klas_app", lat = 36.08f, lon = -115.15f),
    Airport("KPHX", "Phoenix Sky Harbor", "Phoenix", "US", "North America", "kphx_app", lat = 33.43f, lon = -112.01f),
    Airport("KEWR", "Newark Liberty Intl", "Newark", "US", "North America", "kewr", lat = 40.69f, lon = -74.17f),
    Airport("KLGA", "LaGuardia", "New York", "US", "North America", "klga", lat = 40.78f, lon = -73.87f),
    // North America — Canada
    Airport("CYYZ", "Toronto Pearson Intl", "Toronto", "CA", "North America", "cyyz6", lat = 43.68f, lon = -79.63f),
    Airport("CYVR", "Vancouver Intl", "Vancouver", "CA", "North America", "cyvr1_app", lat = 49.19f, lon = -123.18f),
    // Europe — France / Benelux
    Airport("LFPG", "Charles de Gaulle", "Paris", "FR", "Europe", "lfpg3_dep", lat = 49.01f, lon = 2.55f),
    Airport("EHAM", "Amsterdam Schiphol", "Amsterdam", "NL", "Europe", "eham4", lat = 52.31f, lon = 4.76f),
    Airport("EBBR", "Brussels", "Brussels", "BE", "Europe", "ebbr_arr", lat = 50.90f, lon = 4.48f),
    // Europe — South
    Airport("LEMD", "Adolfo Suárez Madrid–Barajas", "Madrid", "ES", "Europe", "lemd", lat = 40.47f, lon = -3.57f),
    Airport("LIRF", "Leonardo da Vinci Fiumicino", "Rome", "IT", "Europe", "lirf", lat = 41.80f, lon = 12.24f),
    Airport("LGAV", "Athens Intl", "Athens", "GR", "Europe", "lgav", lat = 37.94f, lon = 23.94f),
    // Europe — North
    Airport("EKCH", "Copenhagen Kastrup", "Copenhagen", "DK", "Europe", "ekch_twr", lat = 55.62f, lon = 12.65f),
    Airport("ENGM", "Oslo Gardermoen", "Oslo", "NO", "Europe", "engm2_app", lat = 60.19f, lon = 11.10f),
    Airport("ESSA", "Stockholm Arlanda", "Stockholm", "SE", "Europe", "essa", lat = 59.65f, lon = 17.92f),
    Airport("EFHK", "Helsinki-Vantaa", "Helsinki", "FI", "Europe", "efhk", lat = 60.32f, lon = 24.96f),
    // Asia / Pacific
    Airport("RJTT", "Tokyo Haneda", "Tokyo", "JP", "Asia-Pacific", "rjtt_app_dep", lat = 35.55f, lon = 139.78f),
    Airport("RJAA", "Tokyo Narita", "Tokyo", "JP", "Asia-Pacific", "rjaa_app_s", lat = 35.77f, lon = 140.39f),
    Airport("RKSI", "Seoul Incheon", "Seoul", "KR", "Asia-Pacific", "rksi", lat = 37.46f, lon = 126.44f),
    Airport("VHHH", "Hong Kong Intl", "Hong Kong", "HK", "Asia-Pacific", "vhhh5", lat = 22.31f, lon = 113.91f),
    Airport("WSSS", "Singapore Changi", "Singapore", "SG", "Asia-Pacific", "wsss_app", lat = 1.36f, lon = 103.99f),
    Airport("YSSY", "Sydney Kingsford Smith", "Sydney", "AU", "Asia-Pacific", "yssy1", lat = -33.95f, lon = 151.18f),
    Airport("YMML", "Melbourne", "Melbourne", "AU", "Asia-Pacific", "ymml", lat = -37.67f, lon = 144.84f),
    // Middle East
    Airport("OMDB", "Dubai Intl", "Dubai", "AE", "Middle East", "omdb_app", lat = 25.25f, lon = 55.36f),
    Airport("OERK", "King Khalid Intl", "Riyadh", "SA", "Middle East", "oerk", lat = 24.96f, lon = 46.70f),
    // Americas — South / Other
    Airport("SBGR", "São Paulo–Guarulhos", "São Paulo", "BR", "South America", "sbgr", lat = -23.43f, lon = -46.47f),
    Airport("SAEZ", "Ezeiza Intl", "Buenos Aires", "AR", "South America", "saez", lat = -34.82f, lon = -58.54f),
    // Africa
    Airport("FACT", "Cape Town Intl", "Cape Town", "ZA", "Africa", "fact", lat = -33.97f, lon = 18.60f),
    Airport("HAAB", "Addis Ababa Bole", "Addis Ababa", "ET", "Africa", "haab", lat = 8.98f, lon = 38.80f),
)
