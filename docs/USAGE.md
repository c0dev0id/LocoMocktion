# LocoMocktion Usage Guide

## Quick Start Guide

### Prerequisites

1. **Android Device Requirements**:
   - Android 7.0 (API 24) or higher
   - Developer Options enabled
   - USB Debugging enabled (for installation)

2. **Enable Developer Options** (if not already enabled):
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Developer Options will now appear in Settings

### Installation

1. **Install the APK**:
   - Download the APK to your device
   - Tap to install (allow "Install from Unknown Sources" if prompted)
   - Or install via Android Studio: Run → Run 'app'

2. **Set as Mock Location App**:
   - Go to Settings → Developer Options
   - Scroll to "Select mock location app"
   - Select "LocoMocktion"

### Using LocoMocktion

#### Step 1: Prepare a GPX File

You can use the included `sample-route.gpx` or create your own:
- Export a route from apps like Google Maps, Komoot, or Strava
- Use online GPX generators or editors
- Transfer the GPX file to your Android device

#### Step 2: Load the Route

1. Open LocoMocktion
2. Tap **"UPLOAD GPX"** button
3. Navigate to and select your GPX file
4. The route will be displayed on the map with a blue line

#### Step 3: Configure Speed

- Use the **Speed spinner** to select your desired travel speed
- Available speeds: 5, 10, 20, 30, 40, 50, 60, 80, 100, 120 km/h
- Default speed: 30 km/h

#### Step 4: Start Mocking

1. Tap the **"PLAY"** button to start GPS simulation
2. Open your navigation app (e.g., Google Maps, Waze)
3. Your location will follow the route at the selected speed
4. The app automatically slows down in corners

#### Step 5: Control Playback

- **PAUSE**: Tap "PLAY" again to pause at the current location
- **STOP**: Tap "STOP" to end the simulation and return to the start

## Features in Detail

### Map View

- **Interactive Map**: Pan and zoom using touch gestures
- **Route Display**: GPX routes are shown as blue lines
- **Auto-Zoom**: Map automatically centers on the loaded route

### Speed Control

The speed picker allows you to simulate different travel scenarios:
- **5-20 km/h**: Walking or slow cycling
- **30-60 km/h**: City driving
- **80-120 km/h**: Highway driving

### Intelligent Corner Handling

LocoMocktion automatically adjusts speed in corners based on the turn angle:

| Turn Type | Angle | Speed Adjustment |
|-----------|-------|------------------|
| Sharp turn | < 90° | 40% of base speed |
| Medium turn | 90-120° | 60% of base speed |
| Slight turn | 120-150° | 80% of base speed |
| Straight | > 150° | Full speed |

This creates realistic GPS movement patterns similar to actual driving behavior.

### GPX File Support

LocoMocktion supports standard GPX 1.1 format:
- **Tracks** (`<trk>` elements): Primary support
- **Routes** (`<rte>` elements): Also supported
- **Track Segments**: Multiple segments are combined
- **Waypoints**: Used to define the path

## Testing Navigation Apps

### Recommended Apps to Test

1. **Google Maps**
   - Open Google Maps after starting LocoMocktion
   - Your blue location dot should follow the route

2. **Waze**
   - Start navigation in Waze
   - Waze will think you're driving along the route

3. **Other Navigation Apps**
   - Most GPS-based apps will work
   - Apps that use Google Location Services will receive mocked locations

### Tips for Testing

- Start the mock location before opening the navigation app
- Some apps may cache location data; restart them if needed
- Verify that LocoMocktion is set as the mock location app in Developer Options

## Troubleshooting

### Mock Location Not Working

**Problem**: Navigation apps don't show the mocked location

**Solutions**:
1. Check that Developer Options → "Select mock location app" is set to LocoMocktion
2. Restart the navigation app after starting LocoMocktion
3. Make sure location services are enabled on your device
4. Some apps (like Pokemon GO) detect and block mock locations

### Route Not Loading

**Problem**: GPX file doesn't load or shows an error

**Solutions**:
1. Verify the GPX file is valid XML
2. Ensure the file contains `<trk>` or `<rte>` elements with waypoints
3. Check that file permissions allow the app to read it
4. Try using the included `sample-route.gpx` file

### Map Not Displaying

**Problem**: Map tiles are not loading

**Solutions**:
1. Check your internet connection (required for first-time tile download)
2. Grant location permissions when prompted
3. Try zooming in/out to trigger tile loading
4. Map tiles are cached after first download for offline use

### App Crashes on Start

**Problem**: LocoMocktion crashes when tapping PLAY

**Solutions**:
1. Ensure you've granted all required permissions:
   - Location access (Fine and Coarse)
   - Storage access (for reading GPX files)
2. Make sure you've set LocoMocktion as mock location app in Developer Options
3. Try restarting the app

## Advanced Usage

### Creating Custom Routes

You can create custom GPX routes using online tools:

1. **GPX Editor Online**: https://www.gpxeditor.co.uk/
2. **MapHub**: https://maphub.net/
3. **Brouter Web**: http://brouter.de/brouter-web/

Or export from fitness/navigation apps:
- Strava
- Komoot
- Ride with GPS
- Google Earth

### Using Multiple Routes

To test different scenarios:
1. Stop the current simulation
2. Upload a new GPX file
3. Adjust speed as needed
4. Start playing the new route

### Simulating Different Travel Modes

**Walking**: Select 5-10 km/h
**Cycling**: Select 15-30 km/h  
**Car (City)**: Select 30-60 km/h
**Car (Highway)**: Select 80-120 km/h

The corner slowdown feature makes each mode more realistic.

## Privacy & Permissions

LocoMocktion requires these permissions:

- **Location**: To register as a mock location provider
- **Storage**: To read GPX files from your device
- **Internet**: To download map tiles (OSMDroid)

**Privacy Note**: 
- All data stays on your device
- No data is sent to external servers
- Map tiles are downloaded from OpenStreetMap (public service)
- GPX files are only read locally

## Support & Contributions

For issues, questions, or contributions, visit the GitHub repository:
https://github.com/c0dev0id/LocoMocktion

## License

See LICENSE file in the repository.
