<p align="center">
  <img src="docs/assets/minml-readme-banner.png" alt="minml launcher" width="1200">
</p>

<p align="center">
  <a href="https://github.com/Aagat/minml-launcher/actions/workflows/release.yml"><img src="https://github.com/Aagat/minml-launcher/actions/workflows/release.yml/badge.svg?branch=main" alt="Build and publish release"></a>
</p>

A native, text-first Android home screen built around typography, wallpaper,
negative space, and fast access to apps. minml launcher replaces the usual icon
grid with configurable favorites and a searchable app drawer while retaining
Android widgets, work profiles, accessibility, and system-owned configuration
flows.

The project uses native Android Views and platform APIs directly. It does not
embed its prototype in a WebView and does not implement a custom keyboard,
navigation bar, notification reader, account system, advertising, or analytics.

## Features

- A wallpaper-aware Home screen with a clock, date, configurable favorites,
  optional weather, optional screen-time details, and Android widgets.
- A text app drawer with fuzzy search, keyboard-first navigation, custom
  categories, work-profile isolation, hidden apps, and app aliases.
- Swipe, keyboard, and TalkBack paths for opening, filtering, searching, and
  dismissing the drawer.
- Direct on-screen widget movement and resizing with proportional placement
  that survives rotation and window-size changes.
- Configurable fonts, text scale, capitalization, colors, contrast treatments,
  drawer surfaces, margins, animations, clock format, and status-bar behavior.
- Android-owned surfaces for choosing the default Home app, adding and
  configuring widgets, granting permissions, and opening app details.
- Persistent launcher preferences with Android Auto Backup support for durable
  user configuration.

## Privacy

minml launcher has no ads, trackers, analytics, or developer-operated backend.

- App discovery uses Android's `LauncherApps` APIs. The launcher requests
  package visibility because a Home application must enumerate launchable apps.
- Weather is disabled by default. When enabled, it sends the selected manual or
  approximate location to Open-Meteo to retrieve current conditions.
- Screen time and detailed usage are disabled by default. When enabled, Android
  requires the user to grant Usage Access; results are calculated locally from
  system usage events and are not uploaded by the launcher.
- Android may back up selected launcher preferences when the device's system
  backup service is enabled.

## Compatibility

- Android 10 / API 29 or newer
- `targetSdk` 36
- `compileSdk` 37
- Application ID: `dev.obvious.minimallauncher`

## Build

Install JDK 17, Android SDK Platform 37, and Android SDK Build Tools 36.0.0 or
newer, then run:

```bash
./gradlew clean assembleDebug lintDebug testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For an unsigned release build:

```bash
./gradlew clean assembleRelease lintRelease testDebugUnitTest
```

The unsigned APK is written to
`app/build/outputs/apk/release/app-release-unsigned.apk`. Sign public releases
with a private, durable release key; no signing key is stored in this repository.

## Automated releases

Every push to `main` runs the build, lint, and unit-test suite before publishing
a signed APK and its SHA-256 checksum as a uniquely tagged GitHub prerelease.
Downloads are available on the [Releases page](https://github.com/Aagat/minml-launcher/releases).

Release signing is supplied to GitHub Actions through these repository secrets;
the keystore itself is never committed:

- `ANDROID_SIGNING_KEYSTORE_BASE64`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_PASSWORD`

## Device tests

The repeatable device suite uses a connected emulator or starts an AVD named
`medium_phone` headlessly:

```bash
./scripts/ui-test.sh
```

It performs a clean build, runs JVM tests and Android lint, installs the debug
APK, assigns the Android Home role, runs native UI Automator scenarios, and
writes a consolidated report to `app/build/reports/launcher-ui/index.html`.

Use `ANDROID_SERIAL` to select a connected device, `AVD_NAME` to select another
AVD, or `KEEP_AVD=1` to leave an emulator started by the script running.

## Install and run

With an Android device or emulator connected:

```bash
./gradlew installDebug
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

Android may show its Home-app selection screen the first time. Long-press empty
space on Home to open launcher settings; swipe up to open the app drawer.

## Contributing

Bug reports and focused pull requests are welcome. Before submitting code, run
the clean build command above and, when an emulator is available, the device
test script. Please keep new behavior native, accessible, and compatible with
API 29 unless a documented project decision changes the minimum SDK.

## Artwork

Editable brand artwork and the 512 px store-listing icon are kept in
[`artwork/`](artwork/). Android-ready adaptive, monochrome, round, and legacy
launcher icons live alongside the app's other resources in
`app/src/main/res/`.

## License

Copyright © 2026 Aagat Adhikari.

minml launcher is free software licensed under the GNU General Public License,
version 3. See [LICENSE](LICENSE). Bundled fonts retain their respective license
terms under `app/src/main/assets/licenses/`.
