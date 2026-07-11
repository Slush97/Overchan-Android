# esochan Modernization and Cleanup Implementation Plan

> **OBSOLETE for execution — see [`2026-07-11-post-1.0-improvement-program.md`](./2026-07-11-post-1.0-improvement-program.md).**  
> This file is retained as research/history only. Do not schedule PRs from it without reconciling against the 2026-07-11 program doc (Gate A / fail-closed posting already shipped in v1.0.0).


> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Do not batch unrelated tiers. After each task, run the listed verification command and review `git diff` before continuing.

**Goal:** Turn the current fork into a maintainable, modern Android application by addressing obvious lint failures, platform-policy risks, legacy build artifacts, and the largest architecture hotspots in increasing scope tiers.

**Architecture:** Keep the single-module Gradle app for now. Prefer incremental modernization over rewrites: fix correctness/security/platform issues first, then carve app-owned god classes into repositories/view models, and only then consider dependency/API upgrades. Treat vendored legacy widgets differently from app-owned code: isolate or baseline vendored code, refactor app code.

**Tech Stack:** Android Gradle Plugin, Java 17, Kotlin, AndroidX, AppCompat, Material Components, ViewBinding, OkHttp, Media3, SQLite/possible Room, SharedPreferences/possible DataStore, Android lint.

---

## Current Baseline

Captured on 2026-07-07 from `/home/esoc/code/Overchan-Android`.

- Git branch: `master`
- Working tree at audit time: clean
- Build command run: `./gradlew assembleDebug lintDebug`
- `assembleDebug`: succeeds/up-to-date
- `lintDebug`: fails
- Lint summary: 56 errors, 306 warnings
- Lint report: `build/reports/lint-results-debug.html`
- Lint text report: `build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`

Largest app/source hotspots:

- `src/dev/esoc/esochan/ui/presentation/BoardFragment.java` — 3,951 lines
- `src/dev/esoc/esochan/ui/gallery/GalleryActivity.java` — 1,421 lines
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java` — 957 lines
- `src/dev/esoc/esochan/ui/MainActivity.java` — 867 lines
- `src/dev/esoc/esochan/ui/Database.java` — raw SQLite user data layer
- `src/dev/esoc/esochan/common/MainApplication.java` — public singleton/service locator

Major lint error groups:

- `UnsafeImplicitIntentLaunch`: 14
- `WrongConstant`: 12
- `MissingTranslation`: 9
- `StringFormatMatches`: 8
- `MissingQuantity`: 6
- `AppCompatCustomView`: 4
- `MissingSuperCall`: 1
- `UnspecifiedRegisterReceiverFlag`: 1
- `AppLinkUrlError`: 1

---

## Tier 0 — Safety Rails Before Cleanup

**Difficulty:** Low

**Scope:** Documentation/configuration only

**Goal:** Make the cleanup work trackable and prevent accidental regressions.

### Task 0.1: Add a lint baseline decision point

**Objective:** Decide whether this repo should temporarily use an Android lint baseline while real errors are fixed.

**Files:**
- Inspect: `build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`
- Optionally modify: `build.gradle`
- Optionally create: `lint-baseline.xml`

**Steps:**
1. Re-run lint to refresh reports:
   - `./gradlew lintDebug`
   - Expected: fails with current lint errors.
2. Review the first 100 errors/warnings in the text report.
3. If the team wants CI green before fixing all historical lint, add this to `build.gradle` under `android { ... }`:
   - `lint { baseline = file("lint-baseline.xml") }`
4. Generate baseline:
   - `./gradlew updateLintBaseline`
5. Re-run:
   - `./gradlew lintDebug`
   - Expected: either passes against baseline or reports only new/unbaselined issues.

**Recommendation:** Do not baseline `UnsafeImplicitIntentLaunch`, `UnspecifiedRegisterReceiverFlag`, `MissingSuperCall`, or broad file/storage issues. Fix those first if possible.

### Task 0.2: Add an audit checklist to PR workflow

**Objective:** Make every cleanup PR verify build, lint, and no accidental working-tree changes.

**Files:**
- Create: `.github/pull_request_template.md` if absent
- Or modify existing GitHub workflow/docs under `.github/`

**Checklist content:**
- `./gradlew assembleDebug` passes
- `./gradlew lintDebug` passes or failures are explained against baseline
- `git diff` reviewed
- No secrets or credentials added
- No broad storage/file-provider paths added
- No new implicit internal broadcasts added

**Verification:**
- Confirm file exists.
- Confirm checklist appears in the PR template.

---

## Tier 1 — Quick, Low-Risk Fixes

**Difficulty:** Low

**Scope:** Small targeted edits, no architecture changes

**Goal:** Remove obvious obsolete build files and cheap lint failures.

### Task 1.1: Remove obsolete Ant/Eclipse project files

**Objective:** Delete misleading files from the pre-Gradle Android project era.

**Files:**
- Delete: `build.xml`
- Delete: `project.properties`
- Delete: `.classpath`
- Delete: `.project`
- Delete: `.cproject`
- Delete: `.settings/` if not used by current tooling

**Rationale:**
- `build.xml` references Java 1.7 and old Android Ant tooling.
- `project.properties` targets `android-23`.
- The actual build is Gradle.

**Steps:**
1. Remove the files/directories above.
2. Run:
   - `./gradlew assembleDebug`
   - Expected: build succeeds.
3. Run:
   - `git status --short`
   - Expected: only intended deletions.

### Task 1.2: Move deprecated BuildConfig Gradle flag into `build.gradle`

**Objective:** Remove AGP 9-incompatible deprecated property.

**Files:**
- Modify: `gradle.properties:3`
- Modify: `build.gradle:34-37`

**Current issue:**
- `gradle.properties` contains `android.defaults.buildfeatures.buildconfig=true`.
- Gradle warns this is deprecated and will be removed in AGP 9.0.

**Steps:**
1. Remove this line from `gradle.properties`:
   - `android.defaults.buildfeatures.buildconfig=true`
2. In `build.gradle`, update `buildFeatures` to include:
   - `buildConfig true`
3. Run:
   - `./gradlew assembleDebug`
   - Expected: build succeeds and the BuildConfig deprecation warning is gone.

### Task 1.3: Fix `ClickableToast` lifecycle lint errors

**Objective:** Fix the first hard lint failure and Android 13+ receiver flag issue.

**Files:**
- Modify: `src/dev/esoc/esochan/lib/ClickableToast.java:58`
- Modify: `src/dev/esoc/esochan/lib/ClickableToast.java:75`

**Current issues:**
- `onAttachedToWindow()` does not call `super.onAttachedToWindow()`.
- `registerReceiver(...)` lacks exported/not-exported flags on newer Android.

**Steps:**
1. Read `ClickableToast.java` around `onAttachedToWindow()` and receiver registration.
2. Add the required super call at the start of `onAttachedToWindow()`.
3. Replace context receiver registration with AndroidX `ContextCompat.registerReceiver(...)` if AndroidX Core is already available, using `ContextCompat.RECEIVER_NOT_EXPORTED` for this internal receiver.
4. Add imports only if needed.
5. Run:
   - `./gradlew lintDebug`
   - Expected: these two lint errors are gone. Other lint failures may remain.
6. Run:
   - `./gradlew assembleDebug`
   - Expected: build succeeds.

### Task 1.4: Fix AppCompat custom view lint errors

**Objective:** Update custom widgets that extend framework views where AppCompat-compatible widgets are expected.

**Files flagged by lint:**
- `src/dev/esoc/esochan/lib/pullable_layout/CircleImageView.java:29`
- `src/dev/esoc/esochan/lib/JellyBeanSpanFixTextView.java:52`
- `src/dev/esoc/esochan/lib/NonParentFocusableImageView.java:15`
- `src/dev/esoc/esochan/lib/gallery/TouchGifView.java:31`

**Steps:**
1. Inspect each class inheritance and constructor signatures.
2. Where safe, replace framework base classes with AppCompat variants:
   - `ImageView` -> `AppCompatImageView`
   - `TextView` -> `AppCompatTextView`
3. Preserve constructors and behavior exactly.
4. Run:
   - `./gradlew lintDebug`
   - Expected: `AppCompatCustomView` errors gone.
5. Run:
   - `./gradlew assembleDebug`
   - Expected: build succeeds.

---

## Tier 2 — Platform Correctness and Security Boundaries

**Difficulty:** Low to Medium

**Scope:** Small-to-medium behavior-preserving code changes

**Goal:** Fix platform API correctness issues that can affect modern Android devices.

### Task 2.1: Make internal broadcasts package-scoped or replace with explicit app events

**Objective:** Remove unsafe implicit internal broadcast lint errors.

**Files flagged by lint:**
- `src/dev/esoc/esochan/ui/presentation/BoardFragment.java:1366`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:218`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:283`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:293`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:304`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:376`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:573`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:711`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:728`
- `src/dev/esoc/esochan/ui/posting/PostingService.java:189`
- `src/dev/esoc/esochan/ui/posting/PostingService.java:203`
- `src/dev/esoc/esochan/ui/posting/PostingService.java:212`
- `src/dev/esoc/esochan/ui/posting/PostingService.java:311`
- `src/dev/esoc/esochan/ui/tabs/TabsTrackerService.java:359`

**Minimal approach:**
For internal broadcasts, set the app package before sending:
- `intent.setPackage(getPackageName())`

**Preferred approach:**
Create a helper to avoid repeating the pattern:
- Create or update utility, for example `src/dev/esoc/esochan/common/Broadcasts.java`
- Add methods for internal broadcast construction/sending.

**Verification:**
1. Run:
   - `./gradlew lintDebug`
   - Expected: `UnsafeImplicitIntentLaunch` errors removed.
2. Smoke test areas manually if possible:
   - download progress dialog updates
   - posting progress updates
   - tab tracker notification refresh
   - gallery page-loaded restore path

### Task 2.2: Restrict FileProvider paths

**Objective:** Stop exposing all external storage via FileProvider.

**Files:**
- Modify: `res/xml/file_provider_paths.xml`
- Inspect usages: search for `FileProvider.getUriForFile` and authority `dev.esoc.esochan.fileprovider`

**Current risk:**
- `res/xml/file_provider_paths.xml:3` exposes `<external-path name="external" path="." />`.

**Steps:**
1. Locate all FileProvider URI generation usages.
2. Identify which directories actually need to be shared.
3. Replace broad external path with narrower entries, likely:
   - `cache-path` for temporary files
   - `files-path` for app-private files
   - `external-files-path` for app-specific external files
   - `external-cache-path` if needed
4. Avoid `<external-path path="." />` unless there is a documented, reviewed need.
5. Run:
   - `./gradlew assembleDebug`
6. Manually test:
   - share image/video from gallery
   - open downloaded/saved attachment if supported

### Task 2.3: Fix or document app-link manifest issue

**Objective:** Resolve lint `AppLinkUrlError` in the manifest.

**Files:**
- Modify: `AndroidManifest.xml:144-149`

**Current area:**
The intent filter for `application/zip` and `text/html` uses MIME types without a full app-link URL structure.

**Steps:**
1. Inspect the intent filter at `AndroidManifest.xml:144-149`.
2. Decide if this is intended for opening local files, shares, or browser links.
3. Split filters if needed:
   - URL filters: include `scheme`/`host`
   - MIME/file-opening filters: keep as file/content where appropriate
4. Run:
   - `./gradlew lintDebug`
   - Expected: `AppLinkUrlError` resolved.
5. Manually test opening supported local archive/html files.

### Task 2.4: Audit storage compatibility flags

**Objective:** Remove leftover legacy scoped-storage flags if no longer needed.

**Files:**
- Inspect/modify: `AndroidManifest.xml:27-43`
- Inspect: `src/dev/esoc/esochan/ui/CompatibilityImpl.java:162-164`
- Inspect: `src/dev/esoc/esochan/ui/downloading/DownloadStorage.java`

**Current concerns:**
- `preserveLegacyExternalStorage=true`
- `WRITE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE` still present for older SDKs
- `CompatibilityImpl.getDefaultDownloadDir()` uses `Environment.getExternalStoragePublicDirectory(...)`

**Steps:**
1. Confirm which code paths still call `CompatibilityImpl.getDefaultDownloadDir()`.
2. Confirm all Android 29+ download writes go through `DownloadStorage`/MediaStore or app-specific directories.
3. If `preserveLegacyExternalStorage` is no longer needed, remove it.
4. If legacy permissions are only needed for pre-29/pre-33 behavior, keep maxSdk fences and document why in manifest comments.
5. Run:
   - `./gradlew assembleDebug lintDebug`
6. Manual test:
   - save/download attachment on modern emulator/device
   - open saved thread/archive

---

## Tier 3 — Lint Correctness Cleanup

**Difficulty:** Medium

**Scope:** Multiple small fixes across resources and UI code

**Goal:** Reduce lint noise enough that future regressions are obvious.

### Task 3.1: Fix `StringFormatMatches` errors in posting UI

**Objective:** Align string format placeholders with call sites.

**Files:**
- `src/dev/esoc/esochan/ui/posting/PostFormActivity.java:391`
- `src/dev/esoc/esochan/ui/posting/PostFormActivity.java:394`
- `src/dev/esoc/esochan/ui/posting/PostFormFragment.kt:553`
- `src/dev/esoc/esochan/ui/posting/PostFormFragment.kt:555`
- Relevant strings in `res/values*/strings.xml`

**Steps:**
1. Inspect the call sites and the string resource definitions.
2. Fix placeholders in resources or arguments in code so every locale matches.
3. Run:
   - `./gradlew lintDebug`
   - Expected: `StringFormatMatches` errors gone.
4. Run:
   - `./gradlew assembleDebug`

### Task 3.2: Fix Russian plural quantity errors

**Objective:** Add missing plural quantities for Russian resources.

**Files:**
- `res/values-ru/strings.xml:59`
- `res/values-ru/strings.xml:64`
- `res/values-ru/strings.xml:69`
- `res/values-ru/strings.xml:77`
- `res/values-ru/strings.xml:94`
- `res/values-ru/strings.xml:539`

**Steps:**
1. Compare each Russian plural with the base `res/values/strings.xml` plural.
2. Add missing Android plural quantities needed for Russian.
3. Run:
   - `./gradlew lintDebug`
   - Expected: `MissingQuantity` errors gone.

### Task 3.3: Fix or suppress intentional missing translations

**Objective:** Remove `MissingTranslation` errors intentionally.

**Files:**
- `res/values/strings.xml:22`
- `res/values/strings.xml:23`
- `res/values/strings.xml:24`
- `res/values/strings.xml:89`
- `res/values/strings.xml:166-169`
- `res/values/strings.xml:258`
- Corresponding locale files under `res/values-ru`, `res/values-de`, `res/values-uk`

**Steps:**
1. For user-visible strings, add translations or copy English into locale files as a temporary fallback.
2. For non-user-visible strings, add `translatable="false"`.
3. Run:
   - `./gradlew lintDebug`
   - Expected: `MissingTranslation` errors gone.

### Task 3.4: Fix `WrongConstant` errors

**Objective:** Correct invalid constants flagged by lint.

**Files flagged:**
- `src/dev/esoc/esochan/lib/pullable_layout/CircleImageView.java:59`
- `src/dev/esoc/esochan/ui/MainActivity.java:131`
- `src/dev/esoc/esochan/ui/MainActivity.java:136`
- `src/dev/esoc/esochan/ui/MainActivity.java:144`
- `src/dev/esoc/esochan/ui/MainActivity.java:184`
- `src/dev/esoc/esochan/ui/MainActivity.java:233`
- `src/dev/esoc/esochan/ui/MainActivity.java:718`
- `src/dev/esoc/esochan/ui/MainActivity.java:776`
- `src/dev/esoc/esochan/lib/gallery/verticalviewpager/VerticalViewPager.java:1798`
- `src/dev/esoc/esochan/lib/gallery/verticalviewpager/VerticalViewPager.java:2739`
- `src/dev/esoc/esochan/lib/gallery/verticalviewpager/VerticalViewPager.java:2787`

**Steps:**
1. Inspect each flagged call and its expected `@IntDef`/constant family.
2. Fix app-owned code directly.
3. For vendored `VerticalViewPager`, prefer minimal fixes or local `@SuppressLint("WrongConstant")` with comments if behavior is copied from upstream and verified.
4. Run:
   - `./gradlew lintDebug`

### Task 3.5: Replace blocking SharedPreferences `commit()` where safe

**Objective:** Reduce UI-thread stalls from synchronous preference writes.

**Files from lint examples:**
- `src/dev/esoc/esochan/api/AbstractChanModule.java:252`
- `src/dev/esoc/esochan/chans/anonfm/AnonFmModule.java:169`
- `src/dev/esoc/esochan/ui/settings/ApplicationSettings.java:161,255,263,271,279,287,373,398,447`
- `src/dev/esoc/esochan/api/CloudflareChanModule.java:82`
- `src/dev/esoc/esochan/ui/downloading/DownloadingService.java:733`
- `src/dev/esoc/esochan/lib/gallery/JSWebView.java:128`
- `src/dev/esoc/esochan/api/util/LazyPreferences.java:48,73,98`
- `src/dev/esoc/esochan/chans/makaba/MakabaModule.java:152`

**Steps:**
1. Replace `.commit()` with `.apply()` only where immediate persisted success is not required.
2. Keep `.commit()` where subsequent code depends on synchronous disk persistence; document why.
3. Run:
   - `./gradlew assembleDebug lintDebug`

---

## Tier 4 — Maintainability Refactors with Limited Blast Radius

**Difficulty:** Medium to High

**Scope:** App-owned architecture seams, no full rewrite

**Goal:** Reduce spaghetti in core app-owned classes without changing product behavior.

### Task 4.1: Introduce a broadcast helper

**Objective:** Centralize app-internal broadcast safety.

**Files:**
- Create: `src/dev/esoc/esochan/common/InternalBroadcasts.java`
- Modify call sites from Tier 2.1

**Proposed API:**
- `static Intent intent(Context context, String action)` returns `new Intent(action).setPackage(context.getPackageName())`
- `static void send(Context context, String action)`
- `static void send(Context context, Intent intent)` ensures package is set before sending

**Verification:**
- `./gradlew assembleDebug lintDebug`
- Confirm no new implicit internal broadcasts are introduced.

### Task 4.2: Extract BoardFragment database actions into a repository

**Objective:** Stop `BoardFragment` from directly owning persistence details.

**Files:**
- Create: `src/dev/esoc/esochan/ui/presentation/BoardRepository.java` or `src/dev/esoc/esochan/data/BoardRepository.java`
- Modify: `src/dev/esoc/esochan/ui/presentation/BoardFragment.java`
- Existing dependency: `src/dev/esoc/esochan/ui/Database.java`

**Initial methods to extract:**
- add history entry currently around `BoardFragment.java:458`
- update history/favorites currently around `BoardFragment.java:470`
- add hidden item currently around `BoardFragment.java:814`
- remove hidden item currently around `BoardFragment.java:896`

**Steps:**
1. Create repository that wraps `Database` and any required tab/page model mapping.
2. Replace direct calls in `BoardFragment` with repository methods.
3. Do not change database schema in this task.
4. Run:
   - `./gradlew assembleDebug`
5. Manual smoke test:
   - open board/thread
   - hide/unhide post
   - verify history/favorites behavior still works

### Task 4.3: Replace BoardFragment busy-wait adapter nulling

**Objective:** Remove `Thread.yield()` synchronization in `BoardFragment.nullAdapter()`.

**Files:**
- Modify: `src/dev/esoc/esochan/ui/presentation/BoardFragment.java:1384-1395`

**Current issue:**
- Background thread posts to UI, then spins with `while (nullAdapterFlag) Thread.yield();`.

**Safer approach:**
- Refactor caller flow so adapter replacement happens as a UI-thread callback.
- If a synchronous wait is unavoidable short-term, use `CountDownLatch` with timeout, but prefer removing blocking entirely.

**Verification:**
- `./gradlew assembleDebug`
- Test opening large threads and switching tabs while content loads.

### Task 4.4: Move page loading state fully into `BoardViewModel`

**Objective:** Continue the existing ViewModel migration instead of adding more state to `BoardFragment`.

**Files:**
- Modify: `src/dev/esoc/esochan/ui/presentation/BoardViewModel.kt`
- Modify: `src/dev/esoc/esochan/ui/presentation/BoardUiState.kt`
- Modify: `src/dev/esoc/esochan/ui/presentation/BoardFragment.java`

**Steps:**
1. Identify state still owned by `BoardFragment` that is really page-load state.
2. Move one small state cluster at a time into `BoardViewModel`.
3. Expose via existing LiveData/StateFlow pattern.
4. Do not migrate rendering or adapters in the same PR.
5. Run:
   - `./gradlew assembleDebug`

### Task 4.5: Log module registration failures

**Objective:** Stop silently swallowing channel module instantiation failures.

**Files:**
- Modify: `src/dev/esoc/esochan/common/MainApplication.java:164-169`

**Current issue:**
- `catch (Exception e) {}` hides broken channel modules.

**Steps:**
1. Add `Logger.e(...)` or equivalent in the catch block.
2. Include the failing class name.
3. Run:
   - `./gradlew assembleDebug`

---

## Tier 5 — Data and Storage Modernization

**Difficulty:** High

**Scope:** User data persistence and migrations

**Goal:** Make data upgrades safe and reduce raw SQL risk.

### Task 5.1: Stop destructive user DB upgrades

**Objective:** Prevent favorites/history/saved data loss on DB version changes.

**Files:**
- Modify: `src/dev/esoc/esochan/ui/Database.java:392-405`

**Current issue:**
- `onUpgrade()` drops all user tables and recreates them.
- `onDowngrade()` calls `onUpgrade()`.

**Steps:**
1. Before schema changes, document current schema and version in a comment or test fixture.
2. If no schema changes are needed now, do not bump `DB_VERSION`.
3. For future schema changes, add explicit migration blocks by old version.
4. Keep cache DB behavior separate; dropping cache is less risky than dropping user data.
5. Add manual backup/restore test instructions.

**Verification:**
- Install old app version, create history/favorites/saved threads, upgrade to new build, verify data remains.

### Task 5.2: Evaluate Room migration

**Objective:** Decide whether to migrate raw SQLite user DB to Room.

**Files:**
- Inspect: `src/dev/esoc/esochan/ui/Database.java`
- Possible new package: `src/dev/esoc/esochan/data/db/`
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle`

**Steps:**
1. Inventory tables: hidden, history, favorites, saved.
2. Define Room entities/DAOs matching existing schema exactly.
3. Create a migration plan from current DB file, not a fresh schema only.
4. Prototype behind repository from Task 4.2.
5. Do not switch all call sites until migration is tested on real existing DBs.

**Verification:**
- Android instrumentation migration test if feasible.
- Manual upgrade test preserving existing favorites/history.

### Task 5.3: Evaluate DataStore for settings

**Objective:** Decide whether replacing SharedPreferences is worth it.

**Files:**
- Inspect: `src/dev/esoc/esochan/ui/settings/ApplicationSettings.java`
- Inspect: `src/dev/esoc/esochan/common/SecurePreferences.kt`

**Recommendation:**
Do this only after lint/security and BoardFragment cleanup. SharedPreferences is not the biggest current risk.

---

## Tier 6 — Security-Sensitive Review

**Difficulty:** High

**Scope:** Requires behavioral testing and possibly product decisions

**Goal:** Ensure custom trust, WebView, and external file behavior are intentional and safe.

### Task 6.1: Review custom TLS trust behavior

**Objective:** Verify `ExtendedTrustManager` does not silently trust invalid certificates.

**Files:**
- `src/dev/esoc/esochan/http/client/ExtendedSSLSocketFactory.java`
- `src/dev/esoc/esochan/http/client/ExtendedTrustManager.java`
- `src/dev/esoc/esochan/http/client/ExtendedHttpClient.java`

**Questions to answer:**
- When a cert is invalid, does the user explicitly approve it?
- Is approval scoped to the host/cert?
- Can approval be revoked?
- Is hostname verification preserved?
- Are accepted certificates stored securely?

**Verification:**
- Test against a valid HTTPS site.
- Test against a self-signed local endpoint.
- Test against a hostname-mismatched cert.

### Task 6.2: Harden WebView settings

**Objective:** Keep required Cloudflare/gallery JS behavior while disabling unnecessary WebView capabilities.

**Files:**
- `src/dev/esoc/esochan/http/cloudflare/CloudflareChecker.java:138-139`
- `src/dev/esoc/esochan/lib/gallery/JSWebView.java:100-102`
- Search for `setJavaScriptEnabled`, `addJavascriptInterface`, `allowFileAccess`, `allowContentAccess`

**Steps:**
1. For each WebView use case, define required capabilities.
2. Disable file/content access unless required.
3. Set mixed content policy intentionally.
4. Avoid JavaScript bridges unless explicitly audited.
5. Run:
   - `./gradlew assembleDebug`
6. Manual test Cloudflare challenge flow and gallery HTML/JS behavior.

### Task 6.3: Replace private Android API reflection where feasible

**Objective:** Reduce breakage risk on new Android releases.

**Files:**
- `src/dev/esoc/esochan/lib/ClickableLinksTextView.java:144`
- `src/dev/esoc/esochan/lib/ClickableToast.java:134-146`
- `src/dev/esoc/esochan/lib/WebViewProxy.java:48,80`

**Steps:**
1. For each reflection usage, document the behavior it provides.
2. Check whether a public AndroidX/platform replacement exists.
3. Replace app-owned reflection first.
4. If keeping reflection, isolate it and add guarded failure behavior.
5. Run on newest available Android emulator/device.

---

## Tier 7 — Dependency and SDK Upgrades

**Difficulty:** Medium to High

**Scope:** One dependency family per PR

**Goal:** Keep dependencies current without combining upgrades with refactors.

### Task 7.1: Upgrade patch/minor dependencies individually

**Objective:** Upgrade low-risk libraries one at a time.

**Files:**
- Modify: `gradle/libs.versions.toml`

**Candidates from lint:**
- `kotlinx-coroutines`: 1.10.1 -> 1.10.2
- `androidx.annotation`: 1.9.1 -> 1.10.0
- `androidx.media3`: 1.9.2 -> 1.10.1
- `androidx.security:security-crypto`: 1.0.0 -> 1.1.0
- `material`: 1.12.0 -> 1.14.0

**Steps:**
1. Change one version.
2. Run:
   - `./gradlew assembleDebug lintDebug`
3. Smoke test relevant feature.
4. Commit before moving to next dependency.

### Task 7.2: Plan major upgrades separately

**Objective:** Avoid destabilizing the cleanup effort with major toolchain jumps.

**Major candidates:**
- AGP 8.7.3 -> 9.x
- Kotlin 2.1.10 -> 2.3.x
- OkHttp 4.12.0 -> 5.x
- AndroidX Core 1.16.0 -> 1.19.0 may imply compile SDK requirements

**Steps:**
1. Read release notes for each major upgrade.
2. Upgrade in a dedicated branch/PR.
3. Run full build/lint.
4. Test networking, Cloudflare, gallery playback, downloads, and posting.

---

## Tier 8 — Optional Larger Modernization

**Difficulty:** Very High

**Scope:** Multi-week, product-level decisions

**Goal:** Reduce long-term maintenance burden after correctness and safety work is complete.

### Candidate 8.1: Material 3 theming

**Files:**
- `res/values/styles.xml`
- `res/layout/*`
- UI activities/fragments using themes

**Notes:**
This is visible product work. Do after functional cleanup.

### Candidate 8.2: Replace large vendored widgets

**Files/areas:**
- `src/dev/esoc/esochan/lib/gallery/verticalviewpager/VerticalViewPager.java`
- `src/dev/esoc/esochan/lib/dslv/DragSortListView.java`
- `src/dev/esoc/esochan/lib/pullable_layout/*`

**Notes:**
Only do this if bugs or platform incompatibilities justify it. Otherwise isolate and lint-baseline vendored code.

### Candidate 8.3: Incremental Kotlin migration

**Files:**
- New files should be Kotlin.
- Migrate Java files only when already touching behavior.

**Do not do:**
- Full Kotlin rewrite.
- Full Compose rewrite.
- Full DI framework migration solely for style reasons.

---

## Suggested Execution Order

1. Tier 1.2 — BuildConfig deprecation
2. Tier 1.1 — remove Ant/Eclipse files
3. Tier 1.3 — `ClickableToast` lifecycle/receiver lint errors
4. Tier 2.1 — package-scope internal broadcasts
5. Tier 2.2 — restrict FileProvider paths
6. Tier 3.1-3.4 — fix remaining hard lint errors
7. Tier 0.1 — decide on lint baseline for remaining historical warnings
8. Tier 4.5 — log module registration failures
9. Tier 4.1-4.4 — BoardFragment/supporting architecture slices
10. Tier 5+ — data/storage/security/dependency modernization

---

## Definition of Done Per PR

Every implementation PR should include:

- Focused scope from one task or closely related subtasks
- `./gradlew assembleDebug` result
- `./gradlew lintDebug` result, or explanation if baseline/historical failures remain
- Manual smoke test notes if behavior changed
- `git diff` reviewed for accidental broad refactors
- No unrelated formatting churn
- No secrets or credentials
- No new public/broad file sharing paths
- No new unscoped implicit internal broadcasts

---

## Notes for Implementers

- Prefer small commits. Do not combine lint fixes, dependency upgrades, and architecture refactors.
- Treat `src/dev/esoc/esochan/lib/**` as possibly vendored. Avoid heavy refactors there unless necessary.
- Treat `src/dev/esoc/esochan/ui/**`, `common/**`, `http/**`, and `api/**` as app-owned unless proven otherwise.
- For BoardFragment, extract one seam at a time. Avoid a rewrite PR.
- Run `git status --short` before and after every task.
- If lint output is too noisy, fix the hard platform/security errors first, then create a baseline for remaining warnings.
