# ffalcontrol

On-device config control for TCL / FFALCON (雷鸟) Android TV. It runs on the TV, reads and writes the vendor's `ConfigProvider` to disable ads, telemetry, recommendation feeds and update channels — no root, no permanent ADB session.

> 中文说明见 [README.zh-CN.md](README.zh-CN.md)。

The name comes from FFALCON + control. It started as a private diagnostic tool for an MT9655 set with no root and no developer options.

## Supported devices

- FFALCON 鹤7 75R79A (`MT9655_4K_CN`, Android 14, `UTT2.250416.001 AU01`) — tested
- Other TCL / FFALCON MT9655 and MT9653 sets that ship `com.tcl.providers.config` — expected to work, not individually verified

**Only run this on a device you own.** It changes system configuration.

## What it does

TCL keeps a lot of system behaviour in the `com.tcl.providers.config` provider and exposes it to system apps and tools. The vendor's own `com.tcl.guard` (system uid) reads the disable list from there on boot and on wake-from-standby.

ffalcontrol wraps that channel into a point-and-click tool:

| Endpoint | Purpose |
|---|---|
| `GET /health` | Service status, version, uid |
| `GET /snapshot` | Packages, config rows, HOME resolution, app state |
| `GET /app-boot-policy` | Current AppBootPolicy raw content and SHA |
| `GET /install-config` | Current InstallConfig raw content and SHA |
| `GET /provision` | State of the one-click restore |
| `GET /settings` / `GET /settings-all` | Settings snapshots |
| `GET /pkg/<package>` | Single package detail |
| `POST /guard` | Write `Guard_disable_App` (package- and component-level disables) |
| `POST /app-boot-policy` | Write `AppBootPolicy` (activity launch blocking) |
| `POST /install-config` | Write `InstallConfig` (HOME package allowlist) |
| `POST /config-write` | Generic config row write (`AppBootPolicy`, `bootStagedStartupConfig`, `InstallConfig`, `Guard_disable_App`) |
| `POST /provision` | **One-click restore**: Guard + InstallConfig + AppBootPolicy in one call |
| `POST /cyberui-messages/*` | Read/write the vendor launcher message table |

Every write is compare-and-set: the current raw content is hashed, the request carries `expected_sha256`, and the row is re-read afterwards. A mismatch fails the call instead of writing blind.

## Build

Requires JDK 17 and an Android SDK with platform 34 + build-tools 34.0.0:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./build.sh
```

The output is `ffalcontrol.apk`, signed with `~/.android/debug.keystore` by default. To use your own key:

```sh
KS=my.p12 KS_PASS=pass:xxx KS_ALIAS=myalias ./build.sh
```

No Gradle: `build.sh` runs `javac → d8 → aapt2 → zipalign → apksigner` directly.

## What the buttons do

The app has five buttons in one horizontal row:

| Button | Behaviour |
|---|---|
| **刷新诊断** | Re-read the state (read-only) and refresh the summary |
| **一键恢复** | Write `Guard_disable_App` (22 packages + 41 components), append the 44 `AppBootPolicy` entries, and add `app.orchard.tv` to `InstallConfig.launcherStrategy`. If everything already matches, it reports "already executed, nothing to do" instead of writing again |
| **放行 aerial** | Add `app.orchard.tv` to the launcher allowlist |
| **默认桌面** | Open the system default-apps page |
| **停止并退出** | Stop the loopback server and exit |

The API token is generated per install, printed to logcat only, and the UI shows just its first 8 characters.

## Install

`adb install` is usually refused by the TV's installer verifier (`INSTALL_FAILED_VERIFICATION_FAILURE`). The working path is:

1. Put the APK on a USB stick;
2. Plug it into the TV and install through 电视卫士 (the vendor's file/install manager);
3. Or, if the TV has a file manager, open the APK directly.

Launch the app once; the service listens on `127.0.0.1:18742`.

## Usage

The API token is generated per install and printed to logcat on startup:

```sh
adb logcat -d | grep "FFalControl token"
```

Then:

```sh
adb forward tcp:18742 tcp:18742
TOK=<token>
curl -s -H "Authorization: Bearer $TOK" http://127.0.0.1:18742/health
```

One-click restore:

```sh
curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
  -d '{"level":"full","write":true}' http://127.0.0.1:18742/provision
```

Reboot afterwards so `com.tcl.guard` runs the package-level disables on `BOOT_COMPLETED`.

## How it works, and where it stops

- **Package-level disables** go through `Guard_disable_App` and are executed by `com.tcl.guard`; they survive reboots.
- **Component-level disables** use the same row, but guard has a top-skip: if `com.tcl.cyberui` is in the foreground it skips the whole component list (so it cannot disable the running launcher and black-screen the TV). In practice component disables do not run at boot.
- **Activity blocking** therefore uses `AppBootPolicy.activityBlackList` instead. TCL blocks before the activity starts, independent of the top-skip — the only component-level mechanism that reliably works here.
- **Background services** (e.g. `LauncherUpdateService`) are covered by neither: `AppBootPolicy` has no service blacklist and guard skips them. They cannot currently be disabled.
- **Preinstalled apps cannot be truly removed**: they live on read-only partitions and the vendor uninstall allowlist is empty, so `pm uninstall --user 0` fails. Disabling is the ceiling.

## Known TCL limitations (MT9655)

- `WindowManager` hard-codes a launch of `com.tcl.cyberui` at `systemReady`. A third-party launcher stays the default Home, but the vendor launcher is put in front once at boot.
- `PackageManagerService` refuses shell component-state changes (`SecurityException: Shell cannot change component state`).
- `DevicePolicyManager` is shipped with its feature disabled (`mHasFeature=false`), so no device owner.
- Background activity launch is enforced strictly; the `SYSTEM_ALERT_WINDOW`, AlarmClock PendingIntent and device-owner exemptions are all unavailable.

## Companion: aerial-tv

ffalcontrol handles *what to turn off*; the home screen itself is better replaced with [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) — an open-source tvOS-style Android TV launcher with no ads or recommendation feed, adapted for Xiaomi / Hisense / TCL sets.

| Project | Responsibility |
|---|---|
| **ffalcontrol** | Package disables, AppBootPolicy activity blocks, ConfigProvider one-click restore |
| **aerial-tv** | A clean tvOS-style home screen |

Order of use:

1. Run ffalcontrol's `/provision` to turn off preinstalled ads and recommendation entries;
2. Install aerial-tv (its `app.orchard.tv` must be allowlisted in `InstallConfig.launcherStrategy.packages` first — ffalcontrol's `POST /install-config` does that);
3. Set it as Home in the system default-apps page.

Note: on MT9655 the TCL `WindowManager` still launches `com.tcl.cyberui` at boot, so the vendor launcher may appear in front once even when aerial is the default Home; pressing the Home key returns to aerial. The `aerial-guard.apk` variant ships a `HomeGuardService` aimed at this behaviour, but it does not declare the HOME category, so whether it applies depends on the model.

## Responsible disclosure

Everything described here is publicly observable firmware behaviour (`adb` + `dumpsys` is enough to reproduce it). No vendor keys, signing material, or exploit details for unfixed vulnerabilities are included. If the vendor considers any of it a security issue, please open an issue — the description can be adjusted or removed in a later firmware.

## Disclaimer

This tool writes system configuration directly. A wrong write can leave the launcher misbehaving or apps unable to start, and in the worst case needs a factory reset.

- Use it only on devices you own;
- Read the current row and keep its SHA before every write;
- Recovery: write the original content back with `/config-write`, or factory reset.

## License

MIT
