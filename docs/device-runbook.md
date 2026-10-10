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
  release build because the package is not debuggable — `PluginConformance.java`).
- **`<external>`** = `/sdcard/Android/data/org.runelite.mobile/files` (app-specific external dir;
  the only adb-writable drop box, `MobilePluginHub.java`).

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
(`MainActivity.java`).

## 2. AOT compile

The target device runs `dalvik.vm.usejit=false` (JIT disabled), so the game client dex must be
AOT-compiled or it runs interpreted (~10x slower) (`MainActivity.java`). The app cannot
run `pm compile` itself — this is an operator step. Run both commands, because the client dex is a
secondary dex of the package:

```bash
adb shell cmd package compile -m speed -f org.runelite.mobile                  # app dex first
adb shell cmd package compile -m speed -f --secondary-dex org.runelite.mobile  # client dex LAST
adb shell pm art dump org.runelite.mobile     # expect [status=speed] twice
```

**The `--secondary-dex` pass must be last.** `cmd package compile -m speed -f <pkg>` rewrites the
package's oat state and leaves the client dex at `verify` again; running the documented
reverse order therefore ends with the game interpreted (measured on the target device: the
reversed order gave one `[status=speed]` entry, this order gives two). The exact
`--secondary-dex` invocation is the literal `ClientUpdater.AOT_FIX_COMMAND`
(`ClientUpdater.java`). The app's own verdict is a jar-mtime heuristic and logs its inputs
(`AOT check: odex=<ms> jar=<ms> apk=<ms>`); `pm art dump` is the ground truth — it is also the only
way to see the install-directory case, where the odex is invalid but no mtime moved.

### When to re-run

Re-run after **every APK install** and after **every downloaded client-jar update**. ART keys the
odex to the invoking class-loader context, which embeds the base APK path plus the APK's and the
dex's checksums; an install can therefore discard the `speed` odex and fall back to the `verify` vdex
— interpreted (`MainActivity` boot-block comment). A downloaded `<files>/runelite-dex.jar` changes
the dex checksum for the same reason. Whether it actually happened is what `pm art dump` answers.

The app's own verdict is a heuristic and can be wrong in **both** directions, so treat it as a hint:

| Direction | When |
|---|---|
| False `AOT_STALE` | after an install + recompile: `pm art dump` reports `[status=speed]` while the odex file is still older than the freshly installed APK, because ART does not rewrite an odex whose dex input is unchanged. This is why the rule compares the **jar** (the client dex), not the APK. |
| False `AOT_OK` | an install into a *different* `/data/app/~~…==/` directory (uninstall/reinstall, signing change): the context changed, the odex is invalid, but no mtime moved. Only `pm art dump` sees this. |

`ClientUpdater.clientDexAotStatus` compares the newest `<files>/oat/<isa>/runelite-dex.odex` against
`runelite-dex.jar.lastModified()` and reports `AOT_OK` / `AOT_STALE` / `AOT_MISSING` / `AOT_UNKNOWN`;
every call logs `AOT check: odex=<ms> jar=<ms> apk=<ms>` so the verdict can be explained
(`ClientUpdater`).

```mermaid
stateDiagram-v2
    [*] --> "AOT_MISSING"
    "AOT_MISSING" --> "AOT_MISSING": "no runelite-dex.jar"
    "AOT_MISSING" --> "AOT_UNKNOWN": "files/oat unreadable"
    "AOT_MISSING" --> "AOT_STALE": "odex found, older than the client jar"
    "AOT_STALE" --> "AOT_OK": "pm compile after the jar changed"
    "AOT_OK" --> "AOT_STALE": "client jar replaced (update)"
```

`AOT_UNKNOWN` means the check cannot claim anything (it is not treated as bad by the launcher's red
highlighting); only `AOT_STALE`/`AOT_MISSING` are bad.

### Verification and the debuggable caveat

`pm art dump` must show `[status=speed]` for both the APK and `files/runelite-dex.jar` (the dump
lists the secondary dex by path, so the client dex's line is the one that matters). `[status=verify]`
or `run-from-apk` means it is still interpreted.

The installed APK **must not be debuggable**: ART Service rewrites `-m speed` to `verify` for
debuggable packages, and a verify-only odex executes interpreted anyway
(`MainActivity.java`). Only the release build AOT-compiles usefully. The release build is
signed with the debug key but is not marked debuggable, which is why it works and why `-r` keeps
data. The launcher shows a red `NOT AOT-COMPILED - expect ~5 fps (Host tab)` line when stale or
missing (`MainActivity.updateLauncherUi`), and the side panel's Host tab repeats it as a red
`client AOT` row (`SidePanel.buildHostTab`); see [side-panel.md](side-panel.md).

## 3. Push a hub plugin

The app scans two directories for `*.jar`: `<files>/plugins` and `<external>/plugins`
(`MobilePluginHub.java`). Only `<external>/plugins` is adb-writable on a release build, so it
is the sideload drop box:

```bash
mkdir -p /sdcard/Android/data/org.runelite.mobile/files/plugins
adb push MyPlugin.jar /sdcard/Android/data/org.runelite.mobile/files/plugins/
```

Jars in the external dir are **imported by copy** into `<files>/plugins` and made read-only, and
already-imported files are replaced only when the length differs (`MobilePluginHub.java`).
The copy exists because ART refuses a writable dex (`Writable dex file … is not allowed`), which a
jar on shared storage is (`MobilePluginHub.java`). To force a re-import, delete the copy in
`<files>/plugins` (or push a jar of different length) and relaunch.

A jar carrying `.class` bytecode is dexed on-device via the bundled `rl-dexer.jar`; if that is
unavailable the hub logs `on-device dexer unavailable (<reason>); dex <name> on the host with
-PhubPlugin=<name>` (`MobilePluginHub.java`). The host-side alternative is
`./gradlew :android:dexHubPlugin -PhubPlugin=<internalName>`; both paths and the
`runelite_plugin.json` descriptor are documented in [third-party-plugins.md](third-party-plugins.md).

## 4. Trigger and pull a conformance report

The request file is `<external>/conformance.request` (`PluginConformance`). The
render loop polls it once per 5 s and consumes it (`MainActivity.java`):

```bash
adb shell "echo 1 > /sdcard/Android/data/org.runelite.mobile/files/conformance.request"
# the run is posted to the UI thread; wait for the report to appear
adb shell "ls -l /sdcard/Android/data/org.runelite.mobile/files/conformance-report.txt"
adb pull /sdcard/Android/data/org.runelite.mobile/files/conformance-report.txt
```

The run is single-flight: a second request while one is in progress logs `CONFORMANCE: already
running, request ignored` and is dropped (`PluginConformance.java`). When it finishes it
logs one line:

```text
CONFORMANCE: <summary> (report: <path>)
```

If the host is not running the report is headed `# aborted: host not running (<status>)` and the
summary is `aborted: host not running`. You can also trigger a run from the Host tab of the side
panel via `PluginConformance.run(activity)` (`SidePanel.java`). Every metric in the report and
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
`MainActivity.java`), `Pre-registered game dex classloader for ART dexopt`
(`MainActivity.java`), and, once the host starts, `GFX SELFTEST PASS` (or the failing case).
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

Anchors: `MainActivity` (boot block, launcher visibility, AOT status), `ClientUpdater`
(`clientDexAotStatus`), `MobilePluginHub.java:60-71,145`, `OnDeviceDexer.java:60,80`.

## 7. SharedPreferences key table

The only preferences file is `RuneLiteMobilePrefs`, declared once in `MainActivity`
(`PREFS_NAME`, `MainActivity.java`) and once in `SidePanel` with the same literal
(`SidePanel.java`). Re-derive the key set with:

```bash
grep -rnE '\.(putBoolean|putString|putLong|putInt|putFloat|putStringSet)\(' android/src/main/java/
```

That grep returns writes in `MainActivity.saveSessionState`, the credentials import at
`MainActivity.java`, and `SidePanel` (`MainActivity`) — nine distinct keys:

| Key | Type | Written by | Meaning |
|---|---|---|---|
| `jx_mode_enabled` | boolean | `MainActivity.saveSessionState`, import path | signed-in state; false clears the session |
| `jx_session_id` | string | `MainActivity.saveSessionState`, import path | `JX_SESSION_ID` mirror |
| `jx_character_id` | string | `MainActivity.saveSessionState`, import path | `JX_CHARACTER_ID` mirror |
| `jx_display_name` | string | `MainActivity.saveSessionState`, import path | `JX_DISPLAY_NAME` mirror |
| `oauth_access_token` | string | `MainActivity.saveSessionState` | OAuth access token |
| `oauth_refresh_token` | string | `MainActivity.saveSessionState` | OAuth refresh token |
| `oauth_expires_at` | long | `MainActivity.saveSessionState` | access-token expiry |
| `sidePanelOpen` | boolean | `SidePanel.toggle` | drawer open state |
| `sidePanelTab` | int | `SidePanel.selectTab` | selected tab index (0/1/2) |

`loadSessionState` reads the seven `jx_*`/`oauth_*` keys with defaults
(`MainActivity.java`); if `jx_mode_enabled` is false or `jx_session_id` is empty, the
session is treated as signed out. The session/credentials details are in
[login-and-sessions.md](login-and-sessions.md).
