# Be Ummati (Android)

Kotlin + Jetpack Compose port of Be Ummati. Catalog JSON is shared with the [iOS repo](https://github.com/Sprint-Developer/bu-ios).

**Package:** `com.codefixr.beummati`

## Why Android for “just download a file”

Android **can** install an APK from GitHub Releases with no Mac and no developer account on the user’s phone (they only need to allow “install unknown apps” for the browser/Files).

Typical flow:

1. You tag `v1.0.1` and attach `be-ummati.apk` on a GitHub Release  
2. User opens the release on their phone → Downloads APK → Installs  
3. Next version = new release + new APK

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio)  
2. **Open** this folder (`bu-android`)  
3. Let Gradle sync  
4. Run on a device/emulator  

## Build a release APK (local)

```bash
./gradlew assembleRelease
# or debug while iterating:
./gradlew assembleDebug
```

Outputs under `app/build/outputs/apk/`.

## Status

MVP scaffold: library list from `LibraryCatalog.json` + stream lecture audio via Media3 when a `LectureAudioCatalog` track exists. Subtitles/SRT sync, downloads, and full parity with iOS are next.
