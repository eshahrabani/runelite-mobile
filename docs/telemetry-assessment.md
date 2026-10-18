# Client telemetry assessment

**Audience:** developer
**Read this when:** you need to know exactly what the running client can send to Jagex, or you are re-checking this port after a client version bump.
**Verified against:** `android/build.gradle` (`syncRuneLiteJars`, `transformClassBytes`, the crash-beacon pass), `MainActivity.java` (`bootGameClient` steps 4–6, `bindSceneRasterizerToDisplay`), `core/src/main/java/org/runelite/mobile/{TileCompositor,MousePath}.java`, `docs/rendering.md`, `docs/diagnostics.md`

What this port can and cannot send to Jagex, and how to re-check it.

## 1. Purpose & scope

Answers "does running this port expose me to account action / leak client identity?"
by mapping the client-side telemetry substrate that the app actually runs.

- The port does **not** ship a client. `downloadAndDexJar` (build time) downloads RuneLite's
  official `injected-client` + `runelite-api` + `client` jars, ASM-transforms them and dexes
  them into one `assets/runelite-dex.jar`; at runtime `MainActivity.bootGameClient` copies
  that asset into `files/` and loads it through a `DexClassLoader` (bootstrap step 4),
  instantiating the game in step 5. Nothing is patched on-device.
- The version marker `android/src/main/assets/client-version.txt` is **generated** by
  `downloadAndDexJar` (the version pinned by `bootstrap.json`) and is gitignored; it is not
  checked in. Its value in this working tree is **1.13.2**. On the device, `ClientUpdater`
  fetches the *published* `client-version.txt` from the CI release base
  (`BuildConfig.DIST_BASE`) and downloads the matching pre-dexed `runelite-dex.jar`; it never
  fetches `bootstrap.json`.
- Provenance: the channel map below was established by decompiling the shipped client jar
  (last full pass: `injected-client-1.12.38`) and cross-checking each wire writer's bytecode;
  rows that were re-verified against the 1.13.2 jar say so. Class names are obfuscated per
  version — treat the `js.bb`-style names as the 1.12.x mapping and re-derive them on a
  version bump (section 6).

Conclusion up front: the Java client has **no client-identity / anti-tamper reporting,
no automation mode, and no macro/bot detection code**. Account-action exposure is
therefore *policy* (this port is not on Jagex's approved list) and *behavioural*
(input/telemetry shape) — never "the server detected a modified client".

## 2. Client → Jagex channels

| Channel | Carries | Evidence |
|---|---|---|
| Mouse packets `js.bb` (wire id 40, 7 B) | click count, x, y, `(dt<<1)+isClick` | 1.12.x writer bytecode |
| Idle disconnect `js.ac` (id 7) | nothing (0 B) | 1.12.x writer bytecode |
| Keepalive `js.df` (id 99) | nothing (0 B) | 1.12.x writer bytecode |
| Login block UID | 24 bytes from `random.dat` | 1.12.x login path |
| Item transition ledger | `dx.ax` 7-field record → `ca.af(18)` → same socket | 1.12.x dupe analysis |
| Crash beacon | `GET clienterror.ws?cv=&cs=&u=&v1=&v2=&ct=&e=` | 1.13.2: literal in `aat`; neutralised (section 4) |
| Fatal redirects `error_game_*.ws` | path label only | 1.13.2: `to.co(URL)` → `ClientConfiguration.onError` (local) |

Notes:

- **Beacon** (`u` = user id, `e` = condensed stack containing `Class.method` tokens) is armed
  but *statically orphaned*: no class in the 1.13.2 `injected-client` or `client` jar holds a
  method reference to its senders `aat.aj`/`aat.af` (constant-pool scan), and the build pass
  in section 4 forces those methods to return immediately — so no request can be emitted by
  this port regardless of the call graph.
- **`error_game_*`** is routed by `to.co(URL)` to the local
  `net.runelite.api.ClientConfiguration.onError` callback, i.e. to this port's proxy
  (`MainActivity` bootstrap step 6, which logs `ClientConfig.onError: <label>` and returns) —
  the label never reaches the network here.
- **Keys never go on the wire**: the only consumer of the keyboard queue is local widget
  dispatch (1.12.x analysis).
- No third-party analytics/crash SDK is present in this app; the `:android` module depends
  only on `:core`, `androidx.appcompat:appcompat:1.6.1`,
  `com.google.android.material:material:1.11.0`, an `slf4j-api-*.jar` picked out of the
  downloaded RuneLite jars, and `org.slf4j:slf4j-simple:1.7.25` (ASM/R8 are build-script-only:
  `org.ow2.asm:*` is on the buildscript classpath, and d8 comes from the SDK build-tools —
  neither is an app dependency).

## 3. What the client does NOT send

- No client-identity / "modified client" / anti-tamper reporting of any kind.
- No macro/bot/cheat detection strings in the Java client (only `macroExpand`, a text
  function).
- No `--automate_ip/--automate_port` remote-automation mode and no dev console (both exist
  only in Jagex's C++ client, not in what this port runs).
- ISAAC (`yt`) masks only opcode bytes; lengths and payloads are plaintext (1.12.x analysis).

## 4. Port-specific deltas

- **Host**: `DexClassLoader` + AWT/Swing stubs under `core/src/main/java/java/…`; the game
  runs RuneLite's own bytecode, so its wire behaviour is RuneLite's.
- **ASM patch**: `downloadAndDexJar` (`android/build.gradle`) injects read-only
  `TileCompositor` hooks into the render loop and remaps Android-missing APIs at **build time
  only** — the client jar is pre-dexed in CI and the app just downloads it. Every hook is
  guarded (`catch (Throwable)` → `TileCompositor.hookFailed`), so port code cannot throw into
  the game's frame loop and cannot contribute a class name to an error path.
- **Crash-beacon neutralisation**: the ASM pass prepends a typed default return
  (`RETURN`/`ACONST_NULL`/`ICONST_0`/… per the method's return type) to every
  non-`<init>`/`<clinit>` method whose body loads a `clienterror.ws…` string constant.
  Verified on the 1.13.2 jar: `aat.aj`/`aat.af` now start with `0: return` (build log
  `[ASM] Neutered crash beacon in aat.aj`, and `javap -p -c` on
  `android/build/temp-jars/injected-client-cleaned.jar`). The pass runs over every class of
  the jar, so no gate can skip a class that carries the literal.
- **Input synthesis**: touch → AWT `MouseEvent`. `setupTouchInput` replays `MotionEvent`
  history and fills segments via `core/src/main/java/org/runelite/mobile/MousePath.java` so
  the client sees a continuous, monotonic motion stream instead of single jumps. A
  **one-finger drag** and a **two-finger rotate** are both synthesized as a **middle-button**
  (`BUTTON2`) press/drag/release — the client's own camera-drag path — at the finger (or the
  two-finger centroid); a pinch is a synthesized **mouse wheel** stream, and a long press or a
  two-finger tap is a **right click** (`BUTTON3`). The left press is **deferred**: nothing is
  pressed at touch-down, so no gesture can turn into a stray walk/attack click, and a plain
  tap sends press + release + click on lift. The resulting wire shape is ordinary mouse input,
  not an automation signal.
- **Software 3D presentation** (port-only, no wire effect): the 3D rasterizer's per-instance
  pixel target is re-pointed at the display buffer by
  `MainActivity.bindSceneRasterizerToDisplay()` (the client leaves it on a 256×256 scratch and
  only the desktop runtime's resize path re-points it), and the frame bitmap (765×503 in fixed
  mode, the device-aspect frame in fill mode — see section 6) is
  drawn with alpha disabled (`renderBitmap.setHasAlpha(false)`) because the rasterizer emits
  alpha-0 3D pixels that `canvas.drawBitmap` would otherwise blend away. Both are required for
  the world to be visible at all.
- **OS fingerprint is truthful** (Linux/aarch64, no Windows spoofing); the JVM properties
  emulated are only the ones Android lacks.

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

1. Decompile the jar the build actually shipped:
   `java -jar vineflower-<ver>.jar android/build/rl-jars/injected-client-<ver>.jar /tmp/decompiled`
   (any current Java decompiler works — the point is a fresh tree for the *shipped* version,
   because every name in section 2 is obfuscated per version).
2. Re-derive the section 2 channels from the writers: find the socket write path and the
   packet-id dispatch, and re-capture the mouse/idle/keepalive opcodes and their payload
   layouts (they move every version).
3. Re-check the beacon: find the owning class with
   `for c in $(unzip -Z1 <client jar> | grep '\.class$'); do unzip -p <client jar> "$c" | grep -q clienterror\.ws && echo "$c"; done`,
   then confirm this port neutralises it — the build log must print
   `[ASM] Neutered crash beacon in <class>.<method>` and `javap -p -c` on
   `android/build/temp-jars/injected-client-cleaned.jar` must show the method body starting
   with a return. If the literal is absent, the pass is inert and no action is needed.
4. Re-check the `error_game_` path: locate the class holding the `error_game_` literal and
   confirm it still calls `ClientConfiguration.onError` (local callback) rather than
   `java.applet.AppletContext.showDocument` (a real URL open).
5. Re-check `grep -rl "firebase\|crashlytics\|analytics\|sentry" .` returns nothing in
   this repo (no added telemetry SDK).
6. Re-derive the **client-internal names the port drives** (all obfuscated per version; they
   live in exactly two files — `ClientDisplay` for the display-mode applier, `MainActivity`
   for the rasterizer/palette probes):
   - the **display-mode applier** the fill mode uses, `ms.bo(int, byte)`, and its duplicates
     `client.ne/nw/nv(int)`: all four set the static boolean that is the entire body of
     `Client.isResized()` and request the shell maximum (`client.agh(7680, 2160, 0)`) or the
     fixed size. Re-find it from the identity: `javap -p -c client | grep -A3 'isResized'`
     names the field, then grep the jar for its `putstatic`. `ClientDisplay` tries
     `ms.bo` first and verifies `isResized()` actually flipped, so a rename costs one candidate
     and the launcher reports `game fill: unavailable` instead of breaking the game.
     **Only apply the resizable direction** (`ms.bo(2, 0)`): the fixed direction
     (`ms.bo(1, 0)`) requests the resizable 7680×2160 maximum anyway — it does not restore the
     fixed bounds — and the corrupt canvas state it leaves behind made the next client-side
     resize throw `NegativeArraySizeException` out of `tx.<init>`, killing the client thread.
     That is why fill off leaves the client's display mode alone.
   - the 3D rasterizer's binding method `yw.ef(int[], int, int, float[])` with its static target
     field `yw.ah` and depth array `yw.aw` (`bindSceneRasterizerToDisplay`), and the palette
     statics `fq.aq`, `fq.az/an/ah` and `fa.ak` (`paletteInvariant`).
   - the client's own resize path is reached through the **public** `GameEngine.resizeCanvas()`
     (`to.ahi(int)` behind it), which writes the canvas size and allocates the new frame buffer;
     the size is read back through `Client.getCanvasWidth/Height`, so the port never names the
     client's canvas-size fields.
   - a frame buffer's cached destination `java.awt.Graphics`: the client fetches it once per
     frame-buffer instance, so `invalidate...` (the type-based field search in
     `MainActivity.invalidateProviderGraphics`) must find it again if the class layout changes —
     only needed when the client resizes itself rather than the host.
   - the client's **shutdown deadline** `to.re` (a static long, plaintext name): the game loop
     `to.run` exits as soon as it is non-zero and the time has passed it. `ClientDisplay.shutdownDeadline`
     reads it for the `stop=` field of the frame log — `stop=arm` on the last frames before a
     frozen world means the client took its own stop/error path (the `to.stop()`/`destroy()`
     Applet lifecycle, `Client.stopNow()`), which is how the resize-frame rasteriser crash was
     diagnosed; an exception on the client thread is logged by the client itself as a
     `System.err` line tagged `[Client] ERROR injected-client - Client error` plus its stack.
