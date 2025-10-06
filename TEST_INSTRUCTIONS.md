# 🧪 Notifyr App Testing Instructions

## 📱 Installation & Testing

### 1. Install the APK
```bash
# The APK is located at:
./app/build/outputs/apk/debug/app-debug.apk

# Install via ADB (if device connected):
adb install app/build/outputs/apk/debug/app-debug.apk

# Or transfer to phone and install manually
```

### 2. What Should Work Now ✅

**App Launch:**
- ✅ App should open without crashing
- ✅ Shows bottom navigation with 3 tabs (Dashboard, History, Settings)
- ✅ Dashboard tab should be selected by default

**Dashboard Screen:**
- ✅ Shows permission status card (red if not enabled, green if enabled)
- ✅ Shows 3 statistics cards (Urgent: 0, Normal: 0, Ignored: 0)
- ✅ Shows "Recent Urgent Notifications" section
- ✅ "Enable" button opens notification listener settings

**Navigation:**
- ✅ Bottom tabs should switch between screens
- ✅ History and Settings show placeholder screens

### 3. Testing Steps

1. **Launch Test:**
   - Open the app
   - Verify it doesn't crash
   - Verify you see the dashboard

2. **Permission Test:**
   - Tap "Enable" button on permission card
   - Should open Android notification listener settings
   - Enable "Notifyr" in the list
   - Return to app - card should turn green

3. **Navigation Test:**
   - Tap History tab - should show placeholder
   - Tap Settings tab - should show placeholder
   - Tap Dashboard tab - should return to main screen

### 4. Known Limitations (Prototype Phase)

❌ **Not Yet Implemented:**
- Database operations (Room not initialized)
- Actual notification filtering
- Real statistics counting
- Notification history display
- Settings configuration

✅ **Working Features:**
- App launches and runs
- Basic UI navigation
- Permission checking and setup
- Clean Material 3 design

### 5. If App Still Crashes

**Check these common issues:**

1. **Android Version:** App targets API 34, ensure device is compatible
2. **Permissions:** App needs notification access to function properly
3. **Storage:** Ensure device has enough space

**Debug Steps:**
1. Try uninstalling and reinstalling
2. Clear app data if it was previously installed
3. Check if device supports the minimum SDK (API 24)

### 6. Next Development Phase

Once this basic version works, we can add:
- Database initialization
- Real notification processing
- Complete settings screens
- Notification history
- Smart filtering features

---

**Status:** Basic prototype ready for testing
**Build:** DEBUG APK generated successfully
**Last Updated:** Phase 1 completion
