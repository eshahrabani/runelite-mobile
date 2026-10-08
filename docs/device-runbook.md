# Device runbook

**Audience:** operator
**Read this when:** installing the app on a device, AOT-compiling the client dex, sideloading a hub plugin, or pulling a conformance report.
**Verified against:** `android/src/main/java/org/runelite/mobile/MainActivity.java`, `android/src/main/java/org/runelite/mobile/ClientUpdater.java`, `android/src/main/java/org/runelite/mobile/host/MobilePluginHub.java`, `android/src/main/java/org/runelite/mobile/host/OnDeviceDexer.java`, `android/src/main/java/org/runelite/mobile/host/PluginConformance.java`, `android/src/main/java/org/runelite/mobile/SidePanel.java`, `android/build/outputs/apk/release/`

This page is the operator's copy-paste procedure set. It names the exact paths and commands the
code reads back. Build the APK first ([build-and-release.md](build-and-release.md)); update and
AOT-status semantics are in [client-updates.md](client-updates.md); log-reading and the plugin
conformance metrics are in [diagnostics.md](diagnostics.md).

Throughout, the two anchor directories are:

- **`<files>`** = `/data/data/org.runelite.mobile/files` (app-private; not adb-writable on a
  release build because the package is not debuggable — `PluginConformance.java:120-122`).
- **`<external>`** = `/sdcard/Android/data/org.runelite.mobile/files` (app-specific external dir;
  the only adb-writable drop box, `MobilePluginHub.java:57-59`).

## 1. Install / upgrade

The release APK is signed with the AGP default **debug** signing config (`signingConfig
signingConfigs.debug`, `android/build.gradle:42` — there is no `signingConfigs { }` block in the
module). Reinstalling over the debug-signed package therefore keeps app data.

```bash
./gradlew :android:assembleRelease
adb install -r android/build/outputs/apk/release/android-release.apk
```

`-r` reinstalls in place. App data (`<files>`, including `runelite-dex.jar`, the imported plugin
jars and the session preferences) survives. A fresh install or an uninstall wipes it, and any
download you had installed under `<files>/runelite-dex.jar` is gone — the APK's bundled asset
(`android/src/main/assets/runelite-dex.jar`) is re-copied on the next `launchGame`
(`MainActivity.java:1359-1389`).

## 2. AOT compile

The target device runs `dalvik.vm.usejit=false` (JIT disabled), so the game client dex must be
AOT-compiled or it runs interpreted (~10x slower) (`MainActivity.java:275-289`). The app cannot
run `pm compile` itself — this is an operator step. Run both commands, because the client dex is a
secondary dex of the package:

```bash
adb shell cmd package compile -m speed -f --secondary-dex org.runelite.mobile
adb shell cmd package compile -m speed -f org.runelite.mobile
adb shell pm art dump org.runelite.mobile     # expect [status=speed] on both
```

The exact `--secondary-dex` invocation is the literal `ClientUpdater.AOT_FIX_COMMAND`
(`ClientUpdater.java:32-34`); the two-command run is the sequence in the boot-block comment
(`MainActivity.java:284-286`).

### When to re-run

Re-run after **every APK install** and after **every downloaded client-jar update**. ART keys the
odex to the invoking class-loader context, which embeds the base APK path *plus* the checksums of
the APK and the dex. An install lands the package in a new `/data/app/~~…==/` directory, so the
path changes, ART discards the `speed` odex and falls back to the `verify` vdex — interpreted
(`MainActivity.java:277-283`). A downloaded `<files>/runelite-dex.jar` changes the dex checksum
for the same reason.

The app's own heuristic mirrors this: `ClientUpdater.clientDexAotStatus` compares the newest
`<files>/oat/<isa>/runelite-dex.odex` against `max(jar.lastModified, packageCodePath.lastModified)`
and reports `AOT_OK` / `AOT_STALE` / `AOT_MISSING` / `AOT_UNKNOWN` (`ClientUpdater.java:50-78`).

```mermaid
stateDiagram-v2
    [*] --> "AOT_MISSING"
    "AOT_MISSING" --> "AOT_MISSING": "no runelite-dex.jar"
    "AOT_MISSING" --> "AOT_UNKNOWN": "files/oat unreadable"
    "AOT_MISSING" --> "AOT_STALE": "odex found, older than jar/APK"
    "AOT_STALE" --> "AOT_OK": "pm compile after install/jar update"
    "AOT_OK" --> "AOT_STALE": "new install or jar update"
```

`AOT_UNKNOWN` means the check cannot claim anything (it is not treated as bad by the launcher's red
highlighting); only `AOT_STALE`/`AOT_MISSING` are bad (`ClientUpdater.java:74-77`, `MainActivity.java:302-306`).

### Verification and the debuggable caveat

`pm art dump` must show `[status=speed]` on both the APK and the secondary dex. `[status=verify]`
or `run-from-apk` means it is still interpreted.

The installed APK **must not be debuggable**: ART Service rewrites `-m speed` to `verify` for
debuggable packages, and a verify-only odex executes interpreted anyway
(`MainActivity.java:287-290`). Only the release build AOT-compiles usefully. The release build is
signed with the debug key but is not marked debuggable, which is why it works and why `-r` keeps
data. The launcher shows a red `NOT AOT-COMPILED - expect ~5 fps (Host tab)` line when stale or
missing (`MainActivity.java:700-702`), and the side panel's Host tab repeats it as a red
`client AOT` row (`SidePanel.java:778-783`); see [side-panel.md](side-panel.md).

## 3. Push a hub plugin

The app scans two directories for `*.jar`: `<files>/plugins` and `<external>/plugins`
(`MobilePluginHub.java:60-71`). Only `<external>/plugins` is adb-writable on a release build, so it
is the sideload drop box:

```bash
mkdir -p /sdcard/Android/data/org.runelite.mobile/files/plugins
adb push MyPlugin.jar /sdcard/Android/data/org.runelite.mobile/files/plugins/
```

Jars in the external dir are **imported by copy** into `<files>/plugins` and made read-only, and
already-imported files are replaced only when the length differs (`MobilePluginHub.java:80-104`).
The copy exists because ART refuses a writable dex (`Writable dex file … is not allowed`), which a
jar on shared storage is (`MobilePluginHub.java:84-90`). To force a re-import, delete the copy in
`<files>/plugins` (or push a jar of different length) and relaunch.

A jar carrying `.class` bytecode is dexed on-device via the bundled `rl-dexer.jar`; if that is
unavailable the hub logs `on-device dexer unavailable (<reason>); dex <name> on the host with
-PhubPlugin=<name>` (`MobilePluginHub.java:193-197`). The host-side alternative is
`./gradlew :android:dexHubPlugin -PhubPlugin=<internalName>`; both paths and the
`runelite_plugin.json` descriptor are documented in [third-party-plugins.md](third-party-plugins.md).

## 4. Trigger and pull a conformance report

The request file is `<external>/conformance.request` (`PluginConformance.java:67-68,124-127`). The
render loop polls it once per 5 s and consumes it (`MainActivity.java:3270-3278`):

```bash
adb shell "echo 1 > /sdcard/Android/data/org.runelite.mobile/files/conformance.request"
# the run is posted to the UI thread; wait for the report to appear
adb shell "ls -l /sdcard/Android/data/org.runelite.mobile/files/conformance-report.txt"
adb pull /sdcard/Android/data/org.runelite.mobile/files/conformance-report.txt
```

The run is single-flight: a second request while one is in progress logs `CONFORMANCE: already
running, request ignored` and is dropped (`PluginConformance.java:133-158`). When it finishes it
logs one line:

```text
CONFORMANCE: <summary> (report: <path>)
```

If the host is not running the report is headed `# aborted: host not running (<status>)` and the
summary is `aborted: host not running`. You can also trigger a run from the Host tab of the side
panel via `PluginConformance.run(activity)` (`SidePanel.java:792`). Every metric in the report and
the summary string are documented in [diagnostics.md](diagnostics.md).

## 5. Read logcat

Filter on the three tags the app uses:

```bash
adb logcat -s RuneLiteMobile RuneLiteHost MobilePluginHub
```

| Tag | Owner |
|---|---|
| `RuneLiteMobile` | `MainActivity`, `ClientUpdater`, `SidePanel`, `JagexOAuthClient`, `LocalCallbackServer` |
| `RuneLiteHost` | `RuneLiteHost`, `GraphicsSelfTest`, `PluginConformance` |
| `MobilePluginHub` | `MobilePluginHub`, `OnDeviceDexer` |

At boot, look for `Client dex AOT: …` (or the `Client dex is NOT AOT-compiled: …` warning,
`MainActivity.java:302-306`), `Pre-registered game dex classloader for ART dexopt`
(`MainActivity.java:296`), and, once the host starts, `GFX SELFTEST PASS` (or the failing case).
Field-by-field interpretation of `callbacks.draw` and `GameState` lines is in
[diagnostics.md](diagnostics.md).

## 6. On-device artifact table

`<files>` = `/data/data/org.runelite.mobile/files`; `<external>` =
`/sdcard/Android/data/org.runelite.mobile/files`; `<cache>` = the app cache dir. `app_*` dirs are
the private directories returned by `Context.getDir(name, MODE_PRIVATE)` (Android creates
`<data>/app_<name>`).

| Path | Producer | Consumer | Format |
|---|---|---|---|
| `<files>/runelite-dex.jar` | `downloadAndDexJar` (asset) / `ClientUpdater.installBundled` | `DexClassLoader` in `MainActivity.bootstrapGameClient` | dexed jar |
| `<files>/runelite-dex.jar.old` | `ClientUpdater.installBundled` swap | deleted after a successful install | transient backup |
| `<files>/client-version.txt` | `ClientUpdater` / build | `MainActivity`, version line | one version token |
| `<files>/credentials.properties` | `MainActivity.writeCredentialsFile` | the game client's own login | java properties, `JX_*` keys |
| `<files>/oat/<isa>/runelite-dex.odex` | ART `dex2oat` | ART; read by `clientDexAotStatus` | AOT odex |
| `<files>/plugins/` | hub import target | `MobilePluginHub` | `*.jar` |
| `<external>/plugins/` | `adb push` | `MobilePluginHub` | `*.jar` |
| `<files>/rl-dexer.jar` | extracted from `assets/rl-dexer.jar` by `OnDeviceDexer` | `DexClassLoader` for D8 | jar |
| `app_dex/` | `MainActivity` (`getDir("dex", …)`) | ART dexopt output dir for the client dex | dex output |
| `app_dex-hub/` | `MobilePluginHub` (`getDir("dex-hub", …)`) | hub `DexClassLoader` output dir | dex output |
| `app_dex-r8/` | `OnDeviceDexer` (`getDir("dex-r8", …)`) | on-device dexer `DexClassLoader` output dir | dex output |
| `<cache>/fonts/` | `AndroidTextRenderer.setFontDir` | text rasteriser scratch | font cache |
| `<cache>/client-update/` | `ClientUpdater.downloadAndInstall` | deleted after install | download scratch |
| `<external>/conformance.request` | operator | render loop | trigger file |
| `<external>/conformance-report.txt` | `PluginConformance` | operator | plain text report |

Anchors: `MainActivity.java:292-294,755,1362,1391`, `ClientUpdater.java:52-57,136,156,189-212`,
`MobilePluginHub.java:60-71,145`, `OnDeviceDexer.java:60,80`, `MainActivity.java:224`.

## 7. SharedPreferences key table

The only preferences file is `RuneLiteMobilePrefs`, declared once in `MainActivity`
(`PREFS_NAME`, `MainActivity.java:69`) and once in `SidePanel` with the same literal
(`SidePanel.java:65`). Re-derive the key set with:

```bash
grep -rnE '\.(putBoolean|putString|putLong|putInt|putFloat|putStringSet)\(' android/src/main/java/
```

That grep returns writes in `MainActivity.saveSessionState` (`:738-744`), the credentials import at
`MainActivity.java:798-801`, and `SidePanel` (`:253`, `:289`) — nine distinct keys:

| Key | Type | Written by | Meaning |
|---|---|---|---|
| `jx_mode_enabled` | boolean | `MainActivity.saveSessionState`, import path | signed-in state; false clears the session |
| `jx_session_id` | string | `MainActivity.saveSessionState`, import path | `JX_SESSION_ID` mirror |
| `jx_character_id` | string | `MainActivity.saveSessionState`, import path | `JX_CHARACTER_ID` mirror |
| `jx_display_name` | string | `MainActivity.saveSessionState`, import path | `JX_DISPLAY_NAME` mirror |
| `oauth_access_token` | string | `MainActivity.saveSessionState` | OAuth access token |
| `oauth_refresh_token` | string | `MainActivity.saveSessionState` | OAuth refresh token |
| `oauth_expires_at` | long | `MainActivity.saveSessionState` | access-token expiry |
| `sidePanelOpen` | boolean | `SidePanel.toggle` (`:253`) | drawer open state |
| `sidePanelTab` | int | `SidePanel.selectTab` (`:289`) | selected tab index (0/1/2) |

`loadSessionState` reads the seven `jx_*`/`oauth_*` keys with defaults
(`MainActivity.java:718-732`); if `jx_mode_enabled` is false or `jx_session_id` is empty, the
session is treated as signed out. The session/credentials details are in
[login-and-sessions.md](login-and-sessions.md).
