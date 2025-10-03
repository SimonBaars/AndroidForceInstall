# Direct APK Replacement Implementation

## Overview

This document describes the implementation of direct APK file replacement as the primary method for handling signature mismatches during app installation.

## What Changed

### Previous Approach (Backup/Uninstall/Reinstall)
The previous implementation used these steps when encountering signature mismatches:
1. Backup app data from `/data/data/<package>` and external storage
2. Uninstall the existing package with `pm uninstall`
3. Reinstall using `pm install` with location/user flags
4. Restore app data with correct ownership

**Problems with this approach:**
- Data could be lost despite backup/restore
- UID might change
- Runtime permissions were reset
- More complex and slower

### New Approach (Direct APK Replacement)
The new implementation directly replaces the APK file on the filesystem:
1. Find installed APK location with `pm path <package>`
2. Force-stop the app with `am force-stop`
3. Copy new APK over the old one with `cp -f`
4. Set proper permissions: `chmod 644` and `chown system:system`
5. Restore SELinux context with `restorecon`
6. Attempt to refresh Package Manager cache

**Benefits of this approach:**
- ✅ **Perfect data preservation** - no data is touched
- ✅ **UID never changes** - app maintains same user ID
- ✅ **Runtime permissions preserved** - no permission reset
- ✅ **Faster** - no backup/restore cycle
- ✅ **Installation location preserved** - APK stays in place
- ✅ **Simpler** - fewer steps, less code

**Known limitations:**
- ⚠️ App may fail signature verification on launch
- ⚠️ Device reboot may be required
- ⚠️ Split APKs only have base.apk replaced
- ⚠️ May not work on all Android versions
- ⚠️ PackageManager cache may become out of sync

## Implementation Details

### Code Location
- File: `app/src/main/java/com/simonbaars/androidforceinstall/MainActivity.java`
- Method: `installApk()`
- Lines: ~190-350 (signature mismatch handling)

### Key Shell Commands Used

```bash
# Find installed APK location
pm path <package_name>

# Force stop the app
am force-stop <package_name>

# Replace the APK file
cp -f <new_apk> <installed_apk_path>

# Set permissions
chmod 644 <installed_apk_path>
chown system:system <installed_apk_path>

# Restore SELinux context
restorecon <installed_apk_path>

# Attempt cache refresh (best effort)
pm path <package_name> > /dev/null 2>&1
```

### Error Handling

The implementation includes error handling for:
- Package name extraction failure
- APK path detection failure
- Force-stop failure (warning only)
- File replacement failure
- Split APK detection (warning to user)

### User Feedback

The app provides real-time status updates:
- "Signature mismatch detected. Replacing APK directly..."
- "Finding installed APK location for <package>..."
- "Warning: App uses split APKs. This may not work correctly."
- "Force-stopping <package>..."
- "Replacing APK file(s)..."
- "Refreshing Package Manager cache..."
- "APK replaced successfully. App data preserved."
- Warning about potential launch issues and reboot requirement

## Testing

### Manual Testing Steps

1. Install an app (e.g., signed with debug key)
2. Add some data to the app (preferences, files, etc.)
3. Create a new APK signed with a different key
4. Use AndroidForceInstall to install the new APK
5. Verify the APK replacement succeeds
6. Check that all app data is still present
7. Try to launch the app
8. If launch fails, reboot and try again

### Expected Outcomes

**Success case:**
- APK replacement completes
- All data preserved
- App launches successfully (with or without reboot)

**Partial success case:**
- APK replacement completes
- All data preserved
- App fails to launch due to signature verification
- User must manually handle the signature issue

**Failure case:**
- APK replacement fails
- Original app remains functional
- User gets error message

## Why This Approach?

The request was to implement "the approach of replacing the apk file directly" instead of the uninstall/reinstall approach because "the current uninstall reinstall approach definitely doesn't persist data."

While the previous backup/restore mechanism did attempt to preserve data, the new direct replacement approach:
1. **Guarantees** data preservation (no uninstall = no data loss)
2. Is simpler and faster
3. Preserves runtime permissions
4. Maintains the exact same UID

The trade-off is that apps may fail Android's signature verification checks, but this is an acceptable risk for users who understand they're bypassing security measures.

## Security Considerations

**This approach bypasses Android's security model.**

Users should be aware that:
- Signature verification is a security feature
- Replacing APKs with different signatures can be dangerous
- Malicious APKs could replace legitimate apps
- This tool should only be used on apps you trust
- Root access is required (which already implies security risks)

## Future Improvements

1. **Better split APK handling**: Replace all split APK files, not just base.apk
2. **Signature bypass techniques**: Research methods to disable or work around signature verification
3. **Automated reboot**: Offer to reboot automatically after replacement
4. **Verification testing**: After replacement, test if app can launch before declaring success
5. **Rollback capability**: Keep backup of original APK in case replacement fails

## References

- See `APK_REPLACEMENT_DISCUSSION.md` for technical analysis of approaches
- See `SIGNATURE_MISMATCH_FIXES.md` for current implementation details
- See `README.md` for user-facing documentation
