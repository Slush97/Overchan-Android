# Competitor Complaint Investigation Plan

> **Goal:** Map real user pain from Read Chan / KurobaEx / Chance / Clover reviews onto esochan code, then investigate and fix the gaps that win users.

**Sources:** Google Play reviews (Read Chan free+paid), AppBrain comments, /g/ client threads, KurobaEx/Chance GitHub, prior competitive research (2026-07-08).

**Scope of this doc:** Investigation plan + recommendations. No implementation yet. Each section ends with concrete code touchpoints and pass/fail checks.

---

## Executive summary

| Market complaint | esochan status | Priority | Notes |
|------------------|----------------|----------|-------|
| Captcha / posting broken after 4chan changes | **Partially strong** — custom slider + image captcha via WebView | P0 | Product-defining. Needs live regression harness. |
| Email verify for range bans | **Missing** | P0 | Board flags email off; post form only sends sage/empty. |
| Opaque post errors / mobile-data fails | **Partial** | P0 | HTML errmsg parse exists; empty responses not handled well. |
| Video stalls mid-load | **Risk** | P1 | Download-then-play via Media3; no progressive stream. |
| Batch download dies / bot-flag risk | **Better than Read Chan on queue, weak on throttle** | P1 | Sequential queue; no delay/backoff settings. |
| Manual hide posts / filters | **Present** | P2 | DB hide + autohide JSON exist; UX polish TBD. |
| Filename randomizer on upload | **Missing on 4chan** | P2 | `allowRandomHash=false`; filename never rewritten. |
| Keep deleted thread while reading | **Unknown** | P2 | Need refresh/404 path audit. |
| Fingerprint / soft-ban vs browser | **Aware** | P0 | WebView UA shared with OkHttp; captcha ticket ephemeral. |
| Multi-chan if 4chan dies | **Architected, currently 4chan-only** | P3 | `MODULES` only registers FourchanModule. |
| Ads | **N/A** | — | No ads; keep it that way. |
| Abandonment when captcha breaks | Process risk | P0 | Need monitoring + quick-fix path. |

---

## Competitive baseline (what we're up against)

| Client | Distro | Strength | Weakness users cite |
|--------|--------|----------|---------------------|
| **Read Chan** | Play 500K+, 4.6★ | Polish, gestures, maintained, no ads | Captcha lag, video, batch DL, no email verify |
| **KurobaEx** | GitHub | Filters, multi-site, power features | Brittle posting fingerprint, maintenance gaps |
| **Chance** | GitHub Flutter | Cross-platform, often "just works" | Captcha fails for some, settings UX |
| **Clover / NuClover** | GitHub | Classic UI, revived | Feature depth vs Kuroba; captcha maintenance |

**Where esochan can win:** captcha resilience + clear posting errors + email verify + solid media/download, while staying multi-board-capable and ad-free. Read Chan owns casual Play polish; power users want reliability over chrome.

---

## P0 — Posting reliability

### 1. Captcha system

**Market pain:** #1 complaint across every client. New captcha formats break posting while lurking still works. Dev threats to abandon apps land hard.

**Our code today:**
- `chans/fourchan/Chan4Captcha.kt` — InteractiveException; WebView fetch of `sys.4chan.org/captcha`; slider + image-selection UI
- `Chan4CaptchaData.kt` — parses JSON formats: Noop, Cooldown, Error, Slider, ImageTasks; stores `ticket`
- `Chan4CaptchaSolved.kt` — holds solved challenge/response between solve and `sendPost`
- `FourchanModule.getNewCaptcha` / `sendPost` — pass bypass; posts `t-challenge` / `t-response`
- WebView used deliberately to avoid Cloudflare TLS fingerprint mismatch

**Gaps / risks:**
- Ticket is process-global `@Volatile` only — lost on process death; not persisted
- Image-selection multi-step and cooldown UX may lag site changes
- JSON extract from HTML (`postMessage` / brace scan) is fragile if 4chan changes page shell
- No automated test or "captcha smoke" path after site changes
- Cloudflare challenge inside captcha WebView is interactive-only (user must complete)

**Recommendations:**
1. Persist captcha ticket (SecurePreferences) with TTL if 4chan documents/exhibits one
2. Log raw captcha JSON shape (redacted) on parse failure for quick diagnosis
3. Keep WebView path; do not "optimize" to pure OkHttp for captcha without fingerprint tests
4. Add a manual QA checklist: slider, multi-image, noop (pass), cooldown, CF interstitial
5. Track KurobaEx / Chance / 4chan-X captcha commits as early-warning feed

**Investigation tasks:**

| ID | Task | Files | Done when |
|----|------|-------|-----------|
| P0-1a | Document every captcha JSON shape we handle vs live API | `Chan4CaptchaData.kt`, live `sys.4chan.org/captcha` | Matrix of formats + unknown fields |
| P0-1b | Exercise captcha on WiFi + cellular, with/without pass | device | Notes on failure modes |
| P0-1c | Trace ticket lifecycle (set / clear / reuse) | `Chan4Captcha*.kt` | Diagram + gap list |
| P0-1d | Compare approach to Chance/KurobaEx captcha fetch | external | Diff of strengths |
| P0-1e | Decide ticket persistence + failure telemetry design | — | Short design note in this doc or issue |

---

### 2. Email verification (range-ban mitigation)

**Market pain:** Read Chan users: verify email missing/broken; only Pass works. Site-side email verify is the free path around range bans.

**Our code today:**
- `FourchanJsonMapper.getDefaultBoardModel`: `allowEmails = false`
- `FourchanModule.sendPost`: `addString("email", model.sage ? "sage" : "")` — **discards any real email**
- Post form hides email field when `allowEmails` is false (`PostFormActivity` / `PostFormFragment`)
- No UI or network flow for 4chan's "verify email" / verification cookie path
- Pass login exists (`PREF_KEY_PASS_*`, `pass_id` cookie)

**Gaps:**
- Cannot enter email for verification
- No handling of verification-required server responses
- No storage of verification cookies separate from pass

**Recommendations:**
1. Research current 4chan email-verify flow (web form fields, cookies, success markers) — **do not invent**
2. If still relevant: enable a controlled email path for 4chan (verify-only vs post field) separate from sage
3. Surface server messages that mention verification prominently
4. Prefer Pass for power users; email verify for casuals — both should work

**Investigation tasks:**

| ID | Task | Files | Done when |
|----|------|-------|-----------|
| P0-2a | Capture web email-verify flow (HAR / browser) | — | Field list + cookies + URLs |
| P0-2b | Search codebase for any leftover verify logic | whole tree | None or revive path |
| P0-2c | Map server error strings for "verify" / rangeban | `FourchanModule` error parse | String table |
| P0-2d | Spec minimal feature (settings + cookie + post gate) | — | Spec paragraph |

---

### 3. Post error UX + mobile network

**Market pain:** Empty `Submission failed with message:`; works on WiFi not cellular; soft-bans that browsers recover from faster than apps.

**Our code today:**
- Success: HTML comment `<!-- thread:N,no:M -->`
- Errors: regex `<span id="errmsg">…`
- On miss of both matchers: `sendPost` returns `null` (treated as success with no redirect in `PostingService`)
- `PostingService` shows `e.getMessage()` or generic string
- Headers: Origin + Referer + Accept via `getPostHeaders`
- UA: WebView default shared with OkHttp (`HttpConstants.getUserAgentString`) — good for CF

**Gaps / risks:**
- Unrecognized HTML → silent `null` success (draft cleared!)
- No distinction: ban / captcha expired / cloudflare / network / empty body
- Cellular CGNAT / carrier IP reputation not fixable in app, but **messages must be clear**
- Only one attachment field (`upfile`); correct for 4chan

**Recommendations:**
1. Treat non-matching response as hard error; dump body snippet to log + user-facing "unexpected response"
2. Classify common 4chan errmsg strings (banned, captcha, flood, duplicate, file too large…)
3. Retry policy only for clear network failures — never auto-retry ban/captcha
4. Optional "copy error details" for bug reports

**Investigation tasks:**

| ID | Task | Files | Done when |
|----|------|-------|-----------|
| P0-3a | Inventory all post response shapes (success, errmsg, blank, CF HTML) | `FourchanModule.sendPost` | Table |
| P0-3b | Reproduce WiFi vs cellular post | device | Confirmed same/different |
| P0-3c | Fix null-as-success if confirmed | `FourchanModule`, `PostingService` | Failing case shows error |
| P0-3d | Audit cookie jar domains for pass/cf/4chan | `ExtendedHttpClient`, `FourchanModule` | Cookie matrix |

---

### 4. Fingerprinting / soft-ban parity with browser

**Market pain:** KurobaEx users stay banned while browser recovers; anti-bot harder on native clients.

**Our mitigations already present:**
- Captcha via WebView
- Shared UA OkHttp ↔ WebView
- Cloudflare module + cookie persistence (`CloudflareChanModule`)
- Pass support

**Still investigate:**
- Cookie sync between captcha WebView and OkHttp jar (are `cf_clearance` / 4chan cookies shared both ways?)
- Missing headers vs real browser (Sec-Fetch-*, Accept-Language, etc.)
- TLS stack differences (OkHttp vs Chromium) after captcha solved in WebView
- Whether posting should use WebView form submit as nuclear fallback ("post via web")

**Investigation tasks:**

| ID | Task | Files | Done when |
|----|------|-------|-----------|
| P0-4a | Trace cookie flow WebView captcha → OkHttp post | CF + captcha + client | Flow diagram |
| P0-4b | Diff request headers vs Chrome mobile post | `getPostHeaders` | Gap list |
| P0-4c | Evaluate WebView post fallback (Chance/Kuroba patterns) | — | Go/no-go |

---

## P1 — Media

### 5. Video playback

**Market pain:** Videos stall ~66%; gallery + many videos worst case.

**Our code today:**
- Gallery downloads full file first (`AttachmentGetter` → `remote.getAttachment`) then `ExoPlayer` on local `File`
- Media3 ExoPlayer (`GalleryActivity.setVideo`)
- Pref: internal player vs external; "do not download videos"
- History of GIF/WebM complexity in codebase; video path is download-then-play

**Gaps / risks:**
- Full download before play = long wait + memory; not the same as Read Chan stream-stall, but poor UX
- Concurrent gallery page loads can thrash cache/network
- ExoPlayer errors only logged (`Logger.e`); weak user recovery
- No explicit "retry video" control after failure
- TextureView transform path is complex — regression risk

**Recommendations:**
1. Prefer progressive/streaming `MediaItem` from HTTPS URL when possible (with CF cookies / auth if needed)
2. Else: show download progress clearly before play affordance
3. Cancel sibling downloads when swiping away
4. On ExoPlayer error: toast + retry + open external
5. Stress-test threads with many WebMs (same scenario as Read Chan reviews)

**Investigation tasks:**

| ID | Task | Files | Done when |
|----|------|-------|-----------|
| P1-5a | Confirm always full-download vs any stream path | `GalleryActivity`, `GalleryRemote`/`Backend` | One-line answer |
| P1-5b | Profile multi-WebM thread gallery | device | Stall/OOM notes |
| P1-5c | Design stream-or-cache strategy | — | Decision: stream / hybrid / keep |
| P1-5d | Map ExoPlayer error → user action | `GalleryActivity` | UX sketch |

---

### 6. Batch download + rate limiting

**Market pain:** Read Chan batch stops ~6 items; no bandwidth control; bot-flag fear.

**Our code today:**
- `BoardFragment.downloadAllImages()` queues each attachment into `DownloadingService`
- Service: **single sequential worker** over `LinkedBlockingQueue` (not parallel stampede)
- Skip if exists / already in queue
- `DownloadStorage` for MediaStore/legacy
- Thread archive modes: cache / thumbs / all (`MODE_DOWNLOAD_*`)
- No inter-item delay, no concurrency knob, no per-host rate limit

**Gaps:**
- Sequential is safer than parallel, but still no backoff on 429/403
- Failures go to error report; unclear if one failure aborts rest (audit loop)
- No user setting for delay between files
- Large thread = long foreground service; battery/OS kill risk

**Recommendations:**
1. Keep sequential default; add optional delay (e.g. 200–1000 ms) setting
2. On 429/403: exponential backoff + pause queue with notification action "resume"
3. Robust partial failure: continue queue, summarize errors (already partially there)
4. Optional max concurrent = 1 (fixed) documented as intentional anti-ban

**Investigation tasks:**

| ID | Task | Files | Done when |
|----|------|-------|-----------|
| P1-6a | Trace failure handling mid-queue | `DownloadingService.run` | Continue vs stop |
| P1-6b | Test download-all on 50+ media thread | device | Success rate |
| P1-6c | Check CDN responses for rate limits | logs | Codes observed |
| P1-6d | Spec throttle setting + backoff | prefs | Spec |

---

## P2 — Reading UX features users explicitly want

### 7. Hide posts / filters

**Market pain:** Want manual hide of individual troll posts, not only keywords.

**Our code today:**
- `Database` table `hiddenitems`; `addHidden` / `isHidden`
- Context menus: hide post / hide thread (`BoardFragment`)
- Autohide rules JSON (`ApplicationSettings` + `AutohideActivity`)

**Likely OK** — investigate UX discoverability, not existence.

| ID | Task | Done when |
|----|------|-----------|
| P2-7a | Manual hide post/thread end-to-end | Works across refresh |
| P2-7b | Autohide rules match expected (OP-only etc.) | Bugs filed |
| P2-7c | Unhide path exists and is findable | Documented or UX fix |

---

### 8. Filename randomizer on upload

**Market pain:** Default screenshot names leak; want random name on upload.

**Our code today:**
- `SendPostModel.randomHash` + `ExtendedMultipartBuilder.addFile(..., uniqueHash)` appends random **bytes to file content** (hash change), **does not rename**
- 4chan board: `allowRandomHash = false` — never applied
- Multipart uses original `file.getName()` always

**Gaps:**
- No filename rewrite for 4chan
- Content-tail random hash disabled for 4chan (and may break some file types if enabled blindly)

**Recommendations:**
1. Add optional "randomize upload filename" (keep extension) in post form
2. Separate from content-hash trick
3. Enable for 4chan regardless of `allowRandomHash` semantics (rename ≠ content mutate)

| ID | Task | Done when |
|----|------|-----------|
| P2-8a | Confirm 4chan accepts arbitrary original filenames | Live post |
| P2-8b | Spec rename-only option | Spec |
| P2-8c | Wire UI + `addFile` filename override | Implementation later |

---

### 9. Auto-refresh wiping deleted / 404 threads

**Market pain:** Background refresh nukes thread mid-read when it 404s.

**Our code today:**
- Thread updates via tab tracker / board reload; merge via `ChanModels.mergePostsLists` when old list present
- Need dedicated audit of 404 path in `BoardFragment` / page loader / tracker

| ID | Task | Done when |
|----|------|-----------|
| P2-9a | Map behavior on thread 404 during open tab | State diagram |
| P2-9b | Map behavior when post deleted mid-view | Same |
| P2-9c | If wipe: design "keep last snapshot + banner" | Spec |

---

### 10. Settings complexity

**Market pain:** Chance users: settings hard to organize. Power clients bury essentials.

**Recommendations:**
- Prefer progressive disclosure: Posting / Captcha / Downloads / Appearance top-level
- Don't copy Kuroba's kitchen-sink defaults

| ID | Task | Done when |
|----|------|-----------|
| P2-10a | Inventory prefs related to P0–P1 | List |
| P2-10b | Propose regroup only if implementing new prefs | Sketch |

---

## P3 — Positioning / long-term

### 11. Multi-chan architecture vs 4chan-only product

**Market pain:** Users ask for other chans when 4chan is down.

**Our code:**
- Full multi-chan module system (`ChanModule`, abstracts)
- `MainApplication.MODULES` currently **only** `FourchanModule`
- ROADMAP mentions trim 27→13; tree now 4chan-only under `chans/`

**Recommendation:** Keep architecture; re-add chans only when posting/captcha for 4chan is solid. Multi-chan is differentiator vs Read Chan **later**.

| ID | Task | Done when |
|----|------|-----------|
| P3-11a | Confirm intentional 4chan-only product scope | Yes/no in ROADMAP |
| P3-11b | List which modules worth restoring post-P0 | Ordered list |

---

### 12. Distribution & maintenance trust

**Market pain:** Fear of abandonment; Play users want updates; power users want GitHub.

**Recommendations:**
- Public captcha status note in release notes when 4chan changes
- CI already building APKs (ROADMAP) — keep it green
- F-Droid optional later; not a complaint blocker
- Never add ads (Read Chan free already ad-free; Omnichan burned users)

---

## What we already do better than Read Chan (validate, don't break)

1. **Multi-board architecture** (even if only 4chan registered)
2. **Sequential download queue** (less bot-flaggy than naive parallel)
3. **Manual hide + autohide** already in DB/UI
4. **Pass support** with SecurePreferences
5. **Modern captcha implementation** (slider/image, not dead reCAPTCHA-noscript only)
6. **Media3** already on Media3 ExoPlayer (not abandoned ExoPlayer 2)
7. **Shared WebView UA** for CF cookie coherence
8. **No ads / no telemetry product stance** (ACRA disabled per ROADMAP)

---

## Suggested investigation order (calendar)

```text
Week 1 — P0 truth
  P0-1a..e  Captcha live matrix
  P0-3a..d  Post response / null-success
  P0-4a..b  Cookie + header parity
  P0-2a..c  Email verify research

Week 2 — P0 decisions + P1 facts
  P0-2d     Email verify spec or "won't do / Pass only"
  P0-4c     WebView post fallback go/no-go
  P1-5a..b  Video path profiling
  P1-6a..c  Download-all stress

Week 3 — Spec freezes for implementation PR train
  Freeze P0 fix list (must-ship for "can post reliably")
  Freeze P1 media/download improvements
  Park P2 cosmetics behind P0
```

**Implementation should not start until Week 1 tasks produce answers.** The wrong captcha "cleanup" can brick posting.

---

## Implementation PR train (after investigation)

Ordered for dependency safety:

1. **Post response correctness** — never treat unknown HTML as success; better errors  
2. **Captcha hardening** — ticket persistence, parse resilience, logging  
3. **Email verify or explicit Pass-only messaging** — based on P0-2 research  
4. **Header/cookie parity fixes** if P0-4 finds gaps  
5. **Video UX** — progress / stream / error recovery  
6. **Download throttle + backoff**  
7. **Filename randomize**  
8. **404 thread keep-snapshot**  
9. **UX polish** hide/settings

Each PR: `./gradlew assembleDebug`, manual post+captcha on device, no Play push until captcha QA checklist green.

---

## Device QA checklist (reuse forever)

When 4chan changes anything, run:

- [ ] Load catalog + thread on WiFi
- [ ] Load catalog + thread on cellular
- [ ] Solve slider captcha and post text
- [ ] Solve image-selection captcha and post
- [ ] Post with image attachment
- [ ] Post with sage
- [ ] Post with Pass (if available)
- [ ] Cloudflare challenge recovery (if presented)
- [ ] Open gallery WebM, scrub, swipe to next
- [ ] Download all on 20+ image thread; no silent stops
- [ ] Hide post; refresh; still hidden
- [ ] Airplane mode post → clear error, draft retained

---

## Out of scope for this plan

- Play Store listing strategy
- Compose rewrite
- Matching Read Chan gesture chrome 1:1
- Captcha auto-solvers / paid captcha APIs (legal/ToS risk; KurobaEx has this — we skip unless product decision)

---

## Code index (quick map)

| Area | Primary paths |
|------|----------------|
| Captcha | `src/dev/esoc/esochan/chans/fourchan/Chan4Captcha*.kt` |
| 4chan module | `.../chans/fourchan/FourchanModule.java`, `FourchanJsonMapper.java` |
| Posting | `ui/posting/PostingService.java`, `PostFormActivity.java`, `PostFormFragment.kt` |
| Multipart / filename | `http/ExtendedMultipartBuilder.java` |
| HTTP / UA / cookies | `http/HttpConstants.java`, `http/client/ExtendedHttpClient.java` |
| Cloudflare | `http/cloudflare/*`, `api/CloudflareChanModule.java` |
| Gallery / video | `ui/gallery/GalleryActivity.java` |
| Downloads | `ui/downloading/DownloadingService.java`, `BoardFragment.downloadAllImages` |
| Hide | `ui/Database.java`, `BoardFragment` context menus |
| Autohide | `ui/settings/AutohideActivity.java` |
| Modules | `common/MainApplication.java` `MODULES` |

---

## Success criteria for the investigation phase

Investigation is done when we can answer yes/no with evidence:

1. Can we post reliably on current captcha on WiFi and cellular?
2. What exactly happens on unknown post HTML today (draft loss?)?
3. Is email verify still a real 4chan flow we can implement?
4. Do cookies/headers cause avoidable soft-bans vs Chrome?
5. Is video full-download or streamable without CF breakage?
6. Does download-all survive 50+ files without rate death?
7. Which of the above are bugs vs missing features vs won't-fix?

After that, convert freezes into implementation tickets — not before.
