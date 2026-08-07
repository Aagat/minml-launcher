# Minimal Launcher

A native, text-first Android home application built around the approved Minimal
Launcher design. It displays the current system wallpaper, a clock/date panel,
configurable right-aligned favorites, optional weather and screen-time widgets,
and a searchable right-aligned app drawer with built-in and user-defined filters.

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
The current locally installable release artifact is
`release-artifacts/minimal-launcher-0.9.0-release-local.apk`.
It is a non-debuggable release build signed with this workstation's Android
debug certificate for local testing and is not suitable for public
distribution.

### Automated UI validation

Run the complete repeatable launcher check with:

```bash
./scripts/ui-test.sh
```

The runner uses an already connected emulator or starts `medium_phone`
headlessly with the stable `swangle` renderer. It performs a clean debug build,
all JVM tests, Android lint, and eight native UI Automator flows covering Home
registration/settings, preference restoration, drawer rendering modes,
search/IME/app launch/filter/dismiss gestures, rotation, and the screen-time
widget lifecycle. It then writes one self-contained summary to
`app/build/reports/launcher-ui/index.html`, with links to the detailed Gradle
reports plus screenshots, the final UI hierarchy, device metadata,
build/install logs, and filtered logcat output. The scenarios include widget
geometry and the complete color-selection flow.

Set `ANDROID_SERIAL` to target a specific already connected device. Set
`AVD_NAME` to use another AVD, or `KEEP_AVD=1` to leave an AVD started by the
script running after the report is generated.

## Release build

```bash
./gradlew clean assembleRelease lintRelease testDebugUnitTest
```

Gradle writes the unsigned release APK to
`app/build/outputs/apk/release/app-release-unsigned.apk`. A production release
must be signed with a private, durable release key. The repository intentionally
does not contain one.

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
- Swipe down on Home to expand Android's notification shade. Minimal Launcher
  delegates this to SystemUI and does not request notification-listener access.
- Tap filter labels or swipe horizontally over the drawer (including app rows)
  to cycle all/daily/work/media and any custom categories. Vertical gestures
  continue to scroll the list.
- Make a deliberate downward swipe from the top of the app list. The first
  swipe hides the keyboard using the same sensitivity as Home’s opening swipe;
  the next returns Home. Drawer-to-Home distance and speed are independently
  adjustable in launcher settings.
- Personal apps appear in All/Daily/Media. Apps from a secondary/work profile
  appear exclusively in Work and use an `(w)` suffix in settings lists.
- Open **App drawer → Manage apps**, or long-press a visible drawer row, to
  rename an app, hide/show it in the drawer, reset its name, or open Android app
  details. Hidden entries remain available in Manage apps so they can always be
  restored. Names and hidden state are profile-specific and persisted; aliases
  also appear on Home favorites.
- Type with the system keyboard; Enter/Go launches the first ranked result.
- Back dismisses the IME first, then closes the drawer.
- Optional short fade/slide transitions animate drawer opening and closing.
  Horizontal filter swipes move the header and app results in the gesture's
  direction. Both effects can be disabled together under **Appearance →
  Motion → Animations**.
- Optional weather can use manual coordinates or an explicitly granted
  approximate device location, with manual fallback, selectable System/Celsius/
  Fahrenheit units, and a six-hour stale cache. Weather enablement, temperature
  units, location source, saved coordinates, and permission state are exposed
  directly as rows in Home screen settings; only coordinate text entry uses a
  focused dialog.
- Clock format can follow Android or be forced to 12-hour or 24-hour display.
- **Home screen → Show screen time** adds a compact, right-aligned built-in
  widget for today's screen-on duration. It is off by default and uses Android's
  system Usage Access screen; the launcher reads screen interactive/non-
  interactive events locally and does not upload usage data. Long-press the
  widget to hide it. When access is blocked for a sideloaded APK, Home settings
  shows numbered shortcuts for **App info → Allow restricted settings** and
  then **Usage Access → Permit usage access**.
- Once Usage Access is allowed, **Detailed usage** becomes available beside the
  screen-time controls. It is independently opt-in and adds up to four
  right-aligned launchable apps ranked by today's foreground-use duration below
  the screen-on total.
- Typography can use the bundled Geist Mono Nerd Font, Geist, Inter, IBM Plex
  Sans, Manrope, Space Grotesk, B612, B612 Mono, Android Monospace, or Android
  Sans. Geist Mono remains the default. The bundled font license texts are
  packaged under `assets/licenses`.
- Text capitalization can remain lowercase, preserve original app/alias/filter
  capitalization, or use locale-aware uppercase. The setting applies across
  Home and the drawer without changing the user-entered search query.
- The drawer header accents only the filter name. Search is left-aligned with a
  muted hint and configurable left margin; app rows remain right-aligned.
- The search/filter backdrop can use the existing automatic treatment, solid
  black, full transparency, a wallpaper-derived color, or a custom color. The
  optional bottom fade blends into the selected backdrop.
- Auto appearance reacts to Android wallpaper colors while preserving a
  localized clock contrast surface on mixed-brightness wallpaper.
- Select **Appearance → Background mode → Solid** to replace the wallpaper;
  select Auto, Transparent, or Gradient to restore wallpaper rendering. **Solid
  color** edits the saved color without silently enabling it.
- Long-press an Android widget and choose **remove**. Long-press the built-in
  clock/date or screen-time widget and choose **hide**; restore either from
  **Home screen** settings.
- Long-press empty Home space to open persistent, full-screen settings organized
  into Home screen, App drawer, Appearance, System, and About. Editors return to
  their category, Back returns one level at a time, and Android system surfaces
  return to the originating settings page.
- When Minimal Launcher is not the default Home app, tap the small Home-screen
  prompt to open Android's Home-app chooser.

See `SESSION_HANDOFF.md` for implemented scope, validation evidence, and known
limitations.
