# Development Journal

## Software Stack

- **Platform**: Android (minSdk 26, targetSdk 35)
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material3)
- **Architecture**: ViewModel + StateFlow (single-activity, no fragments)
- **GPX parsing**: Custom `GpxParser` (XmlPullParser)
- **Mock location**: Android `MockLocationProvider` via foreground service

## Key Decisions

- **No navigation library**: Single `HomeScreen` composable handles all UI state via `UiState` from `MainViewModel`. Keeps the stack minimal.
- **Speed derivation from timestamps**: When GPX track points lack an explicit `<speed>` element, speed is derived from distance/time between consecutive points. Controlled by a user toggle in settings.
- **Settings persistence**: `SharedPreferences` via `androidx.preference` — straightforward for the small number of scalar settings in use.

## Core Features

- Load GPX files and replay them as mock GPS locations.
- Select individual tracks from multi-track GPX files.
- Configurable playback speed multiplier and travel mode.
- Option to use GPX-derived speed data.
- Discreet Buy Me a Coffee link in the top bar.
