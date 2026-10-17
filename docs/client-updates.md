# Client updates and AOT

**Audience:** developer/operator
**Read this when:** the client jar must be updated, the launcher update line changes, or the app is slow and `client AOT` looks wrong.
**Verified against:** `org.runelite.mobile.ClientUpdater` (`fetchAvailableClientVersion`, `currentClientVersion`, `installedClientVersion`, `downloadAndInstall`, `installBundled`, `download`, `sha256`, `clientDexAotStatus`, `clientDexAotText`, `AOT_FIX_COMMAND`, the `AOT_*` constants), `MainActivity` (`startUpdateCheckAsync`, `updateClientStatus`, `checkForUpdates`, `showUpdateDialog`, `runClientUpdate`, `updateLauncherUi`, `refreshHostCard`, `bootstrapGameClient`, `compareVersions`, `versionIsOlderThanAsset`, `onResume`/`onPause`), `android/build.gradle` (`DIST_BASE` `buildConfigField`, `distBase` property), `.github/workflows/build.yml` (`check` job, `Checksum the dexed client` step, the release `files:` list).

The APK ships a dexed game client, but the game client changes every week. The app
therefore checks a published version marker and can download a replacement dex jar at
runtime. This page covers that updater and the AOT (ahead-of-time compile) status the
app reports. The build/CI side that *publishes* those artifacts is in
[build-and-release.md](build-and-release.md); the adb steps that make an updated dex
fast again are in [device-runbook.md](device-runbook.md).

The updater lives in `org.runelite.mobile.ClientUpdater`. It never dexes anything
on-device: the build pipeline produces `runelite-dex.jar`, and CI publishes it together
with `client-version.txt` and `runelite-dex.jar.sha256` (the release also carries the APK
itself — the exact asset list is the `files:` block of the `distribute` job in
`.github/workflows/build.yml`). `ClientUpdater` only downloads, verifies and swaps files.

## 1. Version discovery and compare

Three version strings are in play:

| Method | Source | On failure |
|---|---|---|
| `ClientUpdater.currentClientVersion(context)` | `assets/client-version.txt` (the version baked into this APK) | returns literal `"unknown"` |
| `ClientUpdater.installedClientVersion(context)` | `files/client-version.txt` (the version actually in use) | falls back to `currentClientVersion` |
| `ClientUpdater.fetchAvailableClientVersion()` | `BuildConfig.DIST_BASE + "client-version.txt"` over HTTP | returns `null` |

`installedClientVersion` returns the asset version when `<filesDir>/client-version.txt`
does not exist (or cannot be read), so a freshly installed app reports the bundled version
(`ClientUpdater.installedClientVersion`). This is the string written by the installer in
§2 and by the boot asset copy in §4.

`fetchAvailableClientVersion` issues a `GET` on the publish URL with an
`HttpURLConnection`, connect timeout 15000 ms and read timeout 30000 ms
(`ClientUpdater.fetchAvailableClientVersion`). A response code `>= 400` logs
`client-version.txt returned <code>` and yields `null`; the body is trimmed and an empty
body also yields `null`; any exception logs `Failed to fetch published client version`
and yields `null`. The base URL is `BuildConfig.DIST_BASE`, declared in
`android/build.gradle` as the `DIST_BASE` `buildConfigField`
(`project.findProperty('distBase') ?:`) with the default literal
`https://github.com/eshahrabani/runelite-mobile/releases/latest/download/`, overridable
at build time with `-PdistBase=<url>/` (trailing slash required). `BuildConfig.DIST_BASE`
is also what the operator changes to point a self-hosted build at a fork; see
[build-and-release.md](build-and-release.md).

**The "is there an update?" decision is an ordered component compare, not string
inequality.** `startUpdateCheckAsync` offers the published version only when
`compareVersions(latest, installed) > 0` and both strings are usable
(`latest != null`, `!installed.equals("unknown")`)
(`MainActivity.startUpdateCheckAsync`). `MainActivity.compareVersions` splits both strings
on `.`, parses each component with `parseInt` (a non-numeric component counts as `0`),
pads the shorter side with zeros and returns the first differing component — so `1.10.x`
is correctly newer than `1.9.x` (`MainActivity.compareVersions`). A published version
*older* than the installed one is refused rather than offered: the status line says
"nothing to do" and the manual path shows a toast (§3). A release that reuses the
*identical* version string never triggers either (`compareVersions` returns `0`), which is
the property the CI freshness guard relies on when it skips a same-version rebuild — its
`check` job compares bootstrap.json's client version with the latest release's
`client-version.txt` by string equality (`[ "$published" = "$released" ]`) and skips the
building jobs when they match (`.github/workflows/build.yml`).

A second caller of `compareVersions` exists but is not the online update decision:
`MainActivity.versionIsOlderThanAsset()` uses it at boot — after a short-circuit on
`installed.equals(asset)` — to decide whether the APK's bundled dex is newer than the one
in `files/` and must be re-extracted (`MainActivity.versionIsOlderThanAsset`). That path
is described in §4.

**Polling while the app is in front.** `onResume` sets `updatePollActive = true` and calls
`startUpdateCheckAsync()`; `onPause` clears the flag and removes the pending callback
(`MainActivity.onResume`, `MainActivity.onPause`). The check itself runs
`fetchAvailableClientVersion` on a thread named `UpdateCheck`, then on the UI thread calls
`updateClientStatus`, decides whether to pop the dialog, and re-arms itself with
`updatePollHandler.postDelayed(updatePollTask, UPDATE_POLL_MS)` — 2 minutes
(`MainActivity.UPDATE_POLL_MS = 120_000`). The dialog is only shown while the launcher is
visible (`launcherScroll` is `VISIBLE`), never for a version the user already declined
(`declinedVersion`, set by the dialog's `Later`), and never while `updateInProgress`. The
code's own measurement: a whole CI run costs ~190 s (Android job 177 s, publish 7 s), so a
2-minute poll bounds publish → offered at ~2 min.

**The launcher carries the whole update state in one line** (`updateClientStatus`, below
the version line and the Check-for-updates button):

| Status line | Condition |
|---|---|
| `Can't reach the update server (last checked <HH:mm>)\nIf the game says the client is out of date, a new build is on its way -- retrying every 2 min.` | `latest == null`; `UiTheme.RED` text in a red rounded box, no click handler |
| `Update available: v<from> -> v<latest> (checked <HH:mm>)\nTap to download and install now.` | `compareVersions(latest, installed) > 0`; amber `0xFFFFD75E`, tap opens `showUpdateDialog` |
| `Client v<installed> is newer than the published v<latest> -- nothing to do (checked <HH:mm>).` | `compareVersions < 0`; muted, no click handler |
| `Client v<installed> is up to date (checked <HH:mm>).` | `compareVersions == 0`; dim, no click handler |

`<from>` is `installed`, or the literal `installed` when the installed version is null or
`"unknown"`. The stamp is refreshed with `SimpleDateFormat("HH:mm", Locale.US)` on every
reachable check and retained across an unreachable one
(`MainActivity.updateClientStatus`).

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
  client-update/<version>/  staging: runelite-dex.jar.sha256 + runelite-dex.jar
```

Steps (`ClientUpdater.downloadAndInstall`):

1. `deleteRecursive` `<cacheDir>/client-update` if it exists, then `mkdirs()`
   `<cacheDir>/<version>`; a `mkdirs()` failure throws
   `IOException("Cannot create cache dir")`.
2. Download `<DIST_BASE>runelite-dex.jar.sha256` into the staging dir (stage string
   `Downloading checksum`).
3. Take the first whitespace-delimited token of the checksum file and require it to match
   `[0-9a-fA-F]{64}`; otherwise throw `IOException("Invalid published checksum")`. The
   published file is a bare 64-hex digest with no filename, which is why the parser takes
   the first token (see the CI `sha256sum … | awk '{print $1}'` step, named
   `Checksum the dexed client`, in `.github/workflows/build.yml` and
   [build-and-release.md](build-and-release.md)).
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
after the **first** block so TLS setup and connection overhead do not skew the rate. Each
request logs `Downloaded <name> (<n> bytes)`.

### The install swap is not a single atomic rename

`ClientUpdater.installBundled` (private) performs the swap in place:

1. Delete any leftover `<filesDir>/runelite-dex.jar.old` (logs
   `Could not delete old backup` if it will not delete).
2. Rename the current `runelite-dex.jar` to `runelite-dex.jar.old` (logs
   `Could not move current dex jar aside` on failure).
3. Move the verified staging jar into place with `renameTo`; on failure it falls back to a
   64 KiB streaming copy.
4. `setReadOnly()` the installed jar — ART refuses a writable dex.
5. Write `version` into `<filesDir>/client-version.txt` (no trailing newline by
   construction).
6. Delete the `.old` backup (logs `Could not delete backup dex jar` on failure) and log
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
| "RuneLite client update available" | the poll (or the manual check) found `compareVersions(latest, installed) > 0`, the launcher is visible, the version was not already declined, and no update is in progress | `Update now` → `runClientUpdate(latest)`; `Later` → `declinedVersion = latest` |
| "Client updated" | after `downloadAndInstall` returns | `Restart now` → `restartClient()`; `Not now` |

The first dialog's message names both versions and states the ~6 MB size plus that the old
client keeps working if declined — but that after a weekly OSRS update the game may refuse
it until the update is installed (`MainActivity.showUpdateDialog`). The second appears
after `updateLauncherUi()` has refreshed the version line and after a fresh
`startUpdateCheckAsync()` has recomputed the status line, so the new version is already
visible behind it (`MainActivity.runClientUpdate`). "Later" is therefore not a permanent
no-op: `declinedVersion` suppresses only that one version's dialog, the poll keeps running
while the activity is resumed, and a *newer* version asks again; a successful install
clears `declinedVersion` (`MainActivity.declinedVersion`,
`MainActivity.startUpdateCheckAsync`).

Failure is contained by design. `runClientUpdate` wraps the download/install in a
`catch (Throwable)` and, on failure, shows "Update failed" with the message plus
"The old client is still installed. You can try again, or if the game stops connecting
after a weekly OSRS update, update the app instead." (`MainActivity.runClientUpdate`).
Both outcomes also hide the progress bar, clear `updateInProgress` and re-enable the
Check-for-updates button; the progress line (`tvUpdateText`) shows
`Downloading update...`, then the listener's stage + percent + ETA, then
`Update failed: <message>`. Because `installBundled` does not touch the installed jar
until the verified staging file is moved in, a failed fetch, a bad digest, a disk error,
or a process kill during download all leave the previously installed client and its
`client-version.txt` intact and runnable. The recovery advice in the dialog — update the
whole app if the game stops connecting after a weekly OSRS update — covers the case where
the published client has moved on but the app's own glue code needs a matching release.

There is also an offline/manual path: the launcher's Check-for-updates button calls
`MainActivity.checkForUpdates(true)`, which does its own `fetchAvailableClientVersion` on
an `UpdateCheck` thread and reports `Checking for updates...`, then
`Could not check for updates (network error).` when the fetch returns `null`. Otherwise it
refreshes the status line and branches on the same compare: `0` → toast
`Client is up to date (v<installed>)`; `< 0` → toast
`Installed client v<installed> is newer than the published v<latest> -- nothing to do`;
`> 0` → the update dialog. The `manual` parameter is never read — the method behaves
identically however it is called (`MainActivity.checkForUpdates`).

## 4. AOT status

The app ships a separate dex (`<filesDir>/runelite-dex.jar`) that ART must ahead-of-time
compile. On this device `dalvik.vm.usejit=false` (JIT disabled), so a dex with no usable
odex runs **interpreted, roughly 10x slower**. Neither the app nor the updater can run
`pm compile` itself (no root), so AOT is an operator step; the app's job is to *report*
whether it has taken effect. See [device-runbook.md](device-runbook.md) for the compile
commands.

### The mtime heuristic

`ClientUpdater.clientDexAotStatus(context)` is a heuristic over file timestamps
(`ClientUpdater.clientDexAotStatus`):

1. If `<filesDir>/runelite-dex.jar` is not a file → `AOT_MISSING`.
2. The odex name is the dex asset name minus `.jar` plus `.odex` → `runelite-dex.odex`.
3. List `<filesDir>/oat/`; if `listFiles()` returns `null` (missing or unreadable) →
   `AOT_UNKNOWN` ("do not claim anything").
4. For every ISA subdirectory, look for `oat/<isa>/runelite-dex.odex` and keep the newest
   mtime. If none exists → `AOT_MISSING`.
5. Compare the newest odex mtime against `runelite-dex.jar.lastModified()` — the **client
   dex**, not the APK. If the odex is at least as new → `AOT_OK`, else → `AOT_STALE`.
6. Any `Throwable` → log `client dex AOT check failed: <t>` and return `AOT_UNKNOWN`.
7. Every call also logs `AOT check: odex=<ms> jar=<ms> apk=<ms>` (the APK mtime is logged
   for diagnosis, not compared) so the verdict can be explained without guessing.

**Why the APK mtime is not a validity input.** The earlier rule used
`max(jar.lastModified(), getPackageCodePath().lastModified())`, which produced a permanent
false `NOT AOT-COMPILED` after every install + recompile: measured on the target device,
`pm art dump` reports `[status=speed]` for `files/runelite-dex.jar` while the odex file is
still older than the freshly installed APK — ART does not rewrite an odex whose dex input
is unchanged, so the file timestamp cannot see the operator's recompile. The actionable
event (the client dex changing, i.e. a downloaded update or an APK that ships a different
client) does move the jar's mtime, and that is what the rule now compares. `pm art dump`
remains the ground truth for the install-dir case (see
[device-runbook.md](device-runbook.md)).

| Constant | Value | Meaning |
|---|---|---|
| `AOT_OK` | `0` | usable `[status=speed]` odex |
| `AOT_STALE` | `1` | odex present but older than the client jar (ART runs the `verify` vdex) |
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
executes it, and the runbook documents the two-pass order it belongs to.

### Where the status shows

| Surface | Behaviour |
|---|---|
| Boot logcat | `MainActivity.onCreate` boot step 5: if the status is neither `AOT_OK` nor `AOT_UNKNOWN`, logs at warning level `Client dex is NOT AOT-compiled: <text> -- dalvik.vm.usejit=false, so the game runs interpreted (~10x slower)`; otherwise logs `Client dex AOT: <text>` |
| Launcher version line | `updateLauncherUi` prints `Client v<current>` when `current.equals(installed)`, else `Client v<installed> (APK ships v<current>)`; when the status is `AOT_STALE` or `AOT_MISSING` it appends `\nNOT AOT-COMPILED - expect ~5 fps (Host section on this screen)` and styles the line as a warning: colour `UiTheme.RED` (`0xFFE57373`), text size 12f, red rounded background, 8 dp vertical padding (`MainActivity.updateLauncherUi`) |
| Host section on the launcher | `refreshHostCard` adds a `client AOT` row rendering `clientDexAotText`; when stale/missing it is replaced by a red full-width `client AOT: <text>` line, otherwise it is a normal host row (`MainActivity.refreshHostCard`) |

`AOT_UNKNOWN` is deliberately not treated as bad: both the launcher line and the Host row
only red-flag `AOT_STALE`/`AOT_MISSING` (`MainActivity.updateLauncherUi`,
`MainActivity.refreshHostCard`). An unreadable `files/oat` should not tell the operator the
game is slow when the app simply cannot tell.

### AOT and the boot reconciliation path

Before login, `MainActivity.onCreate` (boot step 5b) pre-registers the game dex by
constructing the same `DexClassLoader` the client will use, so dex2oat can compile it. It
then chooses between the `files/` copy and the APK asset: `useExisting` requires the local
jar to exist and be non-empty, to not be older than the asset
(`versionIsOlderThanAsset`), and to have an mtime at least the APK's
(`MainActivity.bootstrapGameClient`). If not, the stale jar is deleted and the bundled
asset is copied out, then its mtime is set back to the APK's install time — the bytes are
identical to the asset, so ART keeps using the existing `speed` odex (its class-loader
context is keyed to the dex checksum) and a copy stamped "now" would otherwise make a good
odex report as stale on the next launch. In the same branch the asset version is written
into `files/client-version.txt`, so an APK update over an older client does not re-copy
the asset on every boot and the update check does not nag for a client that is already
installed. Either way the jar is made read-only before the `DexClassLoader` is built,
matching the updater's invariant and ART's "writable dex file is not allowed" rule.

Both the pre-registration and the extracted file feed the same odex cache keyed to the
class-loader context, which is why the AOT warning appears right after a fresh install or
a client-jar update and disappears only once the operator re-runs the compile shown in
[device-runbook.md](device-runbook.md).
