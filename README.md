# OpenTrack

OpenTrack is a local-first Android app for building flexible personal trackers without committing to a fixed health, habit, or fitness schema. It uses the calm **Signal** visual direction: compact cards, clear numbers, restrained color, and native charts.

> [!IMPORTANT]
> **OpenTrack is purely vibecoded.** The implementation was produced through iterative prompting and AI-generated code. Review the source and keep reliable backups before depending on it for important data.

## Features

- Timestamp occurrences with day or date-and-time precision
- Numeric values and units
- Enum/choice events with an optional typed value per choice
- Ordered radio/rating events
- Boolean, counter, and duration events
- Group events made from mixed fields
- Optional notes on every entry
- Starter templates for common trackers
- One-tap and focused quick logging
- Configurable dashboard cards, metrics, charts, sizes, visibility, and ordering
- Range filters, summaries, trends, distributions, calendars, and editable history
- Searchable global history with tracker and date filters
- Spreadsheet-safe CSV export and portable backup/restore

## Privacy and backup

OpenTrack has no account requirement and requests no network permission. Data is stored in the app-private Room database.

Android Auto Backup and device transfer are enabled for automatic restore when supported by the device. Manual backup and restore use Android's system document picker. Portable backups contain a checksummed, versioned logical snapshot plus one human-readable CSV per tracker; imports are bounded, validated, previewed, and replace the current data only after confirmation.

## Install

OpenTrack requires Android 8.0 (API 26) or newer. Tagged releases publish signed APKs to the repository's **Releases** page. Download the APK on the Android device and allow installation from the browser or file manager when prompted.

Because Android only accepts an update when it is signed with the same key as the installed app, install future releases from the same repository and signing lineage.

## Build and test

Requirements:

- JDK 17
- Android SDK 36
- Android Build Tools 36.0.0

Open the project in Android Studio once, or point `ANDROID_HOME`/`ANDROID_SDK_ROOT` at the SDK and create an untracked `local.properties` containing `sdk.dir=<absolute SDK path>`. Accept the installed SDK licenses before building.

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

On macOS/Linux:

```sh
chmod +x gradlew
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Connected tests require a running emulator or an attached Android device.

## Continuous integration and releases

The GitHub Actions workflow runs JVM tests, Android lint, a debug build, and instrumented tests for every branch push and pull request. Debug APKs and test reports are retained as workflow artifacts.

A tag in the form `vMAJOR.MINOR.PATCH` runs the same checks and then builds, verifies, and publishes a signed APK with its SHA-256 checksum to a GitHub Release. Before tagging a release:

1. Increment `versionCode` and set `versionName` to the tag version without the leading `v` in `app/build.gradle.kts`. The tag must point to the default branch, and `versionCode` must be greater than the value in the previous version tag.
2. Create a protected GitHub Actions environment named `release`. Restrict it to `v*` tags, add required reviewers where appropriate, and store these environment secrets:
   - `OPENTRACK_KEYSTORE_BASE64`: the complete JKS or PKCS12 keystore encoded as Base64
   - `OPENTRACK_KEYSTORE_PASSWORD`
   - `OPENTRACK_KEY_ALIAS`
   - `OPENTRACK_KEY_PASSWORD`
3. Add the environment variable `OPENTRACK_SIGNING_CERT_SHA256` with the expected signing certificate's SHA-256 fingerprint. The workflow compares it with the built APK to prevent accidental signing-key changes.
4. Protect the `v*` tag namespace with a repository ruleset. Enable immutable releases in the repository settings when available.
5. Create and push the matching tag, for example:

   ```sh
   git tag -a v1.0.0 -m "OpenTrack 1.0.0"
   git push origin v1.0.0
   ```

The release workflow deliberately fails if its version, signing, branch, or tag-integrity checks fail. It also refuses to replace an existing GitHub Release. Keep the keystore and its credentials backed up securely: losing them prevents upgrades over APKs signed with that key.

To encode a keystore in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\secure\opentrack-release.p12"))
```

Store the resulting single-line value in `OPENTRACK_KEYSTORE_BASE64`; never commit the keystore or its credentials.

To display the certificate fingerprint before storing the keystore, run `keytool -list -v -keystore <keystore> -alias <alias>` and copy its SHA-256 value.

## Local signed release with Podman

On Windows, the release build can also run in Podman. It uses a pinned Gradle/JDK image, installs the pinned Android command-line tools and SDK 36, runs unit tests, builds the signed APK, and verifies its signature:

```powershell
.\build-release.ps1
```

The first run builds the toolchain image and creates a reusable local release key under `%LOCALAPPDATA%\OpenTrack\signing`. Back up that directory. The finished APK is copied to `artifacts\OpenTrack-<version>-release.apk`; generated APKs and signing material are excluded from Git.

Useful options:

```powershell
.\build-release.ps1 -RebuildImage  # Recreate the Android builder image
.\build-release.ps1 -Incremental   # Keep existing Gradle build outputs
.\build-release.ps1 -SkipTests     # Build without running unit tests
```

To sign with an existing production or upload key, set all four variables before invoking the script. The keystore may live outside the repository and is mounted read-only:

```powershell
$env:OPENTRACK_KEYSTORE_FILE = "C:\secure\opentrack-release.p12"
$env:OPENTRACK_KEYSTORE_PASSWORD = "..."
$env:OPENTRACK_KEY_ALIAS = "opentrack"
$env:OPENTRACK_KEY_PASSWORD = "..."
.\build-release.ps1
```

Clear those variables to return to the managed local key.

## License

OpenTrack is free software licensed under the [GNU General Public License v3.0 only](LICENSE) (`GPL-3.0-only`).
