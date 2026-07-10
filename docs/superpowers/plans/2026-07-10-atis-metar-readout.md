# ATIS/METAR Readout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show live METAR weather (temperature, dewpoint, humidity, QNH, wind) in the bottom-right corner of the COMM 1 · ATC module.

**Architecture:** New `MetarRepository` fetches JSON METAR from aviationweather.gov via the project's existing Retrofit + Gson + Hilt stack. `MainViewModel` exposes `atisData: StateFlow<AtisData?>`, refetched on airport change and every 10 minutes. A new `AtisReadout` composable renders three right-aligned lines beside the play button.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Retrofit 2 + Gson, Hilt, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-07-10-atis-metar-readout-design.md`

## Global Constraints

- API endpoint: `https://aviationweather.gov/api/data/metar?ids={icao}&format=json` (HTTPS, no key).
- Relative humidity computed with the Magnus formula (constants 17.625 and 243.04).
- Refresh interval: 10 minutes while an airport is selected.
- Network failures are silent: readout shows dimmed `ATIS ---`, never an `ERR:` line.
- AMBIENT module unchanged.
- All-caps avionics copy style; `labelSmall` typography; labels in `onSurfaceVariant`, values in module accent color.
- Test command: `./gradlew :app:testDebugUnitTest --tests "<class>"`. Compile check: `./gradlew :app:compileDebugKotlin`.
- Commit messages: plain imperative sentences (repo style, e.g. "Add ATIS domain model"), each ending with the line `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: `AtisData` domain model — Magnus humidity + display formatting

**Files:**
- Modify: `app/src/main/java/com/comsat/audio/data/model/Models.kt` (append at end)
- Test: `app/src/test/java/com/comsat/audio/data/model/AtisDataTest.kt` (create)

**Interfaces:**
- Consumes: nothing.
- Produces: `data class AtisData(tempC: Double, dewpointC: Double, qnhHpa: Int?, windDirDeg: Int?, windSpeedKt: Int?, observedUtc: String?)` with `val humidityPct: Int`, `val tempText: String`, `val dewText: String`, `val rhText: String`, `val qnhText: String`, `val windText: String`. Also top-level `fun relativeHumidity(tempC: Double, dewpointC: Double): Double` in the same file.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/comsat/audio/data/model/AtisDataTest.kt`:

```kotlin
package com.comsat.audio.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AtisDataTest {

    private fun atis(
        tempC: Double = 20.0,
        dewpointC: Double = 10.0,
        qnhHpa: Int? = 1013,
        windDirDeg: Int? = 320,
        windSpeedKt: Int? = 8
    ) = AtisData(tempC, dewpointC, qnhHpa, windDirDeg, windSpeedKt, observedUtc = null)

    // ─── Magnus relative humidity ─────────────────────────────────────────────

    @Test
    fun humidityIs100WhenTempEqualsDewpoint() {
        assertEquals(100.0, relativeHumidity(20.0, 20.0), 0.01)
    }

    @Test
    fun humidityForKnownPair20over10() {
        // Magnus with constants 17.625 / 243.04 gives 52.54%
        assertEquals(52.54, relativeHumidity(20.0, 10.0), 0.1)
    }

    @Test
    fun humidityForKnownPair30over0() {
        assertEquals(14.42, relativeHumidity(30.0, 0.0), 0.1)
    }

    @Test
    fun humidityPctRoundsToInt() {
        assertEquals(53, atis(tempC = 20.0, dewpointC = 10.0).humidityPct)
    }

    // ─── Display formatting ───────────────────────────────────────────────────

    @Test
    fun tempAndDewRoundToWholeDegrees() {
        val a = atis(tempC = 23.4, dewpointC = -5.4)
        assertEquals("23°", a.tempText)
        assertEquals("-5°", a.dewText)
    }

    @Test
    fun rhTextHasPercentSign() {
        assertEquals("53%", atis(tempC = 20.0, dewpointC = 10.0).rhText)
    }

    @Test
    fun qnhTextUsesQPrefix() {
        assertEquals("Q1013", atis(qnhHpa = 1013).qnhText)
    }

    @Test
    fun qnhTextPlaceholderWhenMissing() {
        assertEquals("Q----", atis(qnhHpa = null).qnhText)
    }

    @Test
    fun windTextZeroPadsSpeed() {
        assertEquals("320°/08KT", atis(windDirDeg = 320, windSpeedKt = 8).windText)
    }

    @Test
    fun windTextCalmWhenSpeedZero() {
        assertEquals("CALM", atis(windSpeedKt = 0).windText)
    }

    @Test
    fun windTextVariableWhenDirectionMissing() {
        assertEquals("VRB/05KT", atis(windDirDeg = null, windSpeedKt = 5).windText)
    }

    @Test
    fun windTextPlaceholderWhenSpeedMissing() {
        assertEquals("---", atis(windSpeedKt = null).windText)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.comsat.audio.data.model.AtisDataTest"`
Expected: FAIL — compilation error, `AtisData` unresolved.

- [ ] **Step 3: Write the implementation**

Append to `app/src/main/java/com/comsat/audio/data/model/Models.kt`:

```kotlin
// ─── ATIS / METAR ─────────────────────────────────────────────────────────────

// Magnus formula; METAR reports dewpoint but not humidity directly
fun relativeHumidity(tempC: Double, dewpointC: Double): Double {
    fun gamma(t: Double) = 17.625 * t / (243.04 + t)
    return 100.0 * kotlin.math.exp(gamma(dewpointC) - gamma(tempC))
}

data class AtisData(
    val tempC: Double,
    val dewpointC: Double,
    val qnhHpa: Int?,
    val windDirDeg: Int?,      // null with non-zero speed means variable (VRB)
    val windSpeedKt: Int?,
    val observedUtc: String?
) {
    val humidityPct: Int
        get() = relativeHumidity(tempC, dewpointC).roundToInt()

    val tempText: String get() = "${tempC.roundToInt()}°"
    val dewText: String get() = "${dewpointC.roundToInt()}°"
    val rhText: String get() = "$humidityPct%"
    val qnhText: String get() = qnhHpa?.let { "Q$it" } ?: "Q----"

    val windText: String
        get() = when {
            windSpeedKt == null -> "---"
            windSpeedKt == 0 -> "CALM"
            windDirDeg == null -> "VRB/%02dKT".format(windSpeedKt)
            else -> "%d°/%02dKT".format(windDirDeg, windSpeedKt)
        }
}
```

Add to the imports at the top of `Models.kt`:

```kotlin
import kotlin.math.roundToInt
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.comsat.audio.data.model.AtisDataTest"`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/comsat/audio/data/model/Models.kt app/src/test/java/com/comsat/audio/data/model/AtisDataTest.kt
git commit -m "Add ATIS domain model with Magnus humidity and readout formatting

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: `MetarDto` mapping, `MetarApi`, `MetarRepository`, DI

**Files:**
- Modify: `app/src/main/java/com/comsat/audio/data/model/Models.kt` (append)
- Create: `app/src/main/java/com/comsat/audio/data/api/MetarApi.kt`
- Create: `app/src/main/java/com/comsat/audio/data/repository/MetarRepository.kt`
- Modify: `app/src/main/java/com/comsat/audio/di/AppModule.kt`
- Test: `app/src/test/java/com/comsat/audio/data/model/MetarDtoTest.kt` (create)

**Interfaces:**
- Consumes: `AtisData` from Task 1.
- Produces: `MetarDto.toAtisData(): AtisData?`; `interface MetarApi { suspend fun getMetar(icao: String, format: String = "json"): List<MetarDto> }`; `class MetarRepository { suspend fun fetchMetar(icao: String): AtisData? }` (Hilt `@Singleton`, injectable by constructor).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/comsat/audio/data/model/MetarDtoTest.kt`:

```kotlin
package com.comsat.audio.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetarDtoTest {

    @Test
    fun mapsAllFields() {
        val dto = MetarDto(
            temp = 23.4, dewp = 14.1, wdir = "320", wspd = 8.0,
            altim = 1013.2, reportTime = "2026-07-10 11:30:00"
        )
        val atis = dto.toAtisData()!!
        assertEquals(23.4, atis.tempC, 0.001)
        assertEquals(14.1, atis.dewpointC, 0.001)
        assertEquals(1013, atis.qnhHpa)
        assertEquals(320, atis.windDirDeg)
        assertEquals(8, atis.windSpeedKt)
        assertEquals("2026-07-10 11:30:00", atis.observedUtc)
    }

    @Test
    fun variableWindDirectionMapsToNull() {
        val dto = MetarDto(temp = 20.0, dewp = 10.0, wdir = "VRB", wspd = 5.0)
        assertNull(dto.toAtisData()!!.windDirDeg)
        assertEquals(5, dto.toAtisData()!!.windSpeedKt)
    }

    @Test
    fun missingTempOrDewpointYieldsNull() {
        assertNull(MetarDto(temp = null, dewp = 10.0).toAtisData())
        assertNull(MetarDto(temp = 20.0, dewp = null).toAtisData())
    }

    @Test
    fun missingOptionalFieldsStayNull() {
        val atis = MetarDto(temp = 20.0, dewp = 10.0).toAtisData()!!
        assertNull(atis.qnhHpa)
        assertNull(atis.windDirDeg)
        assertNull(atis.windSpeedKt)
        assertNull(atis.observedUtc)
    }

    // wdir arrives as a JSON number normally and as the string "VRB" for
    // variable wind; the DTO declares String so Gson accepts both
    @Test
    fun gsonParsesNumericAndStringWdir() {
        val gson = Gson()
        val numeric = gson.fromJson(
            """{"temp":20.0,"dewp":10.0,"wdir":320,"wspd":8.0}""", MetarDto::class.java
        )
        assertEquals(320, numeric.toAtisData()!!.windDirDeg)

        val variable = gson.fromJson(
            """{"temp":20.0,"dewp":10.0,"wdir":"VRB","wspd":8.0}""", MetarDto::class.java
        )
        assertNull(variable.toAtisData()!!.windDirDeg)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.comsat.audio.data.model.MetarDtoTest"`
Expected: FAIL — compilation error, `MetarDto` unresolved.

- [ ] **Step 3: Write the DTO and mapping**

Append to `app/src/main/java/com/comsat/audio/data/model/Models.kt`:

```kotlin
data class MetarDto(
    @SerializedName("temp") val temp: Double? = null,
    @SerializedName("dewp") val dewp: Double? = null,
    // JSON number for degrees, or the string "VRB" for variable wind
    @SerializedName("wdir") val wdir: String? = null,
    @SerializedName("wspd") val wspd: Double? = null,
    @SerializedName("altim") val altim: Double? = null,
    @SerializedName("reportTime") val reportTime: String? = null
) {
    fun toAtisData(): AtisData? {
        if (temp == null || dewp == null) return null
        return AtisData(
            tempC = temp,
            dewpointC = dewp,
            qnhHpa = altim?.roundToInt(),
            windDirDeg = wdir?.toDoubleOrNull()?.roundToInt(),
            windSpeedKt = wspd?.roundToInt(),
            observedUtc = reportTime
        )
    }
}
```

(`SerializedName` and `roundToInt` are already imported in `Models.kt`.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.comsat.audio.data.model.MetarDtoTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Add the API interface**

Create `app/src/main/java/com/comsat/audio/data/api/MetarApi.kt`:

```kotlin
package com.comsat.audio.data.api

import com.comsat.audio.data.model.MetarDto
import retrofit2.http.GET
import retrofit2.http.Query

interface MetarApi {
    @GET("api/data/metar")
    suspend fun getMetar(
        @Query("ids") icao: String,
        @Query("format") format: String = "json"
    ): List<MetarDto>
}
```

- [ ] **Step 6: Add the repository**

Create `app/src/main/java/com/comsat/audio/data/repository/MetarRepository.kt`:

```kotlin
package com.comsat.audio.data.repository

import com.comsat.audio.data.api.MetarApi
import com.comsat.audio.data.model.AtisData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetarRepository @Inject constructor(
    private val metarApi: MetarApi
) {
    // ATIS is auxiliary panel data; any failure degrades to "no data"
    suspend fun fetchMetar(icao: String): AtisData? = withContext(Dispatchers.IO) {
        runCatching { metarApi.getMetar(icao).firstOrNull()?.toAtisData() }.getOrNull()
    }
}
```

- [ ] **Step 7: Wire DI**

In `app/src/main/java/com/comsat/audio/di/AppModule.kt` add the import:

```kotlin
import com.comsat.audio.data.api.MetarApi
```

and append inside `object AppModule` after `provideSomaFmApi`:

```kotlin
    @Provides
    @Singleton
    fun provideMetarApi(client: OkHttpClient): MetarApi =
        Retrofit.Builder()
            .baseUrl("https://aviationweather.gov/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MetarApi::class.java)
```

- [ ] **Step 8: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/comsat/audio/data/model/Models.kt app/src/main/java/com/comsat/audio/data/api/MetarApi.kt app/src/main/java/com/comsat/audio/data/repository/MetarRepository.kt app/src/main/java/com/comsat/audio/di/AppModule.kt app/src/test/java/com/comsat/audio/data/model/MetarDtoTest.kt
git commit -m "Add METAR API, DTO mapping and repository

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: `MainViewModel.atisData` flow

**Files:**
- Modify: `app/src/main/java/com/comsat/audio/viewmodel/MainViewModel.kt`

**Interfaces:**
- Consumes: `MetarRepository.fetchMetar(icao: String): AtisData?` from Task 2; existing `_selectedAirport: MutableStateFlow<Airport?>`.
- Produces: `val atisData: StateFlow<AtisData?>` on `MainViewModel`.

- [ ] **Step 1: Add the flow and collector**

In `app/src/main/java/com/comsat/audio/viewmodel/MainViewModel.kt`:

Add imports:

```kotlin
import com.comsat.audio.data.model.AtisData
import com.comsat.audio.data.repository.MetarRepository
import kotlinx.coroutines.flow.collectLatest
```

Add `metarRepo` to the constructor:

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val liveAtcRepo: LiveAtcRepository,
    private val somaRepo: SomaFmRepository,
    private val settingsRepo: SettingsRepository,
    private val metarRepo: MetarRepository
) : AndroidViewModel(application) {
```

After the `// ─── Airport state ───` block (below `val selectedAirport`), add:

```kotlin
    // ─── ATIS / METAR state ───────────────────────────────────────────────────

    private val _atisData = MutableStateFlow<AtisData?>(null)
    val atisData: StateFlow<AtisData?> = _atisData
```

In `init`, after `loadStations()`, add:

```kotlin
        observeAtis()
```

Add the collector method (near `loadStations`):

```kotlin
    // METAR updates roughly twice an hour; refetch on airport change, then poll.
    // Failed fetches keep the last value — collectLatest resets it per airport.
    private fun observeAtis() {
        viewModelScope.launch {
            _selectedAirport.collectLatest { airport ->
                _atisData.value = null
                if (airport == null) return@collectLatest
                while (true) {
                    metarRepo.fetchMetar(airport.icao)?.let { _atisData.value = it }
                    delay(10 * 60 * 1000L)
                }
            }
        }
    }
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (no regressions).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/comsat/audio/viewmodel/MainViewModel.kt
git commit -m "Expose ATIS data flow with 10-minute refresh in MainViewModel

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: `AtisReadout` composable + ATC module wiring

**Files:**
- Modify: `app/src/main/java/com/comsat/audio/ui/components/AvionicsComponents.kt` (append)
- Modify: `app/src/main/java/com/comsat/audio/ui/screen/MainScreen.kt`

**Interfaces:**
- Consumes: `AtisData` display properties (`tempText`, `dewText`, `rhText`, `qnhText`, `windText`) from Task 1; `viewModel.atisData` from Task 3.
- Produces: `@Composable fun AtisReadout(data: AtisData?, accent: Color, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Add the `AtisReadout` composable**

Append to `app/src/main/java/com/comsat/audio/ui/components/AvionicsComponents.kt`:

```kotlin
// ─── ATIS readout: METAR-derived weather beside the transport button ─────────

@Composable
fun AtisReadout(
    data: AtisData?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (data == null) {
            Text(
                text = "ATIS ---",
                style = MaterialTheme.typography.labelSmall,
                color = dim
            )
        } else {
            AtisLine(accent, "TEMP" to data.tempText, "DEW" to data.dewText)
            AtisLine(accent, "RH" to data.rhText, "" to data.qnhText)
            AtisLine(accent, "WND" to data.windText)
        }
    }
}

@Composable
private fun AtisLine(accent: Color, vararg parts: Pair<String, String>) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            parts.forEachIndexed { i, (label, value) ->
                if (i > 0) append("  ")
                if (label.isNotEmpty()) {
                    withStyle(SpanStyle(color = dim)) { append(label) }
                    append(" ")
                }
                withStyle(SpanStyle(color = accent)) { append(value) }
            }
        },
        style = MaterialTheme.typography.labelSmall
    )
}
```

Add imports to `AvionicsComponents.kt`:

```kotlin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.comsat.audio.data.model.AtisData
```

- [ ] **Step 2: Wire into `MainScreen`**

In `app/src/main/java/com/comsat/audio/ui/screen/MainScreen.kt`:

Collect the state in `MainScreen` (after `somaSpectrum`):

```kotlin
    val atisData     by viewModel.atisData.collectAsState()
```

Add import:

```kotlin
import com.comsat.audio.ui.components.AtisReadout
```

Add an optional corner slot to `StreamModuleBody` (last parameter):

```kotlin
private fun StreamModuleBody(
    sourceName: String,
    subLabel: String?,
    state: StreamState,
    spectrum: FloatArray,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggle: () -> Unit,
    onSelectSource: () -> Unit,
    accent: Color,
    cornerContent: (@Composable () -> Unit)? = null
) {
```

Replace the bare `SquareToggleButton(...)` call inside `StreamModuleBody` with:

```kotlin
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        SquareToggleButton(
            active = state.isActive,
            accent = accent,
            onClick = onToggle,
            description = if (state.isActive) "Pause" else "Play"
        )
        Spacer(modifier = Modifier.weight(1f))
        cornerContent?.invoke()
    }
```

In the ATC `StreamModuleBody` call (inside `AvionicsModule(title = "COMM 1 · ATC", ...)`), add after `accent = MaterialTheme.colorScheme.primary`:

```kotlin
                    cornerContent = {
                        AtisReadout(
                            data = atisData,
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
```

The AMBIENT call stays untouched — `cornerContent` defaults to `null`, keeping its bottom row identical apart from the wrapping `Row`, which renders the same.

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual device verification**

Install and open the app with an airport selected. Verify:
- Bottom-right of the ATC module shows three lines (TEMP/DEW, RH/Q, WND) in panel style.
- Values look plausible for the selected airport (compare against the airport's current METAR).
- With no airport selected, the corner shows dimmed `ATIS ---`.
- AMBIENT module bottom row is visually unchanged.
- In airplane mode, readout falls back to `ATIS ---` without any error text.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/comsat/audio/ui/components/AvionicsComponents.kt app/src/main/java/com/comsat/audio/ui/screen/MainScreen.kt
git commit -m "Show ATIS/METAR readout in ATC module corner

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
