# Minimal Launcher

A native, text-first Android home application built around the approved Minimal
Launcher design. It displays the current system wallpaper, a clock/date panel,
configurable right-aligned favorites, optional weather, widgets, and a searchable
right-aligned app drawer with all/daily/work/media filters.

The launcher uses Android platform APIs directly: `LauncherApps` for launchable
activities and user profiles, the system IME for search, `AppWidgetHost` and the
system widget picker for widgets, and system settings for Home selection,
permissions, and app details. It does not use a WebView or a custom keyboard.

## Requirements

- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0 or newer
- Android 10 / API 29 or newer device

The application ID is `dev.obvious.minimallauncher`, target SDK is 36, and
compile SDK is 37.

## Build and test

```bash
./gradlew clean assembleDebug lintDebug testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Run

With an Android device or emulator connected:

```bash
./gradlew installDebug
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

Android may show its system Home-selection surface the first time. Long-press
empty space on the launcher Home screen for launcher settings.

On this workstation, the `medium_phone` AVD is stable with ANGLE's software
renderer:

```bash
emulator @medium_phone -gpu swangle -no-snapshot-save
```

The emulator process crashed during injected drawer/keyboard input when using
`-gpu swiftshader_indirect`; `swangle` avoids that host renderer issue.

## Interaction summary

- Swipe up on Home, invoke TalkBack's **Open apps** action, or type on a physical
  keyboard to open the drawer. Search is focused and the system keyboard opens
  automatically.
- Tap filter labels or swipe horizontally over the drawer (including app rows)
  to cycle all/daily/work/media. Vertical gestures continue to scroll the list.
- Make a deliberate downward swipe from the top of the app list. The first
  swipe hides the keyboard; the next returns Home. Its sensitivity is adjustable
  in launcher settings.
- Type with the system keyboard; Enter/Go launches the first ranked result.
- Back dismisses the IME first, then closes the drawer.
- Long-press empty Home space to configure favorites, filters, appearance,
  optional weather, widgets, and Android system surfaces.

See `SESSION_HANDOFF.md` for implemented scope, validation evidence, and known
limitations.
