# ATIS/METAR Readout in the ATC Module — Design

**Date:** 2026-07-10
**Status:** Approved

## Goal

Show live airport weather (ATIS-style data) in the free bottom-right corner of the
COMM 1 · ATC module, below the spectrum visualization. Real ATIS text is not
publicly available, so the data source is the METAR report for the selected
airport's ICAO code.

## Data Source

NOAA Aviation Weather Center API (free, no API key, HTTPS):

```
GET https://aviationweather.gov/api/data/metar?ids={icao}&format=json
```

Relevant JSON fields: `temp` (°C), `dewp` (°C), `wdir` (degrees), `wspd` (knots),
`altim` (hPa), `reportTime`.

## Architecture

### Data layer

- `MetarApi` — Retrofit interface for the endpoint above, using the project's
  existing Retrofit + Gson stack.
- `MetarDto` — Gson DTO matching the JSON fields listed above.
- `AtisData` — domain model:
  `AtisData(tempC, dewpointC, humidityPct, qnhHpa, windDirDeg, windSpeedKt, observedUtc)`.
  Relative humidity is computed from temperature and dewpoint with the Magnus
  formula (METAR has no direct humidity field).
- `MetarRepository` — Hilt singleton, same pattern as `SomaFmRepository`.
  `suspend fun fetchMetar(icao: String): AtisData?` — returns `null` on any
  failure (network, parse, empty response).

### ViewModel

`MainViewModel` gains `atisData: StateFlow<AtisData?>`:

- On `selectedAirport` change: immediate fetch, then refresh every 10 minutes
  while an airport is selected.
- Airport becomes `null` → state resets to `null`.
- Fetch failure: keep the previous value if the airport is unchanged, otherwise
  reset to `null`.

### UI

- New composable `AtisReadout(data: AtisData?, accent: Color)` in
  `AvionicsComponents.kt`.
- `MainScreen`: the ATC module's bottom row becomes
  `Row { SquareToggleButton; Spacer(weight = 1f); AtisReadout }`.
  The AMBIENT module is unchanged — `StreamModuleBody` gets an optional slot
  parameter that defaults to empty.
- Display format — three right-aligned lines in `labelSmall` panel style:

  ```
  TEMP 23° DEW 14°
  RH 57%  Q1013
  WND 320°/08KT
  ```

- Calm wind (`wspd == 0`) → `WND CALM`.
- No data (`null`) → dimmed placeholder `ATIS ---` in `onSurfaceVariant`.
- Colors: numbers in the module accent color, labels in `onSurfaceVariant`,
  matching the existing VOL readout style.

## Error Handling

- Network failures are silent: the readout shows the placeholder; no `ERR:`
  line is added (ATIS is auxiliary information and must not alarm).
- Timeouts come from the existing injected OkHttp client configuration.
- Endpoint is HTTPS — no `network_security_config.xml` change needed.

## Testing

- Unit tests: Magnus RH calculation (known pairs, e.g. temp == dewpoint → 100%),
  `MetarDto` → `AtisData` mapping, wind-calm and placeholder formatting.
- Repository and UI verified manually on device.

## Out of Scope

- Visibility, cloud layers, raw METAR string display.
- ATIS audio decoding.
- Weather for the AMBIENT module.
