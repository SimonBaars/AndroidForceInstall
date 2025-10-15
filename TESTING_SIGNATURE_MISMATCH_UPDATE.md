# Testing Guide: Signature Mismatch Update

## Overview

This document describes how to test the updated signature mismatch flow that now always attempts direct APK replacement first, regardless of PackageManager install status.

## What Changed

Previously, signature mismatch cases where the app was not reported as installed by PackageManager would use the uninstall + install flow (losing data). Now, **all** signature mismatch cases attempt direct APK replacement first.

## Test Scenarios

### Test 1: Signature Mismatch - App Fully Installed

**Setup:**
1. Install an app signed with key A (e.g., debug key)
2. Add some data to the app (open it, create preferences, files, etc.)
3. Build the same app with key B (e.g., release key)

**Test Steps:**
1. Open AndroidForceInstall
2. Select the APK signed with key B
3. Tap "Force Install"

**Expected Result:**
- ✅ Status shows: "Signature mismatch detected. Replacing APK directly..."
- ✅ Status shows: "Finding installed APK location..."
- ✅ Status shows: "Replacing APK file(s)..."
- ✅ Status shows: "Registering APK with Package Manager..."
- ✅ Status shows: "APK replaced and registered successfully. App data preserved."
- ✅ All app data is still present
- ✅ App can be launched (may require reboot)

**What's Happening:**
- `pm install` fails with signature mismatch error
- Code detects signature mismatch
- **NEW**: Skips "is installed" check
- Goes directly to APK replacement flow
- `pm path` succeeds (app is installed)
- Replaces APK on filesystem
- Registers with PackageManager
- Data preserved ✅

---

### Test 2: Signature Mismatch - App Partially Installed/Corrupted

**Setup:**
1. Install an app signed with key A
2. Manually corrupt the installation (requires root):
   ```bash
   # Remove app from PackageManager but leave files
   adb shell
   su
   pm uninstall -k com.example.app  # keeps data
   # Or manually delete some package manager metadata
   ```
3. Build the same app with key B

**Test Steps:**
1. Open AndroidForceInstall
2. Select the APK signed with key B
3. Tap "Force Install"

**Expected Result (Path A - pm path succeeds):**
- ✅ Status shows: "Signature mismatch detected. Replacing APK directly..."
- ✅ Status shows: "Finding installed APK location..."
- ✅ `pm path` finds the APK files still on disk
- ✅ Proceeds with direct APK replacement
- ✅ Data preserved if files still exist

**Expected Result (Path B - pm path fails):**
- ✅ Status shows: "Signature mismatch detected. Replacing APK directly..."
- ✅ Status shows: "Finding installed APK location..."
- ✅ Status shows: "Could not find installed APK location. Trying cleanup and fresh install..."
- ✅ Falls back to uninstall + install
- ✅ App installs successfully (data may be lost)

**What's Happening:**
- `pm install` fails with signature mismatch error
- Code detects signature mismatch
- **NEW**: Skips "is installed" check
- Goes directly to APK replacement flow
- If `pm path` finds files → replace them
- If `pm path` fails → fall back to cleanup

---

### Test 3: Signature Mismatch - App Not Installed At All

**Setup:**
1. Make sure the app is completely uninstalled
2. Create an APK signed with key B

**Test Steps:**
1. Try to install with normal `pm install` first to confirm signature mismatch error occurs
2. Open AndroidForceInstall
3. Select the APK
4. Tap "Force Install"

**Expected Result:**
- ✅ Initial `pm install` may succeed (no conflict) OR fail with signature mismatch
- If fails with signature mismatch:
  - ✅ Status shows: "Signature mismatch detected. Replacing APK directly..."
  - ✅ Status shows: "Finding installed APK location..."
  - ✅ `pm path` fails (app not installed)
  - ✅ Status shows: "Could not find installed APK location. Trying cleanup and fresh install..."
  - ✅ Falls back to uninstall + install
  - ✅ App installs successfully

**What's Happening:**
- In a true "not installed" case, initial `pm install` may succeed
- If signature mismatch error occurs anyway (edge case)
- **NEW**: Goes to APK replacement
- `pm path` fails
- Falls back to cleanup + install

---

### Test 4: Non-Signature Error - App Not Installed (Regression Test)

**Setup:**
1. Make sure test app is not installed
2. Create a corrupted installation scenario (leftover files but not in PackageManager)

**Test Steps:**
1. Open AndroidForceInstall
2. Select the APK
3. Tap "Force Install"

**Expected Result:**
- ✅ Initial `pm install` fails with non-signature error
- ✅ Code checks: NOT a signature mismatch
- ✅ Code checks: App is NOT installed
- ✅ Status shows: "App not installed or corrupted. Cleaning up and installing..."
- ✅ Runs uninstall + install
- ✅ App installs successfully

**What's Happening:**
- **UNCHANGED**: Non-signature errors still check install status first
- If not installed → use cleanup flow
- This is the existing behavior, should still work

---

### Test 5: Non-Signature Error - App Installed (Regression Test)

**Setup:**
1. Install an app
2. Try to install it again with some modification that causes a non-signature error

**Test Steps:**
1. Open AndroidForceInstall
2. Select the APK
3. Tap "Force Install"

**Expected Result:**
- ✅ Initial `pm install` fails with non-signature error
- ✅ Code checks: NOT a signature mismatch
- ✅ Code checks: App IS installed
- ✅ Status shows: "Installation failed: <error message>"
- ✅ Error reported to user
- ✅ No APK replacement attempted

**What's Happening:**
- **UNCHANGED**: Non-signature errors with installed app just report error
- Don't attempt APK replacement
- This is the existing behavior, should still work

---

## Quick Test Procedure

If you don't have time for comprehensive testing, here's a quick test:

### Quick Test: Basic Signature Mismatch

1. Install the AndroidForceInstall app itself (built with debug key)
2. Build AndroidForceInstall with a release key
3. Use the debug version to try to install the release version
4. Should see signature mismatch → direct APK replacement
5. May need to reboot for it to work

## Key Indicators of Success

### ✅ Success Indicators

1. **Signature mismatch detected**: Message appears when signature mismatch occurs
2. **No "is installed" check for signature mismatch**: Goes directly to "Finding installed APK location"
3. **Graceful fallback**: If `pm path` fails, falls back to cleanup + install
4. **Data preserved**: When APK replacement succeeds, all app data remains intact

### ❌ Failure Indicators

1. Signature mismatch cases go to "App not installed or corrupted. Cleaning up and installing..." immediately
2. Data is lost after signature mismatch installation
3. App crashes or fails to install

## Known Limitations

1. **Reboot may be required**: Even with successful APK replacement, device may need reboot for app to launch
2. **Split APKs**: Only base.apk is replaced, split APKs may not work correctly
3. **SELinux issues**: Some devices may have strict SELinux policies that prevent file replacement
4. **Android version dependent**: May not work on all Android versions

## Debugging Tips

### Check Logs

Enable verbose logging in MainActivity.java:
```java
Shell.enableVerboseLogging = true;
```

### Check APK Path

Manually verify if `pm path` works:
```bash
adb shell
su
pm path com.example.app
# Should output: package:/data/app/com.example.app-xxx/base.apk
```

### Check Signature Mismatch Error

Test if signature mismatch error occurs:
```bash
adb shell
su
pm install -d -r /path/to/different-signature.apk
# Should output error containing "signatures do not match" or "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
```

## Reporting Issues

If the new flow doesn't work as expected, please provide:

1. Android version
2. Device/emulator details
3. Full error message from status text
4. Whether `pm path` command works manually
5. Logcat output (if available)
