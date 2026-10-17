# Architecture

**Audience:** developer
**Read this when:** you need the shape of the port before reading any subsystem page.
**Verified against:** `settings.gradle`, `build.gradle`, `core/build.gradle`, `ios/build.gradle`, `android/build.gradle`, `android/src/main/java/org/runelite/mobile/MainActivity.java`, `android/src/main/java/org/runelite/mobile/{SidePanel,SwingSidebarView,SwingWindowLayer}.java`, `android/src/main/java/org/runelite/mobile/host/RuneLiteHost.java`, `android/src/main/java/org/runelite/mobile/host/MobilePluginHub.java`, `hostshims/src/net/runelite/client/ui/ClientUI.java`, `.gitignore`

## 1. The shape of the port

The app ships **no game client of its own**. At build time one `bootstrap.json` fetch pins three
RuneLite jars to the same version — `injected-client-<ver>.jar` (the game), `client-<ver>.jar` (the
plugin API plus the ~130 core plugins) and `runelite-api-<ver>.jar` (a verified superset of the
manifest's `-runtime` flavor) — plus the runtime libraries (guice, gson, okhttp, guava, commons,
protobuf, …). `d8` folds all of them into a single asset dex, `android/src/main/assets/runelite-dex.jar`
(`android/build.gradle`). At runtime the app hands that dex to ART and supplies the desktop-JVM
surface the client expects: a hand-written `java.awt`/`javax.swing` implementation living in `core/`,
a pixel buffer wired to an Android `Bitmap`, and a bridge (`org.runelite.mobile.**`). RuneLite's real
Swing shell ships and runs on that runtime; only the desktop-window/macOS/LAF classes are stripped
and replaced by five shims (`hostshims/`) — see [swing-runtime.md](swing-runtime.md). The app's
drawer (`SidePanel` + `SwingSidebarView`) is that shell's host: it paints the real
`ClientToolbarPanel` + `JTabbedPane` sidebar built by the `ClientUI` shim, not a native
reimplementation — the old native Plugins/Config/Host tabs and `PluginPanelRegistry` are gone. The
client's plugin manager runs unmodified against a build-time plugin index. The port therefore owns
the *host*, not the game.

The desktop surface the client needs is small but load-bearing. `core/` provides it: the
`java.awt` component/event/graphics types the client compiles against, the `javax.swing` widgets,
layout managers and event plumbing RuneLite's Swing shell needs, `java.applet.Applet`/
`AppletContext`, `java.awt.image.BufferedImage`, and the `java.lang.ProcessHandle`/`sun.misc.Unsafe`
replacements the client's indirect references resolve to. None of this exists on Android, so `core/`
implements it as real code where the client reads results (pixel maths, image access, Swing
painting) and as data-only stubs where the client only needs the type to load. `core/build.gradle`
compiles it with `--limit-modules java.base,jdk.unsupported`, which is what legally lets it define
classes in the **`java.applet.*`/`java.awt.*` namespace**; [core-stubs.md](core-stubs.md) owns the
detail.

## 2. Three artifacts, three loaders

There are three class universes, each with its own loader. Loaders delegate **parent-first**, so
the app dex is always the ultimate parent and an app-dex class always wins over the same name in a
child dex.

```mermaid
flowchart TD
  Hub["hub-jar DexClassLoader (one per jar, parent = client loader)"]
  Asset["asset DexClassLoader for assets/runelite-dex.jar (parent = app class loader)"]
  App["app class loader for classes.dex"]
  Hub -->|"loadClass delegates parent-first"| Asset
  Asset -->|"loadClass delegates parent-first"| App
```

The asset loader is constructed in `MainActivity.bootstrapGameClient` as
`new DexClassLoader(localJarFile, dexOutputDir, null, getClassLoader())`
(`MainActivity.java`); each hub jar gets its own child loader whose parent is the client
loader (`MobilePluginHub.java`).

Parent-first has three consequences you must design around:

- **App-dex classes always win.** `java.*`, `javax.*`, `org.runelite.mobile.**` and the slf4j
  binding come from `core/` and the app, never from the RuneLite jars.
- **Asset-dex-only classes are invisible to app-dex code.** `MainActivity` has **zero**
  compile-time `import net.runelite` statements (grep: none); every client type is reached with
  `dexClassLoader.loadClass(...)` and reflection.
- **The only cross-loader links are reflection-only.** The host reaches the client and the
  RuneLite runtime by name through the child loader — `client`, `net.runelite.api.GameEngine`,
  `net.runelite.client.ui.ClientUI`, … (`MainActivity.java`; `RuneLiteHost.get`), because no
  app-dex class has a compile-time `net.runelite.*` dependency; the client reaches the host
  through the app-dex `ClientConfiguration`/`Callbacks` proxies, whose interface types resolve from
  the child loader (`MainActivity.java`), and the asset-dex `ClientUI` shim enters the app through
  the `AWTBridge`/`UiBridge` statics (`hostshims/.../ClientUI.java`).

| Universe | Loader | Contents | Built / anchored at |
|---|---|---|---|
| App dex (`classes.dex`) | the Android application class loader | `core/` stubs (the `java.*`/`javax.*` surface + `org.runelite.mobile.bridge.*`), `org.runelite.mobile.**`, the slf4j-simple binding, androidx/material | `android/build.gradle` |
| Asset dex `assets/runelite-dex.jar` | asset `DexClassLoader` (`parent = getClassLoader()`) | `client-<ver>.jar` (plugin API + core plugins), `injected-client-<ver>.jar` (the game), `runelite-api-<ver>.jar` (duplicates stripped), the runtime libraries, `hostshims/` output, and the `runelite-plugin-index.txt` resource | `android/build.gradle`, `MainActivity` |
| Hub jars | one `DexClassLoader` per jar (`parent = client loader`) | third-party plugin classes plus their resources | `MobilePluginHub.java` |

The two bridges are **dynamic proxies**, not implementors. The app cannot write
`class X implements net.runelite.api.hooks.Callbacks` because that interface lives in the child
loader; instead `Proxy.newProxyInstance(dexClassLoader, {callbacksInterface}, handler)` builds a
class in the child loader at runtime, and the handler switches on the invoked method's name
(`MainActivity.java`). The same technique supplies `ClientConfiguration` and the OTL token requester. This is why a missing method in the
proxy shows up as a silently defaulted return value rather than a compile error — see
[diagnostics.md](diagnostics.md).

## 3. Module map

| Path | Kind | Output artifact | Consumed by |
|---|---|---|---|
| `core/` | `java-library` (plain JVM jar) | `core/build/libs` classes: the `java.awt`/`javax.swing`/`java.applet` stubs plus `org.runelite.mobile` bridge classes | compiled into `:android`'s app dex and `:ios` |
| `android/` | AGP application | APK (`org.runelite.mobile`) **and** the generated `assets/runelite-dex.jar` | shipped to devices |
| `hostshims/` | **not a Gradle module** | the five shim classes compiled by `compileHostShims()` inside `android/build.gradle` and folded into the asset dex | the asset dex (same loader as the client, so Guice can resolve shim signature types) |
| `ios/` | RoboVM `java` project | unsigned IPA `RuneLiteMobile.ipa` | not shipped by the weekly Android gate |
| `tools/` | developer scripts / Java tools | none (invoked by hand or by `verifyHostLinks`) | developers only |
| `docs/` | prose | none | readers |

`settings.gradle` includes exactly `:core`, `:android`, `:ios`; `hostshims/` is absent because its
sources are compiled ad hoc into the asset dex. `verifyHostLinks` — the gate that resolves every
reference the shipped classes make against the app+asset dexes, with `android/hostlink-ignore.txt`
and `android/hostlink-platform-extra.txt` keeping it honest — exists for this tree but is
deliberately **not** wired into `assembleRelease` (`android/build.gradle`).

The asset dex is a **build output**, produced by two Gradle tasks in `android/build.gradle`:
`syncRuneLiteJars` downloads and SHA-256-verifies the pinned jars, and `downloadAndDexJar` strips
replaced classes, rewrites each class through an ASM pass, compiles the shims, runs `d8`, repacks
resources, and writes the plugin index (`android/build.gradle`). `afterEvaluate` wires
`preBuild -> downloadAndDexJar` and makes every `JavaCompile`/`dex*` task wait on
`syncRuneLiteJars`, so a plain `:android:assembleRelease` regenerates the dex if
needed. `:android` compiles only against `:core`, androidx/material, and `slf4j-api` borrowed from
`android/build/rl-jars` — never against a RuneLite artifact, which is what keeps app dex free of
`net.runelite.*`. The full pipeline belongs to [build-and-release.md](build-and-release.md).

> **Don't be misled by root-level files.** `gamepack_*.jar`, `injected_client.jar`,
> `runelite-api.jar`, `temp_disasm/`, `screencap.png` and `build/` are manual
> debugging dumps from past sessions, not build inputs; `.gitignore` lists each of them.
> The one tracked oddity is `jdb_in` (debugger input), which `.gitignore`
> does not cover. The real inputs are `android/build/rl-jars/*.jar` (downloaded) and
> `android/src/main/AndroidManifest.xml`.

## 4. Boot sequence

```mermaid
sequenceDiagram
  participant A as "MainActivity (UI thread)"
  participant B as "device browser"
  participant S as "GameClientBootstrapper thread"
  participant D as "DexClassLoader (asset dex)"
  participant C as "client"
  participant H as "RuneLiteHost thread"
  participant U as "Swing EDT"
  participant R as "AWTBridge-RenderThread"
  A->>A: "onCreate: JVM props, pixel binding, ui-thread registration"
  A->>B: "startJagexLogin -> ACTION_VIEW (OAuth leg 1)"
  B-->>A: "onNewIntent: launcher redirect (code)"
  A->>A: "token exchange -> consent leg 2 -> game session (JX_* tokens)"
  A->>S: "launchGame starts GameClientBootstrapper"
  S->>S: "fetch jav_config, apply session env"
  S->>D: "new DexClassLoader(files/runelite-dex.jar, parent=app)"
  D->>C: "loadClass('client') -> instantiate -> setSize(765,503)"
  S->>C: "bind ClientConfiguration + Callbacks proxies, initialize()"
  S->>H: "new Thread(RuneLiteHost.start).setDaemon(true)"
  H->>H: "OkHttpClient, RuneLiteModule, Guice injector, get components"
  H->>U: "initSidebar: ClientUI.init() via invokeAndWait"
  H->>U: "AWTBridge.post(RuneLiteHost::startPlugins)"
  U->>U: "load plugin index, startPlugins, overlays, self-tests"
  R->>R: "runRenderLoop waits on renderLock for the first frame"
```

The same steps as code paths, in order:

1. `MainActivity.onCreate` (`MainActivity.java`) sets JVM properties, binds `AWTBridge.activePixels`
   to `appletPixels`, registers the Swing EDT as the runtime's UI thread
   (`AWTBridge.registerUiThread(SwingEdt.ensureStarted(), SwingEdt::post)`), installs the text
   bridge and host context, builds the launcher UI, and pre-registers the dex for ART dexopt.
2. `MainActivity.startJagexLogin` drives the browser-based OAuth flow; the redirect returns
   through `onNewIntent` and ends in session tokens or a manual/imported session. Details: [login-and-sessions.md](login-and-sessions.md).
3. `MainActivity.launchGame` hides the launcher and starts the `"GameClientBootstrapper"`
   thread.
4. `MainActivity.bootstrapGameClient` fetches `jav_config`, applies `JX_*` env vars,
   chooses the dex source, builds the `DexClassLoader`, loads `client`, installs the
   `ClientConfiguration` and `Callbacks` proxies, calls `initialize()`, and enables unlocked FPS.
5. `MainActivity` starts the daemon `"RuneLiteHost"` thread calling
   `RuneLiteHost.start(clientObject, dexClassLoader)`.
6. `RuneLiteHost.start` (`RuneLiteHost.java`) builds the injector, initializes the sidebar
   (`initSidebar` runs the `ClientUI` shim's `init()` on the Swing EDT via `AWTBridge.invokeAndWait`)
   and resolves core components, then posts `RuneLiteHost.startPlugins` to the Swing EDT via
   `AWTBridge.post`.
7. `RuneLiteHost.startPlugins` loads the plugin index, calls
   `PluginManager.loadPlugins`, registers the managers on the `EventBus`, calls
   `overlayManager.init()` and `pluginManager.startPlugins()`, then loads sideloaded hub
   plugins and runs `GraphicsSelfTest` and `SwingSelfTest`. Details: [plugin-runtime.md](plugin-runtime.md).
8. `surfaceCreated` starts the render thread, which presents client frames until
   `surfaceDestroyed`. Details: [rendering.md](rendering.md).

Two details in step 4 decide what actually runs. The dex source is
`files/runelite-dex.jar` when it is not older than the APK's bundled asset, otherwise the asset is
copied out and used (`MainActivity.java`); this is what lets an in-app update replace the
client without reinstalling the APK, and it is owned by [client-updates.md](client-updates.md). The
loader path is then `files/runelite-dex.jar` with `getDir("dex", MODE_PRIVATE)` as the optimized
output directory. Step 5 loads the obfuscated top-level class literally named
`client` — that name and every client-internal symbol the port touches is
version-specific and must be re-derived when the client version changes
([telemetry-assessment.md](telemetry-assessment.md)).

The boot is deliberately **idempotent and guarded**: if `clientInstance != null` the bootstrap
returns immediately, and any throw on the way through logs
`"Loader failed to bootstrap client"`, restores the launcher UI, and leaves the app in a
diagnosable state. Failure is recoverable because the client never partially
registered itself with the host: the host thread is only started after `initialize()` returns. [troubleshooting.md](troubleshooting.md) maps the failure messages back to causes.

## 5. Threading model

| Thread | Created / named at | Runs | Crosses the boundary via |
|---|---|---|---|
| Android main / UI thread | the Android framework | launcher view construction, login UI, bitmap presentation, touch/key forwarding | `runOnUiThread` |
| `"Swing-EDT-rlmobile"` | `SwingEdt.ensureStarted()`, registered with `AWTBridge.registerUiThread(...)` from `MainActivity.onCreate` | the RuneLite EDT: `initSidebar`, `startPlugins`, the plugin lifecycle, `SwingUtilities.invokeLater`/`invokeAndWait` routing, sidebar/window painting | `AWTBridge.post` / `AWTBridge.invokeAndWait` |
| `"GameClientBootstrapper"` | `MainActivity.java` | jav_config fetch, dex selection, loader construction, proxy installation, `client.initialize()` | publishes `clientInstance`/`clientObject`; starts the host thread |
| `"RuneLiteHost"` (daemon) | `MainActivity.java` | `RuneLiteHost.start`: runtime config fetch, Guice injector, sidebar init, component resolution | `AWTBridge.invokeAndWait(initSidebar)` then `AWTBridge.post(RuneLiteHost::startPlugins)` to the Swing EDT |
| client's own thread | started inside `GameEngine.initialize()` (`MainActivity.java`) | game loop; invokes the `Callbacks` proxy | `Callbacks.draw` → `renderLock`/`frameSeq` |
| `"AWTBridge-RenderThread"` | `surfaceCreated` (`MainActivity.java`) | blits the latest client frame to the `SurfaceView` | `renderLock.wait` on `frameSeq != lastDrawnSeq` |

Two rules follow from the table. First, **the render thread must never call `client.paint()`** —
the comment at `MainActivity.java` records that doing so tears frames; the loop presents
exactly the frame the client just produced, never one it drives on a timer. Second,
**guarding is by monitor, not by polling**: the client thread and the render thread rendezvous on
`renderLock` with `frameSeq`/`lastDrawnSeq` (`MainActivity.java`), and anything that must
run on the EDT is posted with `AWTBridge.post` rather than touched from another thread.

Three boundary contracts are worth naming explicitly:

- **Client thread -> render thread.** `Callbacks.draw` runs on the client thread, holds
  `renderLock` while overlay compositing and the buffer draw happen, and only then advances
  `frameSeq` (`MainActivity.java`). The render thread waits on the same monitor, so the
  client cannot overwrite `appletPixels` mid-copy.
- **Host thread -> Swing EDT.** `AWTBridge.registerUiThread(SwingEdt.ensureStarted(),
  SwingEdt::post)` (`MainActivity.java`) is what lets `SwingUtilities.invokeLater` and
  `AWTBridge.post` land on the Swing dispatch thread; `RuneLiteHost` relies on it for the
  `PluginManager` EDT assertion (`RuneLiteHost.java`).
- **Swing EDT -> client components.** Plugin lifecycle calls (`startPlugin`/`stopPlugin`,
  config writes) are made from the EDT through `RuneLiteHost`, which recomputes its
  `activePlugins` snapshot after each (`RuneLiteHost.java`).

## 6. iOS

`ios/` is a RoboVM 2.3.24 skeleton (`ios/build.gradle`). `IOSLauncher` extends
`UIApplicationDelegateAdapter`, creates a `UIWindow` from the main screen bounds, attaches a bare
`UIViewController`, and prints a message — there is no rendering and no bridge wiring
(`ios/src/main/java/org/runelite/mobile/IOSLauncher.java`). The build is macOS + Xcode only and
produces an **unsigned** IPA (`iosSkipSigning = true`, `ios/build.gradle`;
`archs = ['arm64']`). `:ios:robovmIPABuild` is a separate CI job that does **not** gate the weekly
Android release (`.github/workflows/build.yml`). Treat iOS as a placeholder that keeps the `core/`
stubs compiling for a second target; [device-runbook.md](device-runbook.md) covers Android only.

## 7. Data flow of one frame

1. The client thread renders the world into its own pixel buffer and invokes
   `callbacks.draw(bufferProvider, Graphics, x, y)` through the `Callbacks` proxy
   (`MainActivity.java`).
2. The proxy re-points the client's software rasterizer at the live buffer with
   `bindSceneRasterizerToDisplay(args[0])` (`MainActivity.java`).
3. If the host's `Hooks` is present, `hooks.draw(...)` composites plugin overlays directly into
   the client frame before anything is copied (`MainActivity.java`).
4. `Graphics.drawImage(img, x, y, null)` draws that frame into `AWTBridge.activePixels`, which is
   `MainActivity.appletPixels` (`MainActivity.java`).
5. The proxy advances `frameSeq` under `renderLock`, waking the render thread.
6. The render thread copies `appletPixels` into `renderBitmap` with `setPixels(...)` and presents
   it with `canvas.drawBitmap(renderBitmap, srcRect, renderDst, scalePaint)` into the centred
   765:503 fit rect, after painting the letterbox bars (`MainActivity.runRenderLoop`).

The invariants behind steps 3-6 — buffer agreement, the palette repoint, alpha handling and the
blit geometry — are owned by [rendering.md](rendering.md). For how a tap or key reaches the client,
see [input.md](input.md); for what the runtime prints while it does, see [diagnostics.md](diagnostics.md).

The three pixel stores that must agree are bound once and never reallocated per frame:

| Store | Type | Bound by | Anchor |
|---|---|---|---|
| `MainActivity.appletPixels` | `int[GAME_W * GAME_H]` | field initialiser | `MainActivity.java` |
| `AWTBridge.activePixels/activeWidth/activeHeight` | same array + `765`/`503` | `onCreate` | `MainActivity.java` |
| `MainActivity.renderBitmap` | `Bitmap` `ARGB_8888`, `setHasAlpha(false)` | `surfaceChanged` | `MainActivity.java` |

The client frame and the presented frame are therefore the same pixels: overlays composited inside
`Hooks.draw` (step 3) are already in `appletPixels` before the render thread touches it. Nothing in
this path is timer-driven — the render thread exists to convert "the client produced a frame" into
"the surface shows that frame", and it blocks when the client is not producing one.
