# Client updates and AOT

**Audience:** developer/operator
**Read this when:** the client jar must be updated, the update dialog appears, or the app is slow and `client AOT` looks wrong.
**Verified against:** `org.runelite.mobile.ClientUpdater` (`fetchAvailableClientVersion`, `downloadAndInstall`, `installBundled`, `clientDexAotStatus`), `MainActivity` (`startUpdateCheckAsync`, `checkForUpdates`, `showUpdateDialog`, `runClientUpdate`, `updateLauncherUi`), `SidePanel` (`client AOT` row), `android/build.gradle:32`, `.github/workflows/build.yml`.

The APK ships a dexed game client, but the game client changes every week. The app
therefore checks a published version marker and can download a replacement dex jar at
runtime. This page covers that updater and the AOT (ahead-of-time compile) status the
app reports. The build/CI side that *publishes* those artifacts is in
[build-and-release.md](build-and-release.md); the adb steps that make an updated dex
fast again are in [device-runbook.md](device-runbook.md).

The updater lives in `org.runelite.mobile.ClientUpdater`. It never dexes anything
on-device: the build pipeline produces `runelite-dex.jar`, and CI publishes it next to
`client-version.txt` and `runelite-dex.jar.sha256` (see `BuildSystemFacts`-verified
asset list in `.github/workflows/build.yml`). `ClientUpdater` only downloads, verifies
and swaps files.

## 1. Version discovery and compare

Three version strings are in play:

| Method | Source | On failure |
|---|---|---|
| `ClientUpdater.currentClientVersion(context)` | `assets/client-version.txt` (the version baked into this APK) | returns literal `"unknown"` |
| `ClientUpdater.installedClientVersion(context)` | `files/client-version.txt` (the version actually in use) | falls back to `currentClientVersion` |
| `ClientUpdater.fetchAvailableClientVersion()` | `BuildConfig.DIST_BASE + "client-version.txt"` over HTTP | returns `null` |

`installedClientVersion` returns the asset version when `<filesDir>/client-version.txt`
does not exist, so a freshly installed app reports the bundled version
(`ClientUpdater.installedClientVersion`). This is the string written by the installer in
§2.

`fetchAvailableClientVersion` issues a `GET` on the publish URL with an
`HttpURLConnection`, connect timeout 15000 ms and read timeout 30000 ms
(`ClientUpdater.fetchAvailableClientVersion`). A response code `>= 400` logs
`client-version.txt returned <code>` and yields `null`; the body is trimmed and an empty
body also yields `null`; any exception logs `Failed to fetch published client version`
and yields `null`. The base URL is `BuildConfig.DIST_BASE`, whose default literal is
declared in `android/build.gradle:32` as
`https://github.com/eshahrabani/runelite-mobile/releases/latest/download/`, overridable
at build time with `-PdistBase=<url>/` (trailing slash required). `BuildConfig.DIST_BASE`
is also what the operator changes to point a self-hosted build at a fork; see
[build-and-release.md](build-and-release.md).

**The "is there an update?" decision is exact-string equality, not version ordering.**
The background check compares `installed.equals("unknown") || latest.equals(installed)`
and does nothing when either holds (`MainActivity.startUpdateCheckAsync`); the manual
Check-for-updates button likewise shows "Client is up to date" only when
`latest.equals(installed)` (`MainActivity.checkForUpdates`). There is no
semver/major-minor ordering in `ClientUpdater` at all. A consequence worth knowing: if
the published version string ever moves *backwards*, the app still offers the download,
because it is not comparing magnitude.

A separate numeric compare does exist, but it is not used for the online update
decision. `MainActivity.compareVersions` splits on `.` and compares each component
numerically; `versionIsOlderThanAsset()` uses it while booting the client to decide
whether the APK's bundled dex is newer than the one in `files/` and must be re-extracted
(`MainActivity.versionIsOlderThanAsset`, `MainActivity.compareVersions`). That path
reconciles the bundled asset at boot and is described in §4. It is not the updater's
update check.

Startup check: `MainActivity.startUpdateCheckAsync()` runs `fetchAvailableClientVersion`
on a thread named `UpdateCheck` and posts the dialog to the UI thread once
(`updateDialogShown` guards against repeat dialogs) (`MainActivity.startUpdateCheckAsync`).

## 2. Download, verify, install

`ClientUpdater.downloadAndInstall(context, version, listener)` stages everything in a
throwaway cache dir first; nothing touches the installed jar until the download has been
hash-verified.

```text
<filesDir>/
  runelite-dex.jar          installed client (read-only)
  runelite-dex.jar.old      transient backup during a swap
  client-version.txt        installed version marker
<cacheDir>/
  client-update/<version>/  staging: .sha256 + runelite-dex.jar
```

Steps (`ClientUpdater.downloadAndInstall`):

1. Delete and recreate `<cacheDir>/client-update`, then `mkdirs()` `<cachedir>/<version>`;
   a `mkdirs()` failure throws `IOException("Cannot create cache dir")`.
2. Download `<DIST_BASE>runelite-dex.jar.sha256` into the staging dir (stage string
   `Downloading checksum`).
3. Take the first whitespace-delimited token of the checksum file and require it to match
   `[0-9a-fA-F]{64}`; otherwise throw `IOException("Invalid published checksum")`. The
   published file is a bare 64-hex digest with no filename, which is why the parser takes
   the first token (see the CI `sha256sum … | awk '{print $1}'` step in
   `.github/workflows/build.yml` and [build-and-release.md](build-and-release.md)).
4. Download `<DIST_BASE>runelite-dex.jar` into the staging dir (stage
   `Downloading game client`).
5. Stream the staged jar through SHA-256 and compare case-insensitively with the expected
   digest; a mismatch throws
   `IOException("Checksum mismatch (expected <lower>, got <actual>)")`
   (`ClientUpdater.sha256`).
6. Report `Installing update` at 100% and call `installBundled`.

The transfer itself (`ClientUpdater.download`) uses a 20000 ms connect timeout and a
60000 ms read timeout, reads in 64 KiB blocks, and throws
`IOException("Download failed (<code>): <url>")` on HTTP `>= 400`. Progress percentage is
`written*100/total` when the server sends a length; the throughput/ETA clock is restarted
after the **first** block so TLS setup and connection overhead do not skew the rate.

### The install swap is not a single atomic rename

`ClientUpdater.installBundled` performs the swap in place:

1. Delete any leftover `<filesDir>/runelite-dex.jar.old` (logs
   `Could not delete old backup` if it will not delete).
2. Rename the current `runelite-dex.jar` to `runelite-dex.jar.old` (logs
   `Could not move current dex jar aside` on failure).
3. Move the verified staging jar into place with `renameTo`; on failure it falls back to a
   64 KiB streaming copy.
4. `setReadOnly()` the installed jar — ART refuses a writable dex.
5. Write `version` into `<filesDir>/client-version.txt` (no trailing newline by
   construction).
6. Delete the `.old` backup and log
   `Client update installed: <n> bytes, version <v>`.

Because the swap is rename-out, rename-in, then delete-backup, a process death between
those steps can leave a `runelite-dex.jar.old` behind. The next install deletes it first
(step 1), and `installedClientVersion` still reads `client-version.txt`, so the leftover
backup is harmless — but it is not a single atomic rename and the app does not fsync any
of it.

> Source quirk: step 3 contains a duplicated nested check
> `if (!bundled.renameTo(target)) { if (!bundled.renameTo(target)) { … copy … } }`
> (`ClientUpdater.installBundled`), so the copy fallback only runs if a second rename
> fails. Documented here so a reader of the code is not surprised; it does not change the
> observable contract.

## 3. Restart prompt and failure semantics

Two dialogs drive the user flow, both in `MainActivity`:

| Dialog | When | Buttons |
|---|---|---|
| "RuneLite client update available" | after the version check finds `latest != installed` | `Update now` → `runClientUpdate(latest)`; `Later` |
| "Client updated" | after `downloadAndInstall` returns | `Restart now` → `restartClient()`; `Not now` |

The first dialog's message names both versions and states the old client keeps working if
declined (`MainActivity.showUpdateDialog`). The second appears after `updateLauncherUi()`
has refreshed the version line, so the new version is already visible behind it
(`MainActivity.runClientUpdate`). Neither dialog is a state machine: declining is a no-op
and the check simply runs again next launch.

Failure is contained by design. `runClientUpdate` wraps the download/install in a
`catch (Throwable)` and, on failure, shows "Update failed" with the message and
`The old client is still installed…` (`MainActivity.runClientUpdate`). Because
`installBundled` does not touch the installed jar until the verified staging file is
moved in, a failed fetch, a bad digest, a disk error, or a process kill during download
all leave the previously installed client and its `client-version.txt` intact and
runnable. The recovery advice in the dialog — update the whole app if the game stops
connecting after a weekly OSRS update — covers the case where the published client has
moved on but the app's own glue code needs a matching release.

There is also an offline/manual path: `MainActivity.checkForUpdates(manual)` shows a
toast "Client is up to date" or the same update dialog, and reports
`Could not check for updates (network error).` when `fetchAvailableClientVersion` returns
`null`. This is what the launcher's Check-for-updates button invokes; it does not differ
in install behaviour from the automatic path.

## 4. AOT status

The app ships a separate dex (`<filesDir>/runelite-dex.jar`) that ART must ahead-of-time
compile. On this device `dalvik.vm.usejit=false` (JIT disabled), so a dex with no usable
odex runs **interpreted, roughly 10x slower**. The app cannot run `pm compile` itself (no
root), so AOT is an operator step; the app's job is to *report* whether it has taken
effect. See [device-runbook.md](device-runbook.md) for the compile commands.

### The mtime heuristic

`ClientUpdater.clientDexAotStatus(context)` is a heuristic over file timestamps
(`ClientUpdater.clientDexAotStatus`):

1. If `<filesDir>/runelite-dex.jar` is not a file → `AOT_MISSING`.
2. The odex name is the dex asset name minus `.jar` plus `.odex` → `runelite-dex.odex`.
3. List `<filesDir>/oat/`; if `listFiles()` returns `null` (missing or unreadable) →
   `AOT_UNKNOWN` ("do not claim anything").
4. For every ISA subdirectory, look for `oat/<isa>/runelite-dex.odex` and keep the newest
   mtime. If none exists → `AOT_MISSING`.
5. Compare the newest odex mtime against
   `max(runelite-dex.jar.lastModified(), getPackageCodePath().lastModified())`. If the
   odex is at least as new → `AOT_OK`, else → `AOT_STALE`.
6. Any `Throwable` → log `client dex AOT check failed: <t>` and return `AOT_UNKNOWN`.

The rationale, from the method's own javadoc: ART keys the odex to the invoking
class-loader context, which embeds the base APK path plus the checksums of the APK and the
dex. A new APK install therefore invalidates the odex, and a downloaded client-jar update
changes the dex checksum — both covered by the timestamp rule above.

| Constant | Value | Meaning |
|---|---|---|
| `AOT_OK` | `0` | usable `[status=speed]` odex |
| `AOT_STALE` | `1` | odex present but older than the APK/jar (ART runs the `verify` vdex) |
| `AOT_MISSING` | `2` | no odex at all |
| `AOT_UNKNOWN` | `3` | `files/oat` unreadable — claim nothing |

`clientDexAotText(context)` turns a status into the UI string
(`ClientUpdater.clientDexAotText`):

| Status | Text |
|---|---|
| `AOT_OK` | `compiled (speed)` |
| `AOT_STALE` | `stale - not AOT-compiled, game runs interpreted (~10x slower); fix: adb shell <AOT_FIX_COMMAND>` |
| `AOT_MISSING` | `missing - not AOT-compiled, game runs interpreted (~10x slower); fix: adb shell <AOT_FIX_COMMAND>` |
| `AOT_UNKNOWN` | `unknown (files/oat unreadable)` |

`AOT_FIX_COMMAND` is the literal
`cmd package compile -m speed -f --secondary-dex org.runelite.mobile`
(`ClientUpdater.AOT_FIX_COMMAND`). It is only a string shown to the user — the app never
executes it.

### Where the status shows

| Surface | Behaviour |
|---|---|
| Boot logcat | if status is neither `AOT_OK` nor `AOT_UNKNOWN`, logs `Client dex is NOT AOT-compiled: <text> -- dalvik.vm.usejit=false, so the game runs interpreted (~10x slower)` at warning level; otherwise logs `Client dex AOT: <text>` (`MainActivity.onCreate`, boot step 5) |
| Launcher version line | `updateLauncherUi` prints `Client v<current>` or `Client v<installed> (APK ships v<current>)`; when status is `AOT_STALE` or `AOT_MISSING` it appends `NOT AOT-COMPILED - expect ~5 fps (Host tab)` and colours the line red (`0xFFE57373`) (`MainActivity.updateLauncherUi`) |
| Host tab | a `client AOT` row rendering `clientDexAotText`; red `client AOT: <text>` when stale/missing, otherwise a normal host line (`SidePanel`) |

`AOT_UNKNOWN` is deliberately not treated as bad: both the launcher line and the Host row
only red-flag `AOT_STALE`/`AOT_MISSING` (`MainActivity.updateLauncherUi`,
`SidePanel`). An unreadable `files/oat` should not tell the operator the game is slow when
the app simply cannot tell.

### AOT and the boot reconciliation path

Before login, `MainActivity.onCreate` (boot step 5b) pre-registers the game dex by
constructing the same `DexClassLoader` the client will use, so dex2oat can compile it. It then chooses between the `files/` copy and the APK asset:
`useExisting` requires the local jar to exist, to not be older than the asset
(`versionIsOlderThanAsset`), and to have an mtime at least the APK's
(`MainActivity.bootstrapGameClient`). If not, the stale jar is deleted and the bundled
asset is copied out. Either way the jar is made read-only, matching the updater's
invariant and ART's "writable dex file is not allowed" rule.

Both the pre-registration and the extracted file feed the same odex cache keyed to the
class-loader context, which is why the AOT warning appears right after a fresh install or
a client-jar update and disappears only once the operator re-runs the compile shown in
[device-runbook.md](device-runbook.md).
