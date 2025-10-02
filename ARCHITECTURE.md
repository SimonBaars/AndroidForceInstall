# AndroidForceInstall - Architecture Overview

## Project Structure

```
AndroidForceInstall/
├── app/
│   ├── build.gradle                 # App-level build configuration
│   ├── proguard-rules.pro          # ProGuard rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml     # App manifest with permissions
│       ├── java/com/simonbaars/androidforceinstall/
│       │   └── MainActivity.java   # Main activity with root install logic
│       └── res/
│           ├── drawable/
│           │   └── ic_launcher.xml # App icon
│           ├── layout/
│           │   └── activity_main.xml # Main UI layout
│           ├── values/
│           │   ├── colors.xml      # Color definitions
│           │   ├── strings.xml     # String resources
│           │   └── themes.xml      # Material theme configuration
│           └── xml/
│               └── file_paths.xml  # FileProvider paths
├── .github/workflows/
│   └── android.yml                 # CI/CD workflow for building APK
├── build.gradle                    # Project-level build configuration
├── settings.gradle                 # Gradle settings and repositories
├── gradle.properties               # Gradle properties
└── gradlew                         # Gradle wrapper script

```

## Key Components

### MainActivity.java
The main activity handles:
- **Root Access Check**: Uses libsu to verify and request root privileges
- **File Selection**: Implements ACTION_GET_CONTENT intent to pick APK files
- **URI to File Conversion**: Copies content:// URIs to cache for installation
- **Force Installation**: Executes `pm install -d -r` with root to bypass restrictions

### Core Functionality

#### Root Check Flow
1. Initialize libsu Shell on app start
2. Check for root access asynchronously
3. Display status and enable/disable UI accordingly
4. Show dialog if root is not available

#### Installation Flow
1. User selects APK file via system picker
2. App copies file from content:// URI to cache directory
3. User taps "Force Install"
4. App executes root shell command: `pm install -d -r <path>`
   - `-d`: Allow version downgrades
   - `-r`: Replace existing package
5. If installation fails with signature mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE):
   - Extract package name using Android's `PackageManager.getPackageArchiveInfo()`
   - Detect current install location (internal/external storage) using `pm path`
   - Detect current user context using `pm list packages --user all`
   - Get current app UID for permission restoration
   - Backup app data directories to `/data/local/tmp/`
     - Internal data: `/data/data/<package>`
     - External data: `/storage/emulated/0/Android/data/<package>`
   - Execute `pm uninstall <package>` to remove existing app
   - Retry installation with `pm install -d -r --install-location <same> --user <same> <path>`
     - Preserves original install location (internal vs external)
     - Preserves user context (main user vs work profile)
   - Get new app UID
   - Restore backed-up data to original locations
   - Fix ownership using `chown` with new UID
   - Restore SELinux contexts using `restorecon`
   - Clean up temporary backup files
6. Display success or error message

## Dependencies

### Core Libraries
- **androidx.appcompat** (1.6.1): AppCompat support
- **material** (1.10.0): Material Design components
- **constraintlayout** (2.1.4): Layout system

### Root Access
- **libsu:core** (5.2.2): Root shell command execution
- **libsu:io** (5.2.2): Root file I/O operations

## Build Configuration

### Gradle Setup
- **Gradle**: 8.0
- **Android Gradle Plugin**: 8.1.2
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Java Version**: 1.8

### Repositories
- Google Maven Repository (for Android dependencies)
- Maven Central
- JitPack (for libsu)

## Permissions

### Required Permissions
1. `READ_EXTERNAL_STORAGE`: Access APK files on device
2. `REQUEST_INSTALL_PACKAGES`: Request installation permission

### Root Permission
- Requested at runtime via libsu
- Required for `pm install` command execution
- Managed by device's root manager (Magisk, SuperSU, etc.)

## CI/CD Workflow

### GitHub Actions Pipeline
1. **Checkout**: Clone repository
2. **Setup Java**: Install JDK 17
3. **Build**: Compile and build project
4. **Assemble Release**: Create release APK
5. **Upload Artifact**: Store APK for download

### Triggers
- Push to `main` branch
- Pull requests to `main` branch

## Security Considerations

### Root Access
- App requires root to function
- Uses well-tested libsu library
- Commands are executed with full root privileges
- Users must grant explicit root permission

### APK Installation
- Bypasses Android's version checking
- Can install incompatible versions
- Handles signature mismatches by backing up data, uninstalling, and restoring
- Preserves installation location (internal vs external storage) and user context
- Attempts to preserve app data during signature mismatch handling
- May cause app instability if used incorrectly
- Users are responsible for APK source validation
- Data restoration may not work for all apps (e.g., those with encrypted data tied to signing key)
- Runtime permissions will be reset to defaults (Android security requirement)

### Why Not Replace APK on Filesystem?
The app uses `pm install` rather than directly replacing APK files because:
- Android's runtime verification prevents mismatched signatures from running
- PackageManager cache synchronization is complex and error-prone
- Split APK handling would be extremely complicated
- System stability could be compromised
- The current approach works within Android's security model
- See APK_REPLACEMENT_DISCUSSION.md for detailed analysis

## Related Documentation

For more information on signature mismatch handling:
- **[SIGNATURE_MISMATCH_FIXES.md](SIGNATURE_MISMATCH_FIXES.md)**: Summary of implemented fixes for installation location and data preservation issues
- **[APK_REPLACEMENT_DISCUSSION.md](APK_REPLACEMENT_DISCUSSION.md)**: Technical analysis of APK filesystem replacement vs PackageManager install approaches

## Future Enhancements

Potential improvements:
- Runtime permission backup and restore
- APK signature verification
- Batch installation support
- Installation history/logs
- Better support for split APKs (Android App Bundles)
- Backup current version before downgrade
- Dark mode support
- Multiple language support

## Technical Notes

### File Handling
- Content URIs are copied to cache directory
- Ensures reliable access during installation
- Cache is cleared by Android system as needed

### Threading
- Root checks run on background threads
- UI updates happen on main thread
- File copying is asynchronous to avoid ANR

### Error Handling
- Graceful handling of missing root
- Clear error messages for failed installations
- File access errors are caught and reported
- Automatic detection and handling of signature mismatch errors
- User feedback during uninstall and reinstall process
