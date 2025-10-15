# Code Flow Explanation

## Entry Point: Installation Failure Handler

When `pm install -d -r --user 0 <apk>` fails (line 187), the error handling begins.

## Step-by-Step Flow

### 1. Extract Package Name (Lines 190-201)
```java
String packageName = null;
try {
    PackageManager pm = getPackageManager();
    PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
    if (info != null) {
        packageName = info.packageName;
    }
} catch (Exception e) {
    // Failed to get package name
}
```

### 2. Detect Signature Mismatch (Lines 213-221)
```java
boolean isSignatureMismatch = output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || 
    output.contains("signatures do not match") ||
    (output.contains("Existing package") && output.contains("signatures do not match"));

if (isSignatureMismatch) {
    statusText.setText("Signature mismatch detected. Replacing APK directly...");
}
```

### 3. Route Based on Error Type (Lines 223-286)

#### Branch A: Signature Mismatch (Line 228)
```java
if (isSignatureMismatch) {
    // Skip "is installed" check
    // Proceed to line 288 (find APK location)
}
```

#### Branch B: Other Error (Lines 231-286)
```java
else {
    // Check if app is installed
    boolean isAppInstalled = false;
    try {
        getPackageManager().getPackageInfo(packageName, 0);
        isAppInstalled = true;
    } catch (PackageManager.NameNotFoundException e) {
        // App is not installed
    }
    
    if (!isAppInstalled) {
        // Branch B1: Not installed - cleanup flow (lines 242-273)
        // pm uninstall + pm install
        return;
    } else {
        // Branch B2: Installed - report error (lines 274-285)
        // Show error message
        return;
    }
}
```

### 4. Find APK Location (Lines 288-327)
*Only reached for signature mismatch cases*

```java
statusText.setText("Finding installed APK location for " + pkgName + "...");

Shell.Result pathResult = Shell.cmd(
    "pm path --user 0 " + packageName
).exec();

if (!pathResult.isSuccess() || pathResult.getOut().isEmpty()) {
    // Fallback: pm path failed (lines 298-327)
    statusText.setText("Could not find installed APK location. Trying cleanup and fresh install...");
    
    Shell.Result forceInstallResult = Shell.cmd(
        "pm uninstall --user 0 " + packageName,
        "pm install -d -r --user 0 \"" + apkPath + "\""
    ).exec();
    
    // Handle result and return
    return;
}
```

### 5. Parse APK Paths (Lines 329-345)
*Only reached if pm path succeeds*

```java
List<String> installedApkPaths = new java.util.ArrayList<>();
for (String line : pathResult.getOut()) {
    if (line.startsWith("package:")) {
        installedApkPaths.add(line.replace("package:", "").trim());
    }
}

if (installedApkPaths.isEmpty()) {
    statusText.setText("Could not parse APK paths");
    return;
}

String baseApkPath = installedApkPaths.get(0);
```

### 6. Force-Stop App (Lines 358-379)
```java
statusText.setText("Force-stopping " + pkgName + "...");

Shell.Result stopResult = Shell.cmd(
    "am force-stop --user 0 " + packageName
).exec();

Thread.sleep(1000);  // Wait for app to stop
```

### 7. Replace APK File (Lines 381-406)
```java
statusText.setText("Replacing APK file(s)...");

Shell.Result replaceResult = Shell.cmd(
    "cp -f \"" + apkPath + "\" \"" + baseApkPath + "\"",
    "chmod 644 \"" + baseApkPath + "\"",
    "chown system:system \"" + baseApkPath + "\"",
    "restorecon \"" + baseApkPath + "\""
).exec();

if (!replaceResult.isSuccess()) {
    statusText.setText("APK replacement failed: " + error);
    return;
}
```

### 8. Register with PackageManager (Lines 408-436)
```java
statusText.setText("Registering APK with Package Manager...");

Shell.Result registerResult = Shell.cmd(
    "pm install -d -r --user 0 \"" + baseApkPath + "\""
).exec();

if (!registerResult.isSuccess()) {
    // Warning but not failure
    statusText.setText("APK replaced but registration had issues. You may need to reboot.");
} else {
    // Success
    statusText.setText("APK replaced and registered successfully. App data preserved.");
}
```

## Complete Flow Diagrams

### Signature Mismatch Flow

```
Line 187: pm install fails
    ↓
Line 213: Detect signature mismatch = TRUE
    ↓
Line 228: if (isSignatureMismatch) → TRUE
    ↓
Line 288: Find APK location
    ↓
Line 294: pm path --user 0 <package>
    ↓
┌───────────┴───────────┐
│                       │
Path Found?        Path NOT Found?
│                       │
▼                       ▼
Line 329:          Line 298:
Parse path         Fallback:
│                  pm uninstall
▼                  pm install
Line 358:              │
Force-stop             ▼
│                  Line 312:
▼                  Return
Line 381:
Replace APK
│
▼
Line 408:
Register with PM
│
▼
SUCCESS!
Data Preserved ✅
```

### Non-Signature Error Flow

```
Line 187: pm install fails
    ↓
Line 213: Detect signature mismatch = FALSE
    ↓
Line 231: else (not signature mismatch)
    ↓
Line 234: Check if app installed
    ↓
┌───────────┴───────────┐
│                       │
Installed?         NOT Installed?
│                       │
▼                       ▼
Line 274:          Line 242:
Report error       Cleanup:
│                  pm uninstall
▼                  pm install
Return                 │
                       ▼
                   Line 259:
                   Return
```

## Key Decision Points

1. **Line 213**: Is it a signature mismatch?
   - YES → Go to signature mismatch flow
   - NO → Go to other error flow

2. **Line 228**: Signature mismatch branch
   - Skips "is installed" check
   - Goes directly to APK replacement

3. **Line 298**: Can we find APK path?
   - YES → Continue with replacement
   - NO → Fall back to cleanup + install

4. **Line 234**: Is app installed? (non-signature errors only)
   - YES → Report error
   - NO → Cleanup + install

## Return Points

The function has multiple return points:

- **Line 273**: After cleanup + install (non-signature error, not installed)
- **Line 285**: After reporting error (non-signature error, installed)
- **Line 326**: After fallback cleanup + install (signature mismatch, pm path failed)
- **Line 344**: If can't parse APK paths (signature mismatch, parse error)
- **Line 405**: If APK replacement fails (signature mismatch, replacement error)
- **Line 436**: After successful APK replacement (signature mismatch, success)

All return points properly re-enable the UI buttons and provide status feedback.

## Critical Change Summary

The **KEY CHANGE** is at line 228:

**Before:** 
```java
if (packageName != null) {
    boolean isAppInstalled = checkIfInstalled();
    
    if (!isAppInstalled) {
        cleanup();  // ← Signature mismatch cases hit this
    } else if (isSignatureMismatch) {
        replaceAPK();
    }
}
```

**After:**
```java
if (packageName != null) {
    if (isSignatureMismatch) {
        replaceAPK();  // ← Signature mismatch cases hit this first
    } else {
        boolean isAppInstalled = checkIfInstalled();
        if (!isAppInstalled) {
            cleanup();
        } else {
            reportError();
        }
    }
}
```

This ensures signature mismatch cases **always** attempt data-preserving APK replacement first.
