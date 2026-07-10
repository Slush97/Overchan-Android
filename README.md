# esochan

esochan is a 4chan client for Android, forked from
[Overchan Android](https://github.com/miku-nyan/Overchan-Android).

**Package ID:** `dev.esoc.esochan`  
**Min Android:** 7.0 (API 24)  
**License:** [GPLv3](LICENSE.txt)

## Install (users)

1. Open the [latest GitHub Release](https://github.com/Slush97/esochan-android/releases/latest).
2. Download `esochan-1.0.0.apk` (or the versioned APK attached to that release).
3. On your phone, allow install from your browser/file manager if prompted.
4. Open the APK and install.

Updates: install a newer release APK over the previous one. Keep using builds signed with
the same release key so Android allows the upgrade.

Project site: [GitHub Pages](https://slush97.github.io/esochan-android/) (if enabled).

### Verify your download

Each release attaches a `SHA256SUMS` file and a `signing-certificate.txt`.

1. **Checksum** — confirm the APK wasn't altered in transit:

   ```sh
   sha256sum -c SHA256SUMS
   ```

2. **Signer** — confirm the APK was signed by esochan's release key. Print the
   signing certificate and compare its SHA-256 to the fingerprint below (and to
   the one in the release's `signing-certificate.txt`):

   ```sh
   # needs Android SDK build-tools
   apksigner verify --print-certs esochan-1.0.0.apk
   # or, with only a JDK:
   keytool -printcert -jarfile esochan-1.0.0.apk
   ```

   Release signing certificate SHA-256:

   ```
   <maintainer: paste your release cert SHA-256 here once, e.g. from
    `apksigner verify --print-certs` — this is a public fingerprint, not a secret>
   ```

   Every genuine esochan release must show this exact fingerprint. A different
   fingerprint means the APK was signed by a different key — do not install it.

## Building (developers)

### Dependencies

* JDK 17 (recommended; AGP may fail on newer JDKs)
* [Android SDK](https://developer.android.com/studio)
* [Android NDK](https://developer.android.com/ndk)

### Debug build

```sh
./gradlew assembleDebug
```

APK: `build/outputs/apk/debug/esochan-debug.apk`

### Signed release build

1. Create a release keystore (once) and copy `keystore.properties.example` → `keystore.properties`.
2. Fill in absolute `storeFile` path, passwords, and alias (see the example file).
3. Build:

```sh
./gradlew assembleRelease
```

Signed APK: `build/outputs/apk/release/esochan-release.apk`  
App Bundle: `build/outputs/bundle/release/esochan-release.aab`

`keystore.properties` and `*.jks` are gitignored. **Back up the keystore and passwords**;
losing them prevents publishing updates that install over existing installs.

### Verify

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
```

### Using Android Studio

Import the project and build from the IDE. Use JDK 17 for the Gradle JVM.

## Versioning

- `versionName` / `versionCode` live in `build.gradle` (`defaultConfig`) and are mirrored in
  `AndroidManifest.xml`.
- Git tags use `vMAJOR.MINOR.PATCH` (e.g. `v1.0.0`).
- See [CHANGELOG.md](CHANGELOG.md).

## Releasing

1. Bump `versionName` / `versionCode` in `build.gradle` and the manifest.
2. Update `CHANGELOG.md`.
3. Commit on `master`, push, and tag:

```sh
git tag -a v1.0.0 -m "esochan 1.0.0"
git push origin v1.0.0
```

4. The **Release** workflow builds and publishes a GitHub Release with APK assets when
   signing secrets are configured; otherwise attach a locally signed APK with
   `gh release create`.

### Optional GitHub Actions signing secrets

For automated signed releases, add repository secrets:

| Secret | Description |
|--------|-------------|
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 esochan-release.jks` |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias (e.g. `esochan`) |
| `SIGNING_KEY_PASSWORD` | Key password (same as store for PKCS12) |

## License

esochan is licensed under the [GPLv3](http://www.gnu.org/licenses/gpl-3.0.txt).

Originally developed by [miku-nyan](https://github.com/miku-nyan); esochan is a fork of
[Overchan Android](https://github.com/miku-nyan/Overchan-Android) (also GPLv3).

Bundled third-party components and their licenses (GIFLIB, android-gif-drawable,
DragSortListView, and others) are documented in
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
