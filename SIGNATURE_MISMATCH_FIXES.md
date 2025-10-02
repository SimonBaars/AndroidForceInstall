# Signature Mismatch Installation Fixes

## ⚠️ IMPORTANT: Read This First

**If you're experiencing signature mismatch errors:**

- ✅ **BEST solution**: Re-sign the APK with the original key (if you have it)
  - See [SIGNATURE_MISMATCH_GUIDE.md](SIGNATURE_MISMATCH_GUIDE.md) for instructions
  - This avoids ALL data loss and permission resets
  
- ⚠️ **Only alternative**: Uninstall and reinstall (what this app does)
  - Some data loss is inevitable
  - Permissions will be reset
  - Read [QUICK_REFERENCE.md](QUICK_REFERENCE.md) for quick guidance

**You CANNOT force an APK to match a different signature** - signatures are cryptographic and cannot be forged.

---

## Issues Addressed

This document summarizes the fixes implemented to address two critical issues when handling signature mismatches during app installation:

### Issue 1: Wrong Installation Location
**Problem**: When reinstalling an app with a different signature, the app would sometimes install to a different location than the original (e.g., a user space app would reinstall to private space).

**Solution**: The app now:
1. Detects the original installation location before uninstall using `pm path <package>`
2. Uses the `--install-location` flag during reinstall to force installation to the same location
   - `--install-location 1` for internal storage
   - `--install-location 2` for external/adoptable storage
   - `--install-location 0` for auto (if original location couldn't be determined)

### Issue 2: Data Loss
**Problem**: App data would be lost during the uninstall/reinstall process despite backup attempts.

**Current Solution**: 
- The app performs comprehensive data backup before uninstall
- After reinstall, data is restored with correct UID ownership
- SELinux contexts are fixed with `restorecon`

**Limitations**:
- Some data loss is **inevitable** with signature changes
- Apps that encrypt data using their signing certificate will lose that encrypted data
- Runtime permissions will be reset (Android security requirement)
- Shared preferences and databases should be preserved
- External storage data (`/storage/emulated/0/Android/data`) is preserved

## Additional Improvements

### User Context Preservation
**Problem**: Apps could reinstall to the wrong user profile on multi-user devices (e.g., work profile vs personal profile).

**Solution**: 
- Detect which user the app is installed for using `pm list packages --user all`
- Use the `--user <userId>` flag during reinstall to maintain the same user context

## Technical Implementation

### Installation Flow with Signature Mismatch

```
1. Attempt normal install with: pm install -d -r <apk>
2. Detect signature mismatch error
3. Detect install location from: pm path <package>
4. Detect user context from: pm list packages --user all
5. Backup app data:
   - /data/data/<package> → /data/local/tmp/<package>_backup
   - /storage/emulated/0/Android/data/<package> → /data/local/tmp/<package>_ext_backup
6. Uninstall existing app: pm uninstall <package>
7. Reinstall with preserved location and user:
   pm install -d -r --install-location <N> --user <userId> <apk>
8. Restore app data with correct ownership
9. Clean up temporary backup files
```

## Why Not Replace APK on Filesystem?

See [APK_REPLACEMENT_DISCUSSION.md](APK_REPLACEMENT_DISCUSSION.md) for a comprehensive analysis of why directly replacing APK files on the filesystem is not recommended.

**TL;DR**: Android's security model will prevent apps with mismatched signatures from running, making filesystem replacement ineffective.

## Expected Behavior

### What Should Work Now
✅ Apps reinstall to the same storage location (internal vs external)
✅ Apps reinstall to the same user profile (main vs work)
✅ Most app data is preserved (databases, shared preferences, files)
✅ External storage data is preserved
✅ Correct file ownership and SELinux contexts

### Known Limitations
⚠️ Runtime permissions will be reset to defaults (Android security requirement)
⚠️ Apps that encrypt data with their signing key will lose encrypted data
⚠️ Some app-specific security measures may still cause issues
⚠️ Split APKs (Android App Bundles) may have additional complications

## Testing Recommendations

To verify the fixes work correctly:

1. Install an app signed with key A
2. Note its installation location (use `adb shell pm path <package>`)
3. Note its user context (use `adb shell pm list packages --user all`)
4. Add some data to the app (create files, preferences, etc.)
5. Try to install the same app signed with key B using AndroidForceInstall
6. Verify:
   - App installs to the same location as before
   - App installs to the same user profile
   - App data is preserved (check files, preferences)
   - External storage data is preserved

## User Guidance

When using this app to handle signature mismatches, users should be aware:

1. **Some data loss is expected**: If the app encrypts data using its signing certificate, that data cannot be recovered
2. **Permissions reset**: All runtime permissions will need to be granted again after reinstall
3. **First launch issues**: Some apps may behave differently on first launch after signature change
4. **Backup important data**: Always backup critical app data through the app's own backup mechanism before using this tool

## Code Changes Summary

### MainActivity.java
- Added install location detection before uninstall (lines 221-240)
- Added user context detection (lines 243-258)
- Modified reinstall command to include location and user flags (lines 314-331)
- Added comprehensive inline documentation

### Documentation Updates
- Updated ARCHITECTURE.md to reflect new signature mismatch handling flow
- Updated README.md to document limitations and expectations
- Created APK_REPLACEMENT_DISCUSSION.md for technical analysis
- Created this summary document

## Future Enhancements

Potential improvements for consideration:

1. **Permission Backup/Restore**: Backup runtime permissions and restore after reinstall
2. **Installation History**: Track which apps were reinstalled and their original signatures
3. **Pre-flight Checks**: Warn users if app likely uses signing-key-based encryption
4. **Better Error Messages**: Provide specific guidance based on failure types
5. **Split APK Support**: Handle Android App Bundles more intelligently
