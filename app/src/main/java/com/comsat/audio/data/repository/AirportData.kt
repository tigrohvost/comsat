package com.comsat.audio.data.repository

import com.comsat.audio.data.model.Airport

// Static curated airport list with known LiveATC feed IDs.
// Feed IDs sourced from liveatc.net — verify at https://www.liveatc.net/feedindex.php
val AIRPORT_CATALOG: List<Airport> = listOf(
    // North America — USA
    Airport("KJFK", "John F. Kennedy Intl", "New York", "US", "North America", "kjfk_app_n"),
    Airport("KLAX", "Los Angeles Intl", "Los Angeles", "US", "North America", "klax5"),
    Airport("KORD", "O'Hare Intl", "Chicago", "US", "North America", "kord5"),
    Airport("KATL", "Hartsfield-Jackson Atlanta", "Atlanta", "US", "North America", "katl_app"),
    Airport("KDEN", "Denver Intl", "Denver", "US", "North America", "kden_app"),
    Airport("KSFO", "San Francisco Intl", "San Francisco", "US", "North America", "ksfo_app2_l"),
    Airport("KBOS", "Logan Intl", "Boston", "US", "North America", "kbos_app_north"),
    Airport("KSEA", "Seattle-Tacoma Intl", "Seattle", "US", "North America", "ksea_app"),
    Airport("KDFW", "Dallas/Fort Worth Intl", "Dallas", "US", "North America", "kdfw_app"),
    Airport("KMIA", "Miami Intl", "Miami", "US", "North America", "kmia_twr"),
    Airport("KLAS", "Harry Reid Intl", "Las Vegas", "US", "North America", "klas_app"),
    Airport("KPHX", "Phoenix Sky Harbor", "Phoenix", "US", "North America", "kphx_app"),
    Airport("KEWR", "Newark Liberty Intl", "Newark", "US", "North America", "kewr"),
    Airport("KLGA", "LaGuardia", "New York", "US", "North America", "klga"),
    // North America — Canada
    Airport("CYYZ", "Toronto Pearson Intl", "Toronto", "CA", "North America", "cyyz6"),
    Airport("CYVR", "Vancouver Intl", "Vancouver", "CA", "North America", "cyvr1_app"),
    // Europe — France / Benelux
    Airport("LFPG", "Charles de Gaulle", "Paris", "FR", "Europe", "lfpg3_dep"),
    Airport("EHAM", "Amsterdam Schiphol", "Amsterdam", "NL", "Europe", "eham4"),
    Airport("EBBR", "Brussels", "Brussels", "BE", "Europe", "ebbr_arr"),
    // Europe — South
    Airport("LEMD", "Adolfo Suárez Madrid–Barajas", "Madrid", "ES", "Europe", "lemd"),
    Airport("LIRF", "Leonardo da Vinci Fiumicino", "Rome", "IT", "Europe", "lirf"),
    Airport("LGAV", "Athens Intl", "Athens", "GR", "Europe", "lgav"),
    // Europe — North
    Airport("EKCH", "Copenhagen Kastrup", "Copenhagen", "DK", "Europe", "ekch_twr"),
    Airport("ENGM", "Oslo Gardermoen", "Oslo", "NO", "Europe", "engm2_app"),
    Airport("ESSA", "Stockholm Arlanda", "Stockholm", "SE", "Europe", "essa"),
    Airport("EFHK", "Helsinki-Vantaa", "Helsinki", "FI", "Europe", "efhk"),
    // Asia / Pacific
    Airport("RJTT", "Tokyo Haneda", "Tokyo", "JP", "Asia-Pacific", "rjtt_app_dep"),
    Airport("RJAA", "Tokyo Narita", "Tokyo", "JP", "Asia-Pacific", "rjaa_app_s"),
    Airport("RKSI", "Seoul Incheon", "Seoul", "KR", "Asia-Pacific", "rksi"),
    Airport("VHHH", "Hong Kong Intl", "Hong Kong", "HK", "Asia-Pacific", "vhhh5"),
    Airport("WSSS", "Singapore Changi", "Singapore", "SG", "Asia-Pacific", "wsss_app"),
    Airport("YSSY", "Sydney Kingsford Smith", "Sydney", "AU", "Asia-Pacific", "yssy1"),
    Airport("YMML", "Melbourne", "Melbourne", "AU", "Asia-Pacific", "ymml"),
    // Middle East
    Airport("OMDB", "Dubai Intl", "Dubai", "AE", "Middle East", "omdb_app"),
    Airport("OERK", "King Khalid Intl", "Riyadh", "SA", "Middle East", "oerk"),
    // Americas — South / Other
    Airport("SBGR", "São Paulo–Guarulhos", "São Paulo", "BR", "South America", "sbgr"),
    Airport("SAEZ", "Ezeiza Intl", "Buenos Aires", "AR", "South America", "saez"),
    // Africa
    Airport("FACT", "Cape Town Intl", "Cape Town", "ZA", "Africa", "fact"),
    Airport("HAAB", "Addis Ababa Bole", "Addis Ababa", "ET", "Africa", "haab"),
)
