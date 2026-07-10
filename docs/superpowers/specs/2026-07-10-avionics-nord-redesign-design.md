# COMSAT avionics-Nord redesign

Date: 2026-07-10
Status: approved (direction iterated with the user in claude.ai/design project
"COMSAT Redesign", cards `concepts/avionics-nord.html` and
`concepts/viz-variants.html`)

## Direction

Replace the neon-cyberpunk look with an aircraft instrument panel ("avionics")
aesthetic rendered in the Nord palette. Key traits, per the approved mockup:

- Polar-night surfaces (#2E3440 / #3B4252), 1px outlines (#4C566A), squared
  corners, no neon glow. Monospace typography everywhere (already in place).
- Per-channel accent colors: ATC = Frost blue `#88C0D0`, ambient = Aurora
  green `#A3BE8C`. Status LEDs, faders, readouts and spectra follow the
  channel color.
- Stream cards become "modules": header strip with LED + `COMM 1 · ATC` /
  `COMM 2 · AMBIENT` title + bordered status placard (LIVE / STBY / …), body
  with station name, spectrum strip, tick fader with 3-digit readout, square
  play/pause button, bordered CHANGE button.
- App header: COMSAT brand + "COMM AUDIO PANEL" subtitle, live UTC clock on
  the right. Decorative footer placard: `PWR ● NORM · SQL AUTO · XPDR 7000`.
- Background: the existing drifting vector world map gains a 10° graticule
  with axis labels (`W070`, `N40`) and a crosshair-ring marker with ICAO label
  on the tuned airport. Muted line colors; module panels are slightly
  translucent (~72% alpha) so the chart ghosts through.

Nord becomes the default theme for fresh installs. DARK (cyberpunk) and LIGHT
remain selectable; the new components draw from `MaterialTheme.colorScheme`
(ATC accent = `primary`, ambient accent = `tertiary`), so every theme keeps
working.

## Real spectrum analyzer (Winamp V1 style)

The mockup's spectrum is fake; the app renders the real signal.

- **Tap**: each ExoPlayer gets a `TeeAudioProcessor` (Media3) wired in via a
  custom `DefaultRenderersFactory.buildAudioSink` override. Its
  `AudioBufferSink` receives raw 16-bit PCM on the playback thread — no
  `RECORD_AUDIO` permission, works per player, pre-fader.
- **Analysis** (`SpectrumProcessor`): mono-mix into a 1024-sample window,
  Hann window, radix-2 FFT, magnitudes folded into 24 log-spaced bands
  (~50 Hz – 16 kHz, clamped to Nyquist), converted to dB with a -60 dB floor,
  normalized 0..1. Published as `StateFlow<FloatArray>` throttled to ~30 Hz.
- **Service → UI**: `AudioService` exposes `atcSpectrum` / `somaSpectrum`;
  `MainViewModel` proxies them like the existing state flows.
- **Rendering** (`SpectrumBar` composable): Canvas strip inside each module —
  dark screen inset with 1px border and `SPECTRUM` tag. Bars rise instantly,
  decay slowly; snow-storm peak caps fall slower still (Winamp behavior).
  Bands are scaled by the channel volume so the display follows the fader.
  When the stream is not playing the bars decay to silence. The 30 fps
  animation loop runs only while the screen is visible.

## Code changes

- `service/SpectrumProcessor.kt` — new: buffer sink + FFT + band folding.
- `service/AudioService.kt` — renderers factory override, two processors,
  spectrum flows.
- `viewmodel/MainViewModel.kt` — proxy spectrum flows.
- `ui/components/AvionicsComponents.kt` — new: `AvionicsModule`,
  `ModuleHeader` (LED + placard), `TickFader` (tick track, rectangular thumb,
  digital readout), `SquareButton`, `PlacardButton`, footer placard.
- `ui/components/SpectrumBar.kt` — new: canvas spectrum.
- `ui/components/WorldMapBackground.kt` — graticule + labels + crosshair
  marker; muted colors.
- `ui/screen/MainScreen.kt` — rebuild with modules, UTC clock header, footer.
- `ui/screen/AirportListScreen.kt`, `StationListScreen.kt` — restyle accents
  to the new component language (bordered placards, squared corners, no glow).
- `ui/components/CyberpunkComponents.kt` — drop `NeonCard`/`NeonSlider`/glow
  once unused; keep search field, dots, empty message.
- `ui/theme/Theme.kt`, `SettingsRepository.kt`, `MainActivity.kt` — default
  theme NORDIC.
- README screenshots regenerated later.

## Out of scope

- No tab bar (navigation stays: CHANGE buttons push list screens).
- No changes to streaming, reconnect, MediaSession, or persistence logic.
- Light/dark cyberpunk themes are kept but not redesigned beyond what the
  shared components change.
