# Flow Diagram: Corrupted Installation Fix

## Before Fix (BROKEN)

```
┌─────────────────────────┐
│  pm install -d -r       │
└────────┬────────────────┘
         │
         ├─ Success? ──→ Done ✓
         │
         ├─ Failed?
         └────────────────────────┐
                                  │
                    ┌─────────────▼────────────────┐
                    │ Check for signature mismatch │
                    │ - INSTALL_FAILED_...        │
                    │ - signatures do not match    │
                    └─────────────┬────────────────┘
                                  │
                      ┌───────────┴───────────┐
                      │                       │
                  YES │                       │ NO
                      ▼                       ▼
          ┌──────────────────┐    ┌─────────────────┐
          │ Extract pkg name │    │  Report error   │
          └────────┬─────────┘    └─────────────────┘
                   │
          ┌────────▼─────────┐
          │ Check installed? │
          └────────┬─────────┘
                   │
          ┌────────┴────────┐
          │                 │
       NO │                 │ YES
          ▼                 ▼
  ┌──────────────┐   ┌────────────────┐
  │ uninstall +  │   │ Direct APK     │
  │ install      │   │ replacement    │
  └──────────────┘   └────────────────┘

PROBLEM: Corrupted installations fail with OTHER errors,
         not signature mismatch, so they fall through
         to "Report error" and never get cleaned up! ❌
```

## After Fix (WORKING)

```
┌─────────────────────────┐
│  pm install -d -r       │
└────────┬────────────────┘
         │
         ├─ Success? ──→ Done ✓
         │
         ├─ Failed?
         └────────────────────────┐
                                  │
                    ┌─────────────▼────────────────┐
                    │ Extract package name first   │
                    │ (from APK, not from system)  │
                    └─────────────┬────────────────┘
                                  │
                                  ├─ Can't extract? ──→ Report error
                                  │
                                  ├─ Extracted!
                                  ▼
                    ┌─────────────────────────────┐
                    │ Check if app is installed   │
                    │ (PackageManager.getInfo)    │
                    └─────────────┬───────────────┘
                                  │
                      ┌───────────┴───────────┐
                      │                       │
                  NO  │                       │ YES
                      ▼                       ▼
          ┌────────────────────┐   ┌─────────────────────┐
          │ NOT INSTALLED      │   │ IS INSTALLED        │
          │                    │   │                     │
          │ Could be:          │   │ Check error type:   │
          │ 1. Fresh install   │   │ - Signature? ──────┐│
          │ 2. CORRUPTED! ✓    │   │ - Other? ──────────┤│
          └────────┬───────────┘   └──────────┬──────────┘
                   │                           │
                   ▼                  ┌────────┴─────────┐
          ┌────────────────┐          │                  │
          │ ALWAYS clean   │    Signature          Other error
          │ up + install:  │          │                  │
          │                │          ▼                  ▼
          │ pm uninstall   │   ┌──────────────┐   ┌────────────┐
          │ pm install     │   │ Direct APK   │   │ Report     │
          └────────────────┘   │ replacement  │   │ error      │
                               │ (preserve    │   └────────────┘
                               │  data)       │
                               └──────────────┘

SOLUTION: Extract package name and check installation
          status FIRST, for ALL failures. This catches
          corrupted installations! ✓
```

## Key Differences

### Before Fix
1. ❌ Only checked signature mismatch errors
2. ❌ Other errors (from corrupted installations) fell through
3. ❌ Corrupted installations never got cleaned up
4. ❌ User got "Could not find installed APK location" error

### After Fix
1. ✅ Extracts package name for ALL failures
2. ✅ Checks installation status for ALL failures
3. ✅ Cleans up ANY uninstalled app (fresh or corrupted)
4. ✅ Only uses APK replacement when app IS installed AND signature mismatch

## Example Scenarios

### Scenario 1: Corrupted Installation (FIXED!)
```
User Input: Install com.example.app
System State: Package partially exists (corrupted)

Before Fix:
  pm install → "Package already exists" (not signature error)
  → Report error ❌

After Fix:
  pm install → fails
  → Extract: com.example.app
  → Check installed: NO (PackageManager doesn't see it)
  → Clean up: pm uninstall com.example.app
  → Install: pm install
  → Success! ✅
```

### Scenario 2: Signature Mismatch (UNCHANGED)
```
User Input: Install com.example.app with signature B
System State: com.example.app installed with signature A

Both Before and After:
  pm install → "signatures do not match"
  → Extract: com.example.app
  → Check installed: YES
  → Detect signature mismatch: YES
  → Direct APK replacement
  → Success with data preserved! ✅
```

### Scenario 3: Fresh Install (UNCHANGED)
```
User Input: Install com.example.app
System State: App not installed

Both Before and After:
  pm install → Success! ✅
  (no special handling needed)
```

## Code Structure

### Old Logic Flow
```java
if (!result.isSuccess()) {
    if (isSignatureMismatch()) {
        extractPackageName();
        if (!isInstalled()) {
            cleanup();
        } else {
            replace();
        }
    }
    // Other errors fall through to generic error handling
}
```

### New Logic Flow
```java
if (!result.isSuccess()) {
    extractPackageName();  // FIRST!
    
    if (packageName != null) {
        if (!isInstalled()) {
            cleanup();  // Works for ANY failure!
        } else if (isSignatureMismatch()) {
            replace();
        } else {
            reportError();
        }
    }
}
```

## Benefits

1. **Handles Corrupted Installations** ✅
   - Cleans up leftover files
   - Installs fresh
   - No more "Could not find installed APK location" error

2. **Broader Error Coverage** ✅
   - Works for any installation failure
   - Not limited to signature mismatches

3. **Better Error Categorization** ✅
   - Distinguishes between different failure modes
   - Provides appropriate action for each case

4. **No Breaking Changes** ✅
   - All existing scenarios continue to work
   - Only adds new functionality

5. **Minimal Code Changes** ✅
   - Just restructured the logic flow
   - ~20 lines changed/moved
