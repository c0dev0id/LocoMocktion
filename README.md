# LocoMocktion

Mock location tool for Android to test navigation applications.

## Features

- ✅ Can be configured as mock location application in Android developer settings
- ✅ Shows a simple UI with an interactive map (using OSMDroid)
- ✅ Allows upload of GPX files (routes and tracks)
- ✅ Plots the route or track from the GPX file onto the map
- ✅ Has a speed picker to select travel speed in km/h (5, 10, 20, 30, 40, 50, 60, 80, 100, 120)
- ✅ Has a play button to start mocking from the start of the route with selected speed
- ✅ Automatically slows down in corners based on turn angle
  - Sharp turns (<90°): 40% of base speed
  - Medium turns (90-120°): 60% of base speed
  - Slight turns (120-150°): 80% of base speed
  - Straight sections: 100% of base speed (full speed)

## Building the App

### Requirements

- Android Studio (latest version recommended)
- Android SDK 34 (compile SDK)
- Minimum Android SDK 24 (Android 7.0)
- Java 8 or higher

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/c0dev0id/LocoMocktion.git
cd LocoMocktion
```

2. Open the project in Android Studio:
   - File → Open → Select the LocoMocktion folder

3. Sync Gradle:
   - Android Studio should automatically sync Gradle
   - Or manually: File → Sync Project with Gradle Files

4. Build the APK:
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Or via command line: `./gradlew assembleDebug`

5. Install on device:
   - Connect your Android device via USB with USB debugging enabled
   - Run → Run 'app' in Android Studio
   - Or via command line: `./gradlew installDebug`

## Usage

### 1. Enable Mock Locations

1. On your Android device, go to **Settings → Developer Options**
2. Find **"Select mock location app"** or **"Allow mock locations"**
3. Select **LocoMocktion** from the list

Note: You need to enable Developer Options first:
- Settings → About phone → Tap "Build number" 7 times

### 2. Using the App

1. **Launch LocoMocktion** on your device
2. **Upload a GPX file**:
   - Tap the "UPLOAD GPX" button
   - Select a GPX file from your device
   - The route will be displayed on the map
3. **Select speed**:
   - Use the speed spinner to select your desired travel speed in km/h
4. **Start mocking**:
   - Tap "PLAY" to start simulating GPS movement along the route
   - The app will automatically slow down in corners
   - Tap "PAUSE" to pause the simulation
   - Tap "STOP" to stop and reset
5. **Test your navigation app**:
   - Open your navigation app (Google Maps, Waze, etc.)
   - The app should now receive the mocked GPS locations

## Technical Details

### Architecture

- **MainActivity**: Main UI with map view and controls
- **LocationMockService**: Background service that handles GPS mocking
- **GPX Parsing**: Uses `io.jenetics:jpx` library for GPX file parsing
- **Map Display**: Uses OSMDroid for offline-capable map rendering

### Permissions

The app requires the following permissions:
- `ACCESS_FINE_LOCATION`: For location services
- `ACCESS_COARSE_LOCATION`: For location services
- `ACCESS_MOCK_LOCATION`: To mock GPS locations
- `INTERNET`: For downloading map tiles
- `ACCESS_NETWORK_STATE`: For checking network connectivity
- `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE`: For reading GPX files (Android 12 and below)

### Corner Speed Reduction Algorithm

The service calculates the angle at each point using bearing calculations:
- Bearing from previous point to current point
- Bearing from current point to next point
- Angle difference determines the corner sharpness
- Speed is adjusted accordingly before moving to the next point

### Dependencies

- AndroidX Core KTX
- Material Components
- OSMDroid (map library)
- JPX (GPX parsing)
- Lifecycle components (ViewModel, LiveData)

## Troubleshooting

### "Error: Enable mock locations in developer settings"
- Make sure Developer Options are enabled
- Select LocoMocktion as the mock location app in Developer Options

### Map tiles not loading
- Check your internet connection
- OSMDroid downloads map tiles on demand
- Tiles are cached for offline use after first download

### GPX file not loading
- Make sure the file has a .gpx extension
- Ensure the file contains valid track or route data
- Check that you've granted file access permissions

## License

See the LICENSE file for details.
