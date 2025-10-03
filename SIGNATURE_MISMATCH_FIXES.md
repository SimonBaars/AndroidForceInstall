# Signature Mismatch Installation Fixes

## Current Implementation

This document describes the direct APK filesystem replacement approach now implemented for handling signature mismatches during app installation.

### New Approach: Direct APK Replacement

**Problem**: When installing an APK with a different signature over an existing app, Android's Package Manager refuses the installation for security reasons.

**Solution**: The app now:
1. Detects the installed APK location using `pm path <package>`
2. Force-stops the running app with `am force-stop`
3. Directly replaces the APK file(s) on the filesystem
4. Sets proper permissions (chmod 644, chown system:system)
5. Restores SELinux contexts with `restorecon`
6. Attempts to refresh Package Manager cache

**Advantages**:
- **Perfect Data Preservation**: No backup/restore needed - all app data remains intact
- **UID Never Changes**: App maintains the same user ID
- **Faster**: No uninstall/reinstall cycle
- **Installation Location Preserved**: APK stays in exact same location
- **Runtime Permissions Preserved**: Permissions are not reset

**Limitations**:
- **May Not Launch**: Android's signature verification may prevent the app from running
- **Reboot May Be Required**: Device may need to be rebooted for changes to take effect
- **Split APKs**: Only base.apk is replaced; split APKs may not work correctly
- **PackageManager Cache**: Cache may become out of sync
- **Android Version Dependent**: May not work on all Android versions

## Technical Implementation

### Installation Flow with Signature Mismatch

```
1. Attempt normal install with: pm install -d -r <apk>
2. Detect signature mismatch error
3. Extract package name from APK
4. Find installed APK location: pm path <package>
5. Parse APK paths (may be multiple for split APKs)
6. Force-stop the app: am force-stop <package>
7. Replace APK file directly:
   - cp -f <new_apk> <installed_apk_path>
   - chmod 644 <installed_apk_path>
   - chown system:system <installed_apk_path>
   - restorecon <installed_apk_path>
8. Refresh Package Manager cache (best effort)
9. Report success with warning about potential launch issues
```

## Why This Approach?

See [APK_REPLACEMENT_DISCUSSION.md](APK_REPLACEMENT_DISCUSSION.md) for a comprehensive analysis of the filesystem replacement approach vs pm install.

**Benefits**: Perfect data preservation since no uninstall occurs.

**Risks**: Android's security model may prevent apps with mismatched signatures from running.

## Expected Behavior

### What Should Work Now
✅ All app data is preserved (databases, shared preferences, files)
✅ External storage data is preserved
✅ UID remains unchanged
✅ Runtime permissions are preserved
✅ Installation location preserved
✅ Correct file ownership and SELinux contexts

### Known Limitations
⚠️ App may fail to launch due to signature verification
⚠️ Device reboot may be required for app to work
⚠️ Split APKs may not work correctly (only base.apk is replaced)
⚠️ PackageManager cache may become out of sync
⚠️ May not work on all Android versions

## Testing Recommendations

To verify the direct APK replacement works:

1. Install an app signed with key A
2. Note its installation location (use `adb shell pm path <package>`)
3. Add some data to the app (create files, preferences, etc.)
4. Try to install the same app signed with key B using AndroidForceInstall
5. Verify:
   - APK replacement completes successfully
   - All app data is preserved (check files, preferences)
   - External storage data is preserved
   - Runtime permissions are still granted
6. Try to launch the app:
   - If it launches successfully, the replacement worked
   - If it fails with verification errors, a reboot may be needed
   - If it still fails after reboot, signature verification is blocking it

## User Guidance

When using this app to handle signature mismatches with direct APK replacement:

1. **Data is preserved**: All app data, preferences, and files remain intact
2. **Permissions preserved**: Runtime permissions are NOT reset (major advantage)
3. **Launch may fail**: The app may fail to launch due to Android's signature verification
4. **Reboot may help**: If the app won't launch, try rebooting the device
5. **Split APK warning**: Apps installed from Play Store may use split APKs and may not work correctly
6. **Backup important data**: While data is preserved, always backup critical app data as a precaution

## Code Changes Summary

### MainActivity.java
- Replaced backup/uninstall/reinstall approach with direct APK replacement
- Added APK path detection using `pm path`
- Added force-stop logic before replacement
- Implemented direct file copy with proper permissions
- Added SELinux context restoration
- Added warnings for split APKs and potential launch issues
- Updated inline documentation to reflect new approach

### Documentation Updates
- Updated APK_REPLACEMENT_DISCUSSION.md to document the implemented approach
- Updated README.md to describe the new installation flow
- Updated this document to reflect the direct replacement method

## Future Enhancements

Potential improvements for consideration:

1. **Split APK Support**: Replace all split APK files, not just base.apk
2. **Signature Bypass**: Explore methods to disable signature verification
3. **Better PM Cache Refresh**: Find more reliable ways to refresh PackageManager cache
4. **Installation History**: Track which apps were replaced and their original signatures
5. **Pre-flight Checks**: Detect split APKs and warn users before attempting replacement
6. **Auto-reboot Option**: Offer to automatically reboot the device after replacement
