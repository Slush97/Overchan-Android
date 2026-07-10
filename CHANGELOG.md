# Changelog

All notable changes to esochan are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2026-07-09

First public release of **esochan**, a 4chan client for Android forked from
[Overchan Android](https://github.com/miku-nyan/Overchan-Android).

### Added

- New application identity: package `dev.esoc.esochan`, modern adaptive launcher icons
- GitHub Actions CI (unit tests, lint, debug/release APK, App Bundle)
- Unit tests for post response parsing, cookies, HTTP streaming, tabs, and attachments
- Secure preferences (`EncryptedSharedPreferences`) for sensitive settings
- System file picker for attachments (replaces bundled picker)
- Material Components and ViewBinding
- Kotlin + coroutines foundation; `TabsViewModel` for tab state
- Settings hub layout and visual polish across board, gallery, drawer, and reply UI
- Directional slide animations when switching tabs
- GitHub Pages project site under `docs/`

### Changed

- Target SDK 35, min SDK 24, Java 17, Android Gradle Plugin 8.7 / Gradle 8.10
- Networking: Apache HttpClient → OkHttp; media: ExoPlayer → Media3
- HTML parsing: TagSoup → Jsoup; Kryo 5
- Scoped storage downloads via `MediaStore` on modern Android
- Foreground service types, notification channels, and runtime notification permission
- 4chan post response handling hardened (no false success / draft wipe on unknown HTML)
- Multi-imageboard modules reduced to a 4chan-focused client
- Attachment imports always copied into app cache before upload

### Removed

- Legacy multi-chan modules and HTML board readers not needed for 4chan
- Bundled custom file picker and unused per-board favicon assets
- Custom TLS trust chain; ACRA crash reporting to third-party endpoints

### Security

- Cookie handling and HTTP retries hardened
- Manifest export flags, PendingIntent mutability, backup rules reviewed
- ProGuard/R8 minify and resource shrink enabled for release builds

### Notes

- Sideload only (GitHub Releases). Not published on Google Play or F-Droid yet.
- Requires Android 7.0 (API 24) or newer.
- Captcha and Cloudflare flows depend on live site behavior; test posting on a real device.

[1.0.0]: https://github.com/Slush97/esochan-android/releases/tag/v1.0.0
