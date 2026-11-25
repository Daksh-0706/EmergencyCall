# AGENTS.md — EmergencyCall working context

> Read this before changing code. Update it when architecture, build steps or design tokens change.
> Last verified: **25 Nov 2026**, commit `6ae61db` (main == origin/main, working tree clean,
> `assembleDebug` green on JDK 21).

## 1. What the project is

EmergencyCall turns **two Android phones into an offline emergency-call system**:

- **Patient phone** — one big SOS button; press-and-hold for exactly **1 s** triggers an alert.
- **Caregiver phone** — receives the alert, plays a loud looping alarm + vibration, shows a full-screen
  alert until **STOP / ACKNOWLEDGE** is pressed.

Transport is multi-path, no backend, no accounts:

| Path | Implementation |
|---|---|
| Nearby Connections (BLE / Wi-Fi Direct) | `networking/NearbyManager.kt`, `networking/transport/NearbyTransport.kt` |
| Local Wi-Fi (same network) | `networking/transport/LocalWifiTransport.kt` |
| Internet fallback | `networking/transport/InternetTransport.kt` |

`networking/transport/EmergencyRouter.kt` picks the route; `TransportType.kt` defines the options.

### Stack

- Kotlin 2.1.0, Jetpack Compose (BOM 2024.10.00), Material 3
- AGP 8.7.3, Gradle wrapper 8.9
- `minSdk 26`, `compileSdk`/`targetSdk 35`
- Debug applicationId: **`com.emergencycall.debug`** (`applicationIdSuffix = ".debug"`), release: `com.emergencycall`
- State: `data/EmergencyState.kt` + `data/EmergencyEvent.kt`; persistence via DataStore (`data/PreferencesManager.kt`)

Repo: `https://github.com/Daksh-0706/EmergencyCall` (remote `origin`, branch `main`).

## 2. Current state (what is done vs pending)

**Done and pushed** — design v2 revamp (commit `c198331`):

- `ui/theme/Color.kt` — v2 tokens (`DesignBackground #0B0B0F`, `DesignSurface #141419`,
  `DesignCardBorder`, `DesignRed/RedLight/RedDeep`, `DesignCareBlue(+Soft)`).
  ⚠️ Legacy `MD3*` colors are still at the bottom of the file — `Theme.kt`'s `LightColorScheme` uses them.
  **Do not delete them** until `Theme.kt` is migrated.
- `ui/components/DesignComponents.kt` — `PremiumSosButton`, `GlassCard`, `InitialsAvatar`, `ContactCard`.
- `ui/components/` — `HeartbeatDot.kt`, `PulseGlow.kt` (`Modifier.pulseGlow`), `FadeSlideIn.kt`.
- `ui/animations/AppMotion.kt` — duration tokens + easing curves.
- `ui/screens/FloatingBottomNav.kt` — glass pill nav; exposes `BottomNavigationBar` with the **same signature**
  used by the three tab screens. `ui/screens/BottomNavigation.kt` now holds only
  `enum NavigationTab { HOME, ICE, CONNECTION, SETTINGS }`.
- `PatientHomeScreen.kt`, `CaregiverHomeScreen.kt`, `RoleSelectionScreen.kt` — full v2 replacements,
  public signatures unchanged (MainActivity still compiles against them).
- `res/values/colors.xml` — window `background` = `#0B0B0F` (v2 screens use `containerColor = Color.Transparent`;
  removing this makes the window flash white).
- `ui/components/PulsingSosButton.kt` was **deleted** — `PremiumSosButton` replaces it.

**Not yet redesigned (next pass, v2 language):**

- `ui/screens/ConnectionScreen.kt`
- `ui/screens/SettingsScreen.kt`
- `alarm/FullScreenAlarmActivity.kt`

`MainActivity.kt` does role routing + screen switching; keep its entry points stable when restyling screens.

## 3. Build

Gradle 8.9 accepts **JDK 17 or 21**. Do **not** use Java 25 or Android Studio's bundled JBR — unsupported.

`gradle.properties` no longer pins a JDK path, and `local.properties` is untracked, so the build follows
`JAVA_HOME` / `ANDROID_HOME`. On this Windows machine the verified command is:

```powershell
$env:JAVA_HOME     = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.10002-hotspot"
$env:ANDROID_HOME  = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat assembleDebug --console=plain
```

Output: `app\build\outputs\apk\debug\app-debug.apk` (~22 MB). Verified green on 25 Nov 2026
(`BUILD SUCCESSFUL in 9m 19s`, JDK 21, after a clean `.gradle`/`app/build` untrack).

Expected benign warnings, not worth chasing: `navigationBarColor` deprecated in `Theme.kt`, and
Java 8 source/target deprecation from the current `compileOptions`.

Notes:

- `platforms/android-35` may be absent while `android-36` is installed; AGP auto-downloads the missing
  platform because the SDK licences are already accepted.
- On WSL/Linux, recreate `local.properties` with `sdk.dir=/home/<user>/android-sdk` and export `JAVA_HOME`;
  it is gitignored and must never be committed again.
- `app/build/` and `.gradle/` are gitignored and untracked now — builds should not dirty `git status`.

## 4. Install & test over ADB

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb install -r -d path\to\app-debug.apk        # -r reinstall, -d allow downgrade
& $adb shell pm path com.emergencycall.debug
& $adb shell am start -n com.emergencycall.debug/com.emergencycall.MainActivity
```

If install fails with *"package conflicts with an existing package"* on a phone that had an older build:

```powershell
& $adb shell pm uninstall com.emergencycall.debug   # plain uninstall, NOT --user 0
```

Root cause seen on a POCO F6: a half-removed package record from a WSL-built APK. A per-user uninstall
leaves the global record behind; the plain `pm uninstall` clears it. Verify with `pm path` returning empty.

Wireless ADB: phone → Developer options → Wireless debugging → `adb connect <ip>:<port>`.

## 5. Design / motion reference (v2)

| Token | Value |
|---|---|
| Duration fast / base / slow | 150 / 300 / 500 ms |
| Entrance | 400 ms + 60–120 ms stagger |
| SOS idle breathing loop | 2.2 s |
| Heartbeat dot | 1.4 s double beat |
| Glow pulse | 850 ms |
| Hold-to-trigger | **exactly 1000 ms** (`HOLD_DURATION_MS` in `DesignComponents.kt`), 150 ms drain on release, `HapticFeedbackType.LONG_PRESS` on trigger |

- SOS gradient: `#FF6B5E → #FF3B30 → #D9261B`; sonar ring alpha `(1 - p) * 0.55`;
  breathing halo scale `0.82 + 0.18 * breath`.
- Radii: buttons 18 dp; cards 20–24 dp with a `DesignCardBorder` hairline; nav pill 26 dp.
- Nav active item: red pill (`DesignRedSoft` background) + `DesignRed` icon.
- Type: headers 17 sp bold; eyebrow labels 10–11 sp, letter-spaced, uppercase; card titles 17–20 sp ExtraBold.
- Screens set `containerColor = Color.Transparent` and paint the `DesignBackground` gradient themselves.

## 6. Known traps

1. **FloatingBottomNav modifier order** — the nav row uses
   `.navigationBarsPadding().height(66.dp)`. Reversing it squashes the pill on 3-button navigation.
   Keep that order.
2. **Legacy MD3 colors** — see §2; `Theme.kt` still depends on them.
3. **Screen signatures** — `MainActivity` calls the screens directly. Keep the public composable signatures
   (`onSosTrigger`, `onNavigate`, etc.) intact during a visual revamp, or update `MainActivity` in the same commit.
4. **`app/build` used to be tracked** — it is now ignored/untracked. If a stale clone has it tracked,
   run `git rm -r --cached app/build .gradle local.properties`.
5. **Release signing is not configured** — `release.keystore` is absent and `signingConfig` is commented out
   in `app/build.gradle.kts` (env-var based block). Debug builds are the supported path right now.

## 7. Next steps

- [ ] Redesign `ConnectionScreen.kt` in the v2 language (pairing states: advertising / searching / connected / error).
- [ ] Redesign `SettingsScreen.kt` (name fields, permissions status, transport preferences).
- [ ] Redesign `FullScreenAlarmActivity.kt` (caregiver alarm: gradient, pulse, ACKNOWLEDGE action, volume-safe layout).
- [ ] End-to-end test on two physical phones: trigger → alarm → acknowledge, plus reconnect after app kill.
- [ ] Optional: wire release signing from environment variables.
