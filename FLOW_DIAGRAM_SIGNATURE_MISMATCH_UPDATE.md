# Installation Flow Diagram - After Signature Mismatch Update

```
┌─────────────────────────────────────────┐
│    pm install -d -r --user 0 <apk>     │
└─────────────────┬───────────────────────┘
                  │
       ┌──────────┴──────────┐
       │                     │
   SUCCESS?              FAILED?
       │                     │
       ▼                     ▼
   ┌───────┐        ┌─────────────────────┐
   │ DONE! │        │ Extract package name │
   └───────┘        │ Check error type     │
                    └──────────┬───────────┘
                               │
                  ┌────────────┴────────────┐
                  │                         │
          SIGNATURE MISMATCH?       OTHER ERROR?
                  │                         │
                  ▼                         ▼
         ┌──────────────────┐    ┌──────────────────┐
         │ Direct APK       │    │ Check if app is  │
         │ Replacement      │    │ installed        │
         │ (ALWAYS)         │    └────────┬─────────┘
         └────────┬─────────┘             │
                  │              ┌────────┴─────────┐
                  │              │                  │
                  │         INSTALLED?         NOT INSTALLED?
                  │              │                  │
                  │              ▼                  ▼
                  │      ┌───────────────┐  ┌──────────────┐
                  │      │ Report Error  │  │ Cleanup +    │
                  │      │ to User       │  │ Fresh Install│
                  │      └───────────────┘  └──────┬───────┘
                  │                                 │
                  │                                 ▼
                  │                          ┌──────────────┐
                  │                          │ pm uninstall │
                  │                          │ pm install   │
                  │                          └──────┬───────┘
                  │                                 │
                  │                                 ▼
                  │                            ┌─────────┐
                  │                            │ SUCCESS │
                  │                            │ or FAIL │
                  │                            └─────────┘
                  │
                  ▼
         ┌──────────────────┐
         │ pm path <package>│
         └────────┬─────────┘
                  │
       ┌──────────┴──────────┐
       │                     │
    SUCCESS?              FAILED?
       │                     │
       ▼                     ▼
┌──────────────┐      ┌──────────────────┐
│ Parse path   │      │ Fallback:        │
│ Force-stop   │      │ Uninstall +      │
│ Replace APK  │      │ Install          │
│ Set perms    │      └────────┬─────────┘
│ restorecon   │               │
└──────┬───────┘               ▼
       │                 ┌──────────────┐
       ▼                 │ pm uninstall │
┌──────────────┐         │ pm install   │
│ pm install   │         └──────┬───────┘
│ <replaced>   │                │
└──────┬───────┘                ▼
       │                  ┌─────────┐
       ▼                  │ SUCCESS │
┌──────────────┐          │ or FAIL │
│ SUCCESS!     │          └─────────┘
│ Data         │
│ Preserved    │
└──────────────┘
```

## Key Flow Paths

### Path 1: Signature Mismatch → Direct Replacement Success
```
pm install fails → Signature mismatch → pm path succeeds → 
Replace APK → pm install on replaced APK → SUCCESS (data preserved)
```

### Path 2: Signature Mismatch → pm path fails → Fallback
```
pm install fails → Signature mismatch → pm path fails → 
Fallback to uninstall + install → SUCCESS (data lost)
```

### Path 3: Non-signature Error → App Not Installed
```
pm install fails → Not signature mismatch → Check installed → 
Not installed → Cleanup + install → SUCCESS
```

### Path 4: Non-signature Error → App Installed
```
pm install fails → Not signature mismatch → Check installed → 
Installed → Report error to user
```

### Path 5: Normal Success
```
pm install succeeds → DONE
```

## Important Notes

1. **Signature mismatch cases ALWAYS attempt direct replacement first**
   - Skips the "is installed" check
   - Goes directly to `pm path`
   - Only falls back if `pm path` fails

2. **Non-signature errors check install status**
   - If not installed: cleanup flow
   - If installed: report error

3. **Fallback is graceful**
   - If `pm path` fails for signature mismatch
   - Falls back to uninstall + install
   - Still completes the installation (even if data is lost)

4. **Data preservation priority**
   - Signature mismatch: Try to preserve data first
   - Other errors: Use appropriate error handling
