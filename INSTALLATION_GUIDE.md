# WebP Converter: Installation Guide

WebP Converter changes selected `.webp` images into JPG or PNG files on your
Android phone or tablet. The **Manage files** tab also copies, moves or deletes
some or all listed WebP files or non-WebP files. Conversion and copy keep originals; move and
delete remove selected source files after confirmation. Deletion has no undo.

> **Before you rely on this version:** This APK has not yet been tested on an
> Android device. Keep backup copies of your images and ask the developer for a
> tested build before using it for important files.

## What you need

- A phone or tablet running **Android 9 or newer**. To check, open **Settings**,
  then look under **About phone** or **About tablet** for **Android version**.
- The `WebP-Converter-v1.4.0-Android.apk` file supplied by the developer.
- Enough free storage for the app and the converted images. The APK is about
  2.2 MB; large batches need more free space.
- An internet connection only if you need to download the APK. Image conversion
  itself is offline.

The steps and menu names can differ slightly between phone manufacturers. You do
not need Android Studio, a computer, or a signing password to install the APK
directly on your phone.

## 1. Get the APK safely

1. Ask the developer for `WebP-Converter-v1.4.0-Android.apk` and obtain it through a source you
   trust. This guide does not provide a public download website.
2. Download it on your phone, or copy it from a computer:
   - Connect your unlocked phone to the computer using a USB cable.
   - If prompted on the phone, choose **File transfer**.
   - Copy `WebP-Converter-v1.4.0-Android.apk` to the phone's **Downloads** folder.
3. Do not install the file if its source is unexpected or you suspect it has
   been altered. Contact the person who supplied it.

### Optional: check the file on a Windows computer

For the APK supplied with version **1.4.0**, the SHA-256 checksum is:

```text
50FC677316D436C51BCC817F84B8E67647D3CFAC4D2F93D080441B8F1F869D1A
```

In PowerShell, run the following if the file is in your Windows Downloads
folder. Otherwise, replace the path with the file's actual location.

```powershell
Get-FileHash -Algorithm SHA256 "$env:USERPROFILE\Downloads\WebP-Converter-v1.4.0-Android.apk"
```

Compare the displayed hash with the value above. If it differs, do not install
the file; ask the developer for a fresh copy. A match confirms that the file
matches this checksum, not that it is safe by itself. Check the expected
checksum through a separate trusted channel. A later APK build may have a
different checksum.

## 2. Install the app

1. On your phone, open **Files**, **My Files**, or your phone's file manager.
2. Open **Downloads** and tap **WebP-Converter-v1.4.0-Android.apk**.
3. If Android says that the file manager is not allowed to install apps:
   - Tap **Settings** in the message.
   - Turn on **Allow from this source** for that file manager.
   - Go back to the installer. If necessary, tap the APK again.
4. Tap **Install**, or **Update** if a compatible 1.3.1 copy is installed.
   Version 1.4.0 is installed as a new app alongside versions 1.0.0–1.3.0;
   see **Moving from an older version** below. When installation finishes, tap **Open**, or find
   **WebP Converter** in your app list. Its icon is a blue-to-teal tile showing
   a picture with an orange arrow.
5. For safety, return to that app's **Install unknown apps** setting and turn
   **Allow from this source** off again. You can search for **Install unknown
   apps** in your phone's Settings if you cannot find it.

If you open the APK directly from a browser or messaging app rather than a file
manager, Android may ask you to allow that particular app instead. Allow it only
if you trust the source, then turn the setting off after installation.

Keep Google Play Protect enabled. If it offers to scan the APK, follow its
instructions. If Android or Play Protect blocks the app or warns that it may be
harmful or unverified, stop and contact the developer rather than bypassing the
warning. Some devices or regions may also restrict apps from unverified
developers.

## 3. Convert your first images

1. Open **WebP Converter**, select **Convert** and tap **Choose input folder**.
2. In Android's folder picker, navigate to the folder containing your WebP
   images. Tap **Use this folder**, then **Allow** if asked.
3. Tap **Choose destination folder** and choose where to save the results.
   Confirm with **Use this folder** and **Allow** if prompted. You may use the
   same folder as the input or choose a different one.
4. Under **Output format**, select:
   - **PNG** to preserve transparent backgrounds.
   - **JPG** to replace transparent areas with white.
5. Scroll down to the image list. Select the checkbox beside one or more files,
   or tap **Select all**. The screen shows how many are selected. Tap
   **Select all** again to clear the selection.
6. Tap **Convert _n_ selected files** in the pinned bottom bar. On Android 13 or
   newer, the app may ask for notification permission. Allow it if you want
   progress notifications; conversion can still start if you decline.
7. Watch the progress on screen. When all files succeed, you will see a
   **Success!** message. If some fail, the screen shows how many succeeded and
   the individual errors.
8. Open your destination folder in your phone's file manager to find the
   `.jpg` or `.png` files. Check the results before deleting any originals.

The list shows only `.webp` files directly inside the chosen folder, not files
inside its subfolders. File sizes are shown when the folder provider supplies
them. Animated WebP files are not supported and will be reported as failures.
Existing output files are kept; a new file may receive a name such as
`photo (1).jpg`.

The app does not copy metadata such as camera details or GPS information to the
converted file. Keep the original if you need that information.

## 4. Copy, move or delete files

Open **Manage files**, choose the input folder, then select **WebP only** or
**Other files**. Other files includes non-WebP documents, images, videos and
extensionless files. Select **Copy**, **Move** or **Delete**, and select
individual files or **Select all** for the displayed category. Switching
categories clears the selection; the Convert tab still lists only WebP files.

- **Copy/Move:** choose a different destination folder, then tap the action in
  the bottom bar. Move asks for confirmation and removes each source only after
  its copy is verified. Copy keeps originals.
- **Delete:** no destination is needed. Tap the bottom action, check the source
  folder, file category and count, then tap **Delete permanently**. **There is no undo.**
- After upgrading from an older version, reselect the input folder and grant
  access before moving or deleting; the previous permission may be read-only.
- Watch progress and review per-file errors. **Cancel operation** stops remaining
  work but does not undo completed copies, moves or deletions.

See the [User Manual](USER_MANUAL.md) for detailed instructions and safety notes.

## Troubleshooting

| Problem | What to try |
|---|---|
| The APK will not open or install. | Check that your device runs Android 9 or later and has free storage. Download the file again from the developer and check its checksum if possible. A work or school device may have installation restrictions; contact its administrator. |
| Android says the app conflicts with an existing app or cannot be updated. | An existing copy may have a different signing key or a newer version. Ask the developer for a compatible update. Uninstall the existing copy only if you accept losing its app data; back up your files first. |
| A folder cannot be selected. | Android restricts some locations, including the root of Downloads and `Android/data`. Create or choose a normal subfolder, such as `Pictures/WebPInput` or `Download/WebPOutput`. Folder names and available locations depend on the device. |
| No images appear. | Make sure the files end in `.webp` and are directly in the selected folder, not a subfolder. Tap **Refresh** after adding files or if the provider is still loading. |
| The Convert button is unavailable. | Choose both folders, wait for the scan to finish, and select at least one image. Wait for any current conversion to finish or cancel it first. |
| Conversion is queued or seems slow. | Android may delay background work, and a single large image can take time. Reopen the app to check progress, keep the phone charged, and try a smaller batch if necessary. Avoid force-stopping the app during conversion. |
| Some images fail. | Read the displayed file-specific errors. Check that the destination is writable and has space. Animated, damaged, inaccessible, or very large images may fail; try a different file or a smaller batch. |
| Converted files are not visible in a gallery app. | Look in the selected destination using the phone's file manager. The gallery may take time to refresh. |

You can tap **Cancel conversion** to stop a batch. Files already converted are
kept.

## Moving from an older version

Versions 1.0.0–1.3.0 used an older internal app ID. Version 1.3.1 and later
cannot replace them, so you may see **two WebP Converter** icons after installing.

1. Open the new app, choose your folders again, and check that it works.
2. Uninstall the old copy: long-press the older **WebP Converter** icon, tap
   **App info**, check that the version is 1.3.0 or lower, then tap **Uninstall**.

Your files are not affected. Only the old app's saved settings and reports are removed.
Version 1.4.0 can update an existing 1.3.1 installation signed with the same key.

## Update or uninstall

- **Update:** Obtain a newer APK from the developer and install it as above.
  Android can update the existing app only if the new APK has the same
  application ID and compatible signing certificate.
- **Uninstall:** Long-press **WebP Converter** in the app list, select
  **App info**, then **Uninstall**. This removes the app's saved settings,
  reports, and folder grants. Images saved in your selected shared folders
  normally remain, but back them up before uninstalling.

App details for this guide: **WebP Converter 1.4.0**,
`com.apps.multiformat.webpconverter`.

For detailed instructions on converting images and resolving common conversion issues, see the [User Manual](USER_MANUAL.md).

For more information about Android's app safety checks, see
[Google Play Protect help](https://support.google.com/android/answer/2812853?hl=en).
