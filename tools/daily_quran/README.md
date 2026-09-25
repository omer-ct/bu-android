# Daily Quran card pack

Pre-rendered 1080×1920 JPEG Story cards matching the Be Ummati **Daily Quran** template.

## Layout

| Zone | Content |
|------|---------|
| Top half | Teal→black gradient, “Daily Quran”, Arabic (Uthmani), Urdu (Nastaliq) |
| Bottom | English + `AL QURAN SURAH … n:n` |
| Right edge | Vertical slogan |
| Bottom-left | Red **Be Ummati** badge |

## Long ayah policy

1. Shrink Arabic / Urdu / English until they fit.
2. If still too tall → **translation-only** (no Arabic on the card).
3. If still too tall → **split** into two images:
   - `NNN_MMM.jpg` / `NNN_MMM_a.jpg` — Arabic half
   - `NNN_MMM_b.jpg` — remaining Arabic + translations

## Generate (on device)

```bash
# Install a debug build, then:
./tools/daily_quran/generate.sh 1 1          # Al-Fatiha only
./tools/daily_quran/generate.sh 1 114        # full mushaf (~6k images, large)
```

Output lands in `tools/daily_quran/out/` after `adb pull`. Copy a subset into
`app/src/main/assets/daily_quran/` for bundling, or host the full pack for
[DailyQuranPack] download later.

Full mushaf is hundreds of MB — prefer Git LFS / CDN, not a fat APK.
