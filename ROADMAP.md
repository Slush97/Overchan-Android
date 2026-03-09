# esochan Modernization Roadmap

Rebranding Overchan Android → esochan, and modernizing for 2026 Android standards.

**Source package `nya.miku.wishmaster` is intentionally kept in Phase 1.** AGP 7.0+ decouples `namespace` (R class generation) from `applicationId` (device/store identity). A full source package rename is deferred to Phase 2 because it touches 262 files, 18 JNI symbols in C code, 39 hardcoded class names, and a runtime `isSFW` check. The app will install as `dev.esoc.esochan` on devices while source stays in `nya.miku.wishmaster`.

---

## Phase 1A: Rebrand Identity

### A1: Core identity (low risk)
- [x] `settings.gradle`: rootProject.name → `esochan`
- [x] `build.gradle`: add `applicationId "dev.esoc.esochan"` in defaultConfig
- [x] `res/values/strings.xml`: app_name, crash_dialog_title, pref_cat_about, pref_about_version_title
- [x] `res/values-ru/strings.xml`: same strings
- [x] `res/values-de/strings.xml`: same strings
- [x] `res/values-uk/strings.xml`: same strings
- [x] `README.md`: rewrite for esochan

### A2: FileProvider authority (medium risk — breaks sharing if wrong)
- [x] `AndroidManifest.xml`: authority → `dev.esoc.esochan.fileprovider`
- [x] `GalleryActivity.java:468`: update hardcoded authority string
- [x] Remove deprecated `package=` attribute from manifest (AGP reads namespace from build.gradle)

### A3: Broadcast actions & ACRA (low risk)
- [x] Update broadcast action strings in PostingService, TabsTrackerService, DownloadingService, BoardFragment
- [x] Disable or replace ACRA (currently points to `miku-nyan.cloudant.com` with embedded credentials)
- [x] `proguard-project.txt`: add `dev.esoc.**` keep rule alongside existing `nya.miku.**`

### A4: Copyright headers (zero functional risk, bulk change)
- [ ] Update GPL headers in all 206+ source files: "Overchan Android" → "esochan"
- [ ] Update URLs pointing to `miku-nyan.github.io/Overchan-Android/`

---

## Phase 1B: Build System Upgrade

### B1: Gradle + AGP bump (medium risk)
- [x] `gradle-wrapper.properties`: Gradle 7.5.1 → 8.10.2
- [x] `build.gradle`: AGP 7.4.2 → 8.7.3
- [x] Migrate deprecated DSL: `compileSdkVersion` → `compileSdk`, `minSdkVersion` → `minSdk`, etc.
- [x] `packagingOptions` → `packaging`
- [x] `gradle.properties`: add `android.defaults.buildfeatures.buildconfig=true` (AGP 8 disables BuildConfig by default)
- [x] `settings.gradle`: add `pluginManagement` and `dependencyResolutionManagement` blocks

### B2: Java 17 + compileSdk 35 (low risk)
- [x] `build.gradle`: Java 8 → Java 17
- [x] `build.gradle`: compileSdk 34 → 35

### B3: Version catalog (low risk, organizational)
- [x] Create `gradle/libs.versions.toml`
- [x] Move all dependency declarations to catalog
- [x] Update `build.gradle` to use `libs.*` references

### B4: Target SDK 28 → 33 (HIGH RISK — most complex step)
- [ ] **Scoped storage (SDK 30):** `CompatibilityImpl.getDefaultDownloadDir()` still uses `getExternalStoragePublicDirectory()` (has TODO comment). Remaining storage paths in FileCache, ApplicationSettings, etc. use `getExternalFilesDir()` or are guarded.
- [x] **Exported components (SDK 31):** Add `android:exported` to all activities/services in manifest
- [x] **PendingIntent mutability (SDK 31):** Add `FLAG_IMMUTABLE` to all PendingIntent calls in TabsTrackerService, DownloadingService, PostingService
- [x] **Notification channels (SDK 26+):** Create channels in MainApplication.onCreate() for downloads, posting, tab tracking
- [x] **POST_NOTIFICATIONS permission (SDK 33):** Add to manifest, add runtime permission request
- [x] **Storage permissions:** Add `maxSdkVersion="32"` to WRITE/READ_EXTERNAL_STORAGE, add READ_MEDIA_IMAGES etc. if needed
- [x] `build.gradle`: targetSdk → 33 (now at 35)

### B5: Target SDK 33 → 35 (medium risk)
- [x] **Foreground service types (SDK 34):** DownloadingService + TabsTrackerService have `dataSync`; PostingService doesn't use `startForeground()`
- [x] Add `FOREGROUND_SERVICE_DATA_SYNC` permission
- [x] Update `startForeground()` calls to pass service type
- [x] **Edge-to-edge (SDK 35):** `WindowCompat.setDecorFitsSystemWindows(window, true)` via `ActivityLifecycleCallbacks` for all activities; GalleryActivity fullscreen opts out
- [x] Remove dead `SDK_INT` branches (minSdk is 21)
- [x] `build.gradle`: targetSdk → 35

**Status:** Phase 1A (A1–A3) and Phase 1B (B1–B5) are complete except: A4 (copyright headers), B4 scoped storage TODO (`getExternalStoragePublicDirectory`).

---

## Phase 2: Source Package Rename (deferred)

Full rename of `nya.miku.wishmaster` → `dev.esoc.esochan` across the entire source tree. Deferred because it requires:

- [ ] Rename 262 Java files' package declarations and all cross-file imports
- [ ] Rename 18 JNI function symbols in `jni/gif.c` (or switch to `RegisterNatives`)
- [ ] Update 39 hardcoded class names in `MainApplication.MODULES`
- [ ] Fix `ApplicationSettings.isSFW` runtime check that depends on package name
- [ ] Update all hardcoded broadcast action strings
- [ ] Move source directories: `src/nya/miku/wishmaster/` → `src/dev/esoc/esochan/`

This should be done as a single atomic commit with IDE refactoring support.

---

## Phase 3: Dependency Modernization

### Replace dead/deprecated libraries
- [ ] Apache HttpClient (`cz.msebera.android:httpclient`) → OkHttp 4.x
- [x] ExoPlayer 2.19.1 → Media3 (`androidx.media3:media3-exoplayer 1.8.0`)
- [ ] TagSoup → Jsoup
- [ ] Kryo 3.0.3 → Kryo 5.x (or kotlinx.serialization)
- [x] `base64-2.3.8.jar` — N/A; no external base64 library present, already uses `android.util.Base64`
- [x] Remove local JARs from `libs/` — N/A; no `libs/` directory exists
- [x] `commons-lang3` → removed direct dependency; replaced Pair/Triple with minimal `Tuples.java` utility

### Bump AndroidX
- [ ] Review all AndroidX deps for latest stable versions
- [x] Remove `android.enableJetifier=true` (already absent from gradle.properties)

---

## Phase 4: Architecture Improvements (ongoing)

### New code in Kotlin
- [ ] Add Kotlin plugin to build
- [ ] Write new files in Kotlin, migrate existing files opportunistically
- [ ] Add coroutines for async operations (replacing raw threads)

### ViewModel + state management
- [ ] Add `androidx.lifecycle:lifecycle-viewmodel` dependency
- [ ] Migrate MainActivity and BoardFragment state into ViewModels
- [ ] Use StateFlow/LiveData for UI updates

### Storage layer
- [ ] Consider Room for database access (currently raw SQLite)
- [ ] Consider DataStore for SharedPreferences replacement

### Networking
- [ ] After OkHttp migration (Phase 3), consider Retrofit for API abstraction
- [ ] Migrate channel modules to use a common HTTP abstraction

---

## Phase 5: Polish & Infrastructure

- [ ] GitHub Actions CI for building APKs on push/PR
- [ ] New app icon and branding assets
- [ ] Review and trim supported imageboards list (many are dead)
- [ ] Material 3 theming
- [ ] ViewBinding to replace `findViewById`
- [ ] Min SDK 21 → 24 (drops Android 5.0-6.x, simplifies code, ~1% of devices)
- [ ] F-Droid metadata if publishing there

---

## What NOT to do (unless rebuilding from scratch)

- **Jetpack Compose**: Massive lift to convert existing XML layouts. Not worth it for maintenance.
- **Hilt/Dagger**: Adds complexity. The singleton pattern is ugly but functional.
- **Navigation Component**: Retrofitting into an existing fragment-based app is painful.
- **Full Kotlin rewrite**: 72K lines is too much. Migrate incrementally.
- **Modularization**: Single-module is fine for this app's scale.

---

## Key Risk Areas

| Risk | Phase | Impact | Mitigation |
|------|-------|--------|------------|
| Scoped storage rewrite | B4 | High — breaks downloads, cache, file browsing | Isolate into helper class, test on API 30+ emulator |
| PendingIntent crashes on Android 12+ | B4 | Medium — crash on notification tap | Audit every PendingIntent; use FLAG_IMMUTABLE |
| Missing foreground service types | B5 | High — service crash on Android 14+ | Straightforward manifest + code fix |
| JNI symbol rename | Phase 2 | High — native crash if mismatched | Consider RegisterNatives pattern instead |
| ExoPlayer → Media3 migration | Phase 3 | Medium — media playback breaks | Media3 has migration guide, API is similar |

---

## Critical Files Reference

| File | Why it matters |
|------|---------------|
| `build.gradle` | SDK versions, deps, namespace, applicationId |
| `AndroidManifest.xml` | Permissions, services, exported flags, FileProvider |
| `src/.../common/MainApplication.java` | App entry point, module registration, init |
| `src/.../cache/FileCache.java` | Storage access — scoped storage rewrite target |
| `src/.../ui/downloading/DownloadingService.java` | Foreground service, notifications, storage |
| `src/.../ui/tabs/TabsTrackerService.java` | Foreground service, notifications |
| `src/.../ui/posting/PostingService.java` | Foreground service, file uploads |
| `src/.../common/ApplicationSettings.java` | isSFW check, storage paths, preferences |
| `jni/gif.c` | JNI symbols tied to package name |
| `proguard-project.txt` | Keep rules for package names |
