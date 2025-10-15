# Test Plan: Corrupted Installation Fix

## Overview
This test plan validates the fix for handling corrupted app installations that prevent APK installation.

## Test Environment
- Android device with root access
- AndroidForceInstall app installed
- Test APK files with various signatures

## Test Cases

### Test Case 1: Corrupted Installation (PRIMARY FIX)
**Objective**: Verify that corrupted installations are properly cleaned up and the app can be installed.

**Setup**:
1. Install a test app (e.g., a simple APK) normally
2. Use root to corrupt the installation:
   ```bash
   # Find the app's package name
   pm list packages | grep <app_name>
   
   # Delete some app files to corrupt it
   pm path <package_name>
   rm <one_of_the_apk_paths>
   ```
3. Verify the app is corrupted:
   - App may crash or not appear in launcher
   - `pm list packages` still shows it OR doesn't show it
   - Trying to install normally fails

**Test Steps**:
1. Open AndroidForceInstall app
2. Select the APK file for the corrupted app
3. Tap "Force Install"
4. Observe the status messages

**Expected Results**:
- ✅ Status shows: "App not installed or corrupted. Cleaning up and installing..."
- ✅ Installation succeeds
- ✅ App is properly installed and works
- ✅ No "Could not find installed APK location" error

**Failure Indicators**:
- ❌ "Could not find installed APK location" error appears
- ❌ Installation fails with unclear error
- ❌ App is not properly installed after process completes

---

### Test Case 2: Fresh Install (Regression Test)
**Objective**: Verify that normal installations still work correctly.

**Setup**:
1. Ensure test app is NOT installed
2. Have APK file ready

**Test Steps**:
1. Open AndroidForceInstall app
2. Select the APK file
3. Tap "Force Install"

**Expected Results**:
- ✅ Installation succeeds without special handling
- ✅ Status shows "Installation successful"
- ✅ App is properly installed

---

### Test Case 3: Signature Mismatch with Installed App (Regression Test)
**Objective**: Verify that signature mismatch handling with data preservation still works.

**Setup**:
1. Install a test app with signature A
2. Add some data to the app (e.g., create a file, save preferences)
3. Build/obtain the same app with signature B (different key)

**Test Steps**:
1. Open AndroidForceInstall app
2. Select the APK with signature B
3. Tap "Force Install"

**Expected Results**:
- ✅ Status shows: "Signature mismatch detected. Replacing APK directly..."
- ✅ Status shows: "Finding installed APK location..."
- ✅ Status shows: "Replacing APK file(s)..."
- ✅ Status shows: "APK replaced and registered successfully. App data preserved."
- ✅ App's data is still present after installation
- ✅ App works correctly with new signature

---

### Test Case 4: Other Installation Errors (Regression Test)
**Objective**: Verify that other installation errors are reported correctly.

**Setup**:
1. Create a corrupted APK file (e.g., truncate the file)
   ```bash
   dd if=valid.apk of=corrupted.apk bs=1024 count=100
   ```

**Test Steps**:
1. Open AndroidForceInstall app
2. Select the corrupted APK
3. Tap "Force Install"

**Expected Results**:
- ✅ Installation fails with appropriate error message
- ✅ Error message is descriptive (e.g., "Package is corrupt" or similar)
- ✅ No crash or unexpected behavior

---

### Test Case 5: Package Name Extraction Failure
**Objective**: Verify graceful handling when package name cannot be extracted.

**Setup**:
1. Create an invalid APK that looks like an APK but isn't parseable

**Test Steps**:
1. Open AndroidForceInstall app
2. Select the invalid APK
3. Tap "Force Install"

**Expected Results**:
- ✅ Error is reported gracefully
- ✅ No crash
- ✅ UI remains responsive

---

## Manual Testing Procedure

### Quick Test (Simulates Corrupted Installation)
If you cannot create a real corrupted installation, you can test the logic by:

1. **Uninstall the test app completely**
2. **Create some leftover files manually** (requires root):
   ```bash
   # Create a dummy "leftover" 
   mkdir -p /data/data/com.example.testapp
   echo "leftover" > /data/data/com.example.testapp/test.txt
   ```
3. **Try to install the app** - the cleanup logic should handle this gracefully

### Recommended Test Apps
Use simple test apps for testing:
- Create a basic "Hello World" app
- Sign it with different keys for signature mismatch testing
- Easy to install/uninstall repeatedly

## Expected Behavior Summary

| Scenario | App Installed? | Error Type | Behavior |
|----------|----------------|------------|----------|
| Corrupted installation | NO | Any | Clean up + install fresh |
| Fresh install | NO | N/A | Normal install |
| Signature mismatch | YES | Signature error | Direct APK replacement |
| Invalid APK | NO | Parse error | Report error |
| Other error (app installed) | YES | Non-signature | Report error |

## Success Criteria

All test cases must pass for the fix to be considered complete:
- ✅ Test Case 1 (Corrupted installation) - PRIMARY
- ✅ Test Case 2 (Fresh install) - CRITICAL
- ✅ Test Case 3 (Signature mismatch) - CRITICAL
- ✅ Test Case 4 (Invalid APK) - IMPORTANT
- ✅ Test Case 5 (Package extraction failure) - NICE TO HAVE

## Notes for Testers

1. **Root Access Required**: All tests require a rooted Android device
2. **Test on Multiple Android Versions**: If possible, test on Android 7, 10, and 12+
3. **Log Output**: Capture logcat output during testing for debugging: `adb logcat | grep -i "AndroidForceInstall"`
4. **Backup**: Test devices should be backed up before testing

## Known Limitations

- Split APKs (Android App Bundles) may not work correctly in signature mismatch scenarios
- Some custom ROMs may behave differently
- Extremely corrupted installations might require manual cleanup via `adb`
