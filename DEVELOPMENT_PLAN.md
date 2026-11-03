# Android WebP Batch Converter: Development and Release Plan

## 1. Product scope and acceptance criteria

### Included

- Native Android, entirely on-device; no login, backend, upload, advertisements or network permission.
- Input folder picker using Storage Access Framework (SAF).
- Immediate-child scan filtering `.webp` extensions case-insensitively; name, size and selectable row per file.
- Single/multiple/all selection, clear selection and live selected count.
- Separate destination folder picker.
- PNG/JPG choice, PNG alpha preservation and automatic white compositing for JPG.
- A Convert action disabled until at least one file and a destination are selected.
- Background processing with notification, file-level progress and cancellation.
- Explicit full-success, partial-failure, cancellation and batch-failure states.
- Unique output filenames, unchanged conversion/copy originals, per-file error details and restart-readable batch reports.
- Convert and Manage files tabs sharing folders and selection; copy/move/delete any subset or all immediate-child WebP files.
- Confirmation before move/delete, byte-verified moves and explicit permanent-deletion warnings; no same-folder copy/move.

### Not included

Recursive scanning, animated conversion, resize/crop, metadata preservation in converted JPG/PNG, quality sliders, file previews, sharing, cloud sync and accounts. Copy/move preserve the original WebP bytes, including animation and metadata.

### Release acceptance criteria

1. A mixed folder lists only immediate-child WebP files, including `.WEBP`, and excludes directories even when their names end in `.webp`.
2. Individual toggles, multiple selection, Select all and clear-all produce the exact expected conversion set.
3. Output files have matching **encoded signatures**, MIME types and extensions, not just renamed inputs.
4. Output dimensions match the decoded originals. PNG retains alpha; fully transparent pixels become RGB white in JPG and partial alpha blends against white (allow JPEG tolerance).
5. Conversion never knowingly replaces an existing output or deletes an original; same-folder conversion works.
6. Progress remains responsive during a representative 100-file batch. No decoding/encoding or folder query runs on the main thread.
7. Unsupported animation, corrupt/truncated images, missing sources, revoked grants, provider failures and insufficient space produce visible errors.
8. A fully successful batch shows the exact successful count. Mixed results do not claim all files succeeded.
9. Backgrounding, rotation, notification denial and activity recreation do not lose an active batch; cancellation keeps completed outputs.
10. The optimized, **owner-signed** release installs, launches and passes acceptance on physical API 28 and current-target devices. APK signature and alignment verification pass.
11. Copy/move/delete affect exactly the selected WebP files; unselected files, other extensions and subfolders are untouched. Delete requires no output folder and cannot start without explicit confirmation.
12. Move deletes a source only after a SHA-256-verified copy and a persisted deletion checkpoint. Copy, verification and source-deletion failures are visible; verified copies are not removed when source deletion fails.

Passing a build alone does not satisfy these device and signing gates.

## 2. Hardware specifications

### Development machine

| Component | Recommended smooth configuration | Practical lower bound / notes |
|---|---|---|
| CPU | Modern Intel Core i7/Ultra 7 or AMD Ryzen 7, 8+ physical cores; Apple M-series Pro also suitable | Modern 6-core CPU; hardware virtualization mandatory for a fast emulator |
| RAM | **32 GB**, or **64 GB** for multiple AVDs and large builds | 16 GB with one light emulator or a physical device; not optimal |
| Storage | **1 TB NVMe SSD**, with 150-200 GB free for SDKs, AVDs and caches | 512 GB SSD with 80+ GB free; avoid HDDs/network drives for Gradle caches |
| GPU | Modern integrated or dedicated GPU with up-to-date drivers | Use hardware graphics acceleration; dedicated GPU is optional |
| Display | 1920x1080 minimum; 2560x1440 or dual displays preferred | UI density, split IDE and emulator benefit from extra space |
| OS | Supported 64-bit Windows 11; supported current macOS or 64-bit Linux alternatives | Windows: enable VT-x/AMD-V and Windows Hypervisor Platform per emulator documentation |

Use an x86_64 emulator image on Windows/Intel/AMD; use arm64 images on Apple Silicon. Allocate roughly 4 GB RAM and 2-4 vCPUs per development AVD, without starving the host. A physical phone avoids emulator virtualization overhead.

Check Android Studio's current [installation requirements](https://developer.android.com/studio/install) before buying hardware; the table above is a productivity recommendation, not a quoted official minimum.

### Physical testing matrix

| Device | OS and memory | Display/density | Architecture / purpose |
|---|---|---|---|
| Older low/mid-range phone | Android 9/API 28; 2-4 GB RAM | 720p, hdpi/xhdpi | arm64-v8a; minimum OS, memory pressure and decode-limit errors |
| Current mainstream phone | Android 13-15/API 33-35; 4-8 GB RAM | 1080p, xxhdpi | arm64-v8a; notification permissions, SAF and foreground-service changes |
| Latest target phone | Android 16/API 36; 8+ GB RAM | 1080p/1440p, xxhdpi/xxxhdpi | arm64-v8a; edge-to-edge, quotas and latest behavior |
| Tablet / resizable AVD | API 35/36 | 600dp+ width; mdpi/xhdpi | arm64 physical or x86_64 AVD; landscape, split-screen and large fonts |

Test at least two OEM document providers, internal storage and removable SD storage if available. Add a 32-bit armeabi-v7a physical device only if that install base matters. The app has no custom NDK/JNI code; Compose transitively packages a native graphics-path library. The universal APK contains arm64-v8a, armeabi-v7a, x86 and x86_64 versions, so separate ABI APKs are not needed.

## 3. Software and tooling

### Pinned implementation stack

| Area | Choice |
|---|---|
| Language | Kotlin **2.2.21**, coroutines/Flow |
| IDE | Current compatible stable Android Studio; official download page currently advertises Rabbit 2 / 2026.2.2 |
| Gradle runtime | **JDK 17**, Java/Kotlin bytecode target 17 |
| Build | Android Gradle Plugin **8.13.2**, Gradle wrapper **8.13**, Kotlin DSL |
| SDK | **minSdk 28**, **targetSdk 36**, **compileSdk 36**, AGP default Build Tools **35.0.0** |
| UI | Jetpack Compose, Material 3, Compose BOM **2025.10.01**, Kotlin Compose compiler plugin **2.2.21** |
| State/lifecycle | Lifecycle **2.9.4**, StateFlow, lifecycle-aware Compose collection, ViewModel |
| Activity | Activity Compose **1.11.0** |
| Background work | WorkManager **2.10.5**, CoroutineWorker with data-sync foreground service |
| Coroutines | kotlinx-coroutines-android **1.10.2** |
| File access | Framework DocumentsContract, SAF tree URIs and persisted grants |
| Image conversion | Framework ImageDecoder, mutable software Bitmap, Canvas and Bitmap.compress |
| Local persistence | WorkManager's internal work database, app-private AtomicFile JSON journals and folder preferences |
| Tests | JUnit **4.13.2**, AndroidX Test, Compose UI tests, Android lint |

These are deliberately pinned compatible versions, **not a claim that every dependency is the newest**. Upgrade them together in a separate tested change, never with `+` versions.

**Why API 28 minimum:** Android 9 introduces ImageDecoder, which provides a consistent platform decoder and orientation handling without a bundled native codec or two decoding paths. Supporting API 26/27 is possible with a separately tested BitmapFactory/EXIF implementation but is not part of this app.

**Why target API 36:** The [current Play policy](https://developer.android.com/google/play/requirements/target-sdk) requires new/updated ordinary phone apps to target Android 16/API 36 from August 31, 2026. Re-check policy at actual submission. A direct sideloaded APK does not require a Play listing; new Play apps ordinarily require an **AAB**, not just an APK.

See [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes) and [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping).

### Library decisions: include only what is needed

- **Compose versus XML:** Compose suits this single state-driven screen and accessible selection controls. XML/RecyclerView is viable for an existing View-based team but would add no functional benefit here. Do not build both.
- **Dependency injection:** Manual constructor injection for a small app. If later adding several features, Hilt is the conventional Android choice; Koin is an alternative. Neither is required now.
- **Database:** No Room layer is needed for a single active batch and local JSON reports. Add Room if introducing searchable conversion history, transactional queues or multiple concurrent jobs.
- **Networking:** No networking client or INTERNET permission. Add Retrofit/OkHttp only if a future real API requirement exists; not for local conversion.
- **Image loading libraries:** No Coil/Glide dependency because there are no previews. Do not decode full-size thumbnails just to list filenames.
- **Native codec:** No WebP JNI dependency; platform decoding avoids ABI packaging and third-party native codec maintenance.

## 4. Architecture and data flow

Use **MVVM with lightweight clean boundaries**, not a many-module enterprise framework.

```text
MainActivity (folder/notification permission launchers)
  -> ConverterScreen (renders immutable ConverterState, emits actions)
  -> ConverterViewModel (selection, validation, scan and work lifecycle)
     -> DocumentStore (SAF cursor queries)
     -> BatchStore (atomic private JSON request/result journal)
     -> WorkManager unique batch
        -> ConversionWorker (sequential orchestration and progress)
           -> WebpHeader (container/animation validation)
           -> ImageConverter (decode, alpha handling, encode)
```

### Folders

```text
app/src/main/java/com/example/webpconverter/
  MainActivity.kt
  ui/
    ConverterScreen.kt
    ConverterViewModel.kt
  data/
    Models.kt
    DocumentStore.kt
    BatchStore.kt
    FileOperationProcessor.kt
  conversion/
    WebpHeader.kt
    ImageConverter.kt
    ConversionWorker.kt
app/src/test/          # Naming/container/operation tests and Robolectric storage/UI integration
app/src/androidTest/   # Real codecs, pixel assertions, UI gating
```

### Reliability details

- Use `content://` URIs, never resolve SAF selections into guessed filesystem paths.
- Scan and conversion run on `Dispatchers.IO`; lazy rows avoid eager rendering of an entire folder.
- Store only the UUID in WorkManager input. Store file lists and results in private storage, avoiding WorkManager's 10 KB Data limit.
- Use one unique WorkManager batch with `KEEP`, and disable changing input/configuration during work.
- Persist batch results after each file; WorkManager status restores after navigation or activity recreation.
- Bound decoded pixels before allocation using the smaller of one-third of runtime max heap and 128 MiB, budgeted at 8 bytes/pixel for codec headroom. There is no automatic resizing; an explicit memory failure preserves user expectations.
- Validate the WebP RIFF container and animation flags/chunks before decoding.
- Decode to mutable software sRGB, draw white **behind** alpha pixels for JPG and mark output opaque. Do not merely clear alpha, which can create black backgrounds.
- Create a uniquely named destination document; journal it before writing. On file failure/cancellation, attempt deletion and surface cleanup failures.
- No atomic transaction exists between an arbitrary SAF provider and private JSON. Keep collision safety and expose the small create-before-journal crash window rather than promising impossible exactly-once filesystem semantics.
- Copy/move stream bytes with bounded buffers and verify SHA-256. Move rechecks source/copy bytes before deletion, including on restart. Do not edit either folder externally during a batch; SAF cannot atomically lock arbitrary provider files.
- Persist operation type, source folder, output URI, copy-verification and source-deletion checkpoints. Accept older conversion journals without the new fields. Retain verified copies if deletion fails or a move is cancelled.
- Use the picker's actual read/write grant flags so read-only sources remain usable for conversion/copy. Require input write permission for move/delete.
- A new delete reports missing sources as failures; resuming a previously checkpointed deletion is idempotent if its source is already absent. Cancellation does not roll back completed destructive changes.
- Android may stop, delay or quota work. Data-sync foreground-service limits and Android 16 job quotas are relevant for huge batches; use smaller batches rather than bypassing platform restrictions. Explicit cancellation/force-stop is not a promise of automatic resumption.

## 5. Security and pre-release hardening

### Permissions and user data

- No `MANAGE_EXTERNAL_STORAGE`, legacy read/write storage permissions or photo-library permissions.
- Persist only user-selected tree grants and use them solely for the selected operation.
- Request `POST_NOTIFICATIONS` on API 33+ when starting conversion; denial must not crash or prevent foreground service operation. With denial, Android may expose the service only in its active-apps/task-manager surface, not the normal notification drawer.
- Declare only the necessary foreground/data-sync service permissions and service type.
- WorkManager also contributes its standard wake-lock, boot-rescheduling and network-state permissions through manifest merging. Network-state observation is not the INTERNET permission; the app cannot initiate uploads.
- No network, analytics or image uploads. Images remain in the user-selected source/output provider.
- Disable backup of app-private folder grants and batch metadata. Do not log image contents or signing secrets.
- Treat provider metadata and file headers as untrusted. Handle unknown size, invalid names, huge dimensions, truncated input, grant revocation and write/delete failure explicitly.
- Metadata is not copied; disclose this as a functional property, not a guarantee of anonymization of visible image content.

### R8/ProGuard

- Release enables `isMinifyEnabled` and `isShrinkResources` with `proguard-android-optimize.txt`.
- Rely on AndroidX consumer rules for WorkManager's reflectively instantiated workers.
- Do not add blanket `-keep class ** { *; }`, `-dontwarn **` or strip useful error reporting.
- Test the **minified signed build**; debug-only success does not prove reflection-sensitive behavior works in release.
- Archive the release `mapping.txt` privately for symbolication, together with the version and build metadata.
- R8 obfuscation is defense-in-depth, not encryption or credential protection.

### Keystore and API keys

- Production signing keys are owned and backed up by the app owner. Never commit keystores, passwords, `local.properties` or machine paths.
- Inject signing values into the Gradle process through protected CI secrets or a local temporary environment. Disable command echoing and avoid `--debug` / public build scans in signing jobs.
- Use the **same signing identity for updates**. Losing a direct-distribution signing key generally prevents updating existing installs.
- For Play distribution, use Play App Signing with a separate upload key and follow its recovery procedures.
- This offline app has **no API keys to obfuscate**. If APIs are added, do not embed privileged secrets. Use server-side credentials, restricted client identifiers, quotas and authorization; BuildConfig, strings, base64 and native code are not secure secret stores.
- Protect CI/release access with MFA and least privilege; independently verify the signing certificate fingerprint before distribution.

## 6. Phased milestone timeline

Estimate: **10-15 working days** for one experienced Android developer, plus external store review if applicable. Large OEM/provider matrices or API 26 support add time. This is a delivery estimate, not a statement of elapsed work on this generated project.

| Phase | Duration | Work | Exit criteria |
|---|---:|---|---|
| 1. Requirements and fixtures | 0.5-1 day | Confirm nonrecursive scan, reject animation, unique naming; prepare transparent, opaque, animated, corrupt and large images | Workflow and acceptance cases agreed |
| 2. Environment and skeleton | 0.5-1 day | Install IDE/JDK/SDK, pin Gradle versions, initialize repository, basic Compose and CI build | Debug APK installs on API 28 and API 36 |
| 3. SAF and selection | 1-2 days | Folder grants, async filtering, names/sizes, row/multi/all selection, configuration gating | Mixed-folder and grant-revocation checks pass |
| 4. Conversion engine | 1-2 days | WebP validation, PNG/JPG encoder, white alpha composite, safe pixel budget and unique names | Signature, dimension, transparency and collision tests pass |
| 5. Background workflow | 1-2 days | Foreground WorkManager, progress notification, cancellation, private journals and per-file results | Background/rotation/process-recreation checks pass |
| 6. Verification and hardening | 3-4 days | Unit/UI/device tests, low RAM, no space, SD/OEM providers, TalkBack and large fonts; lint and R8 validation | Acceptance matrix completed; no release-blocking defects |
| 7. Release candidate | 1-2 days | Owner signing, optimized APK, signature/alignment checks, physical-device smoke tests and artifact archive | Verified owner-signed APK and release checklist approved |

## 7. Final phase: exact APK generation and signing

The examples use **PowerShell on Windows**. Run in the project root. Replace the sample JDK/SDK/key paths with the actual installation. Never paste passwords into source or commit terminal transcripts.

### A. Install/verify SDK and Java

Use Android Studio SDK Manager, or after accepting the SDK license:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
```

Let Android Studio generate `local.properties`, or create its ignored `sdk.dir` property with escaped backslashes. Gradle also recognizes `ANDROID_HOME`.

### B. Run verification before signing

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Verification failed' }

# Requires a booted emulator or attached/authorized physical device.
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
.\gradlew.bat connectedDebugAndroidTest --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Device tests failed' }
```

Do not treat compiled instrumentation tests as executed tests. Complete the manual acceptance matrix below on actual devices.

### C. Create the production keystore ONCE

The final application ID is `com.kensteve4ever.webpconverter`. The Kotlin `namespace` stays `com.example.webpconverter`, which only affects the source package and `R` class. A production keystore has already been created at `%USERPROFILE%\.android-keystores\webpconverter\webpconverter-release.p12` (alias `webpconverter-release`). Run the command below only if you need to create one on another machine. Never regenerate the key for an app that is already published.

```powershell
# Use a protected, backed-up folder OUTSIDE the project.
New-Item -ItemType Directory -Force "$env:USERPROFILE\AndroidSigning" | Out-Null
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore "$env:USERPROFILE\AndroidSigning\webp-release.p12" `
  -storetype PKCS12 -alias webp-release -keyalg RSA -keysize 3072 `
  -validity 10000
if ($LASTEXITCODE -ne 0) { throw 'Keystore generation failed' }
```

The command interactively prompts for a strong password and certificate identity. With PKCS12, use the same key/store password. Restrict filesystem ACLs and maintain at least two encrypted offline backups. Do not regenerate this key for subsequent updates.

### D. Inject signing configuration and build the optimized release

The Gradle release configuration reads four environment variables and **fails closed** if any are absent. It never silently signs with the debug key.

```powershell
$env:WEBP_KEYSTORE = "$env:USERPROFILE\.android-keystores\webpconverter\webpconverter-release.p12"
$env:WEBP_KEY_ALIAS = 'webpconverter-release'
$password = Read-Host 'Keystore password' -AsSecureString
$env:WEBP_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $password).Password
$env:WEBP_KEY_PASSWORD = $env:WEBP_STORE_PASSWORD

try {
    .\gradlew.bat --no-daemon clean testReleaseUnitTest lintRelease assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release verification/build failed' }
} finally {
    Remove-Item Env:\WEBP_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\WEBP_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable password -ErrorAction SilentlyContinue
}
```

`--no-daemon` limits retention in a reusable Gradle process; environment variables still exist as plaintext in the signing process's memory and must be treated as secrets. Avoid signing on an untrusted/shared host. The generated APK contains the public certificate, not the private key/password.

AGP handles D8/R8 compilation, resource shrinking, packaging, **alignment before signing**, and APK signing with Android-compatible schemes. No manual post-signing optimization is needed. Changing bytes or running zipalign on a signed APK invalidates its signature.

**Deliverable:** `app\build\outputs\apk\release\app-release.apk`

### E. Independently verify the APK

```powershell
$apk = '.\app\build\outputs\apk\release\app-release.apk'
$buildTools = "$env:ANDROID_HOME\build-tools\35.0.0"

& "$buildTools\apksigner.bat" verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }

& "$buildTools\zipalign.exe" -c -P 16 -v 4 $apk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed' }

& "$buildTools\aapt2.exe" dump badging $apk
if ($LASTEXITCODE -ne 0) { throw 'APK manifest inspection failed' }

Get-FileHash $apk -Algorithm SHA256
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Release installation failed' }
```

Inspect package ID/version, minSdk 28, targetSdk 36 and certificate SHA-256. A development APK with the same application ID has a different signing certificate, so it cannot be updated in place by the production build. Use a clean test device/profile or explicitly approve uninstalling the development app (which deletes its private state and grants).

Although this app has no custom native codec, Compose includes `libandroidx.graphics.path.so`. Its packaged ELF LOAD segments were inspected and have 16,384-byte alignment for all four ABIs. Still verify the release APK with `zipalign -P 16`, and test on a 16 KB page-size device/emulator before distribution. Re-check native dependencies whenever upgrading Compose.

### F. Manual Android Studio alternative

1. **Build > Generate Signed App Bundle or APK > APK**.
2. Select the app module and an existing owner-controlled keystore, or **Create new** once.
3. Supply the alias/passwords; select release and the default appropriate signature schemes offered by the IDE.
4. Because this project deliberately requires environment-based release credentials, launch Android Studio from a trusted process with the four `WEBP_*` variables already configured. Supplying only wizard fields does not bypass that guard.
5. Build and then perform the same independent verification/device checks. CLI signing is the recommended reproducible path.

### G. Archive and distribute

- Record versionCode/versionName, source revision, exact SDK/JDK/Gradle versions, certificate fingerprint and APK SHA-256.
- Privately archive `app\build\outputs\mapping\release\mapping.txt` and dependency/build reports.
- Distribute the verified signed APK via a trusted HTTPS channel with install instructions and hash.
- For Play, create the signed bundle using the same secret injection and `.\gradlew.bat bundleRelease`; complete Play App Signing, privacy/Data Safety and foreground-service declarations as applicable.
- Increment `versionCode` for each public update; retain the signing identity and test upgrading a previous release.

## 8. Device verification checklist

| Scenario | Expected result |
|---|---|
| Empty folder / mixed extensions / uppercase WebP / nested folder | Correct immediate-child list and explicit empty state |
| Single, multiple, all, clear-all selection | Exact selection count; only selected images processed |
| Picker cancellation | Existing state unchanged; no conversion starts |
| Unknown provider size | Shows Size unavailable, not fabricated zero |
| Transparent and partially transparent WebP to JPG | White/white-blended pixels, never unintended black |
| Same image to PNG | Alpha and full dimensions retained |
| Opaque, lossless and lossy WebP | Correct JPG/PNG file signatures and readable outputs |
| Animated WebP and corrupt/disguised input | Per-file failure; other files attempted |
| Existing same-name output / same source and destination | Existing bytes unchanged, new unique output |
| Destination read-only / storage full / grant revoked | Clear failure and partial-file cleanup attempt |
| Very large image / low memory | Explicit safe-decode/memory failure; no silent resize |
| 100 mixed files | Responsive UI; increasing finished count; exact final totals |
| Background, screen off, rotation, reopen activity | Worker continues subject to Android scheduling; status restored |
| Kill process during file write | Previously committed results preserved; interrupted tracked output handled on work rerun |
| Cancel from app and notification | Completed outputs kept; partial cleanup attempted; no false full success |
| Switch Convert/Manage files tabs | Input/output and selections shared; tabs/configuration locked during a batch |
| Copy/move subset and Select all, with existing WebP names | Exact WebP bytes preserved; unique names; unselected files untouched; same-folder operation rejected |
| Move copy/verification failure or read-only source | Source retained; verified copy retained on deletion failure; per-file error shown |
| Delete subset/all, cancel confirmation, no output selected | Confirmation required; cancel changes nothing; only selected WebP files removed; source list refreshes |
| Restart/cancel between move copy, verification and deletion | No source deleted without a verified copy; no completed deletion repeated; completed changes not undone |
| Upgrade from 1.1.0 with read-only input grant and old journal | Conversion/report compatibility preserved; move/delete request input reselection for write permission |
| Notification permission denied | Conversion still works; in-app progress remains available |
| Android time limit / job quota / force-stop | No claim of unlimited background execution; user can reopen/retry pending inputs |
| 200% font, TalkBack, landscape/tablet | Controls reachable, file rows toggleable, no clipped essential workflow |
| Optimized owner-signed release, fresh install and upgrade | Correct signature/version, worker construction and complete conversion flow |

## 9. Remaining production responsibilities

The repository supplies the application and release pipeline; **owner signing credentials, physical-device acceptance, public package identity, distribution policy review and approval remain release gates**. A build generated with a temporary validation key is not production-ready or suitable for future user updates.
