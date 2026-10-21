# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

LocoMocktion is an Android app that mocks the device's location. It replays a GPX
track as moving GPS coordinates (at a configurable speed / travel mode) or holds a
single fixed coordinate indefinitely. Package: `de.codevoid.locomocktion`.
minSdk 26, targetSdk 35, Kotlin 2.1, Jetpack Compose with Material3, JDK 17.

## Build & CI

**Do not build Android projects locally.** AGP is unreachable behind a firewall and
builds are done by CI. Reference only:

- Debug APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `./gradlew assembleRelease -PversionName=<tag>` (needs `SIGNING_*` env vars)

`versionName` is injected at build time via `-PversionName=`; it defaults to `"dev"`
when unset. `BuildConfig.VERSION_NAME` is the single source of truth the in-app
update checker compares against GitHub releases.

Release builds are minified (R8). The only keep rule in `proguard-rules.pro` is for
the `gpx` package.

CI workflows (`.github/workflows/`):
- `build.yml` — fires **only on push to `main`**. The `nightly` job builds a signed
  release APK with `-PversionName=nightly`, deletes the previous `nightly` release,
  force-moves the rolling `nightly` tag to HEAD and publishes a prerelease. The
  `debug-build` job in the same file is unreachable: its `if` matches `claude/**`
  refs but the workflow never triggers for them.
- `release.yml` — manual `workflow_dispatch`; takes an explicit `vX.Y.Z` input or
  auto-increments the patch of the latest `v*` tag, builds the signed APK, pushes the
  tag and publishes a **draft** release with generated notes.

`reinstall.sh` (uses `gh` + `adb`) installs the APK on a device and grants all
required permissions, including the crucial
`adb shell appops set … android:mock_location allow`. It downloads the `app-debug`
artifact of the latest Build run, which no job produces anymore, so the download
step currently fails.

No test sources, test dependencies, or lint tooling exist. Adding tests means adding
`testImplementation` dependencies to `app/build.gradle.kts` first.

## Architecture

Single-activity Jetpack Compose app. MVVM with `StateFlow`. No navigation
library, no DI, no HTTP library (`HttpURLConnection` + `org.json`).

Data flow: `MainActivity` → `MainViewModel` (`UiState` + settings persistence) →
`MockLocationService` (the engine) → `GpxParser` (input).

### The service is the engine — and it's configured statically

`MockLocationService` is a `LifecycleService` foreground service
(`foregroundServiceType="location"`). Its coupling to the ViewModel is deliberately
*not* via binding:

- Live output (`isRunning`, `progress`, `currentPoint`, `distanceTraveled`) is exposed
  as **companion-object `StateFlow`s** the ViewModel simply re-exposes. Because they
  are process-global, the UI stays correct when the service outlives the activity.
- Input is set via **static `configure(...)` / `configureFixed(...)`** *before*
  `startForegroundService()`. The service reads that static config in `onStartCommand`
  and stops itself if nothing was configured.
- Stop is a self-`Intent` with `action = "STOP"` (also used by the notification action).

Don't "fix" this into a bound service — the static-config pattern is intentional and keeps
the ViewModel↔service handoff trivial.

Which settings apply to a *running* session differs: speed, the GPX-speed toggle and
the update interval are pushed live through `MockLocationService.updateSpeed` /
`updateUseGpxSpeed` / `updateInterval`. Start/end km, travel mode and the fixed
coordinate only take effect on the next start.

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

`PASSIVE_PROVIDER` is never registered; it reflects the others for free. If Play
Services is unavailable the FLP step is skipped and only `LocationManager` is mocked.

### Track playback (`walkTrack` / `walkBetween`)

Positions are interpolated per-segment along the track. Speed is either the user's
`speedKmh` (converted to m/s) or, when `useGpxSpeed` is on and the points carry speed,
interpolated from GPX speed. `TravelMode` (`Normal` / `Loop` / `Bounce`) controls what
happens at the end; `Loop` and `Bounce` pause 3 s at each turnaround. `startKm`/`endKm`
window the played range; `startKm > endKm` plays the track backwards. Bearing is
computed great-circle style between consecutive points.

Fixed mode (`fixedLoop`) just re-emits one coordinate forever at `updateIntervalMs`.

### GPX parsing (`GpxParser.kt`)

`XmlPullParser` with namespace awareness off. `ele`, `speed` and `time` are matched
on the local tag name (prefix stripped via `substringAfterLast(':')`), so
`gpxtpx:speed` works; `trk`, `name` and `trkpt` are matched verbatim. Each `<trk>`
becomes one `GpxTrack`; multi-track files trigger a selection dialog. Per-point speed
comes from an explicit `<speed>`/extension, else it's **derived from `<time>`
timestamps** (`distance / Δt`, last point inherits the previous segment).

`GpxTrack` computes `segmentMeters` and `cumulativeMeters` once at construction, on
the IO thread during parsing. The service walker and the preview canvas both read
them, and `interpolate` in the same file is the one lerp between two points. Add
track geometry there rather than re-deriving it from `points`.

### UI (`ui/screens/HomeScreen.kt`)

One ~2100-line file. `HomeScreen` owns the file picker, dialogs and top bar, then
delegates to `PortraitContent` or `LandscapeContent` (two-pane). Every card and dialog
is a private composable in the same file. `TrackPreviewCard` is the Canvas: pinch-zoom,
two-finger rotation, draggable start/end markers with km labels, direction arrows.

Internal state is always metric (`speedKmh`, `startKm`, `endKm`); `ui/UnitFormat.kt`
converts to/from the selected `UnitSystem` at the UI edge only.

### Settings & persistence

`SharedPreferences` named `"locomocktion"`. Speed, custom-speed mode, interval, travel
mode, GPX-speed toggle, unit system, fixed-location fields, and the last-loaded GPX
(uri/name/track-index + start/end km) are all persisted and restored in
`MainViewModel.init`, which auto-reloads the last track on launch. The file picker
takes a persistable URI permission so that reload works across restarts.

Scalar settings change through `MainViewModel.updateSettings`, which writes every
persisted key on each call: a new setting needs its key added there and in `init`.
Start/end km and the last-GPX keys are written separately because they are
track-scoped. Derived UI conditions (`canStart`, `fixedPoint`, `hasMultipleTracks`)
are getters on `UiState`, not recomputed in the layouts.

### Updater

`UpdateChecker.fetchLatestRelease()` hits the GitHub `releases/latest` endpoint for
`c0dev0id/locomocktion` and takes the first `.apk` asset. Because that endpoint ignores
prereleases and drafts, the nightly never shows up as an update. `isNewerVersion` is a
plain **tag-string inequality** (not semver parsing) against `BuildConfig.VERSION_NAME`,
so a `dev` or `nightly` build always reports an update as available.
`ApkInstaller`/`FileProvider` (cache dir) handle the download+install flow.

## Runtime prerequisites

The app is inert until the user sets it as the **Mock location app** in Developer
options. `util/isMockLocationEnabled` checks the `mock_location` appop before start.
`MainActivity` requests location + notification permissions and battery-optimization
exemption on launch.

## Conventions specific to this repo

- No database / schema-migration code while version < 1.0.0.
- Keep the dependency footprint small; prefer existing APIs over new libraries.
- Update `CHANGELOG.md` (Keep a Changelog format) after user-facing changes.
- `.github/development-journal.md` holds the stack, key decisions and feature list;
  keep it in sync with changes it describes.
