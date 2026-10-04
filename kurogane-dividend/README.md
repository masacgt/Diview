# Kurogane Dividend

This folder is the active application project. The Android app is the distributable product; `src/` contains the React implementation used as a behavior reference where the native app needs it.

## Android app

Open `android/` in Android Studio, or build a debug APK from PowerShell:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. Build outputs and local Gradle/Android Studio state are ignored by Git.

## TSE 33-industry master

The annual dividend screen looks up a Japanese holding's industry by its four-digit securities code in `android/app/src/main/assets/tse33_industry_master.tsv`. The bundled snapshot and its source are documented in that assets folder. Refresh it with:

```powershell
python scripts/sync_tse33_industry_master.py
```

Review the TSV changes before committing an updated master.
