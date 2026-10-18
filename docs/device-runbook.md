# Device runbook

**Audience:** operator
**Read this when:** installing the app on a device, AOT-compiling the client dex, sideloading a hub plugin, or pulling a conformance report.
**Verified against:** `android/src/main/java/org/runelite/mobile/MainActivity.java`, `android/src/main/java/org/runelite/mobile/SidePanel.java`, `android/src/main/java/org/runelite/mobile/ClientUpdater.java`, `android/src/main/java/org/runelite/mobile/host/RuneLiteHost.java`, `android/src/main/java/org/runelite/mobile/host/MobilePluginHub.java`, `android/src/main/java/org/runelite/mobile/host/OnDeviceDexer.java`, `android/src/main/java/org/runelite/mobile/host/PluginConformance.java`, `android/build.gradle`

This page is the operator's copy-paste procedure set. It names the exact paths and commands the
code reads back. Build the APK first ([build-and-release.md](build-and-release.md)); update and
AOT-status semantics are in [client-updates.md](client-updates.md); log-reading and the plugin
conformance metrics are in [diagnostics.md](diagnostics.md).

Throughout, the two anchor directories are:

- **`<files>`** = `/data/data/org.runelite.mobile/files` (app-private; not adb-writable on a
  release build because the package is not debuggable — `PluginConformance`).
- **`<external>`** = `/sdcard/Android/data/org.runelite.mobile/files` (app-specific external dir;
  the only adb-writable drop box, `MobilePluginHub`).

## 1. Install / upgrade

The release APK must carry the **same signing identity as the build already on the device**:
Android refuses an install over an APK signed with a different key with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the only way forward from there is an uninstall, which
wipes app data. The identity is read from `android/keystore.properties` (`storeFile` /
`storePassword` / `keyAlias` / `keyPassword`) or the `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` /
`KEY_ALIAS` / `KEY_PASSWORD` env vars (`android/build.gradle`); CI materialises the pinned keystore
from the `SIGNING_KEYSTORE_B64` secret, alias `runelitemobile`. With none configured, the release
build falls back to the debug key and `packageRelease` prints `[signing] WARNING: no release
keystore configured …` — such an APK will not install over a real release.

```bash
./gradlew :android:assembleRelease
adb install -r android/build/outputs/apk/release/android-release.apk
```

`-r` reinstalls in place. App data (`<files>`, including `runelite-dex.jar`, the imported plugin
jars, the RuneLite config under `.runelite/` and the session preferences) survives as long as the
identity matches. A fresh install or an uninstall wipes it, and any download you had installed
under `<files>/runelite-dex.jar` is gone — the APK's bundled asset
(`android/src/main/assets/runelite-dex.jar`) is re-copied on the next boot
(`MainActivity.bootstrapGameClient`). Either way the install invalidates the AOT odex, so always
**install first, then re-run the AOT compile** (§2).

## 2. AOT compile

The target device runs `dalvik.vm.usejit=false` (JIT disabled), so the game client dex must be
AOT-compiled or it runs interpreted (~10x slower) (`MainActivity`). The app cannot
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
`--secondary-dex` invocation is the literal `ClientUpdater.AOT_FIX_COMMAND`. The app's own verdict
is a jar-mtime heuristic and logs its inputs
(`AOT check: odex=<ms> jar=<ms> apk=<ms>`); `pm art dump` is the ground truth — it is also the only
way to see the install-directory case, where the odex is invalid but no mtime moved.

### When to re-run

Re-run after **every APK install** and after **every downloaded client-jar update** — i.e. install
first (`adb install -r`), then both `cmd package compile` passes above, in that order. ART keys the
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
(`MainActivity`). Only the release build AOT-compiles usefully; it is not marked debuggable,
and any release-signed APK still installs over the previous one with `-r`, which is what keeps app
data across an update (§1). The launcher's version line turns red with `NOT AOT-COMPILED - expect
~5 fps (Host section on this screen)` when the odex is stale or missing
(`MainActivity.updateLauncherUi`), and the Host card repeats it as a red `client AOT` row whose text
carries the fix command (`MainActivity.refreshHostCard` via `ClientUpdater.clientDexAotText`).

## 3. Push a hub plugin

The app scans two directories for `*.jar`: `<files>/plugins` and `<external>/plugins`
(`MobilePluginHub`). Only `<external>/plugins` is adb-writable on a release build, so it
is the sideload drop box:

```bash
mkdir -p /sdcard/Android/data/org.runelite.mobile/files/plugins
adb push MyPlugin.jar /sdcard/Android/data/org.runelite.mobile/files/plugins/
```

Jars in the external dir are **imported by copy** into `<files>/plugins` (`MobilePluginHub`); an
already-imported file is replaced only when its length differs. Right before a jar is handed to a
`DexClassLoader` it is chmod'ed read-only, because ART refuses a writable dex (`Writable dex file …
is not allowed`), which a jar on shared storage is (`MobilePluginHub.loadPlugins`).

A jar carrying `.class` bytecode is dexed on-device into a `<name>.dex.jar` sibling next to it via
the bundled `rl-dexer.jar`; while that sibling exists the raw jar is skipped, so re-importing the
raw jar alone does not change what loads. To replace a raw plugin, delete both
`<files>/plugins/<name>.jar` and `<files>/plugins/<name>.dex.jar` and relaunch (a pushed jar is only
re-imported when its length differs). If the dexer is unavailable the hub logs `on-device dexer
unavailable (<reason>); dex <name> on the host with -PhubPlugin=<name>`
(`MobilePluginHub.dexOnDevice`). The host-side alternative is
`./gradlew :android:dexHubPlugin -PhubPlugin=<internalName>` (or `-PpluginJar=<path>`); its output
name embeds the jar hash, so a new version lands as a new file next to the old one. Both paths and
the `runelite_plugin.json` descriptor are documented in
[third-party-plugins.md](third-party-plugins.md).

## 4. Trigger and pull a conformance report

The request file is `<external>/conformance.request` (`PluginConformance`). The render loop polls it
once per 5 s and consumes it (`MainActivity.runRenderLoop`) — the poll lives in the frame loop, so
it only fires while a client is presenting frames (drop the file, or tap the launcher pill, with the
game running):

```bash
adb shell "echo 1 > /sdcard/Android/data/org.runelite.mobile/files/conformance.request"
# the run is posted to the UI thread; wait for the report to appear
adb shell "ls -l /sdcard/Android/data/org.runelite.mobile/files/conformance-report.txt"
adb pull /sdcard/Android/data/org.runelite.mobile/files/conformance-report.txt
```

The run is single-flight: a second request while one is in progress logs `CONFORMANCE: already
running, request ignored` and is dropped (`PluginConformance`). When it finishes it
logs one line:

```text
CONFORMANCE: <summary> (report: <path>)
```

If the host is not running the report is headed `# aborted: host not running (<status>)` and the
summary is `aborted: host not running`. You can also trigger a run from the launcher's Host card
(`Run plugin conformance` → `MainActivity.requestPluginConformance` →
`PluginConformance.run(activity)`, which posts the pass to the UI thread and shows the report name
in a toast); the pill is disabled unless the host is running and no pass is in flight. Every metric
in the report and the summary string are documented in [diagnostics.md](diagnostics.md).

## 5. Dump all thread stacks (a hung client)

The release APK is not debuggable and the adb shell cannot signal it (`kill -3` is
`Operation not permitted`), so a hung client thread — the "frozen world" case, where the
`callbacks.draw` and `GameState` lines stop while `LoginTick` keeps printing — is localised
through a drop file:

```bash
adb shell "echo 1 > /sdcard/Android/data/org.runelite.mobile/files/stackdump.request"
adb pull /sdcard/Android/data/org.runelite.mobile/files/stackdump.txt
```

`MainActivity.consumeStackDumpRequest` runs on both the main thread's 1 s `LoginTick` and the
render loop's 5 s tick (a hang can block either), deletes the request and writes every thread's
state plus its Java stack to `stackdump.txt` next to it. Look for the client thread's frames
(the game's obfuscated classes) and for `AWTBridge-RenderThread`, whose stack says whether it is
blocked or merely waiting for a frame.)

## 6. Read logcat
Filter on the three tags the app uses:

```bash
adb logcat -s RuneLiteMobile RuneLiteHost MobilePluginHub
```

| Tag | Owner |
|---|---|
| `RuneLiteMobile` | `MainActivity`, `ClientUpdater`, `SidePanel`, `SwingSidebarView`, `JagexOAuthClient`, `LocalCallbackServer` |
| `RuneLiteHost` | `RuneLiteHost`, `GraphicsSelfTest`, `SwingSelfTest`, `PluginConformance`, `StackSampler` |
| `MobilePluginHub` | `MobilePluginHub`, `OnDeviceDexer` |

At boot, look for `Client dex AOT: …` (or the `Client dex is NOT AOT-compiled: …` warning,
`MainActivity`), `Pre-registered game dex classloader for ART dexopt` (`MainActivity`), and, once
the host starts, `GFX SELFTEST PASS` / `SWING SELFTEST PASS (26 cases)` (or the failing case).
Field-by-field interpretation of `callbacks.draw` and `GameState` lines is in
[diagnostics.md](diagnostics.md).

## 7. On-device artifact table

`<files>` = `/data/data/org.runelite.mobile/files`; `<external>` =
`/sdcard/Android/data/org.runelite.mobile/files`; `<cache>` = the app cache dir. `app_*` dirs are
the private directories returned by `Context.getDir(name, MODE_PRIVATE)` (Android creates
`<data>/app_<name>`).

| Path | Producer | Consumer | Format |
|---|---|---|---|
| `<files>/runelite-dex.jar` | `MainActivity.bootstrapGameClient` (APK asset) / `ClientUpdater.installBundled` (update) | `DexClassLoader` in `MainActivity.bootstrapGameClient` | dexed jar |
| `<files>/runelite-dex.jar.old` | `ClientUpdater.installBundled` swap | deleted after a successful install | transient backup |
| `<files>/client-version.txt` | `ClientUpdater.installBundled` / `MainActivity.bootstrapGameClient` (asset version) | `MainActivity`, version line | one version token |
| `<files>/credentials.properties` | `MainActivity.writeCredentialsFile` | the game client's own login | java properties, `JX_*` keys |
| `<files>/oat/<isa>/runelite-dex.odex` | ART `dex2oat` | ART; read by `clientDexAotStatus` | AOT odex |
| `<files>/plugins/` | hub import target | `MobilePluginHub` | `*.jar` |
| `<files>/plugins/<name>.dex.jar` | `MobilePluginHub.dexOnDevice` (`OnDeviceDexer`, from the raw jar) | hub `DexClassLoader` | dexed jar; shadows the raw `<name>.jar` |
| `<external>/plugins/` | `adb push` | `MobilePluginHub` | `*.jar` |
| `<files>/rl-dexer.jar` | extracted from `assets/rl-dexer.jar` by `OnDeviceDexer` | `DexClassLoader` for D8 | jar |
| `app_dex/` | `MainActivity` (`getDir("dex", …)`) | ART dexopt output dir for the client dex | dex output |
| `app_dex-hub/` | `MobilePluginHub` (`getDir("dex-hub", …)`) | hub `DexClassLoader` output dir | dex output |
| `app_dex-r8/` | `OnDeviceDexer` (`getDir("dex-r8", …)`) | on-device dexer `DexClassLoader` output dir | dex output |
| `<cache>/fonts/` | `AndroidTextRenderer.setFontDir` | text rasteriser scratch | font cache |
| `<cache>/client-update/` | `ClientUpdater.downloadAndInstall` | deleted at the start of the next download | download scratch |
| `<external>/conformance.request` | operator | render loop | trigger file |
| `<external>/conformance-report.txt` | `PluginConformance` | operator | plain text report |
| `<external>/stackdump.request` | operator | main-thread 1 s tick + render loop 5 s tick | trigger file |
| `<external>/stackdump.txt` | `MainActivity.consumeStackDumpRequest` | operator | every thread's state + Java stack |

Anchors: `MainActivity` (boot block, launcher visibility, AOT status), `ClientUpdater`
(`clientDexAotStatus`), `MobilePluginHub.loadPlugins`/`dexOnDevice`,
`OnDeviceDexer.ensureLoaded`/`dex`.

## 8. SharedPreferences key table

The only preferences file is `RuneLiteMobilePrefs`, declared once in `MainActivity`
(`PREFS_NAME`) and once in `SidePanel` with the same literal. Re-derive the key set with:

```bash
grep -rnE '\.(putBoolean|putString|putLong|putInt|putFloat|putStringSet)\(' android/src/main/java/
```

That grep returns writes in `MainActivity.saveSessionState`, the credentials import in
`MainActivity.importCredentialsFile`, and `SidePanel.toggle` — eight distinct keys:

| Key | Type | Written by | Meaning |
|---|---|---|---|
| `jx_mode_enabled` | boolean | `MainActivity.saveSessionState`, `MainActivity.importCredentialsFile` | signed-in state; false clears the session |
| `jx_session_id` | string | `MainActivity.saveSessionState`, `MainActivity.importCredentialsFile` | `JX_SESSION_ID` mirror |
| `jx_character_id` | string | `MainActivity.saveSessionState`, `MainActivity.importCredentialsFile` | `JX_CHARACTER_ID` mirror |
| `jx_display_name` | string | `MainActivity.saveSessionState`, `MainActivity.importCredentialsFile` | `JX_DISPLAY_NAME` mirror |
| `oauth_access_token` | string | `MainActivity.saveSessionState` | OAuth access token |
| `oauth_refresh_token` | string | `MainActivity.saveSessionState` | OAuth refresh token |
| `oauth_expires_at` | long | `MainActivity.saveSessionState` | access-token expiry |
| `sidePanelOpen` | boolean | `SidePanel.toggle` | drawer open state |

`loadSessionState` reads the seven `jx_*`/`oauth_*` keys with defaults
(`MainActivity.loadSessionState`); if `jx_mode_enabled` is false or `jx_session_id` is empty, the
session is treated as signed out. The drawer's own state lives in `sidePanelOpen` only — the tab
index is not persisted (`SidePanel`). The session/credentials details are in
[login-and-sessions.md](login-and-sessions.md).
