# EmergencyCall — Offline Emergency Alert System (In Progress: Not Ready to Work)

A two-phone emergency system for Android 12+ that works completely offline using Google's Nearby Connections API (Bluetooth/Wi-Fi Direct).

## System Overview

**Patient Phone** — One huge red **EMERGENCY** button. Press and hold for **≥1 second** to trigger an emergency alert.

**Caregiver Phone** — Receives alert, sounds a **loud looping alarm** with vibration, shows full-screen emergency UI until explicitly **STOP/ACKNOWLEDGE** is pressed.

No Internet, no backend, no accounts — direct phone-to-phone communication.

---

## Requirements

| Requirement | Specification |
|------------|---------------|
| **Android Version** | Android 12–15+ (API 31–35) |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **Hardware** | Bluetooth & Wi-Fi Direct support |
| **Permissions** | Location (required for Nearby Connections), Nearby Devices, Post Notifications, Foreground Service |

---

## Installation

### Option 1: Install via ADB (Recommended)

```bash
# Enable USB debugging on both phones
# Connect via USB

# Install on Patient phone
adb install app-debug.apk

# Install on Caregiver phone
adb install app-debug.apk
```

### Option 2: Transfer APK Directly

1. Copy `app-debug.apk` to both phones (via USB, Bluetooth, or local file share)
2. On each phone: **Settings → Security → Install unknown apps** → enable for your file manager
3. Open the APK and tap **Install**

---

## First-Time Setup (Run on BOTH Phones)

1. **Launch the app** — you'll see the **Role Selection** screen
2. **Choose your role:**
   - **Patient** — the person who may need help
   - **Caregiver** — the person who responds
3. **Grant all requested permissions** when prompted:
   - **Location** (required for Nearby Connections scanning)
   - **Nearby Devices** (Android 12+)
   - **Notifications** (for emergency alerts)
   - **Foreground Service** (for persistent alarm)
   - **Exact Alarm** (Android 14+ for alarm scheduling)
4. **Set your name** in Settings → Patient Name / Device Name

---

## Pairing the Two Phones

### On the Patient Phone:

1. Tap **CONNECTION** (bottom nav) → **PAIR NEW DEVICE**
2. The app starts **advertising** (shows "Advertising..." with your device name)
3. Wait for the Caregiver to discover you

### On the Caregiver Phone:

1. Tap **CONNECTION** (bottom nav) → **PAIR NEW DEVICE**
2. Tap **START SEARCH** — scans for nearby Patient devices
3. When the Patient device appears, tap **CONNECT**
4. Both phones show a **pairing confirmation** — accept on both

### Success Indicators:

- **Patient:** Shows "CONNECTED" with Caregiver name in green
- **Caregiver:** Shows "CONNECTED" with Patient name in green
- Both show a heartbeat indicator (animated pulse) when connected

---

## Testing the Emergency Flow

### 1. Verify Connection First

- Both phones should show **CONNECTED** status
- Heartbeat animation should be pulsing
- If disconnected: tap **RE-PAIR DEVICE** or go to Connection screen

### 2. Trigger Emergency (Patient Phone)

1. Go to **HOME** screen (Patient mode)
2. **Press and HOLD the red EMERGENCY button**
3. Hold for **at least 1 full second** — the button visual changes while pressed
4. Release → "Emergency sent" appears with event ID
5. Status changes to "Waiting for caregiver..."

### 3. Receive & Respond (Caregiver Phone)

**IMMEDIATELY upon emergency trigger:**
- **Loud alarm sounds** (loops continuously)
- **Strong vibration** pulses
- **Full-screen red emergency UI** appears (even if phone is locked!)
- Shows: Patient name, Event ID, "EMERGENCY ACTIVE"

**To acknowledge:**
1. Tap the giant red **STOP / ACKNOWLEDGE** button
2. Alarm stops instantly
3. Screen changes to "ACKNOWLEDGED — Help confirmed"
4. Patient phone updates to "Help Acknowledged"

### 4. Test Alarm (Non-Emergency)

- **Settings → Test Alarm** — triggers the alarm sound/vibration for 5 seconds without sending an emergency
- Useful for verifying volume and vibration work

---

## Key Behaviors to Verify

| Scenario | Expected Behavior |
|----------|-------------------|
| Patient holds button <1s | No emergency sent (prevents accidental triggers) |
| Patient holds button ≥1s | Emergency sent, both phones show status |
| Caregiver phone locked | Full-screen alarm appears over lock screen |
| Caregiver in another app | Alarm interrupts, full-screen UI takes over |
| Phones out of range | Status shows "DISCONNECTED" after ~30s |
| Re-pair after disconnect | Works without app restart |
| Role switch (Settings) | Disconnects current pair, returns to role selection |
| App killed/restarted | Remembers role, paired device, settings |

---

## Troubleshooting

### "Location permission denied"
- **Settings → Apps → EmergencyCall → Permissions → Location → Allow all the time**
- Nearby Connections requires location for Bluetooth scanning (Google requirement)

### "Can't find device" during pairing
- Ensure **Bluetooth is ON** on both phones
- Ensure **Location is ON** (required for BLE scanning)
- Keep phones **within 5 meters** during initial pairing
- Try **restarting Bluetooth** on both phones
- On Caregiver: tap **START SEARCH** again

### Alarm doesn't sound / no vibration
- Check **media volume** is up (alarm uses media stream)
- **Settings → Sound → Vibration** → ensure enabled
- **Settings → Apps → EmergencyCall → Notifications** → ensure allowed
- Try **Settings → Test Alarm** to verify

### "Foreground service not starting" (Android 14+)
- **Settings → Apps → EmergencyCall → Battery → Unrestricted**
- Grant **"Alarms & reminders"** special permission if prompted

### Connection drops frequently
- Keep phones **charged** (some OEMs kill background Bluetooth on battery saver)
- Disable **Battery Optimization** for EmergencyCall
- Avoid metal cases / interference

---

## Architecture Overview

```
┌─────────────────┐     Nearby Connections      ┌──────────────────┐
│   PATIENT       │  ◄─── Bluetooth/Wi-Fi ───►  │  CAREGIVER       │
│   (Advertiser)  │     API (P2P, offline)      │  (Discoverer)    │
└────────┬────────┘                             └────────┬─────────┘
         │                                               │
         ▼                                               ▼
┌─────────────────┐                             ┌──────────────────┐
│ EmergencyButton │                             │ Foreground       │
│ (long-press ≥1s)│                             │ Service +        │
│ ──────────────► │  NetworkMessage:Emergency   │ FullScreenAlarm  │
│                 │                             │ Activity         │
└─────────────────┘                             └────────┬─────────┘
                                                         │
                              ◄─── NetworkMessage:Ack ───┘
                              (Caregiver taps ACKNOWLEDGE)
         │                                               │
         ▼                                               ▼
┌─────────────────┐                             ┌──────────────────┐
│ EmergencyStatus │                             │ EmergencyStatus  │
│ = ACKNOWLEDGED  │                             │ = ACKNOWLEDGED   │
└─────────────────┘                             └──────────────────┘
```

**Core Components:**

| Module | Purpose |
|--------|---------|
| `EmergencyStateManager` | Single source of truth (StateFlow), persists to DataStore |
| `NearbyManager` | Advertising, discovery, connections, message I/O |
| `AlarmService` | Foreground service with MediaPlayer loop + Vibrator |
| `FullScreenAlarmActivity` | Full-screen intent, shows over lock screen |
| `PreferencesManager` | DataStore persistence (role, paired device, settings) |
| `NetworkMessage` | Serialized protocol (Emergency, Ack, Heartbeat, Pairing) |

---

## Building from Source

```bash
# Prerequisites: JDK 17, Android SDK (API 35)
cd EmergencyCall
./gradlew.bat assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Build Config:**
- Kotlin 2.0.20
- AGP 8.7.2, Gradle 8.9
- Compose BOM 2024.10.00, Material3
- Nearby Connections 18.4.0
- DataStore Preferences 1.1.1
- Serialization (protobuf) for message protocol

---

## Security & Privacy

- **Zero network traffic** — no Internet permission declared
- **No accounts, no cloud, no analytics**
- **Local-only** — all data stays on the two paired devices
- **Pairing is symmetric** — both sides must accept
- **Heartbeat** — detects disconnect within ~30 seconds

---

## Known Limitations

1. **Range:** Bluetooth ~10–30m, Wi-Fi Direct ~100m (varies by device/environment)
2. **Android 12+ required** for Nearby Devices permission model
3. **One paired device at a time** — re-pairing replaces the previous
4. **No fallback** — if Nearby Connections fails, no SMS/call backup (by design, offline-only)
5. **Battery:** Continuous advertising/discovery uses ~2–5%/hour

---

## License

MIT License — Free for personal and commercial use.

---

## Support

For issues, check:
1. This README's Troubleshooting section
2. Android logcat: `adb logcat | grep -i emergencycall`
3. Ensure both phones meet Android 12+ requirement

**APK Location:** `app/build/outputs/apk/debug/app-debug.apk`