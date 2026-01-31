# LocoMocktion Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      LocoMocktion App                        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    UI Layer                           │  │
│  │  ┌───────────────────────────────────────────────┐   │  │
│  │  │          OSMDroid MapView                      │   │  │
│  │  │  - Interactive map display                     │   │  │
│  │  │  - Route visualization (blue polyline)         │   │  │
│  │  │  - Pan & zoom controls                         │   │  │
│  │  └───────────────────────────────────────────────┘   │  │
│  │                                                        │  │
│  │  ┌───────────────────────────────────────────────┐   │  │
│  │  │          Control Panel                         │   │  │
│  │  │  - Speed Spinner (5-120 km/h)                 │   │  │
│  │  │  - Upload GPX Button                          │   │  │
│  │  │  - Play/Pause Button                          │   │  │
│  │  │  - Stop Button                                │   │  │
│  │  │  - Status Text                                │   │  │
│  │  └───────────────────────────────────────────────┘   │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                 Business Logic                        │  │
│  │  - GPX file selection (Activity Result API)          │  │
│  │  - GPX parsing (JPX library)                         │  │
│  │  - Route point extraction                            │  │
│  │  - Map rendering control                             │  │
│  │  - Service lifecycle management                      │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       │ Start/Stop Service
                       │ Pass route points & speed
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  LocationMockService                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Location Mocking Logic                   │  │
│  │                                                        │  │
│  │  1. Receive route points and base speed              │  │
│  │  2. Calculate current position along route           │  │
│  │  3. Detect corners using bearing calculations        │  │
│  │  4. Adjust speed based on corner angle:              │  │
│  │     - Sharp (<90°): 40% speed                        │  │
│  │     - Medium (90-120°): 60% speed                    │  │
│  │     - Slight (120-150°): 80% speed                   │  │
│  │     - Straight (>150°): 100% speed                   │  │
│  │  5. Calculate delay using Haversine distance         │  │
│  │  6. Update mock location via LocationManager         │  │
│  │  7. Repeat for next point                            │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       │ setTestProviderLocation()
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│            Android LocationManager (System)                  │
│  - TestProvider API                                          │
│  - GPS_PROVIDER mock                                         │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       │ Broadcasts mock locations
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Navigation Apps                                 │
│  - Google Maps                                               │
│  - Waze                                                      │
│  - Other GPS-based apps                                      │
└─────────────────────────────────────────────────────────────┘
```

## Data Flow

```
User Action          →  MainActivity           →  LocationMockService
─────────────────────────────────────────────────────────────────────
1. Upload GPX       →  Parse with JPX         →
                    →  Extract waypoints      →
                    →  Display on map         →
                                              →
2. Select Speed     →  Update selectedSpeed   →
                                              →
3. Press Play       →  Start service          →  Begin mocking loop
                    →  Pass route & speed     →  - Calculate position
                                              →  - Detect corners
                                              →  - Adjust speed
                                              →  - Mock location
                                              →  - Schedule next update
                                              →
4. Press Pause      →  Send PAUSE action      →  Stop loop (maintain state)
                                              →
5. Press Stop       →  Stop service           →  End mocking
                    →  Remove test provider   →
```

## Component Dependencies

```
MainActivity
├── OSMDroid (org.osmdroid:osmdroid-android:6.1.17)
│   ├── Map tile rendering
│   ├── Route polyline overlay
│   └── Touch controls
│
├── JPX (io.jenetics:jpx:3.0.1)
│   ├── GPX XML parsing
│   ├── Track extraction
│   └── Waypoint parsing
│
├── Material Components (com.google.android.material:material:1.11.0)
│   ├── Buttons
│   ├── Spinner
│   └── Theme
│
└── AndroidX (androidx.*)
    ├── AppCompatActivity
    ├── ConstraintLayout
    └── Lifecycle components

LocationMockService
├── Android Location API
│   ├── LocationManager
│   ├── TestProvider
│   └── Location objects
│
└── Standard Kotlin/Java
    ├── Math functions (sin, cos, atan2)
    ├── Handler for timing
    └── Service lifecycle
```

## Algorithms

### 1. Haversine Distance Formula
```
Input: lat1, lon1, lat2, lon2 (in degrees)
Output: distance in kilometers

1. Convert coordinates to radians
2. Calculate differences: Δlat, Δlon
3. Apply formula:
   a = sin²(Δlat/2) + cos(lat1) × cos(lat2) × sin²(Δlon/2)
   c = 2 × atan2(√a, √(1-a))
   distance = R × c  (R = 6371 km)
```

### 2. Bearing Calculation
```
Input: lat1, lon1, lat2, lon2 (in degrees)
Output: bearing in degrees (0-360)

1. Convert coordinates to radians
2. Calculate:
   y = sin(Δlon) × cos(lat2)
   x = cos(lat1) × sin(lat2) - sin(lat1) × cos(lat2) × cos(Δlon)
   bearing = atan2(y, x)
3. Convert to degrees and normalize to 0-360
```

### 3. Corner Angle Detection
```
Input: previous_point, current_point, next_point
Output: angle in degrees (0-180)

1. Calculate bearing from previous to current point (bearing1)
2. Calculate bearing from current to next point (bearing2)
3. Find difference: angle = |bearing2 - bearing1|
4. Normalize: if angle > 180, angle = 360 - angle
5. Return angle (smaller of the two possible angles)
```

### 4. Speed Adjustment
```
Input: corner_angle, base_speed
Output: adjusted_speed

if angle < 90:
    speed = base_speed × 0.4    // Sharp turn
else if angle < 120:
    speed = base_speed × 0.6    // Medium turn
else if angle < 150:
    speed = base_speed × 0.8    // Slight turn
else:
    speed = base_speed          // Straight
```

### 5. Update Timing
```
Input: distance (km), speed (km/h)
Output: delay (milliseconds)

1. Calculate time: time_hours = distance / speed
2. Convert to milliseconds: delay = time_hours × 3,600,000
3. Apply minimum: delay = max(100, delay)
```

## File Structure

```
LocoMocktion/
│
├── app/
│   ├── build.gradle                      [Dependencies & Build Config]
│   ├── proguard-rules.pro               [ProGuard Rules]
│   └── src/main/
│       ├── AndroidManifest.xml          [Permissions & Components]
│       ├── java/com/locomocktion/app/
│       │   ├── MainActivity.kt          [UI & Control Logic - 284 lines]
│       │   └── LocationMockService.kt   [GPS Mocking - 194 lines]
│       └── res/
│           ├── drawable/
│           │   └── ic_launcher_foreground.xml
│           ├── layout/
│           │   └── activity_main.xml    [UI Layout]
│           ├── mipmap-*/
│           │   ├── ic_launcher.png      [App Icons - All Densities]
│           │   └── ic_launcher_round.png
│           ├── mipmap-anydpi-v26/
│           │   ├── ic_launcher.xml      [Adaptive Icons]
│           │   └── ic_launcher_round.xml
│           └── values/
│               ├── colors.xml           [Color Definitions]
│               ├── strings.xml          [String Resources]
│               ├── themes.xml           [App Theme]
│               └── ic_launcher_background.xml
│
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar           [Gradle Wrapper Binary]
│       └── gradle-wrapper.properties    [Gradle Version Config]
│
├── docs/
│   └── USAGE.md                         [User Guide - 245 lines]
│
├── build.gradle                          [Project Build Config]
├── settings.gradle                       [Project Settings]
├── gradle.properties                     [Gradle Properties]
├── gradlew                              [Gradle Wrapper Script]
├── .gitignore                           [Git Ignore Rules]
├── README.md                            [Main Documentation - 129 lines]
├── IMPLEMENTATION.md                    [Implementation Details - 231 lines]
├── PROJECT_COMPLETE.md                  [Completion Summary - 258 lines]
├── ARCHITECTURE.md                      [This file]
└── sample-route.gpx                     [Sample GPX File]
```

## Key Features Implementation Map

| Feature | Component | Key Methods/Files |
|---------|-----------|-------------------|
| Mock Location Setup | MainActivity | `startMocking()`, `stopMocking()` |
| Map Display | MainActivity | `setupMap()`, OSMDroid MapView |
| GPX Upload | MainActivity | `gpxPickerLauncher`, `openGpxFilePicker()` |
| Route Plotting | MainActivity | `displayRouteOnMap()`, Polyline overlay |
| Speed Picker | MainActivity | `setupSpeedSpinner()`, Spinner widget |
| Play Controls | MainActivity | Button listeners, service management |
| Corner Slowdown | LocationMockService | `getCurrentSpeed()`, `calculateCornerAngle()` |
| Location Updates | LocationMockService | `mockLocation()`, `updateRunnable` |
| Distance Calc | LocationMockService | `calculateDistance()` (Haversine) |
| Bearing Calc | LocationMockService | `calculateBearing()` |
| Update Timing | LocationMockService | `calculateDelay()` |

## State Management

```
MainActivity State:
├── routePoints: List<WayPoint>          (loaded GPX points)
├── selectedSpeed: Int                   (km/h from spinner)
├── isPlaying: Boolean                   (playback state)
└── binding: ActivityMainBinding         (view references)

LocationMockService State:
├── routePoints: List<DoubleArray>       (lat/lon pairs)
├── currentIndex: Int                    (current position in route)
├── baseSpeedKmh: Double                 (selected speed)
├── isPaused: Boolean                    (pause state)
├── isRunning: Boolean                   (service active)
└── locationManager: LocationManager     (system service)
```

## Threading Model

```
Main Thread (UI Thread):
- MainActivity UI updates
- Button clicks
- Map rendering
- Toasts and dialogs

Background/Handler Thread:
- LocationMockService.updateRunnable
- Posted to Handler with calculated delays
- Executes location updates sequentially
- No blocking operations on main thread
```

## Permission Flow

```
App Installation
    ↓
User Opens App
    ↓
[Runtime Permissions Requested]
    ├─→ ACCESS_FINE_LOCATION
    ├─→ ACCESS_COARSE_LOCATION
    └─→ User grants/denies
        ↓
[User Must Manually Enable]
    ↓
Developer Options → Select mock location app → LocoMocktion
    ↓
[App Can Mock Locations]
    ↓
LocationManager.addTestProvider() succeeds
    ↓
Mock locations broadcasted to system
```

## Error Handling

| Error Scenario | Handling | User Feedback |
|----------------|----------|---------------|
| Mock location not enabled | SecurityException caught | Toast: "Enable mock locations in developer settings" |
| GPX parse error | Exception caught | Toast: "Error reading GPX file" |
| No route loaded | Check before play | Toast: "No route loaded. Please upload a GPX file." |
| Empty GPX file | Validation | Toast: "No route data found in GPX" |
| File access denied | Exception caught | Toast: "Error reading GPX file" |
| Map tile load failure | OSMDroid handles | Tiles load when network available |

---

**Note**: This architecture provides a clean separation between UI (MainActivity) and background processing (LocationMockService), following Android best practices for location-based apps.
