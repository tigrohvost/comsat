# COMSAT

Dual-stream ambient audio player for Android: live air-traffic-control radio
from [LiveATC](https://www.liveatc.net/) mixed with ambient music from
[SomaFM](https://somafm.com/), each with its own volume fader. Tune a tower
frequency, put Drone Zone underneath it, and get that "lofi ATC" atmosphere
with full control over the mix.

Inspired by [listen to the.cloud](https://listentothe.cloud/), which pioneered
the ATC-plus-ambient mix in the browser.

![Main screen](comsat_mockup.png)

## Features

- **Two independent streams** — an ATC feed and a SomaFM station play
  simultaneously through separate ExoPlayer instances with per-stream volume
- **Curated airport catalog** with region grouping, search (ICAO / city /
  country) and live online/offline status for each feed
- **Full SomaFM directory** with artwork, genres, listener counts and search
- **Background playback** as a foreground media service: lock-screen /
  headset controls drive both streams through a single MediaSession
- **Resilient streaming** — automatic reconnect with backoff, audio-focus
  handling (ducking, transient loss), pause on headphone unplug
- **Three themes** — cyberpunk dark, high-contrast light, Nord
- **Persistent settings** — volumes, selected sources and theme survive
  restarts (DataStore)

| Airports | Stations |
|---|---|
| ![Airports](comsat_airports.png) | ![Stations](comsat_stations.png) |

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Media3/ExoPlayer · Hilt · Retrofit +
OkHttp · Coil · DataStore · single-activity Navigation Compose.

```
app/src/main/java/com/comsat/audio/
├── data/          # models, SomaFM API, LiveATC + settings repositories
├── di/            # Hilt modules
├── service/       # AudioService: two ExoPlayers behind one MediaSession
├── ui/            # Compose screens, cyberpunk components, themes
└── viewmodel/     # MainViewModel: service binding + UI state
```

## Building

Requires JDK 17–21 (Gradle 8.9 does not run on newer JDKs) and the Android
SDK (compileSdk 35). minSdk is 26 (Android 8.0).

```bash
./gradlew assembleDebug
```

### Release build

The release build is minified and signed. Signing credentials are read from
environment variables so they never enter the repository:

```bash
export COMSAT_KEYSTORE=~/.android/comsat-release.keystore
export COMSAT_KEYSTORE_PASSWORD=…
export COMSAT_KEY_ALIAS=comsat
export COMSAT_KEY_PASSWORD=…
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

## Streams

Audio comes from third-party services — [LiveATC](https://www.liveatc.net/)
(ATC feeds) and [SomaFM](https://somafm.com/) (listener-supported radio;
consider [supporting them](https://somafm.com/support/)). This app is not
affiliated with either. Feed availability depends on volunteer-run receivers
and can change at any time.
