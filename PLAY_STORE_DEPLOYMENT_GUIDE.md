# WebP Converter: Google Play deployment and update guide

**Prepared:** 3 November 2026  
**Audience:** App owner, release manager and Android developer  
**Scope:** First Play Store launch, subsequent updates, signing-key custody and moving development to another PC.

> This is a publishing runbook, not evidence that the app has been uploaded or approved. No Play Console account, public privacy-policy URL or store listing has been configured by this document. Physical-device acceptance testing is still required. Google changes its requirements and Console menus; recheck the official sources in section 13 before each release.

## 1. Understand APKs, bundles and this project's identity

For a **new Google Play app, upload a signed Android App Bundle (`.aab`)**, not the standalone APK currently supplied to users. Google Play generates optimized APKs from the bundle. An AAB cannot be installed by tapping it on a phone.

| Item | Current project value / location |
|---|---|
| App name | WebP Converter |
| Permanent application ID / package | `com.apps.multiformat.webpconverter` |
| Kotlin namespace | `com.example.webpconverter` (different from application ID; this is intentional) |
| Current version | `versionCode = 6`, `versionName = "1.4.0"` |
| Version configuration | [app/build.gradle.kts](app/build.gradle.kts) |
| Minimum Android version | Android 9, API 28 |
| Compile / target SDK | API 36 / API 36 |
| JDK / Gradle / Android Gradle Plugin | JDK 17 / Gradle wrapper 8.13 / AGP 8.13.2 |
| Kotlin / Compose BOM | 2.2.21 / 2025.10.01 |
| SDK installation requirements | Android SDK Platform 36 and Build Tools 35.0.0 |
| Play upload artifact after building | `app\build\outputs\bundle\release\app-release.aab` |
| Standalone APK after building | `app\build\outputs\apk\release\app-release.apk` |
| Latest named standalone APK | `WebP-Converter-v1.4.0-Android.apk` |
| R8 mapping to archive for every release | `app\build\outputs\mapping\release\mapping.txt` |
| Owner production (option A app-signing) certificate SHA-256 | `a50fee2af2e6b9ff1bd7bd28193f1dffa7eee42a63101c77a9b99c97a2816210` |

The ID was changed from `com.kensteve4ever.webpconverter` before the first Play release (see section 2.2). From the first Play upload onward, **never change it again**: Play does not allow it, and it would create a different app. Renaming an APK file, changing the display name or changing the PC does **not** change app identity. A different application ID creates a separate app, not an update.

As checked on the date above, Google's phone/tablet submission policy requires API 36 or higher for new apps and updates from 31 August 2026. This project targets 36; verify the live requirement before submission. `minSdk` is a separate compatibility decision, not the Play target-API requirement.

## 2. Signing keys: make this decision before your first Play release

### 2.1 App-signing key versus upload key

| Key | Purpose | Where it should live |
|---|---|---|
| **App-signing key** | Signs APKs installed on users' devices; establishes update identity | Google protects its copy under **Play App Signing**. Keep your existing original in encrypted owner-controlled backups for standalone APKs and disaster recovery. |
| **Upload key** | Signs the AAB that your team sends to Play; Play verifies the uploader, then signs generated APKs with the app-signing key | Separate protected local keystore or CI secret, with encrypted backups |

The keys may initially be the same, but separate keys reduce exposure of the long-term app-signing key. This project's Gradle variables select one keystore per invocation: use the **upload key for Play bundles** and the **existing app-signing key for standalone release APKs**.

### 2.2 Choose the app-signing key for the new package

The application ID was changed to `com.apps.multiformat.webpconverter` **before** the first Play release. Android treats it as a brand-new app:

- Earlier standalone APKs (1.0.0–1.3.0 built as `com.kensteve4ever.webpconverter`) **cannot be updated** by this package. Both apps can be installed side by side. Ask anyone who has the old app to install the new one, then uninstall the old one. Folder choices and settings do not carry over; they simply choose their folders again.
- Old APK files are kept in `archive\com.kensteve4ever.webpconverter\` for reference only. Do not distribute them anymore.
- There is no installed base to preserve, so you may choose either signing option below. Decide **once**; the app-signing key cannot be casually changed after the first release.

| Option | When to choose it | How |
|---|---|---|
| **A. Reuse the existing owner key (recommended here)** | You also want to give out standalone APKs that can update, or be updated by, the Play version. | In **App integrity / App signing**, choose to **export and upload a key from Java keystore**. Download the PEPK tool and follow the **exact command shown in your own Console**, using the keystore and alias in section 2.3. Upload the encrypted output only through Console. Confirm the Console's app-signing certificate SHA-256 equals `a50fee2a…2816210`. |
| **B. Let Google generate the app-signing key** | Play will be your only distribution channel. | Accept the default Google-generated key. Afterwards, standalone APKs you sign yourself **cannot** update Play installs (and vice versa). |

With either option, prefer a **separate upload key** (section 2.4). Register its public certificate when Console asks, and wait until Console accepts it before uploading.

Test the Play-delivered build through an **internal testing track** before production. Do not use **Internal App Sharing** to check certificates: its builds are re-signed with a test certificate.
### 2.3 Existing owner keystore inventory

These are locations and public identity details, **not passwords**:

```text
Current PC:
C:\Users\labuser\.android-keystores\webpconverter\webpconverter-release.p12

Portable convention:
%USERPROFILE%\.android-keystores\webpconverter\webpconverter-release.p12

Type: PKCS12
Alias: webpconverter-release
Key algorithm: RSA 3072
Certificate subject: CN=WebP Converter, O=Independent Developer, C=IN
```

The prior setup also created `KEYSTORE-CREDENTIALS.txt` alongside that keystore. Move the credentials into a reputable encrypted password manager, verify they work and are backed up, then securely dispose of that plaintext copy using your organization's procedure. Never paste passwords into this guide, source code, support messages or build logs.

To inspect the existing certificate, let `keytool` prompt for the password:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.android-keystores\webpconverter\webpconverter-release.p12" `
  -alias webpconverter-release
```

Compare its SHA-256 fingerprint with section 1 (colons and letter case do not matter). Record the certificate's actual validity dates and backup verification date in your private release inventory.

### 2.4 Optional: generate a separate upload key

Do this **once**, only if you have not already registered an upload key. It is not a replacement for the existing app-signing key.

```powershell
$folder = "$env:USERPROFILE\.android-keystores\webpconverter"
New-Item -ItemType Directory -Force $folder | Out-Null
$uploadStore = Join-Path $folder 'webpconverter-upload.p12'
if (Test-Path $uploadStore) { throw 'Upload keystore already exists. Do not replace it.' }
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore $uploadStore -storetype PKCS12 -alias webpconverter-upload `
  -keyalg RSA -keysize 3072 -validity 10000
if ($LASTEXITCODE -ne 0) { throw 'Upload-key creation failed.' }
& "$env:JAVA_HOME\bin\keytool.exe" -exportcert -rfc `
  -keystore $uploadStore -alias webpconverter-upload `
  -file (Join-Path $folder 'webpconverter-upload-certificate.pem')
if ($LASTEXITCODE -ne 0) { throw 'Upload-certificate export failed.' }
```

Use the interactive prompts; do not put passwords in command arguments. Store them in the password manager. The `.pem` above contains a **public certificate**, not a private key; it can be supplied for upload-key registration. The `.p12` contains the private key and must stay protected.

## 3. Where to store keys and how to survive losing a PC

**The development PC must never be the only copy of a signing key.** Keep app-signing and upload keys under the app owner's control, not solely in a developer's personal account.

Recommended arrangement:

| Copy / record | Recommended storage |
|---|---|
| Working upload keystore | Outside the project in `%USERPROFILE%\.android-keystores\webpconverter\`; owner-only filesystem permissions; BitLocker or equivalent full-disk encryption |
| Original app-signing keystore | Encrypted owner-controlled vault; put on a release PC only when needed for standalone signing |
| Backup A | Encrypted removable drive, disconnected after backup, physically secured |
| Backup B | Encrypted off-site backup in a separate location or controlled storage account with MFA |
| Passwords and recovery material | Encrypted password manager with emergency access/recovery; avoid keeping an unencrypted password beside the keystore |
| Release inventory | Package, key roles, aliases, certificate fingerprints, validity dates, backup locations, owner and last restore-test date; do not include passwords in a public inventory |
| Console account recovery | MFA/passkey recovery methods, backup codes and documented owner/admin access |

Follow a **3-2-1** backup strategy: three copies, two storage media/types, one off-site. Encryption of PKCS12 alone does not replace strong passwords, filesystem protection or encrypted backup storage.

For each backup:

1. Copy the complete `.p12` file using an encrypted channel or encrypted removable drive.
2. Compare `Get-FileHash -Algorithm SHA256 <path>` on original and copy. A keystore-file hash is different from the public certificate fingerprint.
3. On a trusted machine, open the restored copy with `keytool -list -v`; verify alias and certificate fingerprint.
4. Test-sign a non-production artifact and verify it, without uploading or replacing a release.
5. Record a successful restore test privately. Repeat after any key change and periodically.

Never store private keys/passwords in Git, the project ZIP, an APK download folder, public cloud links or documentation. This project's current `.gitignore` covers `.jks` and `.keystore`, but **does not cover `.p12`**. Keep keys outside the workspace; before introducing Git or any in-project signing material, add exclusions such as `*.p12`, `*.pfx`, `KEYSTORE-CREDENTIALS.txt` and private secret files. Ignore rules do not protect files already tracked, and Git ignore is not a backup/security boundary.

## 4. Prepare your developer account and listing

1. Enroll at [Google Play Console](https://play.google.com/console/). Choose personal or organization ownership accurately, pay the displayed registration fee and complete identity/contact/payment verification. Organization enrollment may require D-U-N-S information; personal accounts may require device verification.
2. Enable strong MFA/passkeys. Use Console user roles for developers/release staff rather than sharing the owner's login. Maintain an independent recovery route.
3. Choose **Create app**, default language, app name, **App**, and free/paid status. Review the pricing decision carefully: a free app generally cannot later become a paid download under the same listing.
4. Complete the app dashboard's required setup tasks. Menu labels may differ as Console evolves.
5. Prepare a truthful listing:
   - App name (up to 30 characters), short description (80), full description (4,000).
   - 512 x 512 PNG store icon; adaptive launcher XML is not a store-icon upload.
   - 1,024 x 500 feature graphic and real phone screenshots meeting Console's current asset rules.
   - Category such as **Tools**, support email, and any required website/contact information.
   - Explain WebP conversion, copy/move/delete of WebP or other selected files, JPG white-background handling and permanent deletion accurately. Do not claim animated WebP conversion support.
6. Publish an accessible privacy-policy page and enter its URL. Explain local processing, file access (including non-image files selected for management), permanent deletion, report/settings retention, lack of account/advertising features if unchanged, and support contact. Link the policy from within the app as required by policy **before submitting**; a hosted URL alone does not implement that in-app link.

Use screenshots of the actual release on devices. The documentation's Robolectric renders are useful previews, but do not replace device testing or guarantee store asset compliance.

## 5. Complete policy declarations for this app

| Console requirement | Project-specific guidance |
|---|---|
| Data safety | Inspect the **merged release manifest**, dependency behavior and final binary. The intended app is offline with on-device processing and no analytics/ads; if confirmed, disclose no off-device collection/sharing. Local file access is not automatically data collection under Play's definition. Do not assume answers remain valid after adding SDKs. |
| Privacy policy | Required even for an app that does not collect data. Provide the public URL and required in-app access. |
| Ads | Select no ads only while the shipped app truly has none. |
| App access | Explain that no account/login is required, if unchanged. |
| Target audience and content rating | Answer honestly; do not select children simply to maximize reach. Complete the rating questionnaire. |
| Foreground service | The manifest declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and WorkManager's `dataSync` service. Complete the FGS declaration and provide a demonstration-video link. Describe user-initiated local image processing and file management; show folder selection, starting work, visible progress/notification and Cancel. Review whether each feature fits the current policy; approval is not automatic. |
| File/photo access | The app uses the Storage Access Framework folder picker, not broad storage/media permissions. Do not request `MANAGE_EXTERNAL_STORAGE` or broad photo access merely to publish. Verify what dependencies add to the final manifest. |
| Notifications | `POST_NOTIFICATIONS` is requested on Android 13+; denial must not prevent the core workflow. Test that case. |
| Other declarations | Complete every additional dashboard item that applies, including regional trader/contact or other legal requirements. |

Android limits foreground-service duration and WorkManager execution; do not advertise unlimited background work. Recheck Android 15/16 behavior on real devices.

## 6. Build a signed release bundle on Windows

### 6.1 Prepare the toolchain

- Install a compatible Android Studio, JDK 17, SDK Platform 36 and Build Tools 35.0.0; accept SDK licenses.
- Use the checked-in Gradle wrapper; do not depend on a globally installed Gradle version.
- Set `JAVA_HOME` to the actual JDK installation, not its `bin` folder.
- Let Android Studio generate `local.properties`. It is machine-specific and must not be shared as portable configuration.

Run commands from the project root. For example:

```powershell
Set-Location 'C:\Study\Android\webpToPngConverter'
$env:JAVA_HOME = 'C:\Java\jdk-17' # Replace with the real JDK 17 location.
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat --version
```

### 6.2 Decide the release version

In [app/build.gradle.kts](app/build.gradle.kts), use a `versionCode` **higher than all codes already uploaded across Play tracks**, and higher than installed standalone versions when those users should upgrade.

The source is currently code **6**, name **1.4.0**. Before uploading, confirm that code 6 is higher than every code already uploaded under this application ID. Each later release needs a higher unused code, for example **7** / **1.4.1**.

`versionName` is user-facing text; `versionCode` controls update ordering. Never reuse an uploaded code for a different build, even after abandoning a release.

### 6.3 Set signing variables and build

The project already reads:

| Variable | Meaning |
|---|---|
| `WEBP_KEYSTORE` | Absolute path to the selected keystore |
| `WEBP_KEY_ALIAS` | Alias inside it |
| `WEBP_STORE_PASSWORD` | Keystore password |
| `WEBP_KEY_PASSWORD` | Private-key password (usually the same for this PKCS12 setup) |

Use the registered **upload key** below. If you have intentionally registered the existing production key as both roles, substitute its path and alias instead. Never switch keys just because you moved machines.

The following keeps passwords out of command history; Gradle still receives them in process environment variables, so use a trusted PC and avoid environment dumps, shell transcripts and public CI logs:

```powershell
function Read-ReleaseSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

$env:WEBP_KEYSTORE = "$env:USERPROFILE\.android-keystores\webpconverter\webpconverter-upload.p12"
$env:WEBP_KEY_ALIAS = 'webpconverter-upload'
try {
    $env:WEBP_STORE_PASSWORD = Read-ReleaseSecret 'Upload keystore password'
    $env:WEBP_KEY_PASSWORD = Read-ReleaseSecret 'Upload private-key password'
    .\gradlew.bat --no-daemon clean testReleaseUnitTest lintRelease bundleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release verification/build failed. Do not upload.' }
} finally {
    Remove-Item Env:\WEBP_STORE_PASSWORD, Env:\WEBP_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\WEBP_KEYSTORE, Env:\WEBP_KEY_ALIAS -ErrorAction SilentlyContinue
}
```

`--no-daemon` avoids retaining the release environment in a reusable Gradle daemon. Clearing variables is good hygiene, not a guarantee of zero recoverable copies in memory on a compromised computer.

For direct APK distribution, use the same flow with the **original app-signing key** and `assembleRelease` instead of `bundleRelease`. Do not distribute standalone APKs signed with a separate upload key: they will not update existing installations.

### 6.4 Verify and archive

```powershell
$aab = '.\app\build\outputs\bundle\release\app-release.aab'
if (-not (Test-Path $aab)) { throw 'Release AAB was not produced.' }
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify $aab
if ($LASTEXITCODE -ne 0) { throw 'Bundle signature verification failed.' }
& "$env:JAVA_HOME\bin\keytool.exe" -printcert -jarfile $aab
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect bundle certificate.' }
Get-FileHash $aab -Algorithm SHA256
```

Confirm the AAB signer matches the registered **upload certificate**. Self-signed certificate/trust-chain warnings are normal for Android developer keys; they do not justify ignoring an invalid signature. Use `jarsigner` for AAB signatures, not `apksigner` or `zipalign`.

Optionally validate bundle structure using Google's official [bundletool](https://github.com/google/bundletool/releases), after downloading a verified release:

```powershell
# Replace with the actual downloaded bundletool filename.
& "$env:JAVA_HOME\bin\java.exe" -jar 'C:\Tools\bundletool-all.jar' validate --bundle=$aab
if ($LASTEXITCODE -ne 0) { throw 'Bundle validation failed.' }
```

Check 16 KB page-size support in Play's bundle analysis and on a 16 KB device/emulator, including all native libraries pulled in by dependencies. ZIP alignment alone does not prove native ELF compatibility. See Google's live page-size guidance for enforcement dates.

Archive each approved release outside the disposable `build` directory: AAB, checksum, exact source revision/source snapshot, dependency/toolchain versions, R8 `mapping.txt`, release notes, public signing/upload certificate fingerprints, test results and Console release/track details. Keep private keys/passwords out of this release archive. Preserve any native debug symbols if applicable.

## 7. Test through Play before production

1. In Console, create an **internal testing** release, enroll in Play App Signing as described in section 2, and upload the signed AAB.
2. Check bundle processing, package/version, supported devices, signing fingerprints, native compatibility, warnings and policy blockers.
3. Add testers and distribute the opt-in link; testers need to accept with an eligible Google account.
4. Install the **Play-delivered** build. Test at least:
   - Android 9 and current Android versions; different screens/storage providers.
   - Input/destination selection, permission persistence/revocation, denied notifications.
   - PNG transparency, JPG white background, file-name collisions and corrupt/large inputs.
   - Single/multi/select-all selection, both tabs, progress, background work and cancellation.
   - WebP-only and other-file filters, selection clearing on category/tab changes, copy verification, move failures and permanent delete **using disposable files only**.
   - Reset on both tabs, cleared messages after restart and disabled Reset during active work.
   - If you chose option A, install a standalone APK of the new package, then update it from Play **without uninstalling**. Confirm the old `com.kensteve4ever` app is unaffected and can be uninstalled separately.
5. Review Play pre-launch reports, crashes/ANRs, accessibility and device compatibility. Resolve blockers.
6. If your **personal developer account was created after 13 November 2023**, the currently documented production-access requirement is a **closed test with at least 12 testers continuously opted in for at least 14 days**. Internal testing alone does not satisfy this. Collect genuine usage/feedback, then apply for production access and answer the testing/readiness questions. Access is reviewed, not automatically granted after the timer.

Different account types and future rules may differ; follow your dashboard's actual requirements.

## 8. Publish the first production release

1. Finish all listing, policy, verification, pricing/distribution and production-access tasks.
2. Create the production release or promote the already-tested release artifact. You do not need a new code merely to promote the same AAB between tracks.
3. Add accurate release notes, select countries/regions and review all warnings.
4. Recheck app-signing certificate continuity and ensure you uploaded an AAB, not the root-folder APK.
5. Send the required changes for review. Review duration varies; do not promise a fixed release date.
6. If using **Managed publishing**, approval and publication are separate: explicitly publish approved changes when ready. Check the Publishing overview rather than assuming an upload is live.
7. For a first production launch, use the availability controls offered by Console; percentage staged rollouts are generally for **updates**, not the first production release.
8. After publication, install from the public listing with an eligible non-tester account. Monitor Android vitals, reviews, crash/ANR reports and storage-operation complaints.

Expected listing URL, once published and available to that user:

```text
https://play.google.com/store/apps/details?id=com.apps.multiformat.webpconverter
```

## 9. Repeatable process for pushing updates

| Step | Action / release gate |
|---|---|
| 1. Prepare | Start from the last released source; implement the change and review data/report compatibility. Never require users to uninstall to receive an ordinary update. |
| 2. Version | Increase `versionCode` beyond every uploaded code; update `versionName` and docs/release notes. |
| 3. Recheck policy | Check target API, page sizes, SDKs, merged permissions and Data safety/FGS declarations. Update listing/privacy policy for changed behavior. |
| 4. Build | Use the registered upload keystore and section 6's test/lint/bundle flow. |
| 5. Verify | Check AAB signer, version, checksum and bundle validation; archive mapping and source. |
| 6. Internal/closed test | Test Play delivery and upgrading the currently published build without clearing app data. |
| 7. Promote | Promote the tested artifact to production; supply notes and submit review. |
| 8. Roll out | For updates, start with a small staged rollout if available, observe vitals/feedback, then increase toward 100%. Users may not all receive it immediately. |
| 9. Record | Archive release status, uploaded code, fingerprints, checksum, mapping, notes and monitoring outcome. |

If a production update is bad, **halt the staged rollout** promptly. This does not remove it from devices that already updated. To ship a fix or restore previous behavior, build from the good source with a **new, higher versionCode**, retest and publish it. Do not try to downgrade by re-uploading a lower code.

Uploading an APK to a website does not push an update to Play users. Play updates are released through Console; standalone APK recipients need the new app-signing-key-signed APK through your distribution channel.

## 10. Move development or release work to a new PC

1. **Before retiring the old PC:** verify both encrypted key backups, password-manager access, Console recovery and release archives. Make a private migration checklist. Do not wipe the only working copy.
2. Transfer the source, Gradle wrapper and required project assets through a trusted channel or private repository. Do not treat `.gradle`, `app\build`, `local.properties`, old SDK folders or an old user's home directory as portable requirements.
3. Install Android Studio/JDK 17 and the required SDK components afresh. Set `JAVA_HOME` and Gradle JDK; accept licenses.
4. Open the project and let Studio regenerate `local.properties` with the **new** SDK path. If manually writing it, escape Windows backslashes and drive colon, for example:

   ```properties
   sdk.dir=C\:\\Users\\NEWUSER\\AppData\\Local\\Android\\Sdk
   ```

   Remove or update any old machine-specific `jdk.dir`. Do not reuse the previous session's temporary SDK path.
5. Restore the **upload** keystore from encrypted backup into the new user's protected `.android-keystores\webpconverter` directory. Restore the original app-signing keystore only if that PC needs standalone APK signing.
6. Apply owner-only access permissions and full-disk encryption. Retrieve passwords through the password manager, not a plaintext transfer.
7. Use `keytool -list -v` to confirm aliases/fingerprints against the private inventory and Console. Merely matching filenames is not sufficient.
8. Recreate `WEBP_*` variables using the **new absolute paths**; build a signed test bundle using section 6. Verify it and run upgrade tests through the internal track before any production change.
9. Sign in to Console with your own assigned account and MFA. A new machine needs neither a new package nor a new app-signing/upload key.
10. After a successful restore/build/test and independently verified backups, remove release credentials from the retired PC using the approved secure-retirement process; revoke old sessions/tokens.

**Keep:** signing identities, application ID, source history, released version-code history and owner access.  
**Recreate:** local paths, IDE settings, Gradle caches, SDK installation and transient signing environment variables.

## 11. Lost keys, passwords or account access

| Incident | Correct response |
|---|---|
| PC lost; backups available | Restore on a trusted PC, verify certificates and rebuild. Investigate/revoke credentials exposed by the loss. No routine key change is needed if the keys remained protected. |
| Upload key lost or compromised; Play App Signing enabled | Authorized owner requests an **upload-key reset** through Console, generates/reuses an approved replacement and registers its public certificate per Google's instructions. Wait for activation before uploading. Installed APK identity remains the app-signing key. |
| Original local app-signing key lost, but Google already holds it | Play updates can continue using Play App Signing and a valid/reset upload key. Google does not return the private app-signing key for local use. Independently signing standalone updates with the original identity is no longer possible without your own backup. |
| Original app-signing key lost before enrollment, with no usable backup | Existing standalone installations cannot normally receive updates signed with a fresh unrelated key. Recover the real key/password first; a new listing/package is a separate migration, not a key recovery. |
| App-signing key compromised | Stop releases, secure accounts and contact Play support. Review Google's supported app-signing-key upgrade process and compatibility impact. Upload-key reset alone does not rotate the app-signing key. |
| Password lost | Recover from the password manager/emergency records. A keystore file with no usable password may be unusable; do not overwrite it or assume a reset can decrypt it. |
| Console owner locked out | Use Google account recovery, documented backup admins and Play support. Possession of a keystore alone does not grant Console ownership. |

## 12. Optional CI/CD release automation

Manual Console deployment is sufficient initially. If you later automate:

- Store the **upload key only** in the CI platform's protected secret store, not in Git. Base64 is an encoding, not encryption.
- Supply passwords as masked/protected secrets; materialize the key into a job-local protected path and remove it after the job.
- Restrict releases to protected branches/tags, trusted runners and manual approval. Do not expose secrets to pull requests from forks.
- Use the Google Play Developer API with a minimally privileged service account and supported authentication. Prefer short-lived credentials where supported; otherwise protect/rotate the service-account credential as a separate secret.
- Start automation with internal-track uploads. Do not grant production permissions until the workflow has been reviewed and tested.
- Archive the same non-secret release evidence as manual builds. Never print environment variables, private keys or passwords to diagnose a failed job.

## 13. Official references and final go/no-go checklist

Requirements were checked against these official sources on 3 November 2026. Console-specific tasks and newer published policy take precedence.

- [Create and set up an app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
- [Sign your app / Play App Signing / upload-key reset](https://developer.android.com/studio/publish/app-signing)
- [Upload your app bundle](https://developer.android.com/studio/publish/upload-bundle)
- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Data safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Foreground-service declaration requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
- [16 KB page-size support](https://developer.android.com/guide/practices/page-sizes)
- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en)

Before pressing **Send for review / Publish**:

- [ ] Owner account verified, MFA enabled and recovery tested.
- [ ] Existing app-signing key backed up in two encrypted independent locations; password recovery tested.
- [ ] Application ID is `com.apps.multiformat.webpconverter`; Play App Signing option (A or B) chosen deliberately; registered upload certificate confirmed.
- [ ] Application ID is still `com.apps.multiformat.webpconverter`; versionCode unused and high enough; correct AAB selected.
- [ ] Tests/lint pass and physical-device/upgrade acceptance completed, including destructive operations.
- [ ] Current API/page-size requirements and Play bundle warnings addressed.
- [ ] Public privacy policy and required in-app access implemented.
- [ ] Listing, Data safety, FGS video/declaration, rating and other Console tasks completed truthfully.
- [ ] Required closed test and production-access approval completed, if applicable.
- [ ] AAB, checksum, R8 mapping, source revision and release notes archived without private credentials.
- [ ] Rollout, monitoring and emergency hotfix responsibilities assigned.
