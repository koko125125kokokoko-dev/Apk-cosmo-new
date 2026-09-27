# Cosmo Game Store

[![Build & Release Debug APK](https://github.com/koko125125kokokoko-dev/Apk-cosmo-new/actions/workflows/build-apk.yml/badge.svg)](https://github.com/koko125125kokokoko-dev/Apk-cosmo-new/actions/workflows/build-apk.yml)
[![GitHub Release](https://img.shields.io/github/v/release/koko125125kokokoko-dev/Apk-cosmo-new?color=blue&label=Latest%20Release)](https://github.com/koko125125kokokoko-dev/Apk-cosmo-new/releases)
[![Direct Download](https://img.shields.io/badge/Download-Latest%20APK-blue?logo=android&logoColor=white)](https://github.com/koko125125kokokoko-dev/Apk-cosmo-new/releases/latest/download/CosmoGameStore-debug.apk)

A clean, modern Android game store and package installer built in Kotlin using Jetpack Compose and Material Design 3. Powered by edge-to-edge flat design, atomic split XAPK/APK installation with automatic launch, live download notifications with icons, local Room database persistence, and direct integration with the Cloudflare Worker API.

---

## Direct APK Download

Whenever changes are pushed to `main` or triggered manually, GitHub Actions automatically compiles and releases a debug APK directly to **GitHub Releases**.

### One-Click Download Links:
- **[Download Latest CosmoGameStore-debug.apk](https://github.com/koko125125kokokoko-dev/Apk-cosmo-new/releases/latest/download/CosmoGameStore-debug.apk)** (Direct APK download, always newest build)
- **[View All Releases and Versions](https://github.com/koko125125kokokoko-dev/Apk-cosmo-new/releases)**
- **[View GitHub Actions Build Runs](https://github.com/koko125125kokokoko-dev/Apk-cosmo-new/actions)**

---

## How to Install on Android

1. Tap **[Download Latest APK](https://github.com/koko125125kokokoko-dev/Apk-cosmo-new/releases/latest/download/CosmoGameStore-debug.apk)** in your mobile web browser.
2. When the download finishes, open your **Downloads** folder or tap the download notification.
3. If Android displays **"For your security, your phone is not allowed to install unknown apps from this source"**:
   - Tap **Settings**.
   - Enable **Allow from this source**.
4. Tap **Install** and launch **Cosmo Game Store**.

---

## Four Native Tabs

1. **Browse (Game Store Catalog)**:
   - Live game directory powered by the Cloudflare Worker API (`main_category_id: 2`).
   - Categories filter: Action, Survival, Adventure, Simulation, Sports, Visual Novel, and Horror.
   - Featured hero game banner with quick installation action.
   - Real-time search with zero-padding flat cards showing package format (APK / XAPK), version, size, and rating.
   - Detailed application bottom sheet with preview screenshots, developer credits, specs, and full description.

2. **My Library (Package Management & Storage Access)**:
   - **Downloaded Packages (APK / XAPK)**: Scans public device downloads and local database cache, displaying extracted app icons, version codes, split package counts, OBB presence, and one-tap install.
   - **Installed Apps**: Displays non-system installed games on the device with one-tap Open and Uninstall actions.
   - **Pick File (SAF)**: Integrates with Android Storage Access Framework (`OpenDocument`) to pick and parse any `.apk` or `.xapk` from internal storage, SD cards, or external drives.

3. **Apps (Application Directory)**:
   - Dedicated application store for utility and productivity apps (`main_category_id: 1` including Chrome, Free VPN, Shizuku, VidMate, and Tools).
   - Category filtering, real-time search, and direct download actions.

4. **Settings (Preferences & System Controls)**:
   - **Appearance & Theme**: Toggle between System Default, Deep Space Dark mode, and Clean Light mode.
   - **Auto-Launch Automation**: Configure automatic app launch immediately upon successful package installation.
   - **Permission Monitors**: Live status indicators and direct shortcuts to Android system settings for Unknown App Sources and Notifications.
   - **Storage Management**: Cache and temporary file calculator with one-tap cleaner.
   - **Backend Status**: Live endpoint status display for `https://holy-firefly-9726.play125store.workers.dev`.

---

## Package Installer Architecture

- **XAPK Multi-Split Parser (`com.example.installer.XapkParser`)**:
  - Unpacks and inspects `manifest.json` from `.xapk` zip archives.
  - Identifies base APK and architecture-specific splits (`arm64_v8a`, density splits).
  - Detects and extracts expansion OBB data into `/Android/obb/<package_name>/`.
  - Extracts embedded `icon.png` or inspects internal base APK headers.

- **APK Parser (`com.example.installer.ApkParser`)**:
  - Inspects standalone `.apk` files using `PackageManager.getPackageArchiveInfo`.
  - Extracts package labels, version names, version codes, and application icons.

- **Atomic PackageInstaller Session (`com.example.installer.PackageInstallerHelper`)**:
  - Uses `PackageInstaller.Session` to stream split APKs concurrently to the Android system installer.
  - Employs `FileProvider` with secure content URIs for standard APK installations.
  - Registers `InstallStatusReceiver` with `PendingIntent` callbacks.
  - Triggers auto-launch via `packageManager.getLaunchIntentForPackage(packageName)` on `STATUS_SUCCESS`.

- **Download Notification System (`com.example.notification.DownloadNotificationHelper`)**:
  - Dedicated notification channel with `IMPORTANCE_LOW` for unobtrusive updates.
  - Progress notifications with formatted bytes, percentage, notification icons, and Cancel action.
  - Completion notification with direct one-tap "Install Now" intent.
  - Failure notifications with retry details.

---

## GitHub Actions Automated Build Workflow

The workflow at `.github/workflows/build-apk.yml` handles:
- **Environment**: Ubuntu Linux with Java 21 (Temurin) and Gradle dependency caching.
- **Keystore Management**: Restores `debug.keystore` from `debug.keystore.base64` or creates a build keystore automatically.
- **Compilation**: Executes `./gradlew assembleDebug --no-daemon --stacktrace`.
- **Integrity**: Calculates SHA-256 checksums and file size.
- **Artifacts**: Retains artifacts on GitHub Actions for 30 days.
- **Releases**: Publishes GitHub Releases with direct download links:
  - `CosmoGameStore-debug.apk` (fixed latest release asset)
  - `CosmoGameStore-v1.0.<run_number>-debug.apk` (versioned release asset)

### Required GitHub Repository Settings:
To allow GitHub Actions to create releases and upload the APK:
1. In your GitHub repository, open **Settings** -> **Actions** -> **General**.
2. Scroll down to **Workflow permissions**.
3. Select **Read and write permissions**.
4. Click **Save**.

---

## Git Push to GitHub

To push this codebase to your repository:

```bash
git init
git add .
git commit -m "feat: complete native Jetpack Compose Cosmo Game Store with APK/XAPK installer"
git branch -M main
git remote add origin https://github.com/koko125125kokokoko-dev/Apk-cosmo-new.git
git push -u origin main
```
