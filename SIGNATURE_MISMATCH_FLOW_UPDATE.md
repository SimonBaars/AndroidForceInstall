# Signature Mismatch Flow Update

## Problem Statement

Previously, packages with incompatible signing (signature mismatch) would fall into the corrupted flow when the app was not reported as installed by PackageManager, resulting in a full uninstall and install that does not preserve data.

## Solution

Modified the installation flow so that **all signature mismatch cases attempt direct APK replacement first**, regardless of what PackageManager reports about the installation status.

## New Flow

### Scenario 1: Signature Mismatch (Regardless of Install Status)

When `pm install` fails with a signature mismatch error:

1. **Detect signature mismatch** - Check for error messages containing:
   - `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
   - `signatures do not match`

2. **Skip installation status check** - Do NOT check if app is installed via PackageManager

3. **Proceed directly to APK replacement**:
   - Find APK location with `pm path`
   - Force-stop the app
   - Replace APK file on filesystem
   - Set permissions (chmod, chown, restorecon)
   - Register with `pm install` on the replaced APK

4. **Fallback if `pm path` fails**:
   - If APK location cannot be found
   - Fall back to uninstall + install approach
   - This handles cases where signature mismatch was detected but app isn't actually installed

### Scenario 2: Non-Signature-Mismatch Errors

When `pm install` fails with a non-signature-mismatch error:

1. **Check if app is installed**

2. **If NOT installed**:
   - Treat as corrupted installation
   - Run cleanup (uninstall + install)

3. **If IS installed**:
   - Report the error to user
   - Do NOT attempt APK replacement

### Scenario 3: Successful Install

When `pm install` succeeds:
- Installation complete, no further action needed

## Key Changes

### Before (Original Code)

```java
if (packageName != null) {
    // Check if app is installed
    boolean isAppInstalled = checkIfInstalled();
    
    if (!isAppInstalled) {
        // Uninstall + install (even for signature mismatch)
        cleanup();
    } else if (!isSignatureMismatch) {
        // Report error
    } else {
        // Direct APK replacement (only if installed AND signature mismatch)
        replaceAPK();
    }
}
```

### After (Updated Code)

```java
if (packageName != null) {
    if (isSignatureMismatch) {
        // ALWAYS try direct APK replacement for signature mismatch
        // Skip the "is installed" check
        replaceAPK(); // with fallback if pm path fails
    } else {
        // For non-signature errors, check install status
        boolean isAppInstalled = checkIfInstalled();
        
        if (!isAppInstalled) {
            // Corrupted installation - cleanup
            cleanup();
        } else {
            // Report error
        }
    }
}
```

## Benefits

✅ **Preserves data for all signature mismatch cases** - Even when PackageManager doesn't report the app as installed

✅ **Handles partial/corrupted installations** - If files exist on disk but PackageManager doesn't know about them, direct replacement can fix this

✅ **Maintains backward compatibility** - Non-signature-mismatch errors still use the same logic as before

✅ **Graceful fallback** - If `pm path` fails (truly no app installed), falls back to uninstall + install

## Testing Recommendations

Test the following scenarios:

1. **Signature mismatch with app installed**:
   - Should use direct APK replacement ✅
   - Data preserved ✅

2. **Signature mismatch with app NOT in PackageManager but files exist**:
   - Should attempt direct APK replacement ✅
   - If `pm path` succeeds, replace APK ✅
   - Data preserved ✅

3. **Signature mismatch with no app or files**:
   - Should attempt direct APK replacement
   - `pm path` fails
   - Falls back to uninstall + install ✅

4. **Non-signature error with app NOT installed**:
   - Should use cleanup flow (uninstall + install) ✅

5. **Non-signature error with app installed**:
   - Should report error ✅

## Code Location

- **File**: `app/src/main/java/com/simonbaars/androidforceinstall/MainActivity.java`
- **Lines**: 223-327 (error handling logic)
- **Changes**: Restructured if/else blocks to prioritize signature mismatch handling

## Lines Changed

- **Added**: ~21 lines
- **Modified**: ~35 lines  
- **Net impact**: +21 lines total

## Related Documentation

- See `FIX_CORRUPTED_INSTALLATION.md` for corrupted installation handling
- See `SIGNATURE_MISMATCH_FIXES.md` for direct APK replacement approach
- See `DIRECT_APK_REPLACEMENT_IMPLEMENTATION.md` for implementation details
