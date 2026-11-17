# Onikiri v4.0-0 "Powder"

Release name and version come from the last line of `codenames.txt` (`powder (4.0-0)`):
Gradle bakes them into `BuildConfig.VERSION_NAME` / `BuildConfig.VERSION_CODENAME`, CI uses the same line for the release tag.

## Done
1. **Navigation**: 4 tabs (Modules, Installed, Search, Settings) in a floating capsule bar. All icons live in
   `ui/components/AppIcons.kt` and are returned as `Painter` – swap one function body for
   `painterResource(R.drawable.ic_*)` to use Figma artwork, no screen changes needed.
   Installed tab scans `/data/adb/modules/*/module.prop` (also modules outside any repo): enable/disable (`disable`),
   remove (`remove`, with Undo), Update, and a `+` button for a local `.zip` (OpenDocument → ModuleValidator → live log).
   The catalog shows Install / Installed / Update (versionCode of the repo's `module.prop` or release tag > installed).
2. **Material 3 Expressive**: `MaterialExpressiveTheme` + `MotionScheme.expressive()`, morphing `ContainedLoadingIndicator`
   (SoftBurst, Cookie9Sided, Sunny, Flower) that springs into a check / error badge, spring motion for cards, presets, banners.
3. **Setup**: root re-check closes the cached libsu shell (`Shell.getCachedShell()?.close()`), automatic re-check on `ON_RESUME`
   on the welcome step, confetti capped at 180 particles.
4. **Settings as categories**: Customization (theme, AMOLED, Dynamic color, accents, font, launcher icon via 5 `activity-alias`),
   Repositories & cache, System & Root, About (+ Licenses). Font choice is stored in DataStore.
5. **Codename baking** and licenses screen (GPL-3.0, Material Icons, OFL fonts, libsu, AndroidX, Kotlin).
6. **Archive picker**: all `.zip` assets of the release, optional `asset_filter` in `.jsonl`; one match downloads directly,
   several (or no `module.prop` after download) opens the RadioButton dialog; tried archives are greyed "no module.prop".
7. **Any server** (`http://` and `https://`, cleartext allowed), `# Name` header / file-name fallback for repository names,
   per-repository switches, clipboard export/import, filter chips, clickable source link, "Copy log", reboot banner,
   search by id/author, in-memory GitHub API cache, clear HTTP 403 rate-limit message, string typos fixed.

## Deliberate deviations (to avoid unverifiable API drift)
- Floating bar and docked buttons are small custom composables (shape/spacing follow the Expressive spec) instead of the
  experimental `FloatingToolbar` / `ButtonGroup` APIs, whose signatures changed between 1.4 alphas.
- "Emphasized" typography is `TextStyle.emphasized()` (bold variant) so it follows the selected font.
- OkHttp is **not** listed in Licenses because the app uses `HttpURLConnection`.
- Fonts (Inter, JetBrains Mono, Outfit) are bundled as `.otf/.ttf` in `res/font`, licenses in `assets/licenses`.

## Not verified
Nothing could be compiled here (no Android SDK / Gradle / network). Run `./gradlew assembleDebug testDebugUnitTest`.
