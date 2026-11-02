# WebP Converter for Android

A native, offline Kotlin/Jetpack Compose app for selecting a folder of WebP images and bulk converting selected files to JPG or PNG.

## Installation for users

See the [end-user installation guide](INSTALLATION_GUIDE.md) and [User Manual](USER_MANUAL.md). The versioned APK to share with users is [WebP-Converter-v1.0.0-Android.apk](WebP-Converter-v1.0.0-Android.apk); the standard Gradle release output remains `app\build\outputs\apk\release\app-release.apk`. No public download site has been configured. Complete the device tests described below before distribution.

## User workflow

1. Tap **Choose input folder** and grant access through Android's system folder picker.
2. The app scans **only that folder**, not subfolders, and lists `.webp` filenames (case-insensitive), sizes and checkboxes. Some document providers do not report a size; these show **Size unavailable**.
3. Select one image, several images, or **Select all**. The selected count updates immediately. Tap Select all again to clear the selection.
4. Choose a destination folder and select **PNG** or **JPG**.
5. Tap **Convert**. Progress shows the current file number and the number finished. Conversion continues in a foreground WorkManager worker when the screen closes.
6. On completion, read the success count and any individual errors. Full success is displayed only when every file succeeded. **Cancel conversion** stops the batch and keeps completed images.

The app never modifies originals or intentionally overwrites an existing destination file. Repeated names receive suffixes such as `photo (1).jpg`. Input and output may be the same folder.

### Deliberate behavior

- Animated WebP files are **rejected with a per-file error**, never silently flattened.
- PNG retains alpha. JPG composites transparent and partially transparent pixels onto **white**, then encodes at JPEG quality **95**.
- Full image dimensions are retained; images exceeding the device-dependent safe decoding budget fail explicitly instead of being silently downsampled.
- Output is rendered in sRGB. Source EXIF/XMP/GPS/ICC metadata is not copied; Android's decoder applies supported orientation information.
- Processing is sequential to limit memory use. Progress advances between files, not within a single native codec operation.
- Persisted folder access survives app restarts, unless the provider or user revokes it. File selections are in-memory and reset after process recreation; running batch requests and results are persisted privately.
- Unsupported, corrupt, inaccessible and unwritable files are reported without preventing other files from being attempted.
- Android 11+ restricts selection of certain roots, `Android/data`, `Android/obb` and the Downloads root. Choose a permitted subfolder such as `Pictures/WebPInput` or `Download/WebPOutput`.
- Local device folders are recommended. Third-party/cloud document providers can be slow, unavailable, read-only, still loading, or have different naming semantics. If still loading, the app requests a refresh rather than presenting an incomplete list as complete.

## Open and build

See [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) for hardware, architecture, phased delivery, device acceptance tests and production signing.

Requirements: JDK 17, Android SDK Platform 36, Build Tools 35.0.0, internet for the initial dependency restore, and Android 9/API 28+ to run.

1. Open this folder in a compatible stable Android Studio.
2. Set the Gradle JDK to JDK 17; install SDK Platform 36 and Build Tools 35.0.0 through SDK Manager and accept their licenses.
3. Let Android Studio create `local.properties` with your SDK path. The file is machine-local and ignored by Git.
4. In PowerShell, with `JAVA_HOME` pointing at JDK 17:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Or run the **Verify Android converter** VS Code build task / `.\scripts\verify.ps1`. This helper uses `JAVA_HOME`, or an optional machine-local `jdk.dir` in `local.properties`. Backslashes and the drive colon in property values must be escaped, for example `jdk.dir=C\:\\Java\\jdk-17`.

Outputs:

- Installable development APK: `app\build\outputs\apk\debug\app-debug.apk`
- Device-test APK: `app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk`
- Unit-test report: `app\build\reports\tests\testDebugUnitTest\index.html`
- Lint report: `app\build\reports\lint-results-debug.html`

On an attached device/emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Unit tests cover file filtering, collision-safe naming and WebP container/animation validation. Instrumentation tests exercise actual WebP-to-PNG/JPG encoding, pixel alpha/white compositing, file signatures, output dimensions, originals preservation and UI selection gating.

**A debug APK is not a production release.** Release builds deliberately fail if all four signing environment variables are not configured. Do not distribute any test-signed release as production or lose the production signing key.

### Verification performed in this workspace

- Debug app and instrumentation-test APKs compiled successfully.
- All **10 JVM tests passed in both debug and release variants**.
- Debug and release lint passed with **0 errors**; dependency-version/KTX recommendations remain as warnings.
- The R8/resource-shrunk release packaged successfully and passed APK v2 signature verification and 16 KB ZIP alignment verification.
- Packaged graphics-path ELF LOAD segments have 16 KB alignment for all four bundled ABIs.
- The release-signing guard was tested and rejects missing signing credentials.
- **Production release APK:** `app\build\outputs\apk\release\app-release.apk`
  - Application ID `com.kensteve4ever.webpconverter`, versionCode 1 / versionName 1.0.0, minSdk 28, targetSdk 36.
  - Signed with the owner production key (PKCS12, RSA 3072, alias `webpconverter-release`, `CN=WebP Converter, O=Independent Developer, C=IN`), APK Signature Scheme v2.
  - Certificate SHA-256: `a50fee2af2e6b9ff1bd7bd28193f1dffa7eee42a63101c77a9b99c97a2816210`.
  - The keystore lives outside the project in `%USERPROFILE%\.android-keystores\webpconverter\`. Move the password from `KEYSTORE-CREDENTIALS.txt` into a password manager, delete that file, and keep two encrypted offline backups of the `.p12`. If you lose it, you can't ship updates.
- No Android device is attached and hardware virtualization is unavailable on this host. Instrumentation pixel/UI tests were compiled, **not executed**. Before distributing the APK, run `connectedDebugAndroidTest` and the device checklist in DEVELOPMENT_PLAN.md §8 on a physical device.

## Project layout

```text
app/
  build.gradle.kts
  proguard-rules.pro
  src/
    main/
      AndroidManifest.xml
      java/com/example/webpconverter/
        MainActivity.kt
        ui/               # Compose screen, StateFlow ViewModel
        data/             # SAF queries, models, private atomic batch journal
        conversion/       # WebP validation, image codecs, foreground worker
      res/
    test/                 # JVM tests
    androidTest/          # Device pixel/codec and UI tests
gradle/wrapper/            # Pinned Gradle wrapper and distribution checksum
scripts/verify.ps1
```

## Operational limits

Android decides when queued work runs. A foreground worker is not an unlimited execution guarantee: Android 15+ applies data-sync foreground-service time limits, and Android 16 applies job quotas to WorkManager jobs. Force-stop prevents work until the app is opened again. Cancellation is cooperative around codec operations; a large encode may finish before the cancellation check.

Completed per-file results are atomically journaled. If Android stops and later reruns the same work, completed entries are skipped; a tracked unfinished output is removed before that entry is retried. There is no transaction spanning a document provider and private storage: process death between external document creation and journal persistence can leave an orphan/partial file. A provider refusing deletion is surfaced with the output URI for manual cleanup. Explicit cancellation is not automatically retried.

Batch requests/reports contain local filenames, URIs and errors, not duplicate image data. They are retained in app-private storage until app data is cleared/uninstalled. Clearing app data removes reports and grants, **not** converted images in user folders. Choose smaller batches if platform quotas stop a very long operation.
