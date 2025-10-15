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
3. Uses `pm install -d -r --user 0` command with root to force install the APK
   - `-d` flag allows downgrading
   - `-r` flag replaces the existing application
   - `--user 0` flag ensures installation to user space (not private space)
4. If installation fails due to signature mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE):
   - Automatically extracts the package name from the APK using Android's PackageManager
   - Finds the installed APK location using `pm path`
   - Force-stops the running app
   - Replaces the APK file directly on the filesystem
   - Sets proper file permissions and SELinux contexts
   - Runs `pm install --user 0` on the replaced APK to properly register it with PackageManager
   
**Note**: This hybrid approach preserves ALL app data since no uninstall occurs, while properly registering the new APK to prevent corruption and ensure the app continues to work correctly.

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

When handling signature mismatches with direct APK replacement:
- Split APKs (Android App Bundles) may not work correctly (only base.apk is replaced)
- May not work on all Android versions or custom ROMs

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
