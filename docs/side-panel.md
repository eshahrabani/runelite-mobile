# Side panel (drawer)

**Audience:** developer/operator
**Read this when:** you use, extend or debug the right-edge drawer — the port's only always-present chrome and the host of RuneLite's real Swing sidebar (`SwingSidebarView`) — or the virtual-window layer (`SwingWindowLayer`) above it
**Verified against:** `android/src/main/java/org/runelite/mobile/SidePanel.java`, `android/src/main/java/org/runelite/mobile/SwingSidebarView.java`, `android/src/main/java/org/runelite/mobile/SwingWindowLayer.java`, `android/src/main/java/org/runelite/mobile/UiTheme.java`, `android/src/main/java/org/runelite/mobile/MainActivity.java`, `android/src/main/java/org/runelite/mobile/host/RuneLiteHost.java`, `hostshims/src/net/runelite/client/ui/ClientUI.java`, `core/src/main/java/org/runelite/mobile/bridge/UiBridge.java`

The side panel is an Android `View` tree, not an Activity, added to `MainActivity.rootLayout`; it owns
no client state. It is two things:

- the **chrome** — a 44 dp right-edge column (the drawer chevron and the keyboard toggle) plus a
  collapsible drawer whose header carries the launcher shortcut; and
- the **drawer body** — RuneLite's own Swing sidebar: `ClientToolbarPanel` + a `JTabbedPane` with
  `RIGHT` tab placement + the real plugin panels, built in the asset dex by the `ClientUI` shim,
  painted by `core/` into an ARGB bitmap and blitted by `SwingSidebarView`.

The native Plugins/Config/Host tabs, the plugin search, the generated config forms and
`PluginPanelRegistry` are **gone**: plugin enablement, config editing and every plugin panel
(Notes, Loot tracker, hiscore, …) are RuneLite's own Swing UI now. The core half is
[swing-runtime.md](swing-runtime.md); the game's own gesture handling is [input.md](input.md).

## 1. Geometry, visibility and persistence

`SidePanel(activity, rootLayout)` is constructed once by `MainActivity.buildLauncherUi` and inserts a
`MATCH_PARENT` container (`setClipChildren(false)`) into `rootLayout` after the keyboard bar, before
the virtual-window layer and the login overlay ([input.md](input.md) §8 has the full add order and
what draws over what — the login overlay is last, so it covers the drawer).

- **Column** (`column`, `Gravity.END`, full height, `44 * density` = `handleWidthPx` wide): always
  present. Its upper child fills the remaining height and shows the chevron (`‹` closed / `›` open);
  its foot is the `⌨` keyboard toggle (44 dp × 48 dp, 8 dp bottom margin) →
  `Listener.onKeyboardToggleRequested()` → `MainActivity.toggleKeyboardBar`.
- **Drawer** (`drawer`, `Gravity.END`, full height, 12 dp padding): width
  `min(0.42 * widthPixels, 380 * density)` (`drawerWidthPx`) and `rightMargin = handleWidthPx`, so it
  sits immediately left of the column. Its header is the brand `RuneLite` plus the `⌂` launcher
  shortcut (34 dp) → `Listener.onShowLauncherRequested()` → `MainActivity.showLauncher`; it replaces
  the floating gear button the host used to draw over the game. Its body is
  `content` (weight 1): the Swing sidebar added later, with a `statusText` placeholder behind it.
- The chevron's `OnTouchListener` toggles on `ACTION_UP` and always returns `true` — a `TextView`
  click listener would consume the down event itself and the column's swallowing listener (below)
  would never see it.
- **The column always takes screen space; the drawer floats or takes space** (the launcher's
  Display card, `Sidebar: overlap` / `Sidebar: resize`). `columnWidthPx()` is
  `handleWidthPx` (the always-present column), `occupiedWidthPx()` is
  `handleWidthPx + (open ? drawerWidthPx : 0)`; `MainActivity.applyGameInsets` sets the right
  margin of `surfaceView` and `kbBar` from them (driven by `Listener.onOpenChanged`). The
  game surface is inset by the column in **both** modes, so no game pixel renders under the
  chrome. In the default **overlap** mode the drawer adds nothing to the game's inset — it
  floats over the game (the point of the mode: the resizable layout's minimap and tab panel
  live under the drawer) — so a toggle changes neither the surface nor the frame. In
  **resize** mode the surface is inset by the drawer too, so a toggle fires `surfaceChanged`
  with a narrower surface and the game re-renders at the smaller size and reletterboxes into
  the reduced area — nothing is overlapped, and the surface's own touch mapping follows
  ([rendering.md](rendering.md)). `kbBar` always takes `occupiedWidthPx()`, because the
  keyboard bar is chrome: its pills must stay clear of the drawer in both modes. The inset
  does not depend on `setAvailable` — hiding the chrome on the launcher must not resize a
  running client.
- `toggle()` flips `open`, runs `applyState()` and persists `sidePanelOpen`. `applyState` sets the
  drawer `VISIBLE`/`GONE`, calls `UiBridge.setSidebarVisible(open)` (§2), calls
  `swingSidebarView.setSuspended(!open)` (§2), updates the chevron glyph, and notifies
  `Listener.onOpenChanged`. `open()`, `close()` and `isOpen()` are the programmatic forms.
- `setAvailable(boolean)` shows/hides the whole container and closes the drawer while hidden. It is
  `false` on the launcher screen and from `showLauncher` (the chrome must not float over the
  launcher) and `true` from `launchGame`. Hiding is not closing: `sidePanelOpen` can restore the
  drawer open, so `setAvailable(true)` re-fires `onOpenChanged` and re-applies the inset.
- **Touch ownership.** Only `column` and `drawer` install the swallowing `OnTouchListener` (every
  event returns `true` except `ACTION_OUTSIDE`). The container itself never eats events, so a tap
  outside the drawer reaches the game surface; children receive their events before the parent
  listener, which is why the chevron and the keyboard toggle keep working. Keep this property when
  adding child views — a container-wide listener would eat the whole screen.
- **Persistence.** Only `RuneLiteMobilePrefs` / `sidePanelOpen` (the same `SharedPreferences` file the
  launcher uses). Plugin enablement and config values are not here — they live in RuneLite's own
  profile store ([plugin-runtime.md](plugin-runtime.md) §4).

## 2. The drawer body: RuneLite's Swing sidebar

`MainActivity` builds `SwingSidebarView` before the client boots, hands it the
`SwingWindowLayer` and installs it as the core's `UiBridge.Host`
(`UiBridge.setHost(swingSidebarView)`), then calls `sidePanel.setSwingSidebarView(view)`; the view is
inserted into `content` at index 0, behind `statusText`.

- **The tree** is built by the asset-dex `ClientUI` shim: `RuneLiteHost.initSidebar` invokes
  `ClientUI.init()` on the Swing dispatch thread, which builds a `JPanel` named `RuneLiteSidebar`
  (`BorderLayout`) holding a `JTabbedPane` with `JTabbedPane.RIGHT` placement whose
  `JTabbedPane.trailingComponent` client property is the `ClientToolbarPanel` strip, and ends with
  `UiBridge.setSidebarRoot(root)` (`setSidebarActivation(this)`). A `NavigationButton` with a panel
  becomes a tab, ordered by `NavigationButton.COMPARATOR`; a panel-less one becomes a strip button.
  The first tab is left unselected (the desktop's "strip only" state). The strip also carries
  RuneLite's own sidebar toggle, which arrives as `Host.closeSidebarRequested()` and closes the
  drawer.
- **Activation follows the desktop contract**: opening the drawer
  (`UiBridge.setSidebarVisible(true)`, posted from `SidePanel.applyState`) and switching tabs run
  `SwingUtil.activate`/`deactivate` on the selected `PluginPanel`. The app's touch code never
  activates anything itself. A hub install that adds tabs is handled by the strip, which scrolls when
  the tabs no longer fit (§3).
- **The app owns the pixels, the core owns the tree.** `SwingSidebarView` reports its geometry on
  every size change (`onSizeChanged` → `UiBridge.setSidebarBounds`), asks the core for a repaint on
  the Swing thread via `Host.sidebarDirty()` and blits the resulting device-sized ARGB bitmap 1:1
  with blending disabled
  (`Paint.setFilterBitmap(false)`; the stage background is opaque). Paints are **asynchronous** and
  coalesced to at most one per `FRAME_INTERVAL_MS` (16 ms): a synchronous paint would block the
  Android main thread for hundreds of ms on a heavy panel (the Configuration list is ~120 rows) and
  the platform would report the app as not responding. While the drawer is closed nothing is
  rendered (`setSuspended(true)`); the first open repaints in full.
- **Placeholder.** While the core has no sidebar (`UiBridge.hasSidebar()` is false — the shim has not
  published a root yet, or `ClientUI.init` threw, see §6) the view draws nothing and `statusText`
  shows `"RuneLite runtime not running: " + RuneLiteHost.status()`.
- **Text input** goes through the same keyboard bar as the game's chat line: a pointer-caused focus
  change to a Swing text component arrives as `Host.textInputRequested(true)` →
  `MainActivity.showKeyboardBar`, and the bar's characters are routed back into Swing through
  `SwingSidebarView.dispatchKey` while `UiBridge.focusOwnerIsTextInput()` holds
  ([input.md](input.md) §6). The `⌨` tile in the column toggles the bar by hand.

## 3. Touch mapping in the drawer

One finger is the mouse. `ACTION_DOWN` dispatches `MOUSE_MOVED` (NOBUTTON) — a mouse always moves
before it presses, and a Swing widget (a menu, the sidebar) picks its hover/selection from the move
— but **not** `MOUSE_PRESSED`: the press is deferred until the gesture is known, because the client
acts on mouse *down* (the same reason the game surface defers it, see `MainActivity.beginTapPress`).
`ACTION_MOVE` and `ACTION_UP` then decide which gesture it is. View coordinates are device px
divided by the UI scale (§5) and handed to `UiBridge.dispatchMouse`.

- **Scrollable target** — `UiBridge.isScrollableAt` is true over a `JViewport` whose view is larger
  than the viewport, or a `JTabbedPane` whose strip is scrollable: a one-finger drag becomes wheel
  notches (`scrollByDrag`: at least `WHEEL_STEP_DP` = 24 dp of travel per event; dragging down is a
  negative AWT rotation = scroll up). RuneLite's panels have no drag semantics of their own, so a
  touch device gets scrolling instead. Once the slop (`DRAG_SLOP_DP` = 10 dp) is crossed the gesture
  is marked as a drag and the lift sends nothing at all (no press was ever delivered) — a flick
  must not toggle whatever the finger happened to be over.
- **Anywhere else**: travel beyond 10 dp turns the gesture into a drag: `MOUSE_PRESSED` (BUTTON1) at
  the touch-down point, then a `MOUSE_DRAGGED` (BUTTON1) stream — what a desktop mouse sends. The
  lift then sends `MOUSE_RELEASED` where the finger is, **without** a click.
- **Tap** (the lift arrives without the slop crossed): `MOUSE_PRESSED` + `MOUSE_RELEASED` +
  `MOUSE_CLICKED` (BUTTON1), all at the touch-down point, so the gesture acts on what the finger
  went down on and not on whatever it drifted over. Two taps within `DOUBLE_TAP_MS` (350 ms) and
  `DOUBLE_TAP_SLOP_DP` (24 dp) are a **double click**: all three events carry
  `getClickCount() == 2`, which is what RuneLite's world switcher rows hop on (a triple tap starts a
  new pair).
- **Long press**: a finger still for `LONG_PRESS_MS` (400 ms) fires `MOUSE_PRESSED` +
  `MOUSE_RELEASED` with `BUTTON3` — Swing's popup trigger, e.g. a tab's right-click menu — and toasts
  the tooltip under the point (`UiBridge.tooltipAt`), because a desktop user would hover there. The
  lift then closes the left press (there is none, since a drag would have cancelled the timer) and
  does not become a click.
- **Two fingers** (the second `ACTION_DOWN` cancels the long-press timer and latches the mode): the
  vertical centroid travel accumulates until `WHEEL_STEP_DP`, then `UiBridge.dispatchWheel`, with
  `WHEEL_PIXELS_PER_UNIT` (8 device px) of travel per rotation unit. A **two-finger tap** — both
  fingers down and up within `TWO_FINGER_TAP_MS` (400 ms) without a drag, a long press or a wheel
  notch — is `BUTTON3` press + release: the second route to the context menu.
- The lift of a two-finger gesture keeps two-finger mode until the last finger is up, so a surviving
  finger does not silently become the mouse.

`JViewport` follows the desktop contract: the view's location is the negative `viewPosition` (JDK
`JViewport.setViewPosition`), not a paint-time translation, so `Container.locate`,
`Component.getLocationOnScreen` and every stage-coordinate path follow the scroll. A tap on a
scrolled panel therefore resolves to the row under the finger.

## 4. Virtual windows, popups and input blocking

`SwingWindowLayer` is a full-screen `MATCH_PARENT` view added to `rootLayout` **after** the side
panel and **before** `loginOverlay`: it draws above the game and the drawer, below the login overlay.
It is `GONE` unless `UiBridge.hasVisibleWindow()` is true (a shown `java.awt.Window`/`JDialog` —
plugin dialogs and modal option panes; RuneLite's config colour picker is a `JDialog`).

- A virtual window is not a native window: the core paints it, title bar included
  (`UiBridge.WINDOW_TITLE_HEIGHT` = 22 logical px, close glyph at the right end), into the layer's
  screen-sized ARGB bitmap (alpha kept on — the surround must stay transparent) and the layer centres
  it. Presentation scale is `min(sidebarScale, floor(0.9 * screenW / 410), floor(0.9 * screenH / 380))`
  clamped to 1..3 (`REFERENCE_WIDTH`/`REFERENCE_HEIGHT` = 410/380, `SCREEN_FRACTION` = 0.9), so a
  dialog never renders larger than the drawer and always leaves a tenth of the screen free on each
  side. The chosen geometry is published to the core with `UiBridge.setWindowBounds`.
- **A window shows on its first `setVisible(true)`.** `Component` defaults to visible and a fresh
  `Window` does not start hidden the way the JDK's does, so `Window.setVisible(true)` has to treat the
  "already flagged visible, not yet in `OPEN_WINDOWS`" case as the show — it fires the open/activate
  events, lays the window out and puts it in `OPEN_WINDOWS`, which is what `topMostWindow()` (and so
  `hasVisibleWindow()`) reads. Registering without that list entry left the window registered but
  invisible, which is how the config colour picker "opened" and never appeared.
- **It is the modality gate.** While a window is up, `UiBridge.dispatchMouse`/`dispatchWheel` route
  every event to the window layer, and the layer's `onTouchEvent` consumes everything (it returns
  `true` for every action), so neither the game nor the drawer sees the gesture: a modal dialog cannot
  be tapped through. Touches are mapped back through the window's top-left offset, title bar included,
  and a click in the close square (`x >= width - WINDOW_TITLE_HEIGHT`) closes the window.
- Repaints arrive as `Host.windowDirty()` from the Swing thread and coalesce exactly like the
  sidebar's (≤ 1 per 16 ms, one paint in flight).
- **Popups** (menus, combo-box drop-downs, tooltips) are not windows either: `PopupLayer` holds them
  in stage coordinates above whichever base layer is up and `UiBridge`'s hit-testing asks the layer
  first, so a popup always wins over the content underneath ([swing-runtime.md](swing-runtime.md) §2).

## 5. UI scale

`SwingSidebarView.onSizeChanged` derives the integer UI scale from the drawer's device width:
`uiScale = clamp(round(contentWidthPx / 272), 1, 3)` (`SIDEBAR_LOGICAL_WIDTH` = 272 =
RuneLite's `ClientUI.SIDEBAR_LOGICAL_WIDTH`, hard-coded because the asset dex is invisible to this
dex; `MIN_SCALE`/`MAX_SCALE` = 1/3). It then calls
`UiBridge.setSidebarBounds(round(w / scale), round(h / scale), scale)`: the core lays the root out at
that logical size and paints through a `Graphics` scaled by it. Rounding (rather than flooring) is
deliberate — the drawer should use the largest scale it can afford, because the 12 px logical UI font
at 2× reads too small on a phone — so **the drawer-width rule in `SidePanel` is the one knob that
retunes the whole sidebar.**

Glyphs are rasterised at device resolution: fonts and every `FontMetrics` answer stay *logical*,
`Graphics.drawString` passes the effective scale to `TextBridge.drawString`, and
`AndroidTextRenderer.scaledFont` rasterises at `logicalSize × scale`. Every widget must therefore
measure logically — that was a real bug (icons scaled through `drawImage` while glyphs stayed
logical), and the `SwingSelfTest` `scale` case asserts the invariant. The core-side details are in
[swing-runtime.md](swing-runtime.md) §5.

## 6. Error handling

- **A broken panel costs its tab only.** Constructing a panel's component can throw while the tab is
  inserted (`ClientUI.panelComponentOf` logs `panel construction failed: <tooltip>`), and activating
  or deactivating one can throw while the tab is selected (`installErrorPanel` logs
  `panel activation failed: <tooltip>`). Both paths swap that tab's component for an error panel
  (`net.runelite.client.ui.components.PluginErrorPanel`, RuneLite's own) and log the stack trace; the
  rest of the sidebar — other tabs, the strip, the toggle — keeps working.
- **A sidebar bootstrap failure is not fatal.** If `ClientUI.init` throws, `RuneLiteHost.initSidebar`
  records it in `sidebarFailure`, logs `sidebar init failed`, and the game keeps running. The drawer
  then shows the `statusText` placeholder (§2) and the launcher's Host section reports the failure
  (§7).
- **A paint failure is contained.** `SwingSidebarView` catches a throwing paint and logs
  `sidebar paint failed` (a failing `setSidebarBounds` logs `sidebar bounds failed`);
  `SwingWindowLayer` logs `window layer paint failed`. The view keeps its last frame instead of
  taking the main thread down.
- Both self-tests run once per host start (`GraphicsSelfTest.run()`, `SwingSelfTest.run()`, from
  `RuneLiteHost.startPlugins`): a **failure** is appended to `RuneLiteHost.status` as
  `; gfx self-test FAIL: <detail>` / `; swing self-test FAIL: <detail>`, so it shows up in the Host
  section's `status` row and in the drawer's placeholder line; a pass is logcat only
  (`GFX SELFTEST PASS` / `SWING SELFTEST PASS`). The self-tests themselves are documented in
  [diagnostics.md](diagnostics.md) §4 and [swing-runtime.md](swing-runtime.md) §7.

## 7. Host status surface

The old Host tab is gone; its rows live in the launcher's **Host** card
(`MainActivity.refreshHostCard`, called from `updateLauncherUi`, i.e. whenever the launcher becomes
visible):

| Row | Source |
|---|---|
| `client version` | `RuneLiteHost.clientVersion()` |
| `runtime` | `"running"` / `"not running"` (`RuneLiteHost.isRunning()`) |
| `plugin index` | `RuneLiteHost.indexSize() + " classes"` |
| `active plugins` | `RuneLiteHost.activePluginCount()` |
| `status` | `RuneLiteHost.status()` (self-test failures are appended here, §6) |
| `last error` | `RuneLiteHost.failure()`, or `"none"` |
| `sidebar` | `"ok"`, or the sidebar bootstrap failure as a red line |
| `plugin load failures` | the count, then one red indented line per entry |
| `client AOT` | `ClientUpdater.clientDexAotStatus/Text(activity)`; red when stale or missing |
| `on-device dexer` | literal `"unavailable"` |
| `conformance` | `PluginConformance.isRunning() ? "running…" : lastSummary()` |

Two action pills follow (plain `TextView`s built by `MainActivity.pillAction` — gold text on a
`UiTheme.SURFACE_ALT` `UiTheme.rounded` background with a ripple, not platform `Button`s):

- **`Run plugin conformance`** — enabled only when
  `!PluginConformance.isRunning() && RuneLiteHost.isRunning()`. It calls `PluginConformance.run(this)`
  and toasts `"conformance running — report: <file name>"`; the report also arrives over adb. Metrics
  and report format: [diagnostics.md](diagnostics.md) §5.
- **`Refresh`** — `refreshHostCard()`.

The client version line above the card turns red, with
`NOT AOT-COMPILED - expect ~5 fps (Host section on this screen)`, when the client dex is not
AOT-compiled. The `on-device dexer` row is a hardcoded literal; it does **not** query
`OnDeviceDexer.isAvailable`, even though third-party jar imports do dex on the device
([third-party-plugins.md](third-party-plugins.md)). AOT semantics and the fix command:
[client-updates.md](client-updates.md), [device-runbook.md](device-runbook.md).
