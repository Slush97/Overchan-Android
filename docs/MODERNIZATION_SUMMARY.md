# What's new in esochan

A modernization overview of the 4chan Android client forked from
[Overchan Android](https://github.com/miku-nyan/Overchan-Android).

---

## 1. Executive summary

**esochan** is a GPLv3 4chan client for Android, reborn from the long-running
Overchan / Wishmaster codebase. The first public release (**v1.0.0**, package
`dev.esoc.esochan`) is not a cosmetic fork: it rebrands the app, raises the
platform floor to modern Android (min SDK 24 / target 35), replaces dead
networking and media stacks (OkHttp, Media3, Jsoup), strips multi-imageboard
cruft down to a 4chan-focused product, hardens posting and security defaults,
and ships with CI, signed GitHub Releases, and a small project site. The result
is a maintainable sideload client that still feels like a classic imageboard
app—tabs, drawer, themes, gallery—without the Apache HttpClient / target-SDK-28
era baggage that made Overchan hard to run on current devices.

---

## 2. Identity & rebrand

| Item | Overchan (legacy) | esochan |
|------|-------------------|---------|
| App name | Overchan / Wishmaster strings | **esochan** (en, de, ru, uk) |
| Application ID | historical package identity | **`dev.esoc.esochan`** |
| Source package | `nya.miku.wishmaster` | **`dev.esoc.esochan`** |
| FileProvider | old authority | **`dev.esoc.esochan.fileprovider`** |
| Gradle project | prior name | **`esochan`** |
| Launcher | `@drawable/ic_launcher` | Adaptive **mipmap** icons (`ic_launcher` / round) |

The package rename was a full tree move: Java/AIDL package declarations and
imports, layout custom-view FQCNs, manifest components, ProGuard keep rules
(`dev.esoc.**`), JNI symbols in `jni/gif.c`, and runtime SFW-package checks.
Broadcast action strings, copyright headers, and dead URLs pointing at the old
project site were updated as part of the rebrand. FileProvider paths are limited
to app cache directories rather than broad external storage.

---

## 3. Platform & build system

The build moved from a mid-generation AGP setup into a current single-module
Android application build:

| Setting | Current value |
|---------|----------------|
| Gradle | **8.10.2** |
| Android Gradle Plugin | **8.7.3** |
| Java / Kotlin JVM target | **17** |
| Kotlin plugin | **2.1.10** |
| compileSdk / targetSdk | **35** |
| minSdk | **24** (Android 7.0) |
| versionName / versionCode | **1.0.0** / **100** |
| Archives name | `esochan` (APK/AAB prefix) |

Supporting changes include a Gradle version catalog (`gradle/libs.versions.toml`),
modern `settings.gradle` plugin/dependency management, ViewBinding and
BuildConfig as explicit `buildFeatures`, NDK build for the GIF native library,
release **R8 minify + resource shrink**, and local release signing via
gitignored `keystore.properties`. Obsolete Ant/Eclipse project files were
removed. Lint uses a baseline for historical noise while CI still runs
`lintDebug` on every push.

Platform compliance work that accompanied the target-SDK climb:

- **Scoped storage** via a `DownloadStorage` facade (`MediaStore.Downloads` on
  API 29+; legacy paths only where still required).
- **`android:exported`** on all components; **PendingIntent `FLAG_IMMUTABLE`**.
- Notification **channels** and runtime **`POST_NOTIFICATIONS`**.
- Storage permissions capped with `maxSdkVersion`; **foreground service types**
  (`dataSync`) for download/posting-related services.
- Edge-to-edge enforcement handled so existing layouts remain usable; gallery
  fullscreen opts out where needed.
- Dead pre-minSdk `SDK_INT` branches and compatibility shims removed.

---

## 4. Dependencies & libraries

Legacy and unmaintained dependencies were replaced rather than papered over:

| Area | Before (Overchan-era) | After (esochan) |
|------|------------------------|-----------------|
| HTTP | Apache HttpClient (`cz.msebera…`) | **OkHttp 4.12.0** |
| Video | ExoPlayer 2.x | **Media3 ExoPlayer 1.9.x** |
| HTML | TagSoup | **Jsoup 1.22.x** |
| Serialization | Kryo 3.x | **Kryo 5.6.0** |
| Crash reports | ACRA → third-party endpoint | **Removed** |
| Base64 / org.json / support shims | Vendored or old support libs | **Framework / AndroidX** |
| UI kit | Fragmented Holo-era widgets | **Material Components 1.12**, AndroidX |
| Async | Ad-hoc threads | **Kotlin coroutines 1.10** + lifecycle |
| Sensitive prefs | Plain SharedPreferences | **EncryptedSharedPreferences** |

AndroidX is kept current within compileSdk 35 constraints (e.g. core 1.16,
Fragment 1.8, Lifecycle 2.8). Direct `commons-lang3` was dropped in favor of a
minimal `Tuples` utility; Commons Text remains for text helpers. Jetifier is not
required.

---

## 5. Architecture & code quality

esochan remains a **single-module** app (deliberately: no Compose rewrite, no
Hilt, no Navigation Component retrofit). Modernization is incremental:

- **~11 Kotlin files** introduced for new work: captcha (`Chan4Captcha*.kt`),
  `AppScope` / `CancellableTaskScope`, `SecurePreferences`, `TabsViewModel`,
  `BoardViewModel` / `BoardUiState`, and post form fragment pieces.
- **ViewBinding** enabled and applied across primary UI entry points.
- **ViewModels**: tab state exposed as `StateFlow` (`TabsViewModel`); board data
  loading extracted toward `BoardViewModel` with coroutines—while
  `BoardFragment` is still a large presentation surface.
- **Internal broadcasts** centralized (`InternalBroadcasts`) with safer receiver
  flags; custom views moved to **AppCompat** base classes.
- **Unit tests** cover post-response classification, cookie store behavior, HTTP
  streaming helpers, tab state, and attachment URI utilities.
- **4chan captcha** rewritten for the live slider / image-task flow (WebView
  path preserved for fingerprint parity with the browser), replacing dead
  reCAPTCHA paths.

What the project explicitly avoids for now: full Kotlin rewrite, modularization,
Room/DataStore migration, and Retrofit—documented as “not worth it unless
rebuilding from scratch” or deferred architecture work.

---

## 6. Security & privacy

Security posture improved in concrete, user-visible ways:

- **No ACRA / third-party crash endpoint** and no embedded legacy credentials.
- **Pass and sensitive values** live in **EncryptedSharedPreferences**
  (`secure_prefs`), with migration from plain prefs and recovery if the key is
  unusable. Backup rules **exclude** that file from full backup and device
  transfer.
- **Custom TLS trust chain removed**—system trust only.
- Cookie handling and HTTP retries hardened; Cloudflare and captcha still use
  careful WebView ↔ HTTP client coordination rather than inventing a new
  fingerprint path.
- Manifest **export flags**, narrowed **FileProvider** paths, **immutable**
  PendingIntents, and release **R8** obfuscation/shrinking.
- Product stance: **no ads, no remote telemetry**.

---

## 7. Product scope

Overchan was a multi-imageboard client. esochan is intentionally **4chan-first**:

- Module registration and new-tab UX reduced to **FourchanModule** only.
- Large sets of dead imageboard modules, HTML board readers, and unused
  per-board favicons were deleted (multi-phase cleanup ending in a
  4chan-only tree under `chans/fourchan/`).
- Deep links focus on **4chan.org** hosts; share target still accepts
  image/video for posting flows.
- **Posting reliability** work: fail-closed post response parsing
  (`FourchanPostResponse` — success / server error / unexpected), no
  null-as-success that wiped drafts, clearer post-success refresh, browser-like
  POST headers, attachment imports always copied into app cache before upload,
  captcha deferred to send time, bulk download support, and system file picker
  instead of a bundled picker.
- UX product touches: settings hub, visible reply affordances, FAB for new
  post/reply, bottom-sheet style post form work, sidebar saved threads /
  activity, gallery and board control polish.

---

## 8. UI/UX modernization

Without a Compose rewrite, the UI was refreshed for consistency and modern
platform expectations:

- **Adaptive launcher icons** (including round) and role-named **tintable vector**
  chrome icons shared across light/dark themes.
- **Material Components** themes; MainActivity / GalleryActivity on AppCompat so
  Material widgets and action bars inflate correctly.
- **Settings** migrated toward AndroidX Preference with a hub-style IA (“Settings”
  naming, less nested cruft).
- Drawer / sidebar polish, board and gallery panel controls, reply form contrast,
  thumbnail scaling and popup clipping fixes.
- **Directional slide animations** when switching tabs.
- Custom imageboard themes remain supported; the refresh preferred vectors and
  tint attributes over density-specific PNG chrome.

Full **Material 3** theming is still on the roadmap; the current stack is a
coherent Material Components + AppCompat hybrid.

---

## 9. Infrastructure

| Piece | Status |
|-------|--------|
| **CI** (`.github/workflows/build.yml`) | On push/PR to `master`: unit tests, lint, debug/release APK, App Bundle; artifacts uploaded |
| **Release** (`.github/workflows/release.yml`) | On `v*` tags: signed release build + GitHub Release APK/AAB from CHANGELOG notes when signing secrets are set |
| **Docs site** | GitHub Pages under `docs/` (classic imageboard-styled landing page) |
| **Lint baseline** | `lint-baseline.xml` keeps historical warnings from blocking CI |
| **Signing** | Documented secrets / local `keystore.properties`; same key required for in-place upgrades |

Distribution is **sideload-only** via GitHub Releases (not Play or F-Droid yet).

---

## 10. First release — v1.0.0

| Fact | Value |
|------|--------|
| Version | **1.0.0** (`versionCode` **100**) |
| Package ID | **`dev.esoc.esochan`** |
| Min Android | **7.0 (API 24)** |
| Target / compile SDK | **35** |
| License | **GPLv3** |
| Distribution | **GitHub Releases** APK (`esochan-1.0.0.apk`); optional AAB in CI |
| Tag | `v1.0.0` |
| Notes | Captcha and Cloudflare depend on live site behavior—device testing recommended |

Install by downloading the release APK and allowing install from the browser or
file manager. Updates require the same release signing key.

---

## 11. What's still open

Honest incomplete work from `ROADMAP.md` and posting/visual plans—not promised as
done in 1.0.0:

- Broader **Kotlin migration** and deeper **ViewModel** ownership of
  `BoardFragment` / UI state beyond tabs.
- **Room** / **DataStore** (still raw SQLite + SharedPreferences).
- Optional **Retrofit** / shared HTTP abstraction for modules.
- **Material 3** theming pass and further visual polish.
- **F-Droid** metadata / store publishing.
- Posting depth still evolving vs competitors: captcha ticket persistence across
  process death, fuller captcha WebView cookie bridging, email-verify for range
  bans, optional upload filename randomize, progressive video vs download-then-
  play, download throttle/backoff for large batches, richer in-thread 404 /
  autoupdate signals.

These are tracked as ongoing modernization, not failures of the 1.0.0 platform
lift.

---

## 12. By the numbers

| Metric | Value |
|--------|--------|
| Version | 1.0.0 (code 100) |
| Package | `dev.esoc.esochan` |
| minSdk / targetSdk | 24 / 35 |
| Java / Kotlin | 17 / 2.1.10 |
| AGP / Gradle | 8.7.3 / 8.10.2 |
| OkHttp | 4.12.0 |
| Media3 | 1.9.x |
| Jsoup | 1.22.x |
| Kryo | 5.6.0 |
| Source shape | ~169 Java + ~11 Kotlin under `src/dev/esoc/esochan/` |
| Unit test classes | 5 (post response, cookies, HTTP stream, tabs, attachments) |
| Imageboard modules | 1 (`fourchan`) |
| License | GPLv3 |

---

*Grounded in repository docs (`README.md`, `CHANGELOG.md`, `ROADMAP.md`,
`docs/plans/*`), build configuration, and git history through the v1.0.0 release
packaging. Features not present in the tree are not claimed here.*
