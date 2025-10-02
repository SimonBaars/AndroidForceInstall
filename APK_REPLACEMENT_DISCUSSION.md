# APK Filesystem Replacement vs PM Install: Technical Discussion

## Problem Statement

When handling signature mismatches, the current implementation uses `pm uninstall` followed by `pm install`. This approach has two critical issues:

1. **Wrong Installation Location**: The reinstalled app may install to a different location (e.g., user space app installs to private space)
2. **Data Loss**: App data gets lost despite the backup/restore mechanism

## Current Implementation Analysis

### What Happens Now (pm install approach)

```java
// Current flow for signature mismatch:
1. Detect signature mismatch error
2. Backup app data from /data/data/<package>
3. Execute: pm uninstall <package>
4. Execute: pm install -d -r <new_apk>
5. Restore app data to /data/data/<package>
6. Fix ownership with chown
7. Fix SELinux contexts with restorecon
```

### Issues with Current Approach

1. **Installation Location Problems**:
   - `pm install` uses Android's install logic to determine location
   - Location can change based on:
     - APK manifest settings (android:installLocation)
     - Available storage space
     - System policies
     - User/profile context (main user vs work profile)
   - No control over where app gets installed

2. **Data Preservation Failures**:
   - UID can change between uninstall/install
   - Some apps encrypt data with keys tied to signing certificate
   - Shared UID scenarios may break
   - Content provider data may not be backed up
   - Database locks and file handles

3. **Package Manager State**:
   - PM maintains metadata about packages
   - Uninstall/reinstall resets this state
   - Runtime permissions get reset
   - App settings/preferences may be lost

## Proposed Alternative: APK Filesystem Replacement

### Concept

Instead of using PackageManager, directly replace the APK file in Android's app installation directory:

```bash
# Find where the APK is installed
pm path <package_name>  # Returns: package:/data/app/<package>/base.apk

# Replace the APK file
cp /path/to/new.apk /data/app/<package>/base.apk

# Fix permissions and ownership
chown system:system /data/app/<package>/base.apk
chmod 644 /data/app/<package>/base.apk
restorecon /data/app/<package>/base.apk

# Restart the app or reboot to pick up changes
am force-stop <package_name>
```

### Advantages

#### 1. **Preserves Installation Location**
- APK stays in exact same directory
- No user/profile migration issues
- Consistent with original installation

#### 2. **Perfect Data Preservation**
- No backup/restore needed
- UID never changes
- App data remains completely intact
- All permissions preserved
- Content provider data untouched
- No database corruption risks

#### 3. **Maintains Package Manager State**
- All runtime permissions preserved
- App settings unchanged
- Shared UID relationships intact
- Installation metadata preserved

#### 4. **Faster Operation**
- No uninstall/reinstall cycle
- No data copying
- Simple file replacement

#### 5. **No Data Encryption Issues**
- Since signing key changes are the issue, keeping data intact
  means apps that encrypt with signing keys will break anyway
- But for apps that don't, this preserves everything

### Disadvantages

#### 1. **System Integrity Concerns**
- Bypasses Android's signature verification completely
- Package Manager cache may become out of sync
- Could cause system instability

#### 2. **APK Structure Changes**
- Modern apps use split APKs (base.apk + split_*.apk)
- Must handle multiple APK files
- Directory structure may vary by Android version

#### 3. **Verification Failures**
- Android may detect signature mismatch on app launch
- App may fail to start due to verification
- System may refuse to run mismatched apps

#### 4. **Package Manager Cache Issues**
- PM caches APK information (signatures, permissions, etc.)
- Cache may not update after filesystem replacement
- May require:
  ```bash
  # Force PM to rescan
  pm path <package>  # This may trigger rescan
  # Or restart Package Manager service (risky)
  # Or full device reboot
  ```

#### 5. **SELinux and Security Policies**
- Modern Android enforces signature verification
- SELinux policies may prevent app execution
- App may be blocked by Play Protect or similar

#### 6. **Split APK Complexity**
- Apps installed from Play Store often have splits
- Must replace ALL APK files (base + all splits)
- Single APK replacement won't work for split apps

#### 7. **Android Version Differences**
- APK storage location changed over Android versions:
  - Android 4.x: `/data/app/<package>-<number>.apk`
  - Android 5.0+: `/data/app/<package>-<hash>/base.apk`
  - Android 8.0+: May have splits in same directory
- Code must handle all these variations

#### 8. **App Won't Run**
- Most critically: Android's runtime verification will likely **prevent the app from running**
- When Android launches an app, it verifies the APK signature matches the installed package signature
- With a replaced APK, this verification will fail
- App will crash on launch with signature verification error

## Hybrid Approach: Best of Both Worlds?

### Option 1: Smart Detection
```java
// Detect if we can safely use filesystem replacement
boolean canUseFilesystemReplacement() {
    // Check if single APK (no splits)
    // Check Android version compatibility
    // Check if app is running (must be stopped)
    // Return true only if all conditions met
}

if (canUseFilesystemReplacement()) {
    // Use filesystem replacement
} else {
    // Fall back to pm install with backup/restore
}
```

### Option 2: Fix Installation Location Issues
Instead of filesystem replacement, improve the current approach:

```java
// When reinstalling, specify install location explicitly
pm install -d -r --install-location 0 <path>  // 0 = auto, 1 = internal, 2 = external

// Or better: determine current install location first
String installLocation = getCurrentInstallLocation(packageName);
// Then install to same location
pm install -d -r --install-location <same> <path>
```

### Option 3: Preserve More Metadata
Backup and restore more than just app data:

```bash
# Backup runtime permissions
pm dump <package> | grep "permission:"

# After reinstall, restore permissions
pm grant <package> <permission>
pm revoke <package> <permission>

# Backup app settings
settings get <namespace> <key>

# Restore app settings
settings put <namespace> <key> <value>
```

## Implemented Approach

### Direct APK Filesystem Replacement (IMPLEMENTED)

**The project now uses direct APK filesystem replacement** despite the known limitations:

**Implementation details:**
1. Detects installed APK location via `pm path <package>`
2. Force-stops the running app with `am force-stop`
3. Directly replaces the APK file(s) on the filesystem
4. Sets proper permissions (chmod 644, chown system:system)
5. Restores SELinux contexts with restorecon
6. Attempts to refresh Package Manager cache

**Known Issues:**
1. Android may refuse to run apps with mismatched signatures
2. Split APKs may not work correctly (only base.apk is replaced)
3. Device may require reboot for changes to take effect
4. PackageManager cache may become out of sync
5. May not work on all Android versions

**Advantages:**
- Preserves ALL app data (no backup/restore needed)
- UID never changes
- Faster than uninstall/reinstall
- Installation location preserved

### Alternative: PM Install Method (Previous Implementation)

**The previous approach used pm install with backup/restore:**

#### Fix 1: Determine and Preserve Install Location
```java
// Before uninstall, detect install location
Shell.Result pathResult = Shell.cmd("pm path " + packageName).exec();
String apkPath = pathResult.getOut().get(0).replace("package:", "");
boolean isPrivateSpace = apkPath.contains("/private/") || 
                         detectPrivateSpaceFromPath(apkPath);
boolean isInternalStorage = !apkPath.contains("/mnt/") && 
                           !apkPath.contains("/sdcard/");

// After uninstall, install with location hint
String installLocationFlag = "";
if (isInternalStorage) {
    installLocationFlag = "--install-location 1"; // Force internal
}
Shell.cmd("pm install -d -r " + installLocationFlag + " \"" + apkPath + "\"").exec();
```

#### Fix 2: Better UID Handling
```java
// The UID changing is expected - but ensure backup/restore accounts for it
// Current code already handles this correctly (lines 278-290)
// Just ensure chown happens with new UID, which it does
```

#### Fix 3: Add User Awareness
```java
// Detect which user the app was installed for
Shell.Result userResult = Shell.cmd("pm list packages --user all " + packageName).exec();
// Parse user ID from output
// Reinstall for same user:
pm install -d -r --user <userId> <path>
```

#### Fix 4: Document Limitations
Update documentation to explain:
- Some data loss is inevitable with signature changes
- Apps that encrypt data with signing keys will lose data regardless
- Runtime permissions will be reset (Android behavior)
- Installation location may change (document workaround)

## Implementation Plan

1. **Add install location detection** before uninstall
2. **Preserve user context** during reinstall
3. **Add --install-location flag** to pm install command
4. **Improve error messages** to explain why data loss occurs
5. **Update documentation** with limitations and expectations
6. **Add logging** to help debug install location issues

## Conclusion

**Filesystem APK replacement is NOT recommended** because:
- Android's security model will prevent apps from running
- Too many edge cases and compatibility issues
- Doesn't actually solve the signature mismatch problem

**Improving the current pm install approach IS recommended** because:
- Works within Android's security model
- Addresses the real issues (install location, user context)
- More maintainable and reliable
- Better error handling possible

The core issue is that **signature mismatches are inherently problematic** - some data loss is unavoidable when the signing key changes. The best we can do is:
1. Preserve as much as possible (app data, external data)
2. Install to correct location
3. Maintain correct user context
4. Clearly communicate limitations to users
