# Client telemetry assessment

What this port can and cannot send to Jagex, and how to re-check it.

## 1. Purpose & scope

Answers "does running this port expose me to account action / leak client identity?"
by mapping the client-side telemetry substrate that the app actually runs.

- The port does **not** ship a client. At runtime it downloads RuneLite's official
  `injected-client` + `runelite-api` jars and runs them under `DexClassLoader`
  (`android/src/main/java/org/runelite/mobile/MainActivity.java`, bootstrap step 5).
- The checked-in marker is `android/src/main/assets/client-version.txt` (**1.12.35**);
  `ClientUpdater` re-downloads `static.runelite.net/bootstrap.json` at launch and
  bakes/dexes whatever version is current (observed live: **1.13.1**).
- The static reverse-engineering corpus is `~/git/re-osrs` (`java/BOT_DETECTION_J.md`,
  `java/DUPE_HUNT.md`, `java/JAVA_RE.md`), taken against `injected-client-1.12.38`.
  Class names are obfuscated per version; treat its names as the 1.12.x mapping and
  re-derive on a version bump (section 6).

Conclusion up front: the Java client has **no client-identity / anti-tamper reporting,
no automation mode, and no macro/bot detection code**. Account-action exposure is
therefore *policy* (this port is not on Jagex's approved list) and *behavioural*
(input/telemetry shape) — never "the server detected a modified client".

## 2. Client → Jagex channels

All verified in `~/git/re-osrs/java/BOT_DETECTION_J.md` and re-checked against the
live jar where noted.

| Channel | Carries | Source |
|---|---|---|
| Mouse packets `js.bb` (wire id 40, 7 B) | click count, x, y, `(dt<<1)+isClick` | `BOT_DETECTION_J.md` §1.2–1.3, Appendix A |
| Idle disconnect `js.ac` (id 7) | nothing (0 B) | §1.2, §6.4 |
| Keepalive `js.df` (id 99) | nothing (0 B) | §1.2, §6.4 |
| Login block UID | 24 bytes from `random.dat` | §1.4, §6.2 |
| Item transition ledger | `dx.ax` 7-field record → `ca.af(18)` → same socket | `DUPE_HUNT.md` §4 |
| Crash beacon | `GET clienterror.ws?cv=&cs=&u=&v1=&v2=&ct=&e=` | §2.1 |
| Fatal redirects `error_game_*.ws` | path label only | §2.2 |

Notes:

- **Beacon** (`u` = user id, `e` = condensed stack containing `Class.method` tokens)
  is fully armed but *statically orphaned* in the Java build (§6.1) — no static call
  path reaches its senders; a caller could exist in the non-decompiled runtime jar.
  In the live 1.13.1 jar the sender lives in `aat` (`aat.aj`, `aat.af`), and this port
  now neutralises it (section 4). So no request can be emitted.
- **`error_game_*`** is routed by `tq.qn` to the local
  `net.runelite.api.ClientConfiguration.onError` callback, i.e. to this port's proxy
  (`MainActivity` bootstrap step 6) — the label never reaches the network here.
- **Keys never go on the wire**: the only consumer of the keyboard queue is local
  widget dispatch (§1.1).
- No third-party analytics/crash SDK is present in this app; `android/build.gradle`
  depends only on `androidx.appcompat`, `com.google.android.material`, `guava`,
  `slf4j-api` (ASM/R8 are build-script-only: `org.ow2.asm:*` is on the buildscript
  classpath, and d8 comes from the SDK build-tools — neither is an app dependency).

## 3. What the client does NOT send

- No client-identity / "modified client" / anti-tamper reporting of any kind.
- No macro/bot/cheat detection strings in the Java corpus (only `macroExpand`, a text
  function — `BOT_DETECTION_J.md` §"Ruled out").
- No `--automate_ip/--automate_port` remote-automation mode and no dev console
  (both exist only in Jagex's C++ client — §3 cross-map rows 4–5).
- ISAAC (`yt`) masks only opcode bytes; lengths and payloads are plaintext (§5).

## 4. Port-specific deltas

- **Host**: `DexClassLoader` + AWT/Swing stubs under `core/src/main/java/java/…`; the
  game runs RuneLite's own bytecode, so its wire behaviour is RuneLite's.
- **ASM patch**: `downloadAndDexJar` (`android/build.gradle`) injects read-only
  `TileCompositor` hooks into the render loop and remaps Android-missing APIs at
  **build time only** — the client jar is pre-dexed in CI and the app just downloads
  it, so nothing is patched on-device. Every hook is guarded
  (`catch (Throwable)` → `TileCompositor.hookFailed`), so port code cannot throw into
  the game's frame loop and cannot contribute a class name to an error path.
- **Crash-beacon neutralisation**: the ASM pass forces any non-`<init>`/`<clinit>`
  method that references a `clienterror.ws…` literal to return immediately. Verified
  on the live 1.13.1 jar: `aat.aj`/`aat.af` now start with `return` (build log
  `[ASM] Neutered crash beacon in aat.aj`, and `javap` on
  `android/build/temp-jars/injected-client-cleaned.jar`). The pass runs over every
  class of the jar, so no gate can skip a class that carries the literal.
- **Input synthesis**: touch → AWT `MouseEvent`. `setupTouchInput` replays
  `MotionEvent` history and fills segments via `core/src/main/java/org/runelite/mobile/MousePath.java`
  so the client sees a continuous, monotonic motion stream instead of single jumps.
  A two-finger drag is synthesized as a **middle-button** (`BUTTON2`) press/drag/
  release at the two-finger centroid — the client's own camera-drag path — and the
  single-finger press is held off 120 ms so a second finger can never turn into a
  walk/attack click; the resulting wire shape is an ordinary drag (mouse packets),
  not an automation signal.
- **Software 3D presentation** (port-only, no wire effect): the 3D rasterizer's
  per-instance pixel target is re-pointed at the display buffer by
  `MainActivity.bindSceneRasterizerToDisplay()` (the client leaves it on a
  256×256 scratch and only the desktop runtime's resize path re-points it), and
  the 765×503 frame bitmap is drawn with alpha disabled
  (`renderBitmap.setHasAlpha(false)`) because the rasterizer emits alpha-0 3D
  pixels that `canvas.drawBitmap` would otherwise blend away. Both are required
  for the world to be visible at all.
- **OS fingerprint is truthful** (Linux/aarch64, no Windows spoofing); the JVM
  properties emulated are only the ones Android lacks.

## 5. Policy position

Jagex's approved client list is exactly **RuneLite** and **HDOS**; "the use of any
other third-party client may result in permanent action". The June 2022 update set
enforcement at a two-week ban for a first non-approved-client offence, permanent on
repeat. This port is an unofficial distribution that additionally bytecode-patches the
client, so it is **not** covered by RuneLite's approval. No client-identity channel
exists to trigger this automatically (section 3), but the policy exposure is real and
not appealable.

## 6. Refresh procedure

On a client version bump (watch `bootstrap.json` / `client-version.txt`):

1. Re-decompile: `java -jar ~/git/re-osrs/tools/vineflower-1.11.1.jar <injected-client-<ver>.jar> ~/git/re-osrs/java/decompiled/injected-client`.
2. Re-run the corpus scans in `~/git/re-osrs/java/naming_anchors.py` +
   `naming_candidates.py`, then re-derive the §2 channels from `BOT_DETECTION_J.md`'s
   methodology (send/recv opcode tables move every version).
3. Re-check the beacon: `unzip -p <client jar> '*.class' | strings | grep clienterror`
   to find the owning class, then confirm this port neutralises it — the build log
   must print `[ASM] Neutered crash beacon in <class>.<method>` and `javap` on
   `android/build/temp-jars/injected-client-cleaned.jar` must show a leading `return`.
   If the literal is absent, the pass is inert and no action is needed.
4. Re-check `grep -rl "firebase\|crashlytics\|analytics\|sentry" .` returns nothing in
   this repo (no added telemetry SDK).
