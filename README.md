<div align="center">

<h1>COMSAT</h1>

<p><strong>Air traffic above. Atmosphere below.</strong><br>
An Android audio panel that mixes live ATC with ambient radio — two streams,
two faders, one cockpit-inspired interface.</p>

<p><a href="https://github.com/tigrohvost/comsat/actions/workflows/android-ci.yml"><img alt="Android CI" src="https://github.com/tigrohvost/comsat/actions/workflows/android-ci.yml/badge.svg?branch=main"></a> <img alt="Android 8+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white"> <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&amp;logo=kotlin&amp;logoColor=white"> <img alt="Compose" src="https://img.shields.io/badge/Jetpack-Compose-4285F4?style=flat-square&amp;logo=jetpackcompose&amp;logoColor=white"></p>

<p><a href="https://github.com/tigrohvost/comsat/releases/latest/download/COMSAT.apk"><strong>Download the latest signed APK</strong></a></p>

<img src="comsat_mockup.png" width="520" alt="COMSAT cockpit-inspired dual-stream interface">

<sub>Interface concept. The app UI is implemented natively with Jetpack Compose.</sub>

</div>

## Two channels, one atmosphere

| `COMM 1 · ATC` | `COMM 2 · AMBIENT` | `PANEL` |
|---|---|---|
| LiveATC airport feeds, availability probes and METAR/ATIS weather | The SomaFM catalog plus Rain Radio's generated ambient tracks | Independent volume, live FFT spectra, themes and persistent selections |

Playback continues in the background through a foreground media service. Lock
screen and headset controls drive both ExoPlayer instances through one
MediaSession; reconnect backoff, audio focus, ducking and unplug protection are
built in.

## Get on air

1. Download [`COMSAT.apk`](https://github.com/tigrohvost/comsat/releases/latest/download/COMSAT.apk).
2. Install it on Android 8.0 or newer and allow notifications for background controls.
3. Pick an airport, pick an ambient station and balance the two faders.

> [!TIP]
> Headphones make the mix more immersive — and keep an unexpected tower feed
> from reaching the room. COMSAT pauses automatically when they disconnect.

<details>
<summary><strong>Airport and station selectors</strong></summary>

| Airports | Stations |
|---|---|
| <img src="comsat_airports.png" alt="Airport selector concept" width="390"> | <img src="comsat_stations.png" alt="Station selector concept" width="390"> |

</details>

## Under the panel

`Kotlin` · `Jetpack Compose / Material 3` · `Media3 / ExoPlayer` · `Hilt` ·
`Retrofit / OkHttp` · `DataStore` · `Coil`

The spectrum display taps decoded PCM with `TeeAudioProcessor`, so it needs no
microphone permission. Release builds are minified and resource-shrunk; GitHub
Actions tests, lints, signs and verifies every tagged APK, then publishes it
with a SHA-256 checksum.

<details>
<summary><strong>Build from source</strong></summary>

Requires JDK 21 and Android SDK 37 with Build Tools 37.0.0.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

For a signed release, provide the keystore through environment variables:

```bash
export COMSAT_KEYSTORE=/absolute/path/to/comsat-release.keystore
export COMSAT_KEYSTORE_PASSWORD='<store password>'
export COMSAT_KEY_ALIAS=comsat
export COMSAT_KEY_PASSWORD='<key password>'
./gradlew assembleRelease
```

Without signing variables, Gradle intentionally produces an unsigned release
APK. Credentials and keystores are ignored by Git and never belong in the
repository.

</details>

<details>
<summary><strong>Publish a release</strong></summary>

Set the repository secrets `COMSAT_KEYSTORE_BASE64` and
`COMSAT_KEYSTORE_PASSWORD`, update `versionName` / `versionCode`, then push a
matching semantic tag:

```bash
git tag -a v1.2.0 -m "COMSAT 1.2.0"
git push origin v1.2.0
```

The release workflow rejects a tag that does not match the app version. A
successful run publishes stable assets named `COMSAT.apk` and
`COMSAT.apk.sha256`, so the download link at the top always follows the latest
release.

</details>

## Signal sources

ATC audio comes from [LiveATC](https://www.liveatc.net/); ambient stations come
from listener-supported [SomaFM](https://somafm.com/) and Rain Radio. COMSAT is
not affiliated with LiveATC or SomaFM. Feeds depend on third-party and
volunteer-run infrastructure, so individual stations can disappear or go
offline at any time.

Inspired by [listen to the.cloud](https://listentothe.cloud/), the original
browser-based ATC + ambient mix.
