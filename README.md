# Hello Launcher

A minimal Kotlin Android home-screen application used to validate the launcher project toolchain.

## Build

Requirements:

- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0 or newer

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Run

With an emulator or Android device connected:

```bash
emulator @medium_phone -gpu swiftshader_indirect
./gradlew installDebug
adb shell am start -n dev.hello.launcher/.MainActivity
```

The app is registered for both `LAUNCHER` and `HOME`, so Android can also select it as the device's home app.
