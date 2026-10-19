# AGENTS.md

RuneLite/OSRS port to Android/iOS. The app does **not** ship a custom client:
it downloads RuneLite's official injected client (pre-dexed by CI) and loads it via
`DexClassLoader`, stubbing out the desktop JVM classes the game expects.

## Build commands

- `./gradlew :core:compileJava` — compiles offline, no Android SDK needed.
- `./gradlew :android:assembleDebug` / `:android:assembleRelease` — full APK.
  The `downloadAndDexJar` task hooks into `preBuild` and **requires network**
  (static.runelite.net + repo.runelite.net) and Android SDK build-tools (d8).
  Offline android builds fail at preBuild, not at compile.
  Release signing identity comes from `android/keystore.properties` (gitignored:
  `storeFile`/`storePassword`/`keyAlias`/`keyPassword`) or the `KEYSTORE_FILE`/
  `KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars; without one the release
  APK is debug-key-signed and `packageRelease` prints a warning. Use the **same**
  identity as CI (secret `SIGNING_KEYSTORE_B64` + `SIGNING_KEYSTORE_PASSWORD`,
  alias `runelitemobile`): an APK signed with a different key cannot be installed
  over a release (INSTALL_FAILED_UPDATE_INCOMPATIBLE), so the only way forward is
  a data-wiping uninstall.
- `./gradlew :ios:robovmIPABuild` — macOS + Xcode only (RoboVM 2.3.24).
- CI (`.github/workflows/build.yml`): freshness-gated schedule (Wed 07-21 UTC every
  5 min, thinning outwards; a manual dispatch always builds and publishes); artifact
  at `android/build/outputs/apk/release/android-release.apk`.
- Java 11 bytecode everywhere (`sourceCompatibility`/`targetCompatibility` = 11).
  The CI Android job runs Zulu **17** (cmdline-tools 16.0's `sdkmanager` and AGP 8.2.2
  require it; `javac 17` still honours `core/`'s `--limit-modules`); the iOS job runs
  Zulu 11. Any local JDK ≥ 11 works. No unit-test framework and no lint config —
  verification is the on-device selftests plus the conformance pass.

## Architecture (non-obvious from filenames)

- **`core/`** — java-library that compiles with
  `--limit-modules java.base,jdk.unsupported` (core/build.gradle) so it can
  define stub classes in the **`java.applet.*` / `java.awt.*` namespace**
  (134 files under `core/src/main/java/java/`). The game client paints into
  these. Never remove the `--limit-modules` flag — it's what legally lets a
  `java.*` package compile outside the JDK.
- **`core` stubs that replace missing Android JVM APIs**: `UnsafeHelper`
  (array offsets + `copyMemory` — Android has no `sun.misc.Unsafe`),
  `ProcessHandle`/`ProcessHandleImpl` (Android lacks `java.lang.ProcessHandle`).
  `javax.imageio.ImageIO.read(InputStream)` decodes via BitmapFactory
  (reflection — core can't reference android.*).
- **`android/build.gradle` `downloadAndDexJar`** is the build-time codegen
  pipeline: read RuneLite `bootstrap.json` → download
  `injected-client-<ver>.jar` + `runelite-api-<ver>.jar` (both pinned to the
  **same version from one artifact** — never mismatch them) → ASM-transform
  → d8 → `android/src/main/assets/runelite-dex.jar` + writes
  `assets/client-version.txt`. The ASM transform (`transformClassBytes` in
  build.gradle) runs on **every** class of the jar — never re-introduce a
  per-class gate: a class whose only Android-hostile feature is a
  `CONSTANT_Dynamic` (e.g. `ar.class`) then ships untransformed and d8 aborts
  with "Unsupported dynamic constant (has arguments to bootstrap method)".
  `ConstantBootstraps.invoke` constants are rewritten into explicit bytecode
  **memoised in a synthetic static field**, because the JVM resolves such a
  constant once per constant-pool entry and reuses that instance — the
  obfuscator builds its singletons that way (`client.ib()`/`ar.zb()` return a
  fresh `new T[1]`, one call site stores element 0 through the constant and
  others read it back). A plain `INVOKESTATIC` rewrite hands out a fresh array
  every evaluation and `client.init` then NPEs.
- **`MainActivity.java`** is the launcher + game host. Flow: launcher screen
  (sign in → character picker → Play) → Jagex login via two-leg OAuth driven
  through the device's external browser (`Intent.ACTION_VIEW`) with a loopback
  callback server (`JagexOAuthClient` / `LocalCallbackServer` — see below) →
  bootstrap: emulate only
  the JVM properties Android lacks (`user.home`/`jagex.userhome` → files dir,
  `java.version` "11.0.22", `java.vendor`) — **the OS fingerprint is truthful**
  (Linux/aarch64, no Windows spoofing) → apply `JX_SESSION_ID`/
  `JX_CHARACTER_ID`/`JX_DISPLAY_NAME` via `Os.setenv` + write
  `credentials.properties` (JX_* keys; the client reads it natively from
  user.home) → parse `https://oldschool.runescape.com/jav_config.ws` →
  `DexClassLoader` → instantiate `client`.
- **Bootstrap wiring is obfuscation-proof — do NOT hardcode obfuscated names**
  (none exist in the code anymore): ClientConfiguration via the public
  `net.runelite.api.GameEngine` interface (`setConfiguration`, `initialize` —
  no reflection on fields); the Callbacks field is found by **type-based field
  search** (`findFieldByType(clientClass, "net.runelite.api.hooks.Callbacks")`
  in MainActivity — walks the hierarchy, survives obfuscation).
- **Frame rendering goes through the Callbacks proxy** (bound into the
  client's Callbacks field): the client's own loop renders a frame and then
  calls back `callbacks.draw(buffer, Graphics, x, y)`; the proxy blits
  `buffer.getImage()` into the passed Graphics via `drawImage` (which fills
  `AWTBridge.activePixels`); mouse hooks pass the event through. `drawImage`
  is the per-frame hot path and has two bytecode paths (interpreted on this
  device, so both matter): a 1:1 `System.arraycopy` per row when the requested
  size equals the source size (the game's `drawImage(img, 0, 0, null)`), and a
  divide-free nearest-neighbour accumulator loop for scaled draws (the splash
  logo). The app's
  render thread must **not** call `client.paint()` — that blits the live frame
  while the client thread is rendering the next one (torn frames).
  Two port-side invariants keep the world visible; both are easy to break:
  1. `bindSceneRasterizerToDisplay()` re-points the software rasterizer's
     output array (`yw.ah`) at the display buffer through the client's own
     `yw.ef(int[], int, int, float[])` (the call its `yi.ab(int)` resize path
     makes). The client only runs that on a desktop resize, so without this the
     3D scene is rasterised into the client's original target: a frozen frame
     with a live minimap. **Never bind a frame whose buffer just changed**: on a
     resize frame the provider in flight is the previous buffer, and `yw.ef` also
     sets the *clip*, so binding it leaves the new array with the old clip and the
     next scene paint dies with `ArrayIndexOutOfBoundsException` out of `yw.fb`
     (`length=384796; index=384796`), taking the client thread with it. The
     callbacks proxy skips the bind whenever the provider's size differs from the
     published frame size (`MainActivity`, see `docs/rendering.md` §3.1); the
     client's own resize path leaves the rasterizer consistent. `fa.ak` is **not** a pixel target — it is each
     rasterizer's reference to the `fq.aq` HSL→RGB palette (65536 entries) that
     every shaded fill reads; re-pointing it at the frame turns those lookups
     into screen-pixel reads (grey walls, black ground/trees/actors). The
     client-internal names (`yw.ef`, `yw.ah`, `fq.aq`, `fa.ak`) are
     version-specific and must be re-derived on a client bump
     (docs/telemetry-assessment.md §6).
  2. The frame bitmap is drawn with alpha disabled
     (`renderBitmap.setHasAlpha(false)`, created 765×503 in `surfaceChanged`).
     The software rasterizer writes 3D pixels with alpha 0 and
     `canvas.drawBitmap` blends, so an alpha-0 pixel of an *alpha* bitmap is
     dropped and the previous surface content stays visible (stale login-screen
     remnants). `setHasAlpha(false)` makes Skia treat the bitmap as opaque,
     which is what the older `pixel | 0xFF000000` pass did per pixel.
  A no-op proxy leaves a static gray screen — this is the #1 thing to check if
  rendering "stops". The throttled `callbacks.draw` log prints `world=`/`bridge=`
  (non-zero pixel count + XOR checksum of the left three quarters) for the
  client's own frame and for `AWTBridge.activePixels`; a frozen `world=` with a
  changing `bridge=` means the client stopped redrawing, and differing values
  mean the blit is dropping content.
- **Jagex account login** (`JagexOAuthClient.java`): two-leg
  authorization-code+PKCE (S256) flow against `account.jagex.com/oauth2/…`
  with the launcher client id (`com_jagex_auth_desktop_launcher`, redirect
  `secure.runescape.com/m=weblogin/launcher-redirect`) then the consent client
  (`1fddee4e-b100-4f4e-b2b0-097f9088f9d2`, redirect `http://localhost`,
  `response_type=id_token code` — the id_token arrives in the URL **fragment**,
  which a browser never sends to a server; `LocalCallbackServer` serves a page
  whose JS POSTs the fragment back to `127.0.0.1:80`/`[::1]:80`, and
  `handleConsentFragment` reads it there — there is no WebView). Loopback :80 is
  bindable only where the image sets `net.ipv4.ip_unprivileged_port_start=0`
  (phones); a `1024` floor (Google Play emulator images) fails both binds with
  `EACCES`, and `startJagexLogin` then selects **manual capture** instead of
  aborting: the same leg-2 page runs in the browser, the login overlay grows a
  "Paste login link" pill (`submitConsentLink` accepts the
  `http://localhost/#…` address, prefilled from the clipboard) and
  `handleConsentFragment` — unchanged — consumes it. The redirect URI cannot be
  relaxed to escape this (Jagex refuses every other one, probed). Game session:
  `auth.jagex.com/game-session/v1/sessions` → `sessionId`,
  `…/v1/accounts` (Bearer sessionId) → character picker. Legacy
  (`login_provider=runescape`) accounts are rejected with a message.
  **Never set JX_ACCESS_TOKEN/JX_REFRESH_TOKEN alongside the session vars** —
  the client takes the wrong login branch and fails. The launcher also allows
  manual JX_* entry and imports `credentials.properties` from external
  storage as a fallback. `usesCleartextTraffic` is on in the manifest — the
  `http://localhost` consent redirect needs it; don't remove it.
- **Client auto-update** (`ClientUpdater.java`): the launcher fetches
  `client-version.txt` from the CI-published GitHub Release (base URL
  `BuildConfig.DIST_BASE`, override at build time with `-PdistBase=<url>/`) and
  compares it with the installed version. On a newer version it downloads
  `runelite-dex.jar` + `runelite-dex.jar.sha256` from the same host, verifies
  the SHA-256, then atomically swaps `files/runelite-dex.jar` and
  `files/client-version.txt` and asks to restart. **Nothing is dexed
  on-device** — `downloadAndDexJar` builds the pre-dexed jar once in CI and
  `.github/workflows/build.yml` publishes it (plus `client-version.txt` and the
  checksum) as release assets named exactly `runelite-dex.jar`,
  `client-version.txt`, `runelite-dex.jar.sha256` — those names are the URL
  tails the app fetches. A failed download or checksum mismatch leaves the old
  client installed and working. The launcher carries the whole update state in a
  status line (`updateClientStatus`, under the version row) and re-checks every
  `UPDATE_POLL_MS` (2 min) while the activity is resumed, so a client published
  during a weekly OSRS update is offered without a relaunch; the dialog is only
  popped over the launcher, never mid-game. The trigger is exact string inequality
  of `client-version.txt`, so a same-version rebuild would never reach a user, and
  a published release *older* than the installed client is refused rather than
  offered (`compareVersions`). `bootGameClient` writes the asset version into
  `files/client-version.txt` when it copies the APK's jar, so an APK update over an
  older client no longer re-copies the 10 MB asset on every boot.
  **CI is freshness-gated**: `build.yml`'s `check` job compares bootstrap.json's
  client version with the latest release's `client-version.txt` and skips the
  building jobs when they match, so the frequent crons (Wed 07-21 UTC every 5 min,
  Wed night every 10, Thu every 15 min, otherwise every 2 h) cost one curl when
  there is nothing to do. Downtime target: a whole run is ~190 s measured, so
  RuneLite publishing -> users able to pull is ~5 min (cron) + ~3 min (build and
  publish) + the app's own 2-minute poll.
- **Render loop presents the client's own frame at a live size** (765×503 in the
  client's fixed display mode, a device-aspect frame in the port's **game fill**
  mode): `appletPixels` (MainActivity) ↔
  `AWTBridge.activePixels/activeWidth/activeHeight` ↔ `frameW/frameH`. The first
  two are swapped together by `publishFrameSize(w, h)` under `renderLock` — never
  assign `AWTBridge.activePixels` directly: core's `Component.getGraphics()`
  snapshots it at call time and the client caches that `Graphics` per frame-buffer
  instance. `requestGeometry()` (UI thread) picks the frame size; `ClientDisplay`
  drives the client's display mode and canvas (`GameEngine.resizeCanvas()`), and
  `applyPendingFrameSize()` runs on the client thread at the top of the
  `callbacks.draw` handler, where the client is between two frames. A frame drawn
  through a just-replaced frame buffer is **not** presented (`publish == false`);
  the next one is. The render thread (`runRenderLoop`) blocks on `renderLock` until
  `callbacks.draw` has bumped `frameSeq` — it never presents on a timer, and
  each presented frame is exactly one client frame. Presentation is two native
  calls: `renderBitmap.setPixels(appletPixels, …)` and one
  `canvas.drawBitmap(renderBitmap, srcRect, renderDst, scalePaint)` that lets
  Skia scale the frame into the **centred fit rect** (nearest-neighbour:
  `scalePaint.setFilterBitmap(false)`); four `drawRect`s paint the bars around
  it first, because the surface buffer is not cleared by the system. Do **not**
  reintroduce per-pixel Java loops here — the old scale loop + `| 0xFF000000`
  pass cost 172-229 ms/frame on a Pixel 8 Pro; this path costs ~12-15 ms of one
  core at 2244×1008 (measured 8-18 ms for the 605k-px fill frame). The thread runs
  at `THREAD_PRIORITY_DISPLAY` and idles (no
  canvas lock) while `renderBitmap == null`. Nothing is drawn on the canvas: the
  loader's progress lives in a themed boot overlay (`bootOverlay`/`tvBootStatus`,
  hidden by the first presented frame), and the 5 s GameState log (`scaleMs=`)
  is logcat-only. `rebuildRenderGeometry()` (render thread, under `renderLock`)
  rebuilds the bitmap, `srcRect` and `fitLeft/Top/W/H` from the live frame and
  surface sizes and logs `surface <w>x<h> fit <w>x<h> at <l>,<t> (frame <w>x<h>)`;
  `surfaceChanged` only records `surfaceW/H` and calls `requestGeometry()`. The
  same surface is **always** inset by the always-present right-edge column (44 dp,
  `SidePanel.columnWidthPx()`), so no game pixel renders under the chrome; the
  sidebar's **resize** mode insets it by the open drawer as well, while in the
  default **overlap** mode the drawer floats over the game (`applyGameInsets`).
- **Game fill drives the client's display mode** (`ClientDisplay.java` + the
  launcher's **Display** card): `ClientDisplay.requestResizable(client, cl)` calls the
  game's own applier — `ms.bo(int, byte)`, with `client.ne/nw/nv(int)` as fallbacks —
  which sets the static boolean `Client.isResized()` returns and requests 7680×2160 from
  the shell, then verifies `isResized()` actually flipped. Those obfuscated names are the
  port's only version-specific display internals; re-derive them per the procedure added
  to `docs/telemetry-assessment.md` §6. The switch is **one-way**: the fixed direction
  (`ms.bo(1, 0)`) corrupts the client's canvas fields in this environment (see the resize
  traps below), so fill *off* never calls an applier — at boot the client keeps its own
  fixed mode (what the port always ran) and after a fill session it stays resizable until
  the app restarts, with only the frame returning to 765×503.
  A setup that fails turns fill off for the session (the pref survives), leaving the
  frame at 765×503 — i.e. today's behaviour — and reporting
  `game fill: unavailable: <reason>` on the launcher. Fill mode also forces
  RuneLite's stretch off (`setStretchedEnabled(false)`), because an active stretch
  shrinks the client's real dimensions and the client derives its own size requests
  from those.
- **Two client-resize traps, both fatal to the client thread** (the world freezes while
  every other thread keeps running; the frame log's `stop=` field marks the client
  taking its own shutdown: `to.re` armed, `ClientDisplay.shutdownDeadline`):
  1. **Never bind the rasterizer to a pre-resize buffer** — see the render-loop bullet
     above: `yw.ef` sets the array *and* the clip, so a stale bind leaves the new array
     with the old clip and the next scene paint dies with
     `ArrayIndexOutOfBoundsException` out of `yw.fb`, then `Callbacks.error`.
  2. **Never call `GameEngine.resizeCanvas()` for a size the canvas already has.** The
     mode applier (`ms.bo`, via `agh`) resizes the canvas itself when it switches
     modes, so `applyPendingFrameSize` reads `Client.getCanvasWidth/Height` back first
     and just publishes when it matches. A redundant pass threw
     `NegativeArraySizeException` out of the frame-buffer constructor (`tx.<init>`) —
     the client's canvas fields are stored scrambled, so a redundant re-derivation does
     not round-trip — and the client's own resize path then hit the same corruption and
     the thread died. Both rules are what make an in-game drawer toggle in resize mode
     survivable at all; verify with `fill:`/`resize client canvas`/`callbacks.draw`
     lines continuing after the resize and no `Callbacks.error`.
- **Frame pacing is client-side** (`FPS_TARGET = 60` in MainActivity):
  `initialize()` is followed by `Client.setUnlockedFps(true)` +
  `setUnlockedFpsTarget(FPS_TARGET)` (that order — turning unlocked fps off
  clears the target). The default clock's `mo.xg` sets `mo.bd` once per 20 ms
  catch-up batch, so the client presented only once per up-to-10 cycles
  (~0.87 fps); the unlocked `mo.ag` clock sleeps to the `1e9/FPS_TARGET`
  boundary and presents every rendered frame. Game ticks are unaffected.
- **The client JAR must be AOT-compiled on the device** (this device runs
  `dalvik.vm.usejit=false`, so interpreted code is ~10x slower): the app
  pre-registers `files/runelite-dex.jar` with a `DexClassLoader` on every
  launch (MainActivity step 5b) so the ART Service knows its class-loader
  context, and the operator then runs
  `cmd package compile -m speed -f org.runelite.mobile` **then**
  `cmd package compile -m speed -f --secondary-dex org.runelite.mobile` — that order, the
  `--secondary-dex` pass last: compiling the package without it rewrites the app's oat state and
  drops the client dex back to `verify`, so the documented reverse order leaves the game
  interpreted — after every client-jar update — and
  after **every APK install**: a new install lands in a new `/data/app/~~…==/`
  dir, whose path+checksums are part of the class-loader context the odex is
  keyed to, so ART rejects the existing `speed` odex (artd:
  `kOatContextOutOfDate … ClassLoaderContext check failed`) and runs the
  `verify` vdex instead.
  `pm art dump` must show `[status=speed]` — **not** `run-from-apk` and not
  `[status=verify]`. The installed APK must not be debuggable: ART Service
  rewrites `-m speed` to `verify` for debuggable packages, so AOT only works on
  the release build (same debug signing key → `install -r` keeps app data).
  The app reports that state itself: the launcher's Host section has a `client AOT`
  row and the launcher version line turns red with `NOT AOT-COMPILED` when the
  odex is stale or missing (`ClientUpdater.clientDexAotStatus` — a heuristic over
  `files/oat/<isa>/runelite-dex.odex` vs the **client jar's** mtime, logging
  `AOT check: odex=… jar=… apk=…`; `AOT_UNKNOWN` when `files/oat` is unreadable).
  Comparing against the APK's mtime instead was a permanent false alarm after
  every install + recompile (ART does not rewrite an odex whose dex input is
  unchanged), so the APK is logged but not compared; `pm art dump` is the ground
  truth.
- **Touch input is wired**: ACTION_DOWN/MOVE/UP → MOUSE_PRESSED/DRAGGED/
  RELEASED/CLICKED, ACTION_SCROLL → MOUSE_WHEEL, dispatched to the client
  component itself, falling back to the canvas from `GameEngine.getCanvas()`
  (see `resolveInputTarget()` in MainActivity). ACTION_DOWN emits a
  MOUSE_MOVED at the same point **before** the MOUSE_PRESSED: a mouse always
  moves before it presses, and the client's own menus (the world list) select
  the **hovered** row (`ar.bf` selects `dr`, set from `tk.af`/`tk.ac`, not from
  the press position), so without that move a tap acts on wherever the previous
  gesture left the cursor. Camera rotation is a middle-button (`BUTTON2`) drag,
  which drives the client's own camera-drag path (`tk.aj() == 4` with its `bn.hc`
  setting, forced true for the gesture) to rotate yaw+pitch. Five gestures reach
  it, all routed by `setupTouchInput`. **The left press is deferred** — `ACTION_DOWN`
  sends only `MOUSE_MOVED` and arms a `LONG_PRESS_MS` (400 ms) timer, because the
  client acts on mouse *down* (verified on the device: holding a press on the
  login screen's `Play Now` started the login while the finger was still down),
  so an early press would walk/attack before any long-press timer could fire.
  A single finger then decides by movement alone:
  **moved > `ROTATE_LOCK_DP` (10 dp), any time** → camera drag (no time window:
  a 250 ms window used to steal slow rotations, because the first `ACTION_MOVE`
  of a fast drag can arrive late while the UI thread renders the game);
  **still for `LONG_PRESS_MS` (400 ms)** → `fireLongPress` → `emitRightClick` at
  the published cursor + `suppressUntilUp`, i.e. the context menu; **lift** →
  `beginTapPress` sends the deferred press + release + click. A press is only
  ever sent on lift, so a drag can never walk or attack — the trade-off is that
  dragging inside an interface rotates the camera rather than dragging an item.
  **two-finger rotate vs pinch** — a second finger locks the mode once
  (`handleTwoFingerMove`) and cancels the long press: centroid moved >
  `ROTATE_LOCK_DP` → rotate, span changed > `ZOOM_LOCK_DP` (14 dp) and more than
  the centroid moved → `MOUSE_WHEEL` notches (`ZOOM_DP_PER_NOTCH` 28 dp, ≤ 3 per
  event, sign negated so fingers-apart zooms **in** — verified on the device),
  which is the client's own wheel input (camera zoom in the world, list scrolling
  with an interface open), never a button press; a **two-finger tap** (both
  fingers down and up within 400 ms with neither lock crossed) is the second
  route to the **right click**. Every rotation path goes through
  `startCameraDrag`, which emits a `MOUSE_MOVED` at the centroid **before** the
  `BUTTON2` press: the client measures its first drag delta against the
  *published* cursor position, so pressing without that move rotates by
  (centroid − the finger's start) — the one-time camera jump. Keyboard is wired
  through the side panel's right-edge column: the `⌨` tile (foot of the column,
  the only always-present chrome) toggles the keyboard bar, whose EditText
  `TextWatcher` diffs the text and emits a `VK_BACK_SPACE` per removed char plus
  `dispatchKeyText(added)` for the inserted run, with `Enter`/`Hide` pills;
  `dispatchKeyText` sends KEY_PRESSED + KEY_TYPED + KEY_RELEASED per char and
  `deliverKeyEvent` walks `getKeyListeners()` on the client component and the
  canvas (the game registers its `KeyListener` on the canvas — `to.sk`, the same
  object `GameEngine.getCanvas()` returns). The bar is anchored to the **top** of
  the game area: with Gboard up in landscape a bottom-anchored bar is covered,
  and so is the game's own chat input line. It is an AWT `KeyEvent` bridge, not
  an in-game IME.
- **`ios/`** — RoboVM `IOSLauncher` skeleton only (empty UIWindow, no rendering).

## RuneLite plugin runtime (the third jar)

The port ships **three** RuneLite jars, all pinned by one
`https://static.runelite.net/bootstrap.json` fetch (task `syncRuneLiteJars`, verified
against the manifest SHA-256, so client/injected-client/api can never drift):
`client-<ver>.jar` (the plugin API + ~130 core plugins), `injected-client-<ver>.jar`
(the game) and `runelite-api-<ver>.jar` (the *full* api, checked to be a byte-identical
superset of the manifest's `-runtime` flavor, because core plugins are compiled against
the gameval ID tables it adds). All three are ASM-transformed and d8-dexed into `assets/runelite-dex.jar`, together with
RuneLite's own runtime libraries (guice, gson, okhttp, okio, guava, commons-lang3/text,
protobuf-javalite, json, jsr305, javax.inject, aopalliance, http-api, jopt-simple) and the host shims
(see below). `jopt-simple` is *not* desktop-only: `MusicPlugin.onScriptCallbackEvent` calls
`joptsimple.internal.Strings.isNullOrEmpty`, and without the jar that shipped plugin threw
`NoClassDefFoundError` on every `ScriptCallbackEvent` (113× in one device log) while every
loader counter stayed green — which is what the `verifyHostLinks` gate below now catches.
The **app** dex keeps only the JRE stubs (`core/`), the Android host
(`org.runelite.mobile.**`), `slf4j-api`/`slf4j-simple` (the slf4j binding; logback is not
shipped) and the androidx dependencies. The split is load-bearing: **Guice lives in the
asset dex because it has to resolve the types named in the shims' and plugins'
signatures** (`net.runelite.api.Client` in `ColorPickerManager.create(...)`, for example).
With the shims in the app dex, Guice inspected those descriptors through the app
classloader and 7 plugins (grounditems, groundmarkers, inventorytags, npchighlight,
objectindicators, cluescrolls, party — every plugin that injects a shim) failed with
`NoClassDefFoundError: net.runelite.api.Client`.

- **Two class loaders, parent-first.** `MainActivity.bootGameClient` loads the asset jar
  through a child `DexClassLoader` whose parent is the app classloader. Anything the app
  dex defines therefore *wins*, and anything only the asset dex defines is invisible to
  app-dex code. Consequences that shape the code:
  - Only the classes that need a desktop window / macOS / FlatLaf surface are still
    **stripped out** of the asset dex (`HOST_REPLACED_CLASSES`/`HOST_REPLACED_PREFIXES`
    in `android/build.gradle`, nested classes included — d8 refuses a nest member without
    its nest host) and replaced by the five shims in `hostshims/src/net/runelite/client/**`
    (deliberately NOT in the app source set; `compileHostShims()` compiles+packages them as
    a d8 program input). A stripped class list must stay in sync with those shims (a
    missing entry is a d8 "defined multiple times" error, which is the intended tripwire).
    **RuneLite's real Swing shell ships**: `ClientToolbarPanel`, `ClientToolbar`,
    `NavigationButton`, `PluginPanel`, `MultiplexingPluginPanel`, the config plugin's
    panels, every plugin panel and all of `net/runelite/client/ui/components/**` execute
    against the port's hand-written `javax.swing` — see [docs/swing-runtime.md](docs/swing-runtime.md)
    and the "Swing runtime" section below.
  - `org.runelite.mobile.host.RuneLiteHost` reaches everything RuneLite-specific —
    including the Guice injector, `OkHttpClient` and `RuneLiteAPI.CLIENT` — by **name
    through the child loader**; the app dex has no compile-time RuneLite/Guice/OkHttp
    dependency at all (only `java.awt.*` stubs, `android.*`, slf4j and `core`'s bridge
    classes). Do not add a compile-time `net.runelite.*`/`com.google.inject.*`/`okhttp3.*`
    reference to app-dex code. The other direction **is** compile-time: the asset-dex
    `ClientUI` shim imports `org.runelite.mobile.bridge.AWTBridge`/`UiBridge` (core,
    parent-first), which is how the sidebar reaches the app; a shim may link only to
    `core` bridge statics that way, and app-dex code (including `core`) must still reach
    shim/plugin state reflectively or through those statics.
  - `UiBridge` is the sidebar's bidirectional link: core defines the `Host` interface and
    calls into the app-side `SwingSidebarView` (registered by `UiBridge.setHost`), while
    `ClientUI`/`SwingSidebarView` call back through it (`setSidebarRoot`,
    `closeSidebarRequested`, `dispatchKey`, `hasVisibleWindow`, …).
  - Shim **constructors** may only take parameters resolvable from the asset dex
    (`Injector`, `java.awt.*`/`javax.swing.*` stubs from the app dex via parent-first
    delegation, `String`, …): Guice reflects on them. Method *signatures* must match the
    upstream client jar exactly (that is the binary contract).
- **Plugin discovery is a build artifact.** `PluginManager`'s own discovery is Guava
  `ClassPath.from(...)`, which cannot work on ART, so `downloadAndDexJar` scans the
  client jar with ASM and writes `runelite-plugin-index.txt` into the asset dex (every
  class under `net/runelite/client/plugins/**` with `@PluginDescriptor` extending
  `Plugin`, minus `android/plugin-exclusions.txt`). `RuneLiteHost` feeds that list to
  `PluginManager.loadPlugins` — bulk first (that builds RuneLite's `@PluginDependency`
  ordering), falling back to one class at a time so a single bad plugin is logged and
  skipped instead of aborting startup. An excluded plugin can take others with it:
  `PluginManager.instantiate` refuses a plugin whose `@PluginDependency` is missing
  ("Unmet dependency for ClueScrollPlugin: BankTagsPlugin"), which is why `BankTagsPlugin`
  is deliberately *not* excluded even though its Swing bank-tag editor cannot run here.
  Panel-only plugins are **no longer excluded**: the drawer hosts RuneLite's real Swing
  panels, so `ConfigPlugin`, `LootTrackerPlugin`, `NotesPlugin`, `TimeTrackingPlugin`,
  `SkillCalculatorPlugin`, `ScreenMarkerPlugin`, `WorldHopperPlugin`, `HiscorePlugin`,
  `GrandExchangePlugin`, `KourendLibraryPlugin` and `InfoPlugin` all instantiate. Four of the
  five remaining entries need LWJGL/JNA/a desktop-only library set or a native browser
  (`GpuPlugin`, `DiscordPlugin`, `DevToolsPlugin`, `ScreenshotPlugin`); the fifth,
  `WorldMapPlugin`, predates the sidebar and none of its 142 classes references any of those
  symbols — the exclusion is kept but its reason is unverified (noted in
  `android/plugin-exclusions.txt`).
  Verified on the device (before the sidebar landed): the index was 114 classes, 114
  instantiate with **0 failures**, 65 active at boot, **141** overlays registered. Note `PluginManager.startPlugins`
  stops at the first plugin whose `startUp()` throws, so one aborted plugin leaves the rest
  unstarted until the side panel (or nothing else) starts them.
- **The Callbacks proxy delegates to `Hooks`.** `MainActivity`'s `Proxy` keeps the
  `renderLock`/`frameSeq` handshake and the `bindSceneRasterizerToDisplay` call, and
  forwards every callback to the injector's `net.runelite.api.hooks.Callbacks` instance
  when the host is running (`RuneLiteHost.hooks()`). `Hooks.draw` renders the plugin
  overlays into the client's own frame image and then blits that image into the passed
  `Graphics` (== `appletPixels`), which is why the frame the render thread presents
  contains the overlays. The proxy's `openUrl` deliberately does **not** delegate
  (`LinkBrowser` would reach `Runtime.exec`), and a host that failed to start leaves the
  legacy pass-through behaviour in place.
- **Plugin lifecycle runs on the Swing EDT.** `AWTBridge.registerUiThread(SwingEdt.ensureStarted(),
  SwingEdt::post)` (called from `MainActivity.onCreate`) is the event dispatch thread —
  `Swing-EDT-rlmobile`, not the Android main thread: `javax.swing.SwingUtilities.invokeLater/
  invokeAndWait/isEventDispatchThread` route there, which is what `PluginManager`'s EDT contract
  and `invokeAndWait` need. The Android main thread only presents bitmaps and forwards input.
  The host's own thread does only the blocking work (`RuntimeConfigLoader.get()`) before
  posting the lifecycle.
- **`java.awt`/`javax.swing` stubs.** The Android runtime has no `java.desktop`, so
  `core/` defines the surface the pre-compiled jars reference. At HEAD the tree holds
  381 files under `core/src/main/java` (plus `hostshims/src`): **330 hand-written**
  (the whole Swing runtime — layout managers, `JTable`/`JTree`/`JTextComponent`/`text/*`,
  `plaf/*`, borders, `UndoManager`, `HTMLEditorKit`, the `bridge/` classes, …) and
  **51 generated** data-only stubs (`javax.management.**`, `javax.sound.sampled.**`,
  `com.sun.**`, `javax.accessibility.**`, and a few `java.awt`/`javax.swing` leftovers —
  the generator's marker header is what tells them apart). Regenerate them after a
  client bump with `python3 tools/gen_stubs.py --rl-jars android/build/rl-jars
  --asm-cp <asm:asm-tree jars>`; the member set comes from the real call sites
  (`tools/RefScan.java`), hierarchy/constants/enums from the local JDK
  (`tools/JdkInfo.java`), and files without the generator's marker header are never
  touched — a hand-written class is never overwritten. The scan also records **how** each
  member is used (INVOKESTATIC vs INVOKEVIRTUAL/INVOKEINTERFACE, GETSTATIC vs GETFIELD)
  and the generator emits `static` accordingly: a stub method that is an instance method where the client uses
  `invokestatic` is an `IncompatibleClassChangeError` on device
  (`StyleContext.getDefaultStyleContext()` was exactly that). `tools/HostLinkCheck.java`
  verifies both the existence and the static/interface shape, and it checks `core/`'s own
  classes too -- `java.awt.AWTEvent` was hand-written precisely because a generated stub
  lacked `(Object,int)` and the *game's* UI event dispatch
  (`ActionEvent.<init> -> super(source, id)`) then killed the client thread with a
  `NoSuchMethodError` (frozen frame, no error until that code path ran). Two invariants in that surface are load-bearing:
  1. `Graphics.drawImage` must keep its **opaque** fast paths for `Image` sources: the
     game frame is a `BufferedImage` built from a 3-mask `DirectColorModel`
     (`hasAlpha == false`) whose pixels carry alpha 0, so a blending blit would leave the
     previous frame on screen. Only `hasAlpha` sources blend.
  2. `Image.getGraphics()`/`BufferedImage.getGraphics()` must return a **`Graphics2D`**:
     `Hooks.draw` casts the result, and a plain `Graphics` is a `ClassCastException` on
     the first overlay pass.
  3. The `java.awt.event.*` stubs must declare every member the client's own mouse/key
     path calls — `MouseEvent.getPoint()`/`getComponent()`, `InputEvent.getModifiersEx()`
     /`getModifiersExText(int)`, `KeyEvent.getKeyText(int)`/`getExtendedKeyCode()`
     /`setKeyCode`/`setKeyChar`/`paramString`, the `(Component,…)` `MouseEvent` /
     `MouseWheelEvent` / `Window(Window,int)` constructors. A missing one is a
     device-only `NoSuchMethodError` thrown *inside* `emitPoint`/`deliverKeyEvent`,
     which catches `Throwable`: the tap/key is then silently dropped while logcat still
     shows the `Dispatch mouse id=501 …` line (that is what "taps no longer act on the
     game" looked like). Two rules keep the stub set buildable: a hand-written stub must
     have a **no-arg constructor** (`tools/gen_stubs.py` models a hand-written supertype
     as "a plain class with a no-arg ctor" and emits `super()` into generated subclasses —
     `ItemEvent`/`AdjustmentEvent` extend `java.awt.AWTEvent`), and a hand-written stub's
     supertype must be hand-written too, because the generator only emits the classes the
     client *names*: `java.awt.image.RGBImageFilter` exists by hand solely so
     `javax.swing.GrayFilter` (which `ImageUtil.grayscaleImage` calls) can link.
  Text is the one thing core cannot do itself: `org.runelite.mobile.bridge.TextBridge`
  hands the TTF bytes to `org.runelite.mobile.AndroidTextRenderer` (app dex), which
  materialises them into `Typeface.Builder` (file-based — there is no `ByteBuffer`
  constructor) and rasterises into a reused scratch `Bitmap`.
- **Verification gates.** `./gradlew :android:verifyHostLinks` resolves every reference the
  *shipped* classes make — the cleaned client jars (`temp-jars/{client,injected-client,
  runelite-api}-cleaned.jar`, i.e. what d8 is fed, so `client.$dync$*` and the other
  `transformClassBytes` output resolve against the transformed jar), plus `core/`, the app
  dex classes and the host shims — against the app dex + asset dex + `android.jar` +
  runtime jars, and fails on any gap (a missing class or a wrong descriptor is otherwise a
  device-only `NoClassDefFoundError`/`NoSuchMethodError`). Two checked-in lists keep it
  honest:
  - `android/hostlink-ignore.txt` — prefixes of subsystems/consumers the port never runs
    (`org/lwjgl/`, `com/sun/jna/`, `net/runelite/client/Updater`, …), each with a reason.
    A prefix matches the *referenced owner* or the *referencing class*; a gap inside a
    plugin that stays in the index is never fixed here.
  - `android/hostlink-platform-extra.txt` — references `android.jar` does not model but the
    *device* runtime resolves, verified by a reflection probe on the device
    (`ClassLoader.getPlatformClassLoader`, `Files.readString/writeString`,
    `ConcurrentHashMap.keySet()KeySetView`, `sun.misc.Unsafe`). It is not an ignore list:
    an entry that matches nothing is printed as a warning, and everything else about the
    same owner is still reported.
  The tool also models the pipeline rather than the raw jars: d8's covariant-return rewrite
  of the `java.nio.Buffer` family (`ByteBuffer.position(I)Buffer` in the shipped dex),
  JVMS 5.4.3.3/5.4.3.4 call-site shapes (INVOKESTATIC on an interface, a virtual call
  resolving to a superinterface method, `Object`'s public methods on an interface), the
  array-typed constant-pool owners (`[Lnet/…;` from `ANEWARRAY`/enum `values()`), and the
  signature-polymorphic `MethodHandle.invoke*`/`VarHandle` members. Each gap line names the
  *referencing* class, which is what makes a failure actionable.
  `GraphicsSelfTest` runs once per host start and logs `GFX SELFTEST PASS`/`FAIL <case>`
  (shapes, alpha blending, the opaque frame blit, text).
- **The drawer hosts RuneLite's real Swing sidebar** (`org.runelite.mobile.SidePanel` + the
  always-present right-edge column with the `‹`/`›` chevron and the `⌨` keyboard toggle).
  The column is 44 dp wide; an open drawer adds `min(42% of the screen width, 380 dp)`. The
  column **always takes space**: `MainActivity.applyGameInsets` insets the game
  `SurfaceView` by `sidePanel.columnWidthPx()` in both sidebar modes (so no game pixel is
  covered by the chrome), and by the open drawer as well only in **resize** mode — in the
  default mode the drawer floats over the game, so a toggle there resizes nothing. The
  keyboard bar takes the full `sidePanel.occupiedWidthPx()` in both modes, because it is
  chrome and not game. The column is hidden on the launcher screen by `setAvailable(false)`
  (the inset itself is left unchanged, so hiding the chrome never resizes the client). The
  drawer header carries the `⌂` launcher shortcut.
  The drawer **body** is `SwingSidebarView`: RuneLite's `ClientToolbarPanel` +
  `JTabbedPane` (RIGHT placement, `JTabbedPane.trailingComponent` = the toolbar strip) +
  the real plugin panels, built in the asset dex by the `ClientUI` shim
  (`hostshims/src/net/runelite/client/ui/ClientUI.java`, which mirrors upstream's
  `addNavigation`/`removeNavigation`/`openPanel` + selection history) and painted by the
  core through `UiBridge` — see [docs/swing-runtime.md](docs/swing-runtime.md). The old
  native Plugins/Config/Host tabs, the plugin search, the generated config forms and
  `PluginPanelRegistry` are **gone**: plugin enablement, config editing, the colour
  picker and the Notes/LootTracker/… panels are RuneLite's own Swing UI now. A panel whose
  `onActivate` throws, or whose wrapped panel cannot be built, is replaced by a
  `PluginErrorPanel` for that tab only (logged as `panel activation failed: <tooltip>`);
  the rest of the sidebar keeps working.
  `MainActivity`'s launcher screen carries the **Host** section (client version, runtime
  status, the live `frame` size, plugin index/active counts, load failures, client AOT
  state, on-device dexer,
  the last conformance summary, and the *Run plugin conformance* / *Refresh* pills), and
  the **Display** card (*Game fill: on/off* + *Sidebar: overlap/resize*, with the frame
  size, the fill status and the fallback reason on their rows). The two settings live in
  the launcher's `SharedPreferences` (`gameFill`, `sidebarOverlap`; both default on); a
  fill failure is session-only so a pref set under an older client is retried after a
  client bump.
- **Swing runtime** (`core/src/main/java/{java,javax}/**` + `core/.../bridge/`). The port
  ships a hand-written `java.awt`/`javax.swing` because Android has no `java.desktop`;
  it is what makes the unmodified panels run. Four things to know before touching it:
  1. **Threading.** `SwingEdt` is the port's event dispatch thread (`Swing-EDT-rlmobile`);
     `MainActivity.onCreate` registers it through `AWTBridge.registerUiThread`, so
     `PluginManager`'s EDT assertion, `SwingUtilities.invokeLater/invokeAndWait` and the
     plugin lifecycle all land there. The Android main thread only presents bitmaps and
     forwards input. `SwingEdt.pumpUntil` is the nested loop that keeps a modal
     `JOptionPane` from freezing the app.
  2. **The stage contract** is `UiBridge` (core ↔ app): the app reports the stage geometry
     and forwards touches/keys, the core paints into a device-sized ARGB array. Popups
     (menus, combo drop-downs, colour picker) live in `PopupLayer`, not in native windows;
     virtual windows are painted with a core-drawn 22 px title bar and block input below
     them while visible.
  3. **Paint/repaint.** `JComponent.paint` → `ui.update(g, this)` (or
     `paintComponent`/`paintBorder`/`paintChildren`); `repaint()` walks to the paint root
     and no-ops for anything outside the sidebar/window trees — that is what keeps the
     **game client's** own `repaint()` calls harmless. Never change
     `Component.getGraphics()` (the game's pixel path) or `Component.createImage`.
     `Component.paint` and `Component.paintSelf` are split on purpose: a
     `JComponent.paintComponent` that calls `super.paint(g)` reaches **`Container.paint`**,
     which paints the children too — every level then painted its subtree twice and the
     sidebar cost 5 s per frame (195k component paints for ~600 components) instead of 15 ms.
  4. **UI scale.** The sidebar renders at an integer scale
     (`clamp(round(drawerWidthPx / 272), 1, 3)`) with device-resolution glyphs: fonts and
     `FontMetrics` stay *logical*, `Graphics.drawString` passes the scale to
     `TextBridge`, and `AndroidTextRenderer.scaledFont` rasterises at
     `logicalSize × scale` (the `SwingSelfTest` `scale` case asserts exactly that — it was
     a real bug: icons scaled through `drawImage` while glyphs stayed logical). Every new
     widget must measure logically.
  `SwingSelfTest` (app dex, 26 cases) runs at boot right after `GraphicsSelfTest` and logs
  `SWING SELFTEST PASS` / `FAIL <case>: <detail>`; a failure is appended to `RuneLiteHost.status`
  by `startPlugins` (a pass is logcat-only) and is therefore visible in the launcher's Host section. The `optionpane` case puts a dialog on
  screen, so the boot run skips it — and the conformance pass does **not** run the on-screen
  cases either (a dialog that never gets its click would hang the dispatch thread).
  The drawer's touch mapping is documented in `SwingSidebarView`: a one-finger drag inside a
  scrollable panel **scrolls** it (`UiBridge.isScrollableAt` decides; a swipe never clicks), a
  two-finger drag is the wheel, a two-finger tap and a long press are right clicks, and the
  tab strip itself scrolls when a hub install adds more tabs than fit.
- **Plugin conformance run** (`org.runelite.mobile.host.PluginConformance`). The loader
  counters all derive from successful *instantiation* (the index size, `N plugin(s) active`,
  the overlay count) and say nothing about behaviour — a plugin can be listed as active and
  still throw on its first event. This pass starts every plugin itself, measures it, and
  puts the client back the way it found it: **subs** (declared `@Subscribe` methods vs
  subscribers the bus actually holds), **ovl** (every overlay the plugin registered is
  asked to render into a scratch 512×512 image — *on the client thread*, because
  `Overlay.render` calls client getters that assert it), **cfg** (every `@ConfigItem` is
  read, set to a different value through `ConfigManager` so `ConfigChanged` fires, read
  back and restored), **rc** (entity render callbacks that appeared while it started) and
  **probes** — `entityVeto` (the client's own `Callbacks.draw(Renderable, boolean)` counts,
  so "hide NPCs" becomes a number), `menuEntry` (a synthetic `MenuEntryAdded` seeded from
  the last real menu entry and posted through the real bus; a plugin that reacts neither
  modifies nor appends entries is recorded as a skip, not a failure — a synthetic entry
  cannot reproduce every plugin's target conditions) and `eventFlow` (per-event-class
  `post`/`postDeferred` counts the proxy saw; `GameTick`/`BeforeRender` are posted by
  RuneLite's own `Hooks` straight to the bus, so they are never asserted on). An exception
  while the game is not `LOGGED_IN` is recorded as `SKIP(state)`, a link error always fails.
  The pass must always produce a report: the on-screen self-test case (a modal dialog) and
  the panel-render metric (`PluginConformance.VERIFY_PANELS`) are **not** part of it, because a
  dialog that never gets its click and a panel whose layout loops both hang the dispatch
  thread. Panel behaviour is covered by the manual device checks. Trigger it from the
  launcher's Host section or from adb (the render loop consumes the request file every 5 s):
  `adb shell "echo 1 > /sdcard/Android/data/org.runelite.mobile/files/conformance.request"`,
  then `adb pull …/files/conformance-report.txt`; the summary also goes to logcat as
  `CONFORMANCE: plugins=… pass=… fail=… skip=…`. Verified on the device (login screen,
  current build): `plugins=125 pass=12 fail=0 skip=113` (the skips are `SKIP(state)`).
  Verified earlier logged in: 115/115 plugins reported, 0 FAIL, `subs=<n>/<n>` equal on every line, `cfg=<n>/0`,
  `ovl=<n>/0`, `rc=1` and `entityVeto(entities=112 denied=24)` for `EntityHiderPlugin`
  (denials stop the moment the plugin is stopped, so the veto is provably the plugin's).
- **Third-party plugins are dexed on the device.** A hub jar is Java 11 `.class`
  bytecode, which ART cannot load, so `org.runelite.mobile.host.MobilePluginHub` scans
  `files/plugins/` (and the app-specific external dir, which is the adb-push drop box:
  `adb push <jar> /sdcard/Android/data/org.runelite.mobile/files/plugins/`), imports raw
  jars into the private dir, and — when the jar still contains `.class` entries — dexes it
  with `org.runelite.mobile.host.OnDeviceDexer`, which loads the bundled R8/D8 compiler
  from `assets/rl-dexer.jar` (3.7 MB; `downloadAndDexJar` dexes `<build-tools>/lib/d8.jar`
  through the same ASM transform in tolerant mode) and calls
  `D8.run(D8Command.builder()…build())` reflectively. Each jar then gets its own
  `DexClassLoader` (parent = the client loader) and `runelite_plugin.json` names the plugin
  classes. Jars can also be pre-dexed on the host with
  `./gradlew :android:dexHubPlugin -PhubPlugin=<internalName>` (manifest
  `https://repo.runelite.net/plugins/manifest/<ver>_lite.js`, SHA-256 verified against the
  manifest's `jarHash`) — those load without any on-device work. `files/plugins` must be
  readable *and* read-only before ART will open a dex: an imported jar is chmod'ed read-only,
  and a jar left on shared storage fails with `Writable dex file … is not allowed`.
- **Where RuneLite keeps its config on this port.** `files/.runelite/profiles2/`
  (`profiles.json` + `<name>-<id>.properties`), *not* `settings.properties` — 1.13 moved
  the default profile into the profile store, and `ProfileManager` creates the directory
  itself. Verified: after a boot that file is ~41 KB, i.e. every plugin's config *item*
  defaults are written (the write path works). **RuneLite only flushes that file from
  `ConfigManager.sendConfig()`, which it runs from a `scheduleWithFixedDelay` task
  (minutes) and on a profile switch — a desktop process exits gracefully, an Android one
  is force-stopped without warning, so a panel change used to be lost on the next launch.
  The port flushes explicitly: `MainActivity.onStop()` calls `RuneLiteHost.flushConfig()`,
  the conformance pass flushes after its config probe, and `RuneLiteHost.setPluginEnabled`
  flushes too (though it has no caller at HEAD — the Swing panel toggles through RuneLite's
  own `PluginListPanel` → `PluginManager.setPluginEnabled`), so the guaranteed boundary for
  a drawer toggle or config edit is `onStop()`. Verified earlier with the native panel:
  enable FPS Control (`host=65p` → `66p`), `am force-stop`, relaunch → `66 plugin(s) active`.
  The side panel's own UI state (open/closed) lives in `SharedPreferences` and persists too.
  The drawer is a `rootLayout` child added *before* the login overlay, so the login overlay
  covers it while it is up.
- **Diagnostics.** The throttled `callbacks.draw` line prints `host=` (active plugins),
  `ovl=` per overlay layer with a couple of names, `iface=<id>/<overlays>` (the last
  interface the client drew and how many overlays are registered for it),
  `entities=<calls>/<denied>(p=<players> npc=<npcs>)` (cumulative since boot: how often the
  client asked `callbacks.draw(Renderable, boolean)` and how often the answer was "no", so
  a hiding plugin is visible as `denied > 0`; `calls=0` means nothing in the scene can be
  vetoed at all), `yellow=`/`red=`
  (pixels of RuneLite's FPS-overlay text colour in the frame's top-right strip), plus the
  frame/palette/blit timing. Useful when an overlay "does not appear": `ABOVE_WIDGETS`
  overlays (the FPS overlay, infoboxes) are registered under *interface ids* and drawn from
  `OverlayRenderer.renderAfterInterface`, so on the login screen — where the client draws no
  interface (`iface=-1`) — they legitimately never draw.

## Don't be misled by root-level jars

`gamepack_*.jar`, `injected_client.jar`, `runelite-api.jar` and `temp_disasm/`
are manual experiment dumps from previous debugging sessions — **not build
inputs**. The build downloads its own copies. Don't edit or depend on them.

## Repo state

`master`, clean working tree; `origin` is
`https://github.com/eshahrabani/runelite-mobile.git`. `local.properties` holds the
machine-specific `sdk.dir` and is already in `.gitignore`. No JVM test framework
exists: verification is the on-device selftests (`GraphicsSelfTest`, `SwingSelfTest`),
the plugin conformance pass and manual checks on the APK. `README.md` is the public
entry point; this file and `docs/*.md` are the developer doc set (the README's index
maps pages to topics).
