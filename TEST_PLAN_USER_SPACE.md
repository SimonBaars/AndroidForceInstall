# Test Plan: User Space Installation Fix

## Prerequisites
- Android device with root access
- ADB debugging enabled
- AndroidForceInstall app installed
- Test APK files (both signed and unsigned versions)

## Test Cases

### Test 1: Fresh Installation to User Space
**Objective**: Verify that new apps install to user space, not private space

**Steps**:
1. Select an APK file that is NOT currently installed
2. Tap "Force Install"
3. Wait for installation to complete
4. Run: `adb shell pm list packages --user 0 | grep <package>`
5. Run: `adb shell pm path --user 0 <package>`

**Expected Result**:
- Installation succeeds
- Package is listed in user 0
- APK path is in `/data/app/` (user space), not `/data/app/private/`

### Test 2: Signature Mismatch with Direct APK Replacement
**Objective**: Verify signature mismatch handling works with user space

**Steps**:
1. Install an app signed with key A
2. Create the same app signed with key B
3. Run: `adb shell pm path --user 0 <package>` (note the path)
4. Use AndroidForceInstall to install version signed with key B
5. Wait for the signature mismatch detection and replacement
6. Run: `adb shell pm path --user 0 <package>` (verify path is the same)
7. Launch the app to verify it works

**Expected Result**:
- Signature mismatch is detected
- APK is replaced directly
- App is registered with PackageManager
- App data is preserved
- App path remains the same
- App launches successfully

### Test 3: Corrupted Installation Cleanup
**Objective**: Verify corrupted app cleanup works in user space

**Steps**:
1. Manually corrupt an app by deleting its APK: `adb shell rm /data/app/<package>/base.apk`
2. Try to launch the app (should fail)
3. Use AndroidForceInstall to reinstall the app
4. Run: `adb shell pm list packages --user 0 | grep <package>`

**Expected Result**:
- Corrupted installation is detected
- Old package is uninstalled from user 0
- New package is installed to user 0
- Installation succeeds

### Test 4: Force Stop in User Space
**Objective**: Verify force-stop targets the correct user

**Steps**:
1. Install an app using AndroidForceInstall
2. Launch the app
3. Trigger a signature mismatch replacement (install different signature)
4. Monitor logcat during installation: `adb logcat | grep force-stop`

**Expected Result**:
- App is force-stopped from user 0
- No errors about app not found

### Test 5: Multi-User Environment (if available)
**Objective**: Verify installations don't interfere with other user profiles

**Steps**:
1. Set up a work profile or secondary user (if supported)
2. Install an app in the main user using AndroidForceInstall
3. Check that it's NOT installed in the work profile
4. Run: `adb shell pm list packages --user all | grep <package>`

**Expected Result**:
- App is only listed for user 0
- App is not installed in other profiles

## Verification Commands

```bash
# Check if package is in user 0
adb shell pm list packages --user 0 | grep <package>

# Get package path for user 0
adb shell pm path --user 0 <package>

# Verify app data location
adb shell ls -la /data/data/<package>

# Check running processes for user 0
adb shell ps -u 0 | grep <package>

# List all packages in all users
adb shell pm list packages --user all | grep <package>
```

## Success Criteria
- All commands consistently use `--user 0` flag
- Apps install to user space, not private space
- Signature mismatch handling preserves app data
- No errors about packages not found
- Apps launch and function correctly after installation

## Known Limitations
- Testing requires physical device with root access
- Cannot test in emulator without root
- Split APK handling may still have issues (out of scope for this fix)
