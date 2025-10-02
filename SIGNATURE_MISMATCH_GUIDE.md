# Signature Mismatch: Understanding and Solutions

## The Problem

When you try to install an APK over an existing app and see errors like:
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
- `signatures do not match`
- `Existing package signatures do not match newer version`

This means **the new APK was signed with a different cryptographic key than the installed app**.

## Why Does This Happen?

Every Android APK is signed with a private key. This signature:
- Proves the APK came from a specific developer
- Prevents unauthorized modifications
- Ensures app authenticity

When signatures don't match, Android blocks the installation to protect users from:
- Malicious apps impersonating legitimate ones
- Unauthorized modifications to installed apps
- Security vulnerabilities

## The PROPER Solution: Re-sign the APK

**If you have access to the original signing key**, re-sign the new APK:

### Using apksigner (Android SDK)
```bash
# Remove old signature
zip -d your-app.apk META-INF/\*

# Sign with original key
apksigner sign --ks your-keystore.jks --ks-key-alias your-alias your-app.apk

# Verify signature
apksigner verify --verbose your-app.apk
```

### Using jarsigner (Java JDK)
```bash
# Remove old signature
zip -d your-app.apk META-INF/\*

# Sign with original key
jarsigner -verbose -keystore your-keystore.jks your-app.apk your-alias

# Verify signature
jarsigner -verify -verbose -certs your-app.apk
```

### When You DON'T Have the Original Key

If you don't have the original signing key:
- **You CANNOT avoid the signature mismatch**
- There is NO way to "force" an APK to match a different signature
- The only option is **uninstall and reinstall** (which this app handles)

## What This App Does (When Re-signing Is Not Possible)

When AndroidForceInstall detects a signature mismatch, it:

1. ✅ **Backs up app data** (internal and external storage)
2. ✅ **Detects install location** (internal vs external)
3. ✅ **Detects user context** (main user vs work profile)
4. ✅ **Uninstalls the old app**
5. ✅ **Installs the new app** (preserving location and user)
6. ✅ **Restores app data** (with correct ownership)

## What CANNOT Be Preserved

Due to Android's security model, these limitations are **UNAVOIDABLE**:

### ⚠️ Runtime Permissions Reset
**Why**: Android resets permissions when an app is uninstalled
**Impact**: You must re-grant permissions (camera, location, storage, etc.)
**Workaround**: None - this is by design for security

### ⚠️ Encrypted Data Loss
**Why**: Some apps encrypt data using their signing certificate
**Impact**: Encrypted data cannot be decrypted with a different signature
**Workaround**: None - data is cryptographically tied to the signature

### ⚠️ App-Specific Security Tokens
**Why**: Authentication tokens may be tied to app signature
**Impact**: You may need to log in again
**Workaround**: None - re-authentication required

### ⚠️ Keystore Data
**Why**: Android Keystore entries are tied to app signature
**Impact**: Stored passwords/keys will be lost
**Workaround**: None - must re-create keystore entries

## Decision Flow: What Should You Do?

```
Do you have the original signing key?
│
├─ YES → Re-sign the APK with the original key
│        This is the BEST solution - no data loss!
│
└─ NO → Is preserving data critical?
         │
         ├─ YES → ⚠️ WARNING: Some data WILL be lost
         │        Consider alternatives:
         │        - Contact original developer for re-signed APK
         │        - Export app data through app's backup feature
         │        - Accept data loss and use AndroidForceInstall
         │
         └─ NO → Use AndroidForceInstall for uninstall/reinstall
                  Most data will be preserved
```

## Common Scenarios

### Scenario 1: Debug vs Release Builds
**Problem**: Debug APK has different signature than release APK
**Solution**: 
- Use the same keystore for both builds, OR
- Use AndroidForceInstall to switch between them (expect permission resets)

### Scenario 2: Different Developer Versions
**Problem**: Community-built APK vs official APK
**Solution**:
- If you trust the community build, use AndroidForceInstall
- Expect to re-grant permissions
- Be aware some data may be lost

### Scenario 3: Modified/Patched APKs
**Problem**: Patched APK (e.g., with Lucky Patcher) has different signature
**Solution**:
- Use AndroidForceInstall for installation
- Be aware this may violate terms of service
- Expect permission resets and possible data loss

### Scenario 4: Downgrading Apps
**Problem**: Want to install older version, but signature changed
**Solution**:
- If signatures match, use `pm install -d -r` (this app does this)
- If signatures don't match, use AndroidForceInstall's uninstall/reinstall
- Older version may have less secure signature algorithm

## Frequently Asked Questions

### Q: Can I force the new APK to use the old signature?
**A**: No. Signatures are cryptographic and cannot be forged or transferred.

### Q: Why does the app lose data even with backup/restore?
**A**: Most data IS preserved. Only data encrypted with the signing key is lost.

### Q: Can I backup permissions before uninstall?
**A**: Technically yes (with root), but Android will reset them anyway for security.

### Q: Will this void my warranty?
**A**: Using root and forcing installations may void warranties. Check your device terms.

### Q: Is this legal?
**A**: Installing apps with different signatures may violate app terms of service, but is not illegal. Use responsibly.

### Q: Why not just replace the APK file on disk?
**A**: Android's runtime verification would prevent the app from launching. See [APK_REPLACEMENT_DISCUSSION.md](APK_REPLACEMENT_DISCUSSION.md) for technical details.

## Best Practices

1. **Always backup critical data** through the app's own export/backup feature
2. **Use original signing keys** when possible to avoid signature mismatches
3. **Document your keystores** and keep them secure
4. **Test on non-critical devices** before deploying to production
5. **Verify APK sources** to ensure you're not installing malware

## Technical References

For more technical details, see:
- [SIGNATURE_MISMATCH_FIXES.md](SIGNATURE_MISMATCH_FIXES.md) - Implementation details
- [APK_REPLACEMENT_DISCUSSION.md](APK_REPLACEMENT_DISCUSSION.md) - Why filesystem replacement doesn't work
- [ARCHITECTURE.md](ARCHITECTURE.md) - Overall app architecture

## Summary

**The Bottom Line:**
- ✅ **Best solution**: Re-sign APK with original key (if you have it)
- ⚠️ **Only alternative**: Uninstall and reinstall (what this app does)
- ❌ **Not possible**: Force new APK to match old signature
- ⚠️ **Expect**: Permission resets and possible encrypted data loss

Signature mismatches are a security feature, not a bug. This app does the best possible job of preserving data when re-signing is not an option.
