# LocoMocktion

Mock location tool for Android. Load a GPX track file and simulate device movement along the route at a configurable speed.

## Features

- **GPX file import** — pick any `.gpx` file containing a track
- **Track preview** — visual canvas showing the loaded route with start/end markers
- **Configurable speed** — slider from 1–200 km/h with presets for walking, cycling, driving
- **Live progress** — real-time position updates and progress bar while mocking
- **Foreground service** — keeps running reliably in the background with a persistent notification
- **Stop from notification** — tap "Stop" on the notification to end mocking instantly

## Prerequisites

1. Enable **Developer options** on your Android device
2. Set this app as the **Mock location app** under Developer options
3. Grant location permissions when prompted

## Building

```
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## How it works

The app registers as a test location provider for `GPS_PROVIDER` and feeds interpolated coordinates from the GPX track at regular intervals. The speed setting controls how many meters per second the simulated position advances along the track segments.
