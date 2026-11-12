# Autonomous Offline Geocaching & Navigation System (Wear OS + Companion)

## 1. System Overview & Hardware Target
- **Primary Watch Target:** OnePlus Watch 2 (Wear OS 4+, round 1.43" AMOLED display, Snapdragon W5 Gen 1, Dual-frequency L1+L5 GNSS receiver).
- **Phone Target:** Android 10+ (API level 29+) smartphone.
- **Core Mission:** Completely offline, autonomous geocaching navigation tool. Receives targets via standard Android `geo:` intents from the official Geocaching app, runs native offline vector Mapsforge (`.map`) rendering on the watch, features a dual-mode interface (Map Canvas vs. Compass/Arrow HUD), supports offline trail routing with straight-line last-mile projection, and grants explicit, uninhibited access to watch-internal L1+L5 GPS hardware.

---

## 2. Architecture & Tech Stack

- **Language:** Kotlin 2.0+ with Coroutines and StateFlow.
- **Build System:** Gradle Kotlin DSL (`build.gradle.kts`) powered by Version Catalogs (`gradle/libs.versions.toml`).
- **UI Toolkits:**
  - `:wear`: Jetpack Compose for Wear OS (`androidx.wear.compose:compose-material`, `compose-foundation`), Horologist (Rotary support & layout helpers).
  - `:mobile`: Jetpack Compose with Material 3.
- **Inter-Device Communication:** Google Play Services Wearable APIs (`MessageClient`, `ChannelClient`, `DataClient`).
- **Multi-Module Layout:**
  - `:core:model` – Shared data structures: `Waypoint`, `NavigationTarget`, `AppConfig`, `AccentColor`, `ThemeMode`, `GpsSource`, `RouteSegment`.
  - `:core:communication` – Serialization contracts, Wearable message path constants, payload parsers.
  - `:mobile` – Headless intent interceptor, Mapsforge `.map` transfer manager, configuration synchronization, GitHub Releases self-updater.
  - `:wear` – Standalone Wear OS runtime: Mapsforge Android engine, custom Compose Canvas compass/radar, hardware GNSS location engine, rotary zoom handler.

---

## 3. Wear OS Module Specification (`:wear`)

### A. AMOLED & Display Architecture
- Mandatory pure black (`#000000`) canvas on all non-map backgrounds to maximize battery longevity.
- High-contrast minimal vector iconography designed specifically for circular screen boundaries.

### B. Color Themes & Monochromatic Mode
The UI must allow runtime selection of:
- **High-Visibility Accents:** Signal Green (`#00FF66`), Electric Cyan (`#00E5FF`), Safety Orange (`#FF6D00`), Signal Gold (`#FFD700`).
- **Neutral Accents:**
  - Crisp Pure White (`#FFFFFF`): Maximum outdoor daylight contrast without clashing with map layers.
  - Cool Slate / Silver (`#E2E8F0`): Glare-free neutral for dusk and overcast conditions.
- **Night / Monochromatic Red Mode:**
  - Pure Red (`#FF2222`) / Dark Crimson (`#8B0000`) for all text, controls, and radar icons.
  - Applies a post-processing red color matrix filter (`ColorFilter.tint` or PorterDuff overlay) across the map tile renderer to preserve human dark-adaptation during nighttime searches.

### C. Screen Layout & On-Screen Controls

```text
                           [ + ]         [ - ]                 <-- Top Bar Controls (Map Zoom)
                                 .------------.
                              .-'              '-.
               [GPS Toggle]  /                    \  [Snap / Compass N]
                            |      AMOLED VIEW     |
               [Mode Switch]|       (Circular)     |  [Route / WakeLock]
                             \                    /
                              '-.              .-'
                                 '------------'
                                    [ MENU ]                   <-- Bottom Settings Trigger
```

#### 1. Top Bar Controls
- **In Map Mode:**
  - `[ + ]` (Zoom In) and `[ - ]` (Zoom Out) touch targets.
  - Full Rotary Input (physical crown scrolling) mapped to smooth zoom increments.
- **In Compass / Arrow Mode:**
  - Top zoom buttons are hidden to keep the compass canvas clean and distraction-free.

#### 2. Left Edge Vertical Controls (Persistent in both modes)
- **Button 1 (GNSS Source Switcher):**
  - Mode A: `PHONE` (Uses `FusedLocationProviderClient` to optimize battery).
  - Mode B: `WATCH L1+L5` (Directly opens hardware `LocationManager.GPS_PROVIDER` on the watch, completely bypassing Bluetooth phone tethering to leverage dual-frequency satellite fixes).
  - Visual status pill showing satellite count and fix state.
- **Button 2 (Mode Switcher):**
  - Toggles the central display between **Vector Map Canvas** and **Minimalist Compass/Radar View**.

#### 3. Right Edge Vertical Controls (Context-Aware)
- **In Map Mode:**
  - **Button 1 (Bearing Snap):** Single-shot alignment. Queries watch orientation sensor/gyroscope and rotates the map canvas once so current physical heading points directly to 12 o'clock, then locks rotation in place to avoid jitter and unnecessary CPU redraw cycles while walking.
  - **Button 2 (Route Planning):** Triggers local routing from current coordinates toward the active geocache along trail/road networks. When the trail network ends, projects a distinct **dashed direct line** from the final road node straight to the cache coordinates.
- **In Compass / Arrow Mode:**
  - **Button 1 (N / Compass Overlay):** Toggles cardinal reference markings (N, S, E, W) on the outer rim of the display.
  - **Button 2 (WakeLock / Keep Screen On):** Toggles `FLAG_KEEP_SCREEN_ON` on the current window to prevent screen timeout during active ground-zero hunting.

#### 4. Bottom Menu Drawer (Swipe Up / Tap Icon)
Opens a modal sheet covering:
- **Map Selection:** Lists stored `.map` files with multi-select checkboxes. Includes an **"Automatic (By Location)"** toggle that inspects bounding boxes and loads/unloads `.map` files dynamically based on current position to prevent OutOfMemory (OOM) exceptions.
- **Appearance:** Palette picker (High-Vis Colors, Neutrals, Night Red).
- **Measurement Units:** Metric (`m`, `km`) vs. Imperial (`ft`, `mi`).

### D. Mapping Engine
- Uses **Mapsforge** (`org.mapsforge:mapsforge-map-android` and `mapsforge-themes`).
- Memory cache layer capped to prevent memory starvation on Wear OS.
- Compass view rendered via pure Jetpack Compose `Canvas`, drawing an azimuth pointer dynamically adjusted for magnetic declination (`target.bearing - sensor.azimuth`) and bold distance typography.

---

## 4. Mobile Companion Specification (`:mobile`)

### A. Intent Interceptor (`geo:` Protocol)
- Registers an activity alias inside `AndroidManifest.xml` targeting `android.intent.action.VIEW` for:
  - `geo:0,0?q=lat,lon(label)`
  - `geo:lat,lon`
- Implements `Theme.NoDisplay`:
  - Parses incoming coordinates and waypoint labels.
  - Connects to `Wearable.getMessageClient(context)`.
  - Transmits payload across path `/set_target` to the paired node.
  - Closes immediately via `finish()` without interrupting user workflow.

### B. Mapsforge File Manager & Transfer
- UI guide explaining Mapsforge vector `.map` requirements (with quick-links to OpenAndroMaps and BBBike extracts).
- Storage Access Framework (SAF) document picker to select downloaded `.map` files.
- Chunked, reliable transfer to watch storage via `ChannelClient` with on-screen progress bar and transfer verification.

### C. Configuration Synchronization
- Synchronizes accent color choices, unit preferences, and night-mode states bi-directionally using Wearable `DataClient`.

### D. Self-Updating Engine (GitHub Releases API)
- Client querying `https://api.github.com/repos/{owner}/{repo}/releases/latest`.
- Compares semantic version strings against `BuildConfig.VERSION_NAME`.
- Downloads matching APK artifacts (`mobile-release.apk` and `wear-release.apk`).
- Prompts system package installer for the phone app.
- Pushes the watch APK through `ChannelClient` to trigger the Wear OS package installation interface.

---

## 5. Repository Structure

```text
├── .github/
│   └── workflows/
│       └── build.yml             # Automated CI building debug and release APKs
├── gradle/
│   └── libs.versions.toml        # Version Catalog
├── build.gradle.kts              # Root build script
├── settings.gradle.kts           # Module definitions
├── core/
│   ├── model/                    # Shared data classes & enums
│   └── communication/            # Wearable API protocols & serializers
├── mobile/                       # Mobile companion Android app
└── wear/                         # Standalone Wear OS 4+ application
```

---

## 6. Implementation Milestones

1. **Phase 1: Build Infrastructure:** Configure `libs.versions.toml`, root, and submodule `build.gradle.kts` files. Verify via `./gradlew tasks`.
2. **Phase 2: Core Contracts:** Implement models (`Waypoint`, `GpsSource`, `AccentColor`, `ThemeMode`) and binary serializations under `:core`.
3. **Phase 3: Wear OS Engine:** Build the Wear OS Compose interface, Mapsforge integration, rotary zoom listener, context-aware right-edge controls, and low-level `GPS_PROVIDER` manager.
4. **Phase 4: Mobile Pipeline:** Implement the headless `geo:` intent receiver, `ChannelClient` map pusher, and GitHub updater.