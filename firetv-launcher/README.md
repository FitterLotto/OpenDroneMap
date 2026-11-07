# TV Home — a custom Fire TV home screen

A lightweight launcher for Amazon Fire TV (and any Android TV device), built for the remote.

## What's on the screen

- **Info header**: large clock, date, a time-of-day greeting, and current weather
  (temperature, conditions, today's high/low, city). Weather needs no API key: the
  approximate location comes from your IP address (ipwho.is), and the forecast from
  Open-Meteo. °F or °C is picked automatically by country.
- **Status chips**: network state (Wi-Fi / Ethernet / Offline) and how many apps are installed.
- **★ Favorites**: apps you pin, in the order you choose.
- **Recently used**: the last 12 apps you opened from TV Home.
- **All apps**: every installed app, alphabetical, shown with its TV banner when it has one.
- **System**: Settings, Network, Manage apps and Refresh weather.

Focused tiles grow and get a highlighted border. **Press ☰ (Menu)** or long-press Select on any
app for: Open, Add/Remove Favorites, Move left/right, App info, Uninstall.

## Install on your Fire TV

The ready-made APK is `dist/TVHome-1.0.apk`.

**Option A: Downloader app (no computer needed)**
1. Fire TV: *Settings → My Fire TV → Developer options → Install unknown apps* → allow **Downloader**.
   (If Developer options is hidden: *Settings → My Fire TV → About*, then click the device name 7 times.)
2. Open Downloader and enter the raw URL of `dist/TVHome-1.0.apk` from GitHub. If the repo is
   private, upload the APK somewhere the TV can reach instead.
3. Install, then open **TV Home** from *Your Apps & Channels*.

**Option B: adb from a computer on the same network**
1. *Developer options → ADB debugging* → On. Find the TV's IP in *Settings → My Fire TV → About → Network*.
2. `adb connect <tv-ip>:5555`, accept the prompt on the TV, then `adb install -r dist/TVHome-1.0.apk`.

## About the Home button

Newer Fire OS versions don't let a third-party app replace Amazon's home screen, so the
**Home** button still goes to Amazon's screen. Your options:
- Open TV Home from *Your Apps & Channels*, and move it to the front of your apps row.
- Use a button-remapping app from the Amazon Appstore to make a remote button (or a long
  press of Home) open TV Home.

On older Fire OS releases and on stock Android TV devices, TV Home declares itself as a home
app, so you can pick it as the default launcher.

## Build it yourself

Requires JDK 17+ and the Android SDK (platform 34).

```
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it can be sideloaded straight away.
To publish it anywhere, set up your own signing config.

## Code layout

- `app/src/main/java/.../MainActivity.java` — screen, rows, tiles, focus and the options menu
- `app/src/main/java/.../AppRepository.java` — finds installed apps; stores favorites and recents
- `app/src/main/java/.../WeatherClient.java` — location + Open-Meteo weather, off the main thread
- `app/src/main/res/` — layout, colors, focus styles, icon and banner
