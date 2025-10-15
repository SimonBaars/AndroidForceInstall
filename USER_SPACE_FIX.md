# User Space Installation Fix

## Problem
The app was using `pm install` without specifying a user context, which by default installs to private space. However, other operations (like `pm path` and file operations) were checking user space, causing inconsistencies.

## Solution
Added `--user 0` flag to all package manager and activity manager commands to ensure consistent operation in user space (user 0 = primary user).

## Changes Made

### Code Changes (MainActivity.java)
1. **Initial Installation** (line 183): Added `--user 0` to `pm install -d -r`
2. **Corrupted App Cleanup** (lines 250-251): Added `--user 0` to both `pm uninstall` and `pm install`
3. **APK Path Detection** (line 294): Added `--user 0` to `pm path`
4. **Force Stop** (line 343): Added `--user 0` to `am force-stop`
5. **APK Registration** (line 395): Added `--user 0` to `pm install -d -r`

### Documentation Updates
1. **README.md**: Updated to reflect `--user 0` flag usage
2. **ARCHITECTURE.md**: Updated to reflect `--user 0` flag usage

## Impact
- All package manager operations now consistently target user space
- Apps will install, uninstall, and be queried from the same user context
- Fixes the issue where apps were being installed in private space but queried from user space

## Testing
This fix requires testing on a rooted Android device to verify:
- Apps install correctly to user space
- Signature mismatch handling works correctly
- Apps can be found and replaced in the correct location
