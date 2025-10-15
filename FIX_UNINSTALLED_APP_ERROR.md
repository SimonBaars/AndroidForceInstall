# Fix: "Could not find installed APK location" Error

## Problem

The app was failing with the error "could not find installed APK location" when trying to install certain apps. This occurred because the code was attempting to find the APK installation path for apps that were not actually installed on the device.

## Root Cause

The flow was:
1. Attempt `pm install -d -r` for the APK
2. Installation fails with a signature mismatch-like error
3. Code assumes app is installed and tries to find APK location with `pm path <package>`
4. `pm path` fails because app is not installed → Error: "Could not find installed APK location"

**Issue**: The signature mismatch detection logic was triggering even for uninstalled apps, causing the code to attempt direct APK replacement when there was nothing to replace.

## Solution

Added a check to verify if the app is actually installed before attempting direct APK replacement:

```java
// Check if the app is currently installed
boolean isAppInstalled = false;
try {
    getPackageManager().getPackageInfo(packageName, 0);
    isAppInstalled = true;
} catch (PackageManager.NameNotFoundException e) {
    // App is not installed
}

if (!isAppInstalled) {
    // Fall back to uninstall/reinstall approach (which will just install)
    Shell.Result forceInstallResult = Shell.cmd(
            "pm uninstall " + packageName,
            "pm install -d -r \"" + apkPath + "\""
    ).exec();
    // Handle result...
    return;
}

// If we reach here, app IS installed, proceed with direct APK replacement...
```

## How It Works Now

### Scenario 1: App is NOT installed
1. Initial `pm install -d -r` fails
2. Signature mismatch detection triggers
3. **NEW:** Check if app is installed → NO
4. Fall back to uninstall + install approach
   - Uninstall is a no-op (app doesn't exist)
   - Install proceeds normally
5. Success!

### Scenario 2: App IS installed with signature mismatch
1. Initial `pm install -d -r` fails
2. Signature mismatch detection triggers
3. **NEW:** Check if app is installed → YES
4. Proceed with direct APK replacement (existing behavior)
   - Find APK location with `pm path`
   - Force-stop app
   - Replace APK file
   - Register with PackageManager
5. Success with data preserved!

### Scenario 3: Normal installation (no signature mismatch)
1. Initial `pm install -d -r` succeeds
2. Done! (existing behavior unchanged)

## Benefits

✅ Works with both installed and uninstalled apps (as required)
✅ No more "could not find installed APK location" error for uninstalled apps
✅ Preserves existing behavior for signature mismatch with installed apps
✅ Minimal code changes (40 lines added)
✅ No breaking changes to existing functionality

## Testing Recommendations

Test the following scenarios:

1. **Uninstalled app**: Install an APK that is not currently installed → Should work without errors
2. **Installed app, same signature**: Install an APK over existing app with same signature → Should work normally
3. **Installed app, different signature**: Install an APK over existing app with different signature → Should use direct APK replacement and preserve data
4. **Invalid APK**: Try to install a corrupted or invalid APK → Should fail gracefully with appropriate error message

## Code Changes

**File**: `app/src/main/java/com/simonbaars/androidforceinstall/MainActivity.java`

**Lines added**: 40 lines (222-260)

**Changes**:
- Added installation check using `PackageManager.getPackageInfo()`
- Added fallback logic for uninstalled apps
- Added user feedback for the uninstalled app scenario
