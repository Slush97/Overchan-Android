# esochan Modernization Roadmap

Rebranding Overchan Android → esochan, and modernizing for 2026 Android standards.

**Source package `nya.miku.wishmaster` is intentionally kept in Phase 1.** AGP 7.0+ decouples `namespace` (R class generation) from `applicationId` (device/store identity). A full source package rename is deferred to Phase 2 because it touches 262 files, 18 JNI symbols in C code, 39 hardcoded class names, and a runtime `isSFW` check. The app will install as `dev.esoc.esochan` on devices while source stays in `nya.miku.wishmaster`.

---

## Phase 1A: Rebrand Identity

### A1: Core identity (low risk)
- [ ] `settings.gradle`: rootProject.name → `esochan`
- [ ] `build.gradle`: add `applicationId "dev.esoc.esochan"` in defaultConfig
- [ ] `res/values/strings.xml`: app_name, crash_dialog_title, pref_cat_about, pref_about_version_title
- [ ] `res/values-ru/strings.xml`: same strings
- [ ] `res/values-de/strings.xml`: same strings
- [ ] `res/values-uk/strings.xml`: same strings
- [ ] `README.md`: rewrite for esochan

### A2: FileProvider authority (medium risk — breaks sharing if wrong)
- [ ] `AndroidManifest.xml`: authority → `dev.esoc.esochan.fileprovider`
- [ ] `GalleryActivity.java:468`: update hardcoded authority string
- [ ] Remove deprecated `package=` attribute from manifest (AGP reads namespace from build.gradle)

### A3: Broadcast actions & ACRA (low risk)
- [ ] Update broadcast action strings in PostingService, TabsTrackerService, DownloadingService, BoardFragment
- [ ] Disable or replace ACRA (currently points to `miku-nyan.cloudant.com` with embedded credentials)
- [ ] `proguard-project.txt`: add `dev.esoc.**` keep rule alongside existing `nya.miku.**`

### A4: Copyright headers (zero functional risk, bulk change)
- [ ] Update GPL headers in all 206+ source files: "Overchan Android" → "esochan"
- [ ] Update URLs pointing to `miku-nyan.github.io/Overchan-Android/`

---

## Phase 1B: Build System Upgrade

### B1: Gradle + AGP bump (medium risk)
- [ ] `gradle-wrapper.properties`: Gradle 7.5.1 → 8.10.2
- [ ] `build.gradle`: AGP 7.4.2 → 8.7.3
- [ ] Migrate deprecated DSL: `compileSdkVersion` → `compileSdk`, `minSdkVersion` → `minSdk`, etc.
- [ ] `packagingOptions` → `packaging`
- [ ] `gradle.properties`: add `android.defaults.buildfeatures.buildconfig=true` (AGP 8 disables BuildConfig by default)
- [ ] `settings.gradle`: add `pluginManagement` and `dependencyResolutionManagement` blocks

### B2: Java 17 + compileSdk 35 (low risk)
- [ ] `build.gradle`: Java 8 → Java 17
- [ ] `build.gradle`: compileSdk 34 → 35

### B3: Version catalog (low risk, organizational)
- [ ] Create `gradle/libs.versions.toml`
- [ ] Move all dependency declarations to catalog
- [ ] Update `build.gradle` to use `libs.*` references

### B4: Target SDK 28 → 33 (HIGH RISK — most complex step)
- [ ] **Scoped storage (SDK 30):** Rewrite `Environment.getExternalStorageDirectory()` in FileCache, ApplicationSettings, PostFormActivity, CustomThemeListActivity, CompatibilityUtils, UriFileUtils → use `context.getExternalFilesDir()` / MediaStore / SAF
- [ ] **Exported components (SDK 31):** Add `android:exported` to all activities/services in manifest
- [ ] **PendingIntent mutability (SDK 31):** Add `FLAG_IMMUTABLE` to all PendingIntent calls in TabsTrackerService, DownloadingService, PostingService
- [ ] **Notification channels (SDK 26+):** Create channels in MainApplication.onCreate() for downloads, posting, tab tracking
- [ ] **POST_NOTIFICATIONS permission (SDK 33):** Add to manifest, add runtime permission request
- [ ] **Storage permissions:** Add `maxSdkVersion="32"` to WRITE/READ_EXTERNAL_STORAGE, add READ_MEDIA_IMAGES etc. if needed
- [ ] `build.gradle`: targetSdk → 33

### B5: Target SDK 33 → 35 (medium risk)
- [ ] **Foreground service types (SDK 34):** Add `foregroundServiceType` to all services in manifest (dataSync for DownloadingService, shortService for PostingService, etc.)
- [ ] Add `FOREGROUND_SERVICE_DATA_SYNC` permission
- [ ] Update `startForeground()` calls to pass service type
- [ ] **Edge-to-edge (SDK 35):** Handle window insets if content is obscured by system bars
- [ ] Remove dead `SDK_INT < ECLAIR` branches (minSdk is 21)
- [ ] `build.gradle`: targetSdk → 35

**Recommended commit order:** A1 → A2 → A3 → B1 → B2 → B3 → A4 → B4 → B5

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
- [ ] ExoPlayer 2.19.1 → Media3 (`androidx.media3:media3-exoplayer`)
- [ ] TagSoup → Jsoup
- [ ] Kryo 3.0.3 → Kryo 5.x (or kotlinx.serialization)
- [ ] `base64-2.3.8.jar` → `java.util.Base64` (built into Java 8+)
- [ ] Remove local JARs from `libs/` where Maven equivalents exist (minlog, objenesis, reflectasm)
- [ ] `commons-lang3:3.4` → evaluate if still needed, bump or remove

### Bump AndroidX
- [ ] Review all AndroidX deps for latest stable versions
- [ ] Remove `android.enableJetifier=true` once all deps are AndroidX-native

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
