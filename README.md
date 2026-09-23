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

Full app with asset parity with iOS. Every bundled catalog from the iOS app ships under `app/src/main/assets/` and is read directly:

| Asset | Used by |
| --- | --- |
| `LibraryCatalog.json`, `Library/{seriesId}/{chapterId}.json` | Library tab — series → chapters → transcript reader |
| `LectureAudioCatalog.json` | Lecture player (archive.org streams, offline downloads) |
| `AlQalam/srt/*.srt` | Timed subtitles in the player (English / Urdu / Arabic) |
| `HadithCatalog.json` | Hadith tab — collections → kitāb list |
| `HisnAlMuslim.json` | Library → Hisn al-Muslim duas, Today adhkar shortcuts |
| `ScholarQuotes.json` | Scholars tab, Today reflection |
| `SahabaStories.json` | Library → Sahaba stories |

### Tabs (matches iOS `RootView`)

1. **Today** — Gregorian + Hijri date, prayer-times card (stub), continue listening, lecture series shortcuts, adhkar, daily scholar quote
2. **Qur’an** — 114 surahs; Arabic (Uthmani) + English (Saheeh) from `api.alquran.cloud`, optional Urdu Tafsir Ibn Kathir; cached to disk for offline
3. **Hadith** — collections from `HadithCatalog.json`, texts from `fawazahmed0/hadith-api` (Arabic, English, Urdu when available)
4. **Library** — lecture series, Tareekh Ibn Kathir, Sahaba stories, Hisn al-Muslim
5. **Scholars** — quotes filterable by scholar and theme
6. **Saved** — bookmarks + notes (JSON in SharedPreferences); lecture “moments” replay from the saved timestamp

### Lecture player

Mini player above the tab bar + full player: play/pause, seek, ±15 s, playback speed, sleep timer (15/30/45/60 min or end of lecture), SRT subtitles with transcript view, per-series subtitle sync offset (persisted), auto-queue of the rest of the series, offline download to app storage, and a +15 dB `LoudnessEnhancer` boost (on by default for the quiet Book of Jihad masters). Playback continues in the background via a Media3 `MediaSessionService` with a media notification.

### Code layout

```
app/src/main/java/com/codefixr/beummati/
  data/        catalog models, asset repository, Qur’an / Hadith / Tafsir APIs, saved store
  player/      LecturePlayerSession (ExoPlayer singleton), SrtCueParser, PlaybackService
  ui/          tab navigation shell + shared components
  ui/home  ui/quran  ui/hadith  ui/library  ui/scholars  ui/saved  ui/player
```
