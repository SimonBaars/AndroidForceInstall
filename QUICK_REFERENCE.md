# Quick Reference: Signature Mismatch

## ❓ What's Happening?

You're seeing an error like:
- `signatures do not match`
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

This means the new APK is signed with a **different cryptographic key** than the installed app.

## ✅ BEST Solution: Re-sign the APK

**If you have the original signing key:**

```bash
# Step 1: Remove old signature
zip -d your-app.apk META-INF/\*

# Step 2: Sign with original key
apksigner sign --ks your-keystore.jks your-app.apk

# Step 3: Install normally
adb install your-app.apk
```

**Result**: ✅ No data loss, no permission resets, no issues!

## ⚠️ Fallback: Use This App (Uninstall/Reinstall)

**If you DON'T have the original key:**

1. Use AndroidForceInstall to install the APK
2. The app will automatically:
   - ✅ Backup your data
   - ✅ Uninstall old app
   - ✅ Install new app
   - ✅ Restore your data

**Result**: ⚠️ Most data preserved, but:
- ❌ Permissions WILL be reset (must re-grant)
- ❌ Encrypted data MAY be lost
- ❌ Keystore data WILL be lost

## 🚫 What's NOT Possible

- ❌ Force new APK to use old signature
- ❌ Avoid uninstall if signatures don't match
- ❌ Preserve permissions across signature change
- ❌ Decrypt data encrypted with old signature

## 📖 More Information

- **Full guide**: [SIGNATURE_MISMATCH_GUIDE.md](SIGNATURE_MISMATCH_GUIDE.md)
- **Technical details**: [SIGNATURE_MISMATCH_FIXES.md](SIGNATURE_MISMATCH_FIXES.md)
- **Why filesystem replacement fails**: [APK_REPLACEMENT_DISCUSSION.md](APK_REPLACEMENT_DISCUSSION.md)

## 💡 Key Takeaway

**Signatures are cryptographic and cannot be forged.**
- Best option: Re-sign with original key
- Only alternative: Uninstall and reinstall
- Some data loss is inevitable without the original key
