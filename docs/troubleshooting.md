# Troubleshooting

**Audience:** operator/developer
**Read this when:** a device build runs but renders, taps, plugins, or plugins' state misbehave
**Verified against:** `android/src/main/java/org/runelite/mobile/MainActivity.java`, `android/src/main/java/org/runelite/mobile/host/RuneLiteHost.java`, `android/src/main/java/org/runelite/mobile/host/PluginConformance.java`, `android/src/main/java/org/runelite/mobile/host/MobilePluginHub.java`, `android/src/main/java/org/runelite/mobile/ClientUpdater.java`, `android/build.gradle`, `core/src/main/java/org/runelite/mobile/TileCompositor.java`, `core/src/main/java/java/awt/Graphics.java`

Every section is one symptom: what you see, why it happens (with a code anchor), and the fix.
Logs all use the tag `RuneLiteMobile` unless stated. Deep mechanics live in the linked pages;
this page only routes you. Diagnostic surfaces and exact log-field meanings are in
[diagnostics.md](diagnostics.md); opaqueness/blit/palette invariants in [rendering.md](rendering.md).

## Static grey screen (rendering never starts)

- **What you see.** The themed boot overlay (`"Starting RuneLite…"` + the loader status line) stays
  up over a flat grey game area instead of disappearing; the throttled `callbacks.draw:` log never
  appears. The overlay is hidden by the *first presented frame* (`firstFramePresented` in the
  frame-blit proxy), so an overlay that never goes away and no `callbacks.draw:` line are the same
  symptom.
- **Why.** The `Callbacks` proxy bound into the client implements `draw` as a no-op or throws
  before it blits. `callbacks.draw` is the *only* thing that bumps `frameSeq`; with no bump the
  render thread's `while (frameSeq == lastDrawnSeq) renderLock.wait(100)` never wakes, so it keeps
  presenting the initial bitmap. `MainActivity` reports this as a `callbacks.draw failed` warning
  or `Callbacks.draw(Renderable,boolean) -> true` when no `Hooks` is bound. This is the #1 thing to
  check when rendering "stops".
- **Fix.** Read the first `callbacks.draw` line after boot (the boot overlay card's status text shows
  the same `Status Update:` string the log carries). If the line never prints, the proxy is not in
  the path: confirm `findFieldByType(clientClass, "net.runelite.api.hooks.Callbacks")` bound the
  proxy (`Callbacks proxy bound (field …)`), and that no `callbacks.NAME failed` warning precedes
  it. A `NoClassDefFoundError`/`NoSuchMethodError` thrown inside the frame path appears as
  `callbacks.draw failed`; resolve that stub gap first. See [rendering.md](rendering.md) and
  [diagnostics.md](diagnostics.md).

## Frozen world with a live minimap

- **What you see.** The minimap and UI update, but the 3D scene is a still image from the first
  presented frame.
- **Why.** The client's software rasterizer still writes into its original `int[]` target instead
  of the display buffer. `MainActivity.bindSceneRasterizerToDisplay` re-points the static
  rasterizer output field (`yw.ah`) at `BufferProvider.getPixels()` through the client's own
  `yw.ef(int[], int, int, float[])`, the call its resize path makes; the client only runs that on
  a desktop resize, which never happens here. The minimap is drawn by a different path, so it
  keeps animating over the frozen scene.
- **Fix.** Confirm the `Bound 3D rasterizer target to display buffer …` log appears
  (`MainActivity.bindSceneRasterizerToDisplay`). If it is absent or the bind field is `null`, the
  obfuscated names `yw.ah`/`yw.ef` no longer exist for the installed client version — the client
  was bumped and the internal names must be re-derived per
  [telemetry-assessment.md](telemetry-assessment.md) §6. In the throttled `callbacks.draw` line, a
  frozen `world=` with a changing `bridge=` is the same failure from the other side.

## Grey walls, black ground / trees / actors

- **What you see.** Geometry draws, but all shaded surfaces are grey and the ground, trees and
  actors are black.
- **Why.** `fa.ak` is each rasterizer's reference to the `fq.aq` HSL→RGB palette (65536 entries)
  that every shaded fill and model face reads (`MainActivity.paletteInvariant`). If `fa.ak` was
  re-pointed at the frame buffer, those palette lookups read screen pixels instead of palette
  entries, which collapses to grey/black. `fa.ak` is **not** a pixel target.
- **Fix.** Never retarget `fa.ak`; only `yw.ah` is a pixel target. Confirm `paletteInvariant()` in
  the `callbacks.draw` line reports the palette references still point at `fq.aq`. The
  version-specific names are re-derived on a client bump
  ([rendering.md](rendering.md), [telemetry-assessment.md](telemetry-assessment.md)).

## Stale login-screen remnants

- **What you see.** Pieces of the launcher/login screen stay visible behind or around the game
  frame, or the frame "smears" instead of clearing.
- **Why.** The software rasterizer writes 3D pixels with alpha 0. If the frame bitmap is treated as
  having alpha, `canvas.drawBitmap` blends and an alpha-0 pixel is *dropped*, leaving the previous
  surface content (the login screen) visible.
- **Fix.** `renderBitmap.setHasAlpha(false)` in `MainActivity.surfaceChanged` makes Skia treat the
  bitmap as opaque; it replaces the older per-pixel `| 0xFF000000` pass. Do not remove it. This is
  the same invariant as the opaque `Graphics.drawImage` fast paths — only `hasAlpha` sources blend
  ([rendering.md](rendering.md), [core-stubs.md](core-stubs.md)).

## Torn frames

- **What you see.** A horizontal seam, half of one frame and half of the next.
- **Why.** The render thread called `client.paint()`, which blits the game's *live* frame buffer
  while the client thread is still rendering the next frame into it. `MainActivity.runRenderLoop`
  documents that the thread must present only the frame delivered by `callbacks.draw` (the
  `frameSeq`/`renderLock` handshake), never on a timer and never via `client.paint()`.
- **Fix.** Remove any `client.paint()`/timer presentation. The one-frame handshake is
  `frameSeq` bumped by the `draw` proxy and consumed under `renderLock` by
  `renderBitmap.setPixels(appletPixels, …)` ([rendering.md](rendering.md)).

## ~0.87 fps

- **What you see.** The client presents roughly once per second, far below `FPS_TARGET = 60`.
- **Why.** Frame pacing is client-side. The default clock (`mo.xg`) sets its target (`mo.bd`) once
  per 20 ms catch-up batch, so only one frame per up-to-10 cycles is presented. `MainActivity`
  switches the client to the unlocked clock by calling `Client.setUnlockedFps(true)` **then**
  `setUnlockedFpsTarget(FPS_TARGET)`; turning unlocked fps off clears the target, so the order is
  load-bearing. The log line is `unlocked fps target=60`, or `unlocked fps unavailable` if the
  calls were not reachable.
- **Fix.** Keep the `setUnlockedFps(true)` → `setUnlockedFpsTarget(...)` order and both calls.
  Game ticks are unaffected by this setting ([rendering.md](rendering.md)).

## Taps do nothing while logcat shows `Dispatch mouse id=501 …`

- **What you see.** A press logs `Dispatch mouse id=…at (x,y) to …` (`MainActivity.dispatchMouseEvent`)
  but the client never reacts; no exception is printed.
- **Why.** `emitPoint` builds a `java.awt.event.MouseEvent` and dispatches it; the client's handler
  calls a member the port's stub lacks, and the resulting `NoSuchMethodError` is caught as
  `Throwable` *after* the dispatch log line, so the failure is silent. Missing members that produce
  exactly this include `MouseEvent.getPoint()/getComponent()`,
  `InputEvent.getModifiersEx()/getModifiersExText(int)`, and the event constructors.
- **Fix.** Compare the reference `MouseEvent`/`InputEvent` stubs against the members the client's
  handler invokes; add the missing members to the hand-written stubs under `core/`. The load-bearing
  member list and the no-arg-ctor/hand-written-supertype rules are in
  [core-stubs.md](core-stubs.md); the event path is in [input.md](input.md).

## `⌨` tile missing, or typing does nothing

- **What you see.** There is no `⌨` tile on the launcher; or the tile exists, the keyboard bar
  appears, but typed characters never reach the game.
- **Why (tile).** The tile is the foot of the side panel's right-edge column, and the whole column is
  hidden (`setAvailable(false)`) until the game runs — so it only exists on the game screen, by
  design. `launchGame` brings it back with `setAvailable(true)`.
- **Why (typing).** The `TextWatcher` diff in `kbBar` emits `VK_BACK_SPACE` per removed char and
  `dispatchKeyText(added)` for new text; `deliverKeyEvent` walks `getKeyListeners()` on the target
  and canvas and constructs a `java.awt.event.KeyEvent`. If the `KeyEvent` stubs are incomplete
  (`getKeyText(int)`, `getExtendedKeyCode()`, `setKeyCode`/`setKeyChar`, `paramString`), the event
  is dropped silently. Two non-bug cases that look identical:
  - the game's chat input is **not open** — the client ignores chat characters until `Enter` (the
    bar's `Enter` pill or the IME's action) has opened the chat line, and the chat line is drawn at
    the bottom of the game frame, which the soft keyboard covers. The bar itself (top-anchored) shows
    the text you typed, which is the reliable indicator that the bridge is working;
  - the IME is in Gboard's landscape fullscreen "extract" mode, which takes over the screen. That is
    what `kbEdit`'s non-password input type plus `IME_FLAG_NO_EXTRACT_UI` are for (see
    [input.md](input.md) §6).
- **Fix.** Start the client before expecting the tile; verify the `KeyEvent` stub members against
  the client's key handler. This is an AWT `KeyEvent` bridge, not an in-game IME — see
  [input.md](input.md) and [core-stubs.md](core-stubs.md).

## Overlays absent on the login screen

- **What you see.** A plugin's overlay (FPS box, infoboxes) draws in-game but never on the login
  screen.
- **Why.** `ABOVE_WIDGETS` overlays are registered under interface ids and rendered from
  `OverlayRenderer.renderAfterInterface`. On the login screen the client draws no interface, so
  `iface=-1` and those layers legitimately have nothing to attach to.
- **Fix.** None — this is expected. Confirm via the `iface=<id>/<overlays>` field of the
  `callbacks.draw` line: a `-1` interface id explains the absence. World/entity overlays that draw
  through `Hooks.draw` instead should still appear ([rendering.md](rendering.md),
  [diagnostics.md](diagnostics.md)).

## A plugin listed as active but inert

- **What you see.** The Host tab reports a plugin as loaded/active, but it has no visible effect.
- **Why.** The loader counters derive from successful *instantiation* only and say nothing about
  behaviour: a plugin can be active and still throw on every event (e.g. a missing class) with no
  counter moving (the `PluginConformance` class doc calls this out). Instantiation is not a proof
  it works.
- **Fix.** Run a conformance pass and read the behavioural metrics: `subs=<reg>/<decl>`,
  `ovl=<ok>/<bad>`, `cfg=<ok>/<bad>`, `rc=<n>`, and the `entityVeto`/`menuEntry`/`eventFlow`
  probes. A subscription-count mismatch and any link error always fail. The metric meanings and the
  trigger are in [diagnostics.md](diagnostics.md); the plugin lifecycle is in
  [plugin-runtime.md](plugin-runtime.md).

## A plugin toggle is lost after relaunch

- **What you see.** Enabling/disabling a plugin or editing a config item, then force-stopping or
  relaunching the app, loses the change.
- **Why.** RuneLite only writes its profile file from `ConfigManager.sendConfig()`. The port flushes
  that explicitly; if the flush did not run before the process died, the change was never written to
  `files/.runelite/profiles2/`. `RuneLiteHost.flushConfig()` is called from the panel toggle/config
  write paths (`SidePanel`) and from `MainActivity.onStop`.
- **Fix.** Ensure the config write path reaches `RuneLiteHost.flushConfig()` and that the app is
  backgrounded normally (`onStop`) rather than force-stopped immediately after the change. Storage
  layout and flush timing are in [plugin-runtime.md](plugin-runtime.md); the panel wiring in
  [side-panel.md](side-panel.md).

## `Writable dex file … is not allowed`

- **What you see.** A hub jar is present in `files/plugins/` but fails to load; the log shows ART
  refusing a writable dex.
- **Why.** ART rejects a dex that is writable. A jar pushed onto shared storage is writable, and so
  is a freshly imported copy until its mode is tightened.
- **Fix.** The hub imports external jars into private storage and then calls
  `setWritable(true, true); setReadOnly()` before loading (`MobilePluginHub.loadPlugins`). If you see
  this error, the read-only step was skipped or the file was replaced afterwards. Import/load
  semantics are in [third-party-plugins.md](third-party-plugins.md).

## `Unsupported dynamic constant` at d8

- **What you see.** The build fails in `downloadAndDexJar` with d8 rejecting a `CONSTANT_Dynamic`.
- **Why.** d8 cannot compile `CONSTANT_Dynamic` with bootstrap arguments. The obfuscated client uses
  it to express singletons without static fields, so `transformClassBytes` rewrites every occurrence
  through `loadDynamicConstant`; the ASM pass runs on **every** class, not just scanned ones, which
  is why the old byte-scan gate was removed.
- **Fix.** Do not gate the transform by class. If a new client version introduces an unhandled
  bootstrap shape, extend `transformClassBytes`/`loadDynamicConstant` in `android/build.gradle`; the
  pipeline and the rewrite catalogue are in [build-and-release.md](build-and-release.md).

## `NoClassDefFoundError: net.runelite.api.Client` from a shim

- **What you see.** The host starts but plugins that inject a host shim fail with
  `NoClassDefFoundError: net.runelite.api.Client` (or another asset-dex type) during Guice
  resolution.
- **Why.** Host shims live in `hostshims/` (compiled by `compileHostShims()`), not in the app source
  set, because they replace client-jar classes and must share the *asset* class loader so Guice can
  resolve their signature types (`net.runelite.api.Client` in `ColorPickerManager.create`). A shim
  placed in the app dex cannot see asset-dex types.
- **Fix.** Put the shim under `hostshims/src`; do not move it into `android/src/main/java`. Ctor
  parameters and method signatures must be resolvable from the asset loader and match upstream
  exactly ([plugin-runtime.md](plugin-runtime.md), [architecture.md](architecture.md)).

## App runs slow; `pm art dump` shows `verify` instead of `speed`

- **What you see.** The game is roughly 10x slower than expected; `pm art dump` reports the client
  dex as `verify` rather than `[status=speed]`; the launcher shows a red `NOT AOT-COMPILED` line and
  the Host tab a red `client AOT` row.
- **Why.** The target device runs `dalvik.vm.usejit=false`, so interpreted code is ~10x slower. ART
  keys the odex to the class-loader context (APK path + APK/dex checksums), so an APK install or a
  downloaded client-jar update can invalidate it. `ClientUpdater.clientDexAotStatus` is a **heuristic**
  over `files/oat/<isa>/runelite-dex.odex` vs the **client jar's** mtime: `AOT_STALE` (odex older than
  the jar) or `AOT_MISSING`; `AOT_UNKNOWN` means `files/oat` was unreadable and nothing can be claimed.
  It logs `AOT check: odex=… jar=… apk=…` on every call, and it can be wrong in both directions (it
  cannot see an install into a *different* app directory, and it no longer reports stale just because
  the APK is newer — ART does not rewrite an odex whose dex input is unchanged). `pm art dump` is the
  ground truth.
- **Fix.** Re-run the AOT compile after every install and every client-jar update:
  `cmd package compile -m speed -f org.runelite.mobile` and then
  `cmd package compile -m speed -f --secondary-dex org.runelite.mobile` — the `--secondary-dex` pass
  **last**, because the plain package compile drops the client dex back to `verify`. Verify the result with `pm art dump`. Exact copy-pasteable steps and the
  debuggable-build caveat are in [device-runbook.md](device-runbook.md); the AOT-status semantics and
  the launcher/Host-tab display are in [client-updates.md](client-updates.md).

## Where to look next

| Symptom family | Owning page | Also |
|---|---|---|
| Grey/frozen/torn/smeared frame, palette, fps | [rendering.md](rendering.md) | [telemetry-assessment.md](telemetry-assessment.md) |
| No `callbacks.draw` line, conformance, log fields | [diagnostics.md](diagnostics.md) | [rendering.md](rendering.md) |
| Taps/scroll/keyboard not reaching the client | [input.md](input.md) | [core-stubs.md](core-stubs.md) |
| Missing stub member, `NoSuchMethodError`, static-shape errors | [core-stubs.md](core-stubs.md) | [tools.md](tools.md) |
| Plugin inert / no effect after load | [diagnostics.md](diagnostics.md) | [plugin-runtime.md](plugin-runtime.md) |
| Plugin toggle or config lost | [plugin-runtime.md](plugin-runtime.md) | [side-panel.md](side-panel.md) |
| Hub jar refuses to load / dexing | [third-party-plugins.md](third-party-plugins.md) | [build-and-release.md](build-and-release.md) |
| Build/dex failure, pipeline rewrite | [build-and-release.md](build-and-release.md) | [tools.md](tools.md) |
| Slow app, AOT status, install/verify | [device-runbook.md](device-runbook.md) | [client-updates.md](client-updates.md) |
| Login/OAuth/session failures | [login-and-sessions.md](login-and-sessions.md) | [diagnostics.md](diagnostics.md) |
