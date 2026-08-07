# Minimal Launcher

A native, text-first Android home application built around the approved Minimal
Launcher design. It displays the current system wallpaper, a clock/date panel,
configurable right-aligned favorites, optional weather, widgets, and a searchable
right-aligned app drawer with built-in and user-defined filters.

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

The generic debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
The current device-tested artifact is
`app/build/outputs/apk/debug/minimal-launcher-0.1.6-debug.apk`.

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
  to cycle all/daily/work/media and any custom categories. Vertical gestures
  continue to scroll the list.
- Make a deliberate downward swipe from the top of the app list. The first
  swipe hides the keyboard using the same sensitivity as Home’s opening swipe;
  the next returns Home. Drawer-to-Home distance and speed are independently
  adjustable in launcher settings.
- Personal apps appear in All/Daily/Media. Apps from a secondary/work profile
  appear exclusively in Work and use an `(w)` suffix in settings lists.
- Type with the system keyboard; Enter/Go launches the first ranked result.
- Back dismisses the IME first, then closes the drawer.
- Optional weather can use manual coordinates or an explicitly granted
  approximate device location, with manual fallback and a six-hour stale cache.
- Auto appearance reacts to Android wallpaper colors while preserving a
  localized clock contrast surface on mixed-brightness wallpaper.
- Toggle **solid background** under Customization to replace or restore the
  wallpaper; its checked state is shown explicitly. **solid color** edits the
  saved color without silently enabling it. The Appearance dialog also labels
  Solid separately from the three wallpaper modes.
- Long-press an Android widget and choose **remove**. Long-press the built-in
  clock/date and choose **hide**; restore it with **Customization → built-in
  clock/date**.
- Long-press empty Home space to add custom filters and configure favorites,
  font size/color, accent color, automatic keyboard display, filter-label/fade/
  underline visibility, list margins, status bar, appearance, optional weather,
  widgets, and Android system surfaces.
- When Minimal Launcher is not the default Home app, tap the small Home-screen
  prompt to open Android's Home-app chooser.

See `SESSION_HANDOFF.md` for implemented scope, validation evidence, and known
limitations.
