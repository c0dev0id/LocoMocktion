# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

LocoMocktion is an Android app that mocks the device's location. It replays a GPX
track as moving GPS coordinates (at a configurable speed / travel mode) or holds a
single fixed coordinate indefinitely. Package: `de.codevoid.locomocktion`.

## Build & CI

**Do not build Android projects locally.** AGP is unreachable behind a firewall and
builds are done by CI. Reference only:

- Debug APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `./gradlew assembleRelease -PversionName=<tag>` (needs `SIGNING_*` env vars)

`versionName` is injected at build time via `-PversionName=`; it defaults to `"dev"`
when unset. `BuildConfig.VERSION_NAME` is the single source of truth the in-app
update checker compares against GitHub releases.

CI workflows (`.github/workflows/`):
- `build.yml` — on push to `main`: builds a signed **nightly** prerelease and force-moves
  the rolling `nightly` tag. Debug builds run only for `claude/**` branches.
- `release.yml` — manual `workflow_dispatch`; builds signed APK, auto-increments the
  patch version from the latest `vX.Y.Z` tag (or takes an explicit input), publishes a draft.

To pull the latest CI APK onto a device and grant all required permissions/appops,
use `reinstall.sh` (uses `gh` + `adb`; includes the crucial
`adb shell appops set … android:mock_location allow`).

No test suite exists yet.

## Architecture

Single-activity Jetpack Compose (Material3) app. MVVM with `StateFlow`. No navigation
library — one `HomeScreen` composable renders everything off a single `UiState`.

Data flow: `MainActivity` → `MainViewModel` (`UiState` + settings persistence) →
`MockLocationService` (the engine) → `GpxParser` (input).

### The service is the engine — and it's configured statically

`MockLocationService` is a `LifecycleService` foreground service. Its coupling to the
ViewModel is deliberately *not* via binding:

- Live output (`isRunning`, `progress`, `currentPoint`, `distanceTraveled`) is exposed
  as **companion-object `StateFlow`s** the ViewModel simply re-exposes.
- Input is set via **static `configure(...)` / `configureFixed(...)`** *before*
  `startForegroundService()`. The service reads that static config in `onStartCommand`.
- Stop is a self-`Intent` with `action = "STOP"`.

Don't "fix" this into a bound service — the static-config pattern is intentional and keeps
the ViewModel↔service handoff trivial.

### Why mocking is done two ways at once

To keep navigation apps (e.g. DMD2 Next) locked onto the mock and avoid GPS flicker, the
service does **both** of these every tick — do not reduce it to just `GPS_PROVIDER`:

1. Registers **every non-passive `LocationManager` provider** as a test provider at runtime
   (`allProviders` minus `PASSIVE_PROVIDER`) and pushes locations to all of them. This
   covers GPS, network, fused, and OEM providers so apps can't fall back to a real one.
2. Puts **FusedLocationProvider itself into mock mode** (`setMockMode(true)` +
   `setMockLocation`). Without this, FLP duty-cycles the GPS hardware every ~10 s and the
   ~1 s gap leaks the real position. The git history ("Mock FLP directly…") is this fix —
   don't regress it.

`PASSIVE_PROVIDER` is never registered; it reflects the others for free.

### Track playback (`walkTrack` / `walkBetween`)

Positions are interpolated per-segment along the track. Speed is either the user's
`speedKmh` (converted to m/s) or, when `useGpxSpeed` is on and the points carry speed,
interpolated from GPX speed. `TravelMode` (`Normal` / `Loop` / `Bounce`) controls what
happens at the end. `startKm`/`endKm` window the played range. Bearing is computed
great-circle style between consecutive points.

Fixed mode (`fixedLoop`) just re-emits one coordinate forever at `updateIntervalMs`.

### GPX parsing (`GpxParser.kt`)

`XmlPullParser`, **namespace-agnostic** (matches on local tag name via
`substringAfterLast(':')`, so `gpxtpx:speed` works). Each `<trk>` becomes one `GpxTrack`;
multi-track files trigger a selection dialog. Per-point speed comes from an explicit
`<speed>`/extension, else it's **derived from `<time>` timestamps**
(`distance / Δt`, last point inherits the previous segment).

### Settings & persistence

`SharedPreferences` named `"locomocktion"`. Speed, custom-speed mode, interval, travel
mode, GPX-speed toggle, unit system, fixed-location fields, and the last-loaded GPX
(uri/name/track-index + start/end km) are all persisted and restored in
`MainViewModel.init`, which auto-reloads the last track on launch.

### Updater

`UpdateChecker.fetchLatestRelease()` hits the GitHub releases API for
`c0dev0id/locomocktion`, finds the first `.apk` asset. `isNewerVersion` is a plain
**tag-string inequality** (not semver parsing) against `BuildConfig.VERSION_NAME`.
`ApkInstaller`/`FileProvider` handle the download+install flow.

## Runtime prerequisites

The app is inert until the user sets it as the **Mock location app** in Developer
options. `util/isMockLocationEnabled` checks the `mock_location` appop. `MainActivity`
requests location + notification permissions and battery-optimization exemption on launch.

## Conventions specific to this repo

- No database / schema-migration code while version < 1.0.0.
- Keep the dependency footprint small; prefer existing APIs over new libraries.
- Update `CHANGELOG.md` (Keep a Changelog format) after user-facing changes and keep
  `.github/development-journal.md` current.
