# LocoMocktion UI Mockup

```
╔══════════════════════════════════════════════════════════════════╗
║  LocoMocktion                                            [☰]     ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║                        MAP VIEW                                  ║
║                    (OSMDroid Interactive)                        ║
║                                                                  ║
║        [Zoom In/Out]   [Pan & Drag to Navigate]                 ║
║                                                                  ║
║    ╭────────────────────────────────────────────────╮          ║
║    │                                                 │          ║
║    │         🗺️  Map with Blue Route Line           │          ║
║    │                                                 │          ║
║    │              ┌─────────────────┐                │          ║
║    │              │  Route Polyline │                │          ║
║    │              │   (Blue Line)   │                │          ║
║    │              └─────────────────┘                │          ║
║    │                                                 │          ║
║    │    OpenStreetMap tiles displayed here          │          ║
║    │                                                 │          ║
║    ╰────────────────────────────────────────────────╯          ║
║                                                                  ║
╠══════════════════════════════════════════════════════════════════╣
║                        CONTROL PANEL                             ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  📍 Status: No route loaded. Please upload a GPX file.          ║
║                                                                  ║
║  ┌─────────────────────────────────────────────────────────┐   ║
║  │  Speed (km/h): [ 30 km/h  ▼ ]                           │   ║
║  │                  ┌────────────────────────────────────┐ │   ║
║  │                  │ 5 km/h                             │ │   ║
║  │                  │ 10 km/h                            │ │   ║
║  │                  │ 20 km/h                            │ │   ║
║  │                  │ 30 km/h  ✓ (Selected)             │ │   ║
║  │                  │ 40 km/h                            │ │   ║
║  │                  │ 50 km/h                            │ │   ║
║  │                  │ 60 km/h                            │ │   ║
║  │                  │ 80 km/h                            │ │   ║
║  │                  │ 100 km/h                           │ │   ║
║  │                  │ 120 km/h                           │ │   ║
║  │                  └────────────────────────────────────┘ │   ║
║  └─────────────────────────────────────────────────────────┘   ║
║                                                                  ║
║  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         ║
║  │  📤 UPLOAD   │  │   ▶ PLAY     │  │   ⏹ STOP     │         ║
║  │     GPX      │  │  (disabled)  │  │  (disabled)  │         ║
║  └──────────────┘  └──────────────┘  └──────────────┘         ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝

                        INITIAL STATE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

╔══════════════════════════════════════════════════════════════════╗
║  LocoMocktion                                            [☰]     ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║                        MAP VIEW                                  ║
║                    (Route Displayed)                             ║
║                                                                  ║
║    ╭────────────────────────────────────────────────╮          ║
║    │                                                 │          ║
║    │    🗺️                                           │          ║
║    │                                                 │          ║
║    │    🔵━━━━━━━━━━━━━━╮                          │          ║
║    │    Start Point      ┃                          │          ║
║    │                     ┃  Blue Route              │          ║
║    │                     ┗━━━━━╮                    │          ║
║    │                           ┃                    │          ║
║    │                           ┃                    │          ║
║    │                           ┗━━━━━━━━🔵         │          ║
║    │                              End Point         │          ║
║    │                                                 │          ║
║    ╰────────────────────────────────────────────────╯          ║
║                                                                  ║
╠══════════════════════════════════════════════════════════════════╣
║                        CONTROL PANEL                             ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  📍 Status: Route loaded: 17 points                             ║
║                                                                  ║
║  ┌─────────────────────────────────────────────────────────┐   ║
║  │  Speed (km/h): [ 60 km/h  ▼ ]                           │   ║
║  └─────────────────────────────────────────────────────────┘   ║
║                                                                  ║
║  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         ║
║  │  📤 UPLOAD   │  │   ▶ PLAY     │  │   ⏹ STOP     │         ║
║  │     GPX      │  │   (enabled)  │  │  (disabled)  │         ║
║  └──────────────┘  └──────────────┘  └──────────────┘         ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝

                      ROUTE LOADED STATE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

╔══════════════════════════════════════════════════════════════════╗
║  LocoMocktion                                            [☰]     ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║                        MAP VIEW                                  ║
║                  (Mocking in Progress)                           ║
║                                                                  ║
║    ╭────────────────────────────────────────────────╮          ║
║    │                                                 │          ║
║    │    🗺️                                           │          ║
║    │                                                 │          ║
║    │    ⚫━━━━━━━━━━━━━━╮  <- Traveled              │          ║
║    │    Past             ┃                          │          ║
║    │                     ┃                          │          ║
║    │                     📍 <- Current Position     │          ║
║    │                     ┃                          │          ║
║    │                     🔵━━━━━╮ <- Remaining      │          ║
║    │                           ┃                    │          ║
║    │                           ┗━━━━━━━━⚫         │          ║
║    │                                                 │          ║
║    ╰────────────────────────────────────────────────╯          ║
║                                                                  ║
╠══════════════════════════════════════════════════════════════════╣
║                        CONTROL PANEL                             ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  📍 Status: Location mocking started                            ║
║  ℹ️  Current speed: 42 km/h (70% - medium turn)                ║
║                                                                  ║
║  ┌─────────────────────────────────────────────────────────┐   ║
║  │  Speed (km/h): [ 60 km/h  ▼ ]                           │   ║
║  └─────────────────────────────────────────────────────────┘   ║
║                                                                  ║
║  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         ║
║  │  📤 UPLOAD   │  │   ⏸ PAUSE    │  │   ⏹ STOP     │         ║
║  │  (disabled)  │  │   (active)   │  │   (enabled)  │         ║
║  └──────────────┘  └──────────────┘  └──────────────┘         ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝

                       PLAYING STATE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## UI Components Description

### Map View (Top Section)
- **Technology**: OSMDroid MapView
- **Features**:
  - Interactive pan and zoom
  - OpenStreetMap tiles
  - Blue polyline showing GPS route
  - Auto-zoom to fit entire route
  - Touch controls for navigation

### Status Display
- Shows current state:
  - "No route loaded" → Initial
  - "Route loaded: X points" → After GPX upload
  - "Location mocking started" → During playback
  - "Location mocking stopped" → After stop
- Real-time speed display (current adjusted speed)

### Speed Selector (Spinner/Dropdown)
- Material Design Spinner
- 10 preset speeds: 5, 10, 20, 30, 40, 50, 60, 80, 100, 120 km/h
- Default: 30 km/h
- Updates instantly on selection

### Control Buttons

#### 1. UPLOAD GPX Button
- Opens Android document picker
- Supports .gpx, .xml file types
- Parses and displays route
- Disabled during playback

#### 2. PLAY/PAUSE Button
- Toggles between Play and Pause
- Starts location mocking service
- Disabled until route loaded
- Shows current state

#### 3. STOP Button
- Stops mocking service
- Resets to start of route
- Cleans up test provider
- Disabled until playback starts

## Color Scheme

- **Primary**: Purple (#6200EE)
- **Secondary**: Teal (#03DAC5)
- **Route Line**: Blue (#0000FF)
- **Background**: White/Light Gray
- **Text**: Black/Dark Gray

## Responsive Design

- Layout uses ConstraintLayout
- Map takes majority of screen space
- Control panel fixed at bottom
- Buttons sized for easy touch
- Minimum touch target: 48dp
- Material Design elevation and shadows

## States & Transitions

```
Initial State
    ↓ (Upload GPX)
Route Loaded
    ↓ (Tap Play)
Playing
    ↓ (Tap Pause)
Paused
    ↓ (Tap Play)
Playing
    ↓ (Tap Stop)
Route Loaded
    ↓ (Upload new GPX)
Route Loaded (new route)
```

## Toast Messages

- "GPX loaded: X points" → Success
- "No route data found in GPX" → Error
- "Error reading GPX file" → Error
- "Enable mock locations in developer settings" → Warning
- "Location mocking started" → Info
- "Location mocking stopped" → Info

---

**Note**: This is a conceptual UI mockup. The actual app renders using Android's native components with Material Design, providing a smooth and responsive user experience.
