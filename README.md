# AndroidForceInstall

Root app which can downgrade apps or install incompatible versions of existing apps

## Features

- **Root-based Force Installation**: Uses root access to bypass Android's normal installation restrictions
- **Downgrade Apps**: Install older versions of apps over newer ones
- **Install Incompatible Versions**: Force install apps that Android would normally reject
- **Signature Mismatch Handling**: Automatically detects and handles signature mismatches by backing up app data, uninstalling, reinstalling, and restoring data
- **Simple UI**: Easy-to-use interface with file picker for selecting APK files

## Requirements

- Android device with **root access**
- Minimum Android version: Android 5.0 (API 21)
- Root management app (like Magisk or SuperSU)

## How It Works

The app uses the `libsu` library to execute shell commands with root privileges. When you select an APK file:

1. The app checks for root access
2. Copies the selected APK to the app's cache directory
3. Uses `pm install -d -r` command with root to force install the APK
   - `-d` flag allows downgrading
   - `-r` flag replaces the existing application
4. If installation fails due to signature mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE):
   - Automatically extracts the package name from the APK using Android's PackageManager
   - Detects and preserves the original install location (internal vs external storage)
   - Detects and preserves the user context (main user vs work profile)
   - Backs up the existing app's data directories (`/data/data` and `/storage/emulated/0/Android/data`)
   - Uninstalls the existing app with mismatched signature
   - Reinstalls the new APK to the same location and user context
   - Restores the backed-up app data with correct ownership and SELinux contexts
   
**Note**: Some data loss may occur with signature changes. Apps that encrypt data using their signing key will lose that encrypted data. Runtime permissions will be reset to defaults (Android security requirement).

## Usage

1. Launch the app
2. Grant root access when prompted
3. Tap "Select APK File" to choose an APK from your file system
4. Tap "Force Install" to install the selected APK
5. The app will show installation status

## Building

### Prerequisites
- JDK 17 or higher
- Android SDK with API 34

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all checks
./gradlew build
```

The APK will be generated in `app/build/outputs/apk/`

## CI/CD

The project includes a GitHub Actions workflow that:
- Automatically builds the app on push to main branch
- Runs on pull requests
- Uploads the release APK as an artifact

## Permissions

- `READ_EXTERNAL_STORAGE`: To read APK files from storage
- `REQUEST_INSTALL_PACKAGES`: To request installation permissions

## Root Access

This app **requires** root access to function. When you first launch it:
1. You'll see a root access check
2. Your root manager will prompt you to grant access
3. The app will confirm when root access is granted

## Known Issues & Limitations

When handling signature mismatches, some limitations apply:
- Runtime permissions will be reset (Android security requirement)
- Apps that encrypt data with signing keys will lose that data
- Installation location is preserved, but may not work for all scenarios

For more details on signature mismatch handling, see:
- [SIGNATURE_MISMATCH_FIXES.md](SIGNATURE_MISMATCH_FIXES.md) - Summary of fixes and limitations
- [APK_REPLACEMENT_DISCUSSION.md](APK_REPLACEMENT_DISCUSSION.md) - Technical analysis of approaches

## Disclaimer

This app is for educational and personal use only. Force installing apps can:
- Bypass security checks
- Lead to app instability
- Void warranties
- Violate app terms of service

Use at your own risk.

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.

## Credits

- Uses [libsu](https://github.com/topjohnwu/libsu) by topjohnwu for root access
- Built with AndroidX and Material Design components
