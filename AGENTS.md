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

## RuneLite plugin runtime (the third jar)

The port ships **three** RuneLite jars, all pinned by one
`https://static.runelite.net/bootstrap.json` fetch (task `syncRuneLiteJars`, verified
against the manifest SHA-256, so client/injected-client/api can never drift):
`client-<ver>.jar` (the plugin API + ~130 core plugins), `injected-client-<ver>.jar`
(the game) and `runelite-api-<ver>.jar` (the *full* api, checked to be a byte-identical
superset of the manifest's `-runtime` flavor, because core plugins are compiled against
the gameval ID tables it adds). All three are ASM-transformed and d8-dexed into `assets/runelite-dex.jar`, together with
RuneLite's own runtime libraries (guice, gson, okhttp, okio, guava, commons-lang3/text,
protobuf-javalite, json, jsr305, javax.inject, aopalliance, http-api) and the host shims
(see below). The **app** dex keeps only the JRE stubs (`core/`), the Android host
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
  - `net.runelite.client.ui.*` classes the host replaces are **stripped out of the asset
    dex** (`HOST_REPLACED_CLASSES`/`HOST_REPLACED_PREFIXES` in `android/build.gradle`,
    nested classes included — d8 refuses a nest member without its nest host) and the
    shims take their place *inside that same dex*: their sources live in
    `hostshims/src/net/runelite/client/ui/**` (deliberately NOT in the app source set)
    and `compileHostShims()` compiles+packages them as a d8 program input. A stripped
    class list must stay in sync with those shims (a missing entry is a d8 "defined
    multiple times" error, which is the intended tripwire).
  - `org.runelite.mobile.host.RuneLiteHost` reaches everything RuneLite-specific —
    including the Guice injector, `OkHttpClient` and `RuneLiteAPI.CLIENT` — by **name
    through the child loader**; the app dex has no compile-time RuneLite/Guice/OkHttp
    dependency at all (only `java.awt.*` stubs, `android.*` and slf4j). Do not add a
    compile-time `net.runelite.*`/`com.google.inject.*`/`okhttp3.*` reference to app-dex
    code, and do not add a compile-time `org.runelite.mobile.*` reference to a shim: the
    only cross-dex links are lambdas/fields the host installs reflectively (see
    `ClientToolbar.navigationListener`).
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
  `InfoPlugin` and `KourendLibraryPlugin` are excluded because their `startUp()` builds a
  Swing panel that cannot exist (`JEditorPane.getEditorKit().getStyleSheet()` and
  `GroupLayout.createParallelGroup().addComponent(...)` NPE). Verified on the device: the
  index is 114 classes, 114 instantiate with **0 failures**, 65 are active at boot (the
  `@PluginDescriptor(enabledByDefault)` set — the side panel toggles the rest), and the
  plugins register **141** unmodified RuneLite overlays. Note `PluginManager.startPlugins`
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
- **Plugin lifecycle runs on the UI thread.** `AWTBridge.registerUiThread(mainThread,
  handler::post)` (called from `MainActivity.onCreate`) is the event dispatch thread:
  `javax.swing.SwingUtilities.invokeLater/invokeAndWait/isEventDispatchThread` route
  there, which is what `PluginManager`'s EDT contract and `invokeAndWait` need. The
  host's own thread does only the blocking work (`RuntimeConfigLoader.get()`) before
  posting the lifecycle.
- **`java.awt`/`javax.swing` stubs.** The Android runtime has no `java.desktop`, so
  `core/` defines the surface the pre-compiled jars reference: hand-written pixel/text
  code (`Graphics`, `Graphics2D`, `Image`, `BufferedImage`, `Font`, `FontMetrics`) plus
  ~230 **generated** data-only stubs (`javax.swing.**`, `java.beans.**`,
  `javax.sound.sampled.**`, `com.sun.net.httpserver.**`, …). Regenerate them after a
  client bump with `python3 tools/gen_stubs.py --rl-jars android/build/rl-jars
  --asm-cp <asm:asm-tree jars>`; the member set comes from the real call sites
  (`tools/RefScan.java`), hierarchy/constants/enums from the local JDK
  (`tools/JdkInfo.java`), and files without the generator's marker header are never
  touched. The scan also records **how** each member is used (INVOKESTATIC vs
  INVOKEVIRTUAL/INVOKEINTERFACE, GETSTATIC vs GETFIELD) and the generator emits `static`
  accordingly: a stub method that is an instance method where the client uses
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
- **Verification gates.** `./gradlew :android:verifyHostLinks` resolves every JDK-surface
  reference the client jar and the app classes make against what the app dex + asset dex
  provide, and fails on any gap (a missing class or a wrong descriptor is otherwise a
  device-only `NoClassDefFoundError`/`NoSuchMethodError`). `GraphicsSelfTest` runs once
  per host start and logs `GFX SELFTEST PASS`/`FAIL <case>` (shapes, alpha blending, the
  opaque frame blit, text).
- **Native side panel** (`org.runelite.mobile.SidePanel`, opened from the `☰` button):
  Plugins / Config / Host tabs. Plugin enablement goes through
  `PluginManager.setPluginEnabled` + `startPlugin`/`stopPlugin` on the UI thread; config
  forms are generated from `ConfigManager.getConfigDescriptor` (widgets chosen by the
  item's return type, writes through the config proxy setter so `ConfigChanged` fires).
  A plugin's config interface is resolved the way RuneLite declares it — the
  `@ConfigGroup`-annotated interface that is the type of an injected field, or of a
  helper method's return type (`RuneLiteHost.pluginConfigClass`); there is no
  `getConfig()` on a plugin, and one that has no config at all (`AccountPlugin`) gets
  "this plugin has no configuration" instead of an error.
  RuneLite's Swing panels are never rendered: `ClientToolbar.addNavigation` records
  buttons in `PluginPanelRegistry`, and a plugin with a panel gets a "panel not available
  on mobile" note that opens its config instead.
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
  `ConfigManager.sendConfig()`**, which it runs from a `scheduleWithFixedDelay` task
  (minutes) and on a profile switch — a desktop process exits gracefully, an Android one
  is force-stopped without warning, so a panel toggle used to be lost on the next launch.
  `RuneLiteHost.flushConfig()` therefore calls `sendConfig()` after every panel-driven
  plugin toggle and config write, and `MainActivity.onStop()` flushes on the way out.
  Verified: enable FPS Control in the panel (`host=65p` → `66p`), `am force-stop`,
  relaunch → `66 plugin(s) active`. The side panel's own UI state (open/closed, tab)
  lives in `SharedPreferences` and persists too. The panel is a `rootLayout` child added
  *before* the login overlay, so the launcher overlay covers the drawer while it is up
  (the `☰` handle still toggles it).
- **Diagnostics.** The throttled `callbacks.draw` line prints `host=` (active plugins),
  `ovl=` per overlay layer with a couple of names, `iface=<id>/<overlays>` (the last
  interface the client drew and how many overlays are registered for it), `yellow=`/`red=`
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

`git init`-ed but **zero commits** — every file is untracked, no history/PRs.
`local.properties` holds the machine-specific `sdk.dir` — keep it out of any
future VCS. No tests exist; manual verification is via the APK on a
device/emulator.
