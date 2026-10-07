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
- `./gradlew :ios:robovmIPABuild` — macOS + Xcode only (RoboVM 2.3.24).
- CI (`.github/workflows/build.yml`): weekly Wednesday cron (after Jagex's weekly
  game update) + manual dispatch; artifact at
  `android/build/outputs/apk/release/android-release.apk`.
- Java 11 source/target (CI uses Zulu 11; a newer local JDK works). No tests,
  no lint config, no README.

## Architecture (non-obvious from filenames)

- **`core/`** — java-library that compiles with
  `--limit-modules java.base,jdk.unsupported` (core/build.gradle) so it can
  define stub classes in the **`java.applet.*` / `java.awt.*` namespace**
  (~85 files under `core/src/main/java/java/`). The game client paints into
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
  (sign in → character picker → Play) → Jagex login via two-leg OAuth in an
  in-app WebView (`JagexOAuthClient` — see below) → bootstrap: emulate only
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
     with a live minimap. `fa.ak` is **not** a pixel target — it is each
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
  intercepted by `shouldOverrideUrlLoading`). Game session:
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
  client installed and working.
- **Render loop is fixed 765×503** (the client's own frame size — `GAME_W` in
  MainActivity must match it): `appletPixels` (MainActivity) ↔
  `AWTBridge.activePixels/activeWidth/activeHeight`. Change all three
  together. The render thread (`runRenderLoop`) blocks on `renderLock` until
  `callbacks.draw` has bumped `frameSeq` — it never presents on a timer, and
  each presented frame is exactly one client frame. Presentation is two native
  calls: `renderBitmap.setPixels(appletPixels, …)` and one
  `canvas.drawBitmap(renderBitmap, srcRect, dstRect, scalePaint)` that lets
  Skia scale 765×503 to the surface (nearest-neighbour:
  `scalePaint.setFilterBitmap(false)`). Do **not** reintroduce per-pixel Java
  loops here — the old scale loop + `| 0xFF000000` pass cost 172-229 ms/frame
  on a Pixel 8 Pro; this path costs ~12-15 ms of one core at 2244×1008.
  The thread runs at `THREAD_PRIORITY_DISPLAY` and idles (no canvas lock) while
  `renderBitmap == null`. A debug overlay + 5 s GameState log (`scaleMs=`) are
  drawn on top of the frame.
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
  `cmd package compile -m speed -f --secondary-dex org.runelite.mobile`
  (+ the same without `--secondary-dex`) after every client-jar update.
  `pm art dump` must show `[status=speed]` — **not** `run-from-apk` and not
  `[status=verify]`. The installed APK must not be debuggable: ART Service
  rewrites `-m speed` to `verify` for debuggable packages, so AOT only works on
  the release build (same debug signing key → `install -r` keeps app data).
- **Touch input is wired**: ACTION_DOWN/MOVE/UP → MOUSE_PRESSED/DRAGGED/
  RELEASED/CLICKED, ACTION_SCROLL → MOUSE_WHEEL, dispatched to the client
  component itself, falling back to the canvas from `GameEngine.getCanvas()`
  (see `resolveInputTarget()` in MainActivity). ACTION_DOWN emits a
  MOUSE_MOVED at the same point **before** the MOUSE_PRESSED: a mouse always
  moves before it presses, and the client's own menus (the world list) select
  the **hovered** row (`ar.bf` selects `dr`, set from `tk.af`/`tk.ac`, not from
  the press position), so without that move a tap acts on wherever the previous
  gesture left the cursor. A **two-finger drag** instead emits a middle-button
  (`BUTTON2`) press/drag/release at the two-finger centroid, which drives the
  client's own camera-drag path (`tk.aj() == 4` with its `bn.hc` setting, forced
  true for the gesture) to rotate yaw+pitch; the single-finger press is held off
  `TAP_PRESS_DELAY_MS` so a two-finger gesture can never fire a stray
  walk/attack. Keyboard is NOT wired (no on-screen keyboard;
  in-game chat is future work).
- **`ios/`** — RoboVM `IOSLauncher` skeleton only (empty UIWindow, no rendering).

## Don't be misled by root-level jars

`gamepack_*.jar`, `injected_client.jar`, `runelite-api.jar` and `temp_disasm/`
are manual experiment dumps from previous debugging sessions — **not build
inputs**. The build downloads its own copies. Don't edit or depend on them.

## Repo state

`git init`-ed but **zero commits** — every file is untracked, no history/PRs.
`local.properties` holds the machine-specific `sdk.dir` — keep it out of any
future VCS. No tests exist; manual verification is via the APK on a
device/emulator.
