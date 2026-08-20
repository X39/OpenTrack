# OpenTrack

OpenTrack is a local-first Android app for building personal trackers without committing to a fixed health, habit, or fitness schema. It uses the calm **Signal** visual direction: compact cards, clear numbers, restrained color, and native charts.

## What it tracks

- Timestamp occurrences with day or date-and-time precision
- Numeric values and units
- Enum/choice events with an optional typed value per choice
- Ordered radio/rating events
- Boolean, counter, and duration events
- Group events made from mixed fields
- Optional notes on every entry

The dashboard supports one-tap logging where an entry is deterministic, focused quick-entry sheets where input is needed, and configurable widgets with alternate metrics and charts, sizes, visibility, and ordering. Tracker detail pages add range filtering, summaries, trends, distributions, calendars, and a complete editable history. Entries can be edited, deleted, exported as spreadsheet-safe CSV, or included in a portable logical backup ZIP.

## Privacy and backup

OpenTrack has no account requirement and requests no network permission. Data is stored in the app-private Room database. Android Auto Backup and device transfer are enabled for automatic restore, while manual backup/export and restore use Android's system document picker. Portable backups contain a checksummed, versioned logical snapshot plus one human-readable CSV per tracker; imports are bounded, validated, previewed, and only replace data after confirmation.

## Build

Requirements:

- JDK 17 or newer
- Android SDK 36 and Build Tools 36.0.0

On Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

On macOS/Linux:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The connected test requires a running emulator or attached Android device.

## Signed release APK with Podman

On Windows, the release build is self-contained in Podman. It uses a pinned Gradle/JDK image, installs the pinned Android command-line tools and SDK 36 in that image, runs unit tests, builds the signed APK, and verifies its signature:

```powershell
.\build-release.ps1
```

The first run builds the toolchain image and creates a reusable local release key under `%LOCALAPPDATA%\OpenTrack\signing`. Back up that directory: Android only permits an installed app to be upgraded by an APK signed with the same key. The finished APK is copied to `artifacts\OpenTrack-<version>-release.apk`; generated APKs and signing material are excluded from Git.

Useful options:

```powershell
.\build-release.ps1 -RebuildImage  # Recreate the Android builder image
.\build-release.ps1 -Incremental   # Keep existing Gradle build outputs
.\build-release.ps1 -SkipTests     # Build without running unit tests
```

To sign with an existing production or upload key, set all four variables before invoking the script. The keystore may live outside the repository and is mounted read-only:

```powershell
$env:OPENTRACK_KEYSTORE_FILE = "C:\secure\opentrack-release.jks"
$env:OPENTRACK_KEYSTORE_PASSWORD = "..."
$env:OPENTRACK_KEY_ALIAS = "opentrack"
$env:OPENTRACK_KEY_PASSWORD = "..."
.\build-release.ps1
```

Clear those variables to return to the managed local key. Never commit a keystore or its passwords.
