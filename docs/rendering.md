# Rendering

**Audience:** developer
**Read this when:** you are changing the render loop, the frame buffer, the AWT bridge, or debugging a grey, frozen, stale, or torn screen.
**Verified against:** `android/src/main/java/org/runelite/mobile/MainActivity.java` (`surfaceChanged`, `runRenderLoop`, `bindSceneRasterizerToDisplay`, `paletteInvariant`, `logFrameDiagnostics`, callbacks proxy), `core/src/main/java/java/awt/Graphics.java`, `core/src/main/java/java/awt/image/BufferedImage.java`, `android/src/main/java/org/runelite/mobile/AndroidTextRenderer.java`, `core/src/main/java/org/runelite/mobile/bridge/AWTBridge.java`, `core/src/main/java/org/runelite/mobile/bridge/TextBridge.java`, `docs/telemetry-assessment.md`

The port does not draw the game. The client renders its own 765×503 software frame; the port only
binds the client's output to a display buffer, lets RuneLite's `Hooks` composite plugin overlays into
that same frame, and copies it to the Android surface. Everything below is the plumbing around those
three facts.

## 1. The contract

The game world is a fixed 765×503 ARGB space (`GAME_W`/`GAME_H`, `MainActivity.java`). That is
the client's own frame size, not a device size; the surface is a separate, larger coordinate space.
Three buffers must agree or the frame is wrong:

| Buffer | Owner | Role |
|---|---|---|
| `appletPixels` `int[GAME_W * GAME_H]` | `MainActivity` (`MainActivity.java`) | the display buffer `Graphics.drawImage` writes into; the render thread reads it |
| `AWTBridge.activePixels` / `activeWidth` / `activeHeight` | `core` static (`AWTBridge.java`) | the handle core-side code uses to reach that same array |
| `renderBitmap` | `MainActivity` (`MainActivity.java`) | the Android `Bitmap` the array is copied into and blitted from |

`onCreate` binds the first two together exactly once: `AWTBridge.activePixels = appletPixels;
AWTBridge.activeWidth = GAME_W; AWTBridge.activeHeight = GAME_H;` (`MainActivity.java`). Change
`GAME_W`/`GAME_H` and you must change the client's frame size, the array length, and the bridge
dimensions together — they are not independently adjustable.

`renderBitmap` is recreated on every surface change as `Bitmap.createBitmap(GAME_W, GAME_H, ARGB_8888)`
with `srcRect` set to the whole frame (`MainActivity.surfaceChanged`). The destination is **not** the
whole surface: the surface is letterboxed to the frame's 765:503 aspect (uniform scale, centred) and
`fitLeft/fitTop/fitW/fitH` are that fit rect, recomputed on every `surfaceChanged` and logged as
`surface <w>x<h> fit <w>x<h> at <l>,<t>`.

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 250" role="img" aria-label="Pixel flow from the client frame to the surface">
  <text x="30" y="28" font-size="16" fill="var(--fg)">One client frame: the pixel path (client thread &#8594; render thread)</text>
  <g stroke="var(--border)" fill="var(--surface)">
    <rect x="30" y="60" width="160" height="80" rx="6"/>
    <rect x="220" y="60" width="160" height="80" rx="6"/>
    <rect x="410" y="60" width="160" height="80" rx="6"/>
    <rect x="600" y="60" width="160" height="80" rx="6"/>
    <rect x="790" y="60" width="180" height="80" rx="6"/>
  </g>
  <g fill="var(--fg)" font-size="14" text-anchor="middle">
    <text x="110" y="92">client frame int[]</text>
    <text x="110" y="112" font-size="12" fill="var(--muted)">getImage()</text>
    <text x="300" y="92">Hooks.draw</text>
    <text x="300" y="112" font-size="12" fill="var(--muted)">overlay compositing</text>
    <text x="490" y="92">appletPixels</text>
    <text x="490" y="112" font-size="12" fill="var(--muted)">int[765*503]</text>
    <text x="680" y="92">renderBitmap</text>
    <text x="680" y="112" font-size="12" fill="var(--muted)">setPixels()</text>
    <text x="880" y="92">Surface canvas</text>
    <text x="880" y="112" font-size="12" fill="var(--muted)">drawBitmap()</text>
  </g>
  <defs>
    <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
      <path d="M0,0 L8,4 L0,8" fill="var(--accent)"/>
    </marker>
  </defs>
  <g stroke="var(--accent)" fill="none" marker-end="url(#arrow)">
    <line x1="190" y1="100" x2="218" y2="100"/>
    <line x1="380" y1="100" x2="408" y2="100"/>
    <line x1="570" y1="100" x2="598" y2="100"/>
    <line x1="760" y1="100" x2="788" y2="100"/>
  </g>
  <g fill="var(--muted)" font-size="12">
    <text x="30" y="180">The software rasterizer writes the frame through the static yw.ah array, re-bound each frame by</text>
    <text x="30" y="198">bindSceneRasterizerToDisplay(bufferProvider). The client thread owns every step up to frameSeq++.</text>
    <text x="30" y="216">The render thread owns setPixels + drawBitmap and presents exactly one completed frame per handshake.</text>
  </g>
</svg>
```

## 2. The frame handshake

The client calls the `Callbacks.draw(MainBufferProvider, Graphics, int, int)` overload once per
completed frame. The proxy's frame-blit branch receives the provider as `args[0]` and the destination
`Graphics` as `args[1]` (callbacks proxy). It binds the rasterizer, grabs the frame
`Image` via reflection (`args[0].getClass().getMethod("getImage")`), and inside `synchronized
(renderLock)` either delegates to `Hooks.draw` (which composites overlays and blits the frame into
`args[1]`) or does `((java.awt.Graphics) args[1]).drawImage(img, 0, 0, null)` when no host is running.
Only then does it bump `frameSeq` and `renderLock.notifyAll()`, and on the first frame it clears
`firstFramePresented` so the boot overlay hides.

```mermaid
sequenceDiagram
    participant C as "client thread"
    participant P as "callbacks.draw proxy"
    participant H as "RuneLite Hooks"
    participant R as "render thread"
    participant S as "Surface"
    C->>P: "draw(bufferProvider, Graphics, x, y)"
    P->>P: "bindSceneRasterizerToDisplay(bufferProvider)"
    P->>H: "Hooks.draw(args)"
    H->>H: "composite plugin overlays into the client frame"
    H->>P: "return"
    P->>P: "frameSeq++ ; renderLock.notifyAll()"
    R->>P: "wait on renderLock while frameSeq == lastDrawnSeq"
    P-->>R: "frame ready"
    R->>R: "renderBitmap.setPixels(appletPixels, 0, GAME_W, ...)"
    R->>S: "drawRect bars + drawBitmap(renderBitmap, srcRect, renderDest, scalePaint)"
```

`runRenderLoop` blocks in `while (isRunning && frameSeq == lastDrawnSeq) renderLock.wait(100)` before
presenting. The loop is **not** on a timer, and the render thread must
**never** call `client.paint()`: that would read the game's live frame buffer while the client thread
is rendering the next frame into it, which is the torn-frame symptom. The only correctness rule is
that each presented frame is exactly one completed client frame.

### Surface lifecycle

| Callback | Action |
|---|---|
| `surfaceCreated` | sets `isRunning = true`, starts the `"AWTBridge-RenderThread"` thread |
| `surfaceChanged` | recreates `renderBitmap`, disables its alpha, sets `srcRect`, and recomputes the letterbox fit (`surfaceW/H`, `fitLeft/Top/W/H`) |
| `surfaceDestroyed` | sets `isRunning = false`, interrupts and joins the render thread |

The surface is inset by the right-edge chrome whenever the drawer or its column is showing
(`applyGameInsets`, see [side-panel.md](side-panel.md)), so a drawer toggle fires `surfaceChanged`
with a smaller width and the game reletterboxes to the reduced area instead of being overlapped.

The render thread runs at `THREAD_PRIORITY_DISPLAY` so it stays scheduled while the client thread
saturates a core with the software renderer (`MainActivity.runRenderLoop`). While `renderBitmap == null`
(no surface yet) the loop sleeps 50 ms per iteration so background work — updates, dexing — is not
starved. It never holds the canvas lock while idle.

## 3. The three invariants

### 3.1 Re-bind the 3D rasterizer output

- **Symptom:** a static grey screen, or a frozen world with a still-live minimap. Nothing the client
  updates in the world region reaches the surface.
- **Cause:** the client's software 3D rasterizer writes through its own static output array (`yw.ah`),
  which the desktop runtime re-points on resize but the Android runtime never does. The app blits
  `appletPixels`, so unless the rasterizer writes into that same array the display buffer stays stale.
- **Fix:** `bindSceneRasterizerToDisplay(bufferProvider)` re-points the target every frame. It reads
  the provider's pixels, width, and height, fetches the static depth array `yw.aw`, and calls the
  static `yw.ef(int[], int, int, float[])` — which sets output, clip, and depth together
  (`MainActivity.java`). It early-returns when the array reference is unchanged, so it is
  idempotent, and it re-checks per frame because the client can re-point `yw.ah` behind the app's
  back.
- **Trap:** do **not** retarget `fa.ak`. It is not a pixel target; it is each rasterizer's reference to
  the 65536-entry HSL→RGB palette (`fq.aq`). Re-pointing it at the frame makes shaded fills read
  screen pixels — walls take grey from the upper screen, ground and tree trunks go black
  (`MainActivity.java`). `paletteInvariant()` checks the palette references are still intact
  and is surfaced in the frame log (§8).
- The client-internal names `yw.ef`, `yw.ah`, `yw.aw`, `fq.aq`, `fa.ak` are version-specific and must
  be re-derived after a client bump; see [telemetry-assessment.md](telemetry-assessment.md) §6 for the
  procedure.

### 3.2 Alpha-0 rasterizer pixels and `setHasAlpha(false)`

- **Symptom:** stale login-screen remnants — old content showing through the current frame.
- **Cause:** the software rasterizer writes 3D pixels with alpha 0. `canvas.drawBitmap` composites
  SRC_OVER, so if the bitmap carries alpha, its alpha-0 pixels are dropped and the previous surface
  content stays visible.
- **Fix:** `renderBitmap.setHasAlpha(false)` right after the bitmap is created
  (`MainActivity.surfaceChanged`). Skia then treats the bitmap as opaque. This replaces the older
  `pixel | 0xFF000000` pass that forced alpha per pixel on the Java side.

### 3.3 The two `drawImage` paths and the `hasAlpha` gate

`Graphics.drawImage` writes into a shared non-premultiplied ARGB `int[]`; destination alpha is ignored
for colour arithmetic and preserved on write (`Graphics.java`). It branches once on
`img.hasAlpha` (`Graphics.java`):

- **Opaque, 1:1:** one `System.arraycopy` per row (`Graphics.java`). This is the game's
  every-frame path — `drawImage(img, 0, 0, null)` with the source already at display size — so it must
  stay branch-free and native.
- **Opaque, scaled:** a divide-free nearest-neighbour loop stepping source row/column with accumulators
  (`Graphics.java`), used by the splash logo.
- **Blended:** only sources with `hasAlpha` take `drawImageBlend`, the same accumulator walk with a
  per-pixel source-over (`Graphics.java`).

`blendPixel` scales source alpha by the composite alpha; when `!destHasAlpha` it blends RGB but keeps
the destination alpha byte (`Graphics.java`), which is why overlays drawn over the alpha-0 game
frame blend visually instead of turning opaque. This is the same contract that keeps
`BufferedImage.getRGB` returning an opaque colour: with an empty alpha mask,
`DirectColorModel.getRGB` returns `0xFF000000 | (pixel & 0xFFFFFF)` (`BufferedImage.java`).
See [core-stubs.md](core-stubs.md) for the full `Graphics`/`Image` fidelity limits.

## 4. Presentation cost model

Presentation is two native calls and no Java per-pixel work: `renderBitmap.setPixels(appletPixels, 0,
GAME_W, 0, 0, GAME_W, GAME_H)` and a single
`canvas.drawBitmap(renderBitmap, srcRect, renderDst, scalePaint)`. Skia scales
765×503 to the fit rect in native code. Four `drawRect` calls paint the letterbox bars first (empty
rects are skipped), because the surface buffer is not cleared by the system and bar pixels are left
over from the previous geometry.

Do **not** reintroduce the historical per-pixel Java scale loop plus `| 0xFF000000` pass: recorded in
the port's own recorded measurement at 172-229 ms/frame on a Pixel 8 Pro, versus ~12-15 ms of one core at 2244×1008
for the current path. `scalePaint` is configured `setFilterBitmap(false)` and `setDither(false)`
so the upscale is nearest-neighbour — the same sampling the old loop
used — with no dithering pass. `scaleMs=` in the GameState log measures exactly `setPixels` + the blit.

SVG #2 shows the blit geometry: `srcRect` is the whole 765×503 frame and `renderDst` is the centred
fit rect inside the surface, so the scale is uniform and the leftover strips are painted with
`barPaint` (`UiTheme.BG_DEEP`).

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 920 330" role="img" aria-label="765 by 503 frame scaled into the centred letterbox fit rect">
  <text x="30" y="28" font-size="16" fill="var(--fg)">Blit geometry: srcRect (765&#215;503) &#8594; renderDst (centred fit rect)</text>
  <rect x="60" y="100" width="230" height="151" fill="var(--surface)" stroke="var(--border)"/>
  <text x="175" y="90" font-size="14" fill="var(--fg)" text-anchor="middle">srcRect (0,0,765,503)</text>
  <text x="175" y="182" font-size="13" fill="var(--muted)" text-anchor="middle">renderBitmap</text>
  <rect x="520" y="60" width="340" height="223" fill="var(--surface)" stroke="var(--border)"/>
  <rect x="520" y="103" width="340" height="137" fill="var(--c1)" fill-opacity="0.25" stroke="var(--accent)"/>
  <text x="690" y="50" font-size="14" fill="var(--fg)" text-anchor="middle">Surface canvas (surfaceW &#215; surfaceH)</text>
  <text x="690" y="176" font-size="13" fill="var(--fg)" text-anchor="middle">renderDst = fit rect</text>
  <line x1="292" y1="175" x2="516" y2="171" stroke="var(--accent)" fill="none" marker-end="url(#blit)"/>
  <defs>
    <marker id="blit" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
      <path d="M0,0 L8,4 L0,8" fill="var(--accent)"/>
    </marker>
  </defs>
  <text x="330" y="205" font-size="12" fill="var(--fg)">drawBitmap(scalePaint)</text>
  <text x="330" y="222" font-size="12" fill="var(--muted)">Skia nearest-neighbour, setFilterBitmap(false)</text>
  <text x="175" y="272" font-size="12" fill="var(--muted)" text-anchor="middle">765 px wide</text>
  <text x="47" y="180" font-size="12" fill="var(--muted)" text-anchor="middle" transform="rotate(-90 47 180)">503 px high</text>
  <text x="690" y="299" font-size="12" fill="var(--muted)" text-anchor="middle">barPaint fills the four strips around the fit</text>
  <text x="30" y="318" font-size="12" fill="var(--muted)">Uniform scale (min of the two ratios), centred: the game keeps its 765:503 aspect and is never stretched.</text>
</svg>
```

## 5. Frame pacing

The client has its own frame clock; the port only unlocks it. After `client.initialize()`, bootstrap
calls `Client.setUnlockedFps(true)` and then `Client.setUnlockedFpsTarget(FPS_TARGET)` with
`FPS_TARGET = 60` (`MainActivity.java`, `MainActivity.java`).

Order matters: `setUnlockedFps` first, `setUnlockedFpsTarget` second, because turning unlocked fps off
clears the target. With the default clock the presented rate is decoupled from the rendered rate — the
clock's 20 ms catch-up batch presents once per up to ten cycles — which measured ~0.87 fps on the
target device (a recorded measurement on the target device). The unlocked clock sleeps to a `1e9/FPS_TARGET`
boundary instead and presents every rendered frame. Game ticks are unaffected by the pacing change;
this only controls how often a completed frame is handed to the render thread.

## 6. Overlay compositing

Plugin overlays render into the client's own frame, not into a separate layer. Inside the frame-blit
proxy, `Hooks.draw` composites overlays into the client frame image (`mainBufferProvider.getImage()`)
and then blits that image into `args[1]`, which is the `Graphics` over `appletPixels`
(`MainActivity.java`). The consequence: the presented frame already contains overlays, and
the render thread copies frame-plus-overlays in one `setPixels`.

The `ABOVE_WIDGETS` overlay layer needs an interface to exist. It is drawn from `renderAfterInterface`,
so an overlay only appears when its interface is drawn. `drawInterface` records the interface id and
counts overlays registered for it (`MainActivity.java`); `lastInterfaceDrawn` starts at `-1`
(`MainActivity.java`), so on the login screen (no interface) the `ABOVE_WIDGETS` overlays never
draw. Entity rendering is a separate path: the `draw(Renderable, boolean)` overload lets plugins veto
individual actors and is counted by `countEntityDraw` (`MainActivity.java`).

See [plugin-runtime.md](plugin-runtime.md) for overlay registration and [diagnostics.md](diagnostics.md)
for the overlay-count probes.

## 7. Text

Core cannot rasterise text and cannot reference `android.*` (it is compiled with
`--limit-modules java.base,jdk.unsupported`, see [core-stubs.md](core-stubs.md)). Text therefore crosses
a bridge:

| Step | Where |
|---|---|
| RuneLite asks `Graphics.drawString` | `core/src/main/java/java/awt/Graphics.java` |
| `Font` lazily resolves an opaque platform handle | `Font.getTextRendererHandle()` (`Font.java`) |
| `TextBridge` forwards measure/draw | `core/src/main/java/org/runelite/mobile/bridge/TextBridge.java` |
| App renders glyphs | `AndroidTextRenderer` |

`MainActivity.onCreate` installs the renderer once: `AndroidTextRenderer.setFontDir(new
File(getCacheDir(), "fonts")); TextBridge.renderer = new AndroidTextRenderer();`
(`MainActivity.java`). `AndroidTextRenderer` turns the TTF bytes into an
`android.graphics.Typeface` via `Typeface.Builder` (which only reads files, hence the cache font dir),
caches one `Paint` per (family, style, size), and draws into a single reusable scratch `Bitmap` that
it then composites source-over into the destination ARGB array (`AndroidTextRenderer.java`). The
scratch bitmap is clamped (`MAX_SCRATCH_WIDTH` 1024, `MAX_SCRATCH_HEIGHT` 64), so unusually wide text
is clipped. With no renderer installed every helper degrades to a `str.length()*6` approximation
instead of throwing, so overlay code never dies because text is unavailable.

## 8. Telemetry and debug surface

Two periodic logs and one overlay describe the frame.

**`callbacks.draw` frame log** — throttled to one line per 2 s, prefixed `callbacks.draw: <w>x<h> fps=<n> (<n> in <ms>ms)` and followed by the fields built in
`logFrameDiagnostics` (`MainActivity.java`):

| Field | Meaning |
|---|---|
| `px=` | identity hash and length of the client frame array, plus six sampled ARGB values |
| `world=` | non-zero pixel count / XOR checksum of the left three quarters of the client frame |
| `bridge=` | same two numbers for `AWTBridge.activePixels` (what the render thread presents) |
| `tr=` | XOR checksum of the top-right strip of the client frame (where overlays land) |
| `ovl=` | `ALWAYS_ON_TOP` overlay count plus per-layer names, from `alwaysOnTopOverlayCount()` |
| `iface=` | `lastInterfaceDrawn` / overlays registered for it |
| `entities=` | entity draw calls / denials, with `p=` player and `npc=` NPC subsets |
| `yellow=` / `red=` | count of pure-yellow / pure-red pixels in the top-right strip (FPS overlay presence) |
| `pal=` | `ok|BAD(nonZero/65536)` — the §3.1 palette invariant |
| `host=` | `Np` active plugin count, or `-` when the host is not running |
| `blitMs=` | last blit duration (`lastBlitNanos`) |

Read it as a differential: a frozen `world=` with a changing `bridge=` means the client stopped
redrawing; differing `world=`/`bridge=` values mean the blit is dropping content. A no-op `draw` proxy
leaves a static grey screen — this is the #1 thing to check if rendering "stops". The detail of every
field and how to trigger a run is in [diagnostics.md](diagnostics.md).

**GameState log** — every 5 s from `runRenderLoop`, prefixed
`GameState:` with `state`, `loginIndex`, `scaleMs=` (last `setPixels`+`drawBitmap` time), then probes
for world, position, camera, local player, map regions, FPS, canvas size, and world list. The same
block consumes the on-device conformance request file.

**Boot overlay** — the loader's progress is a themed overlay card (`bootOverlay` + `tvBootStatus`),
built in `buildLauncherUi` between the launcher and the side panel, shown by `launchGame`/`restartClient`
and hidden on the first presented frame (`firstFramePresented` is set in the frame-blit proxy, right
after `frameSeq++`). `updateStatus` feeds it the same string the log prints. Nothing is drawn on the
canvas: `runRenderLoop` paints the letterbox bars and the frame only.

Related: [device-runbook.md](device-runbook.md) for the adb commands to read these logs, and
[telemetry-assessment.md](telemetry-assessment.md) for the wire-visible side of the same pipeline.
