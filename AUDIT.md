# Onikiri — audit and changes

## Bugs found and fixed
Build / dependencies
1. Outdated `core-ktx`, `lifecycle`, `activity-compose` next to Compose BOM 2026 → updated, lifecycle libs share one version.
2. `material-icons-extended` hard-coded outside the version catalog → moved into `libs.versions.toml`.
3. `kotlinx-coroutines` used but not declared → added.
4. libsu needs JitPack, blocked by `FAIL_ON_PROJECT_REPOS` → JitPack added for `com.github.topjohnwu` only.
5. `Theme.Onikiri` extended a Light parent theme → white flash in dark/AMOLED. Added `values-night`, window background follows the app theme.
6. CI: version parsing failed on a trailing blank line of `codenames.txt` → parsing made robust.

JSONL parsing / network
7. Every catalog load hit the GitHub API for a SHA (rate limit 60/h, no offline use) → ETag conditional requests on the raw file, on-disk fallback.
8. One malformed JSONL line failed the whole catalog → bad lines skipped.
9. Duplicate module ids crashed `LazyColumn` (duplicate keys) → deduplicated.
10. Index was written straight into the final file (partial downloads) → atomic temp file + rename.
11. Every error shown as "JSONL parse error", no retry → real message, Retry button, pull-to-refresh.

Compose / UI state
12. `ModulesScreen` was re-created on every tab switch (spinner, refetch, lost scroll) → state in ViewModels, tabs keep state.
13. Selected tab lost on rotation → `rememberSaveable`.
14. `Module` was a Java class (unstable for Compose, name clash with `java.lang.Module`) → Kotlin `@Immutable RootModule`.
15. `catch (_: Exception)` swallowed `CancellationException` → proper cancellation handling in ViewModels.
16. Settings read SharedPreferences during composition, duplicated constants, silent Save → `SettingsStore` + `StateFlow`.
17. Install button had an empty handler, Search tab was a stub → implemented.
18. Status-bar icon contrast ignored the in-app theme → set from the app theme.

## Added
Safe download + validation + libsu install with live log, Reboot / Soft Reboot, expandable cards with README,
instant search, animated navigation, theme settings (Dynamic color, 8 presets, System/Light/Dark, AMOLED),
repository manager, cache cleaning, system summary, first-run root-manager setup with 3D confetti.

## Not verified
The project could not be compiled in the authoring environment (no Android SDK, Gradle or network).
Run `./gradlew assembleDebug testDebugUnitTest` and fix any version-specific API drift.
