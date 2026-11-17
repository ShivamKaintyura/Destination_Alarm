# Destination Alarm — Android app

Kotlin + Jetpack Compose app. For the product overview, screenshots and website, see the
[root README](../README.md).

## Requirements

- JDK 17
- Android SDK 35 (`compileSdk` / `targetSdk` 35, `minSdk` 26)
- Gradle 8.11.1 — the wrapper is included (`gradlew` / `gradlew.bat`)

## Build

**Windows**

```bat
set JAVA_HOME=C:\path\to\jdk-17
set ANDROID_HOME=C:\path\to\Android\Sdk
gradlew.bat assembleDebug
```

**macOS / Linux**

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` (your SDK path) is git-ignored and is created automatically by Android Studio.
No signing keys are stored in this repository.

## Install on a phone

1. Copy the APK to the phone, or download it from the [Releases page](../../releases/latest).
2. Open it and allow **Install unknown apps** for your browser / file manager.
3. On first launch grant, in order:
   - **Location → Allow**
   - **Background location → Allow all the time** (needed when the screen locks)
   - **Notifications → Allow**
   - **Battery → Fix** (disable battery optimisation)
4. Xiaomi / Oppo / vivo / realme also need **Autostart** enabled in the app settings.

## Source map

| Path | Purpose |
|---|---|
| `MainActivity.kt` | Compose shell: search, radius chips, saved places, settings sheet |
| `ui/MapPane.kt` | MapLibre map, pin picker, route glow, destination halo |
| `ui/MainViewModel.kt` | Trip state, place search, saved places, delete-all |
| `ui/Onboarding.kt`, `ui/Permissions.kt` | First-launch permission flow |
| `alarm/AlarmService.kt` | Foreground tracking, distance/ETA, alarm trigger |
| `alarm/AlarmActivity.kt` | Full-screen lock-screen alarm with Dismiss / Snooze |
| `alarm/Sounds.kt` | Alarm-stream playback and volume handling |
| `service/TrackingService.kt`, `TrackingControl.kt` | Background trip tracking control |
| `service/BootReceiver.kt` | Restores an active trip after reboot |
| `data/Prefs.kt` | Local persistence (trip, saved places, settings) |
| `model/` | Trip, saved place and shared state models |
| `net/Net.kt` | Photon / Nominatim search + OSRM routing |
| `util/Geo.kt` | Distance and bearing helpers |

## Notes

- Alarm audio uses `AudioAttributes.USAGE_ALARM` / `STREAM_ALARM`, so silent mode does not mute it.
- The full-screen alarm uses a full-screen intent; some OEMs require the Autostart permission.
- Maps and search use free public services (OpenFreeMap, Photon, Nominatim, OSRM) — suitable for
  personal/demo use, not for high-volume production traffic.
