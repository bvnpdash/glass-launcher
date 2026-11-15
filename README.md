# Glass — Transparent Liquid Glass Launcher

Native Android launcher (Kotlin, Jetpack Compose, AGSL RuntimeShader, AppWidgetHost, DataStore).
minSdk 26 · targetSdk/compileSdk 35 · AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.9 (wrapper included).

> **Status:** the source was written without access to the Android SDK, so it has **not been compiled or run on a device**.
> Expect to fix a few compile errors or small runtime issues on the first build. Everything below describes what the code is designed to do.

## Build the APK

Requirements: Android Studio Ladybug (2024.2) or newer, or JDK 17+ plus the Android SDK (platform 35, build-tools).

**Android Studio:** File ▸ Open ▸ select this folder ▸ let Gradle sync ▸ Build ▸ Build APK(s).
The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

**Command line:**
```
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (signed with the debug key so it installs; use your own key for distribution)
```

## Install and set as home

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Or copy the APK to the phone and open it (allow "install unknown apps"). Then press Home and choose **Glass ▸ Always**,
or Settings ▸ Apps ▸ Default apps ▸ Home app ▸ Glass.

## Architecture (package `com.glass.launcher`)

| Package | Role |
|---|---|
| `glass` | Liquid Glass engine: `Modifier.glass(material, radius)`, AGSL shader (API 33+), Canvas fallback (API 26–32), `Backdrop` (sharp + pre-blurred wallpaper sampled per surface position), tokens |
| `home` | Clock, icon grid, floating dock, drag-and-drop between grid and dock |
| `appdrawer` | Drawer (A–Z index, categories, recents, favourites, hidden apps) and Liquid Search |
| `widgets` | Real `AppWidgetHost`: picker, bind permission, configure, move, resize, remove, optional glass frame |
| `gestures` | Non-consuming gesture detector (4 swipes with live feedback, pinch) + empty-space double tap / long press |
| `dashboard` | Weather (Open-Meteo, coarse location), calendar, battery, screen time, frequent apps, shortcuts |
| `customization` | Studio with live preview; every setting persists via DataStore |
| `data` | `AppRepository` (LauncherApps), `IconCache` (+ ADW/Nova icon packs), `GlassSettings` (DataStore) |

Materials: **Clear**, **Frosted**, **Crystal**, **Liquid** (animated distortion). Dock, cards and shortcuts use different materials for hierarchy.
Icons use a cheap non-shader tile so large grids stay fast; shader surfaces are limited to dock, cards, search, menus.

## Behaviour notes and limits

- **No background services.** Weather/battery/calendar refresh only while the launcher is resumed; the animation clock stops when stopped and runs at half frame rate.
- **Weather** needs location permission (asked from the dashboard card) and network; uses the last known coarse location.
- **Screen time** needs Usage Access (opens the system page). **Calendar** needs the calendar permission.
- **Double-tap lock** needs the optional accessibility service (Android 9+); it never reads screen content.
- **Swipe down → notifications** uses the status-bar service via reflection; if a device blocks it, Glass opens search instead.
- **"System wallpaper"** may be unavailable on newer Android versions without storage access; Glass falls back to the built-in aurora wallpaper. Use "Choose image" for a reliable custom wallpaper.
- **App shortcuts** (long-press menu) appear only when Glass is the default home app.
- Below Android 13 the glass uses the Canvas fallback: real blurred-wallpaper backdrop, tint, sheen and border, without refraction/dispersion/ripple.
- App-launch uses the system's default transition; a custom icon-to-app expand animation is not implemented.
