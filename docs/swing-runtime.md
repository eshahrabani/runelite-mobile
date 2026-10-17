# The port's Swing runtime

**Audience:** developer
**Read this when:** you touch `core/src/main/java/{java,javax}/**`, `hostshims/**`, the drawer
(`SidePanel`), `SwingSidebarView`/`SwingWindowLayer`, or you debug a plugin panel that does not
paint or does not react.
**Verified against:** `core/src/main/java/org/runelite/mobile/bridge/{SwingEdt,AWTBridge,UiBridge,PopupLayer,TextBridge}.java`,
`core/src/main/java/java/awt/Component.java`, `core/src/main/java/javax/swing/{JComponent,UiScale}.java`,
`hostshims/src/net/runelite/client/ui/ClientUI.java`,
`android/src/main/java/org/runelite/mobile/{SwingSidebarView,SwingWindowLayer}.java`,
`android/src/main/java/org/runelite/mobile/host/SwingSelfTest.java`

The port runs RuneLite's **unmodified Swing shell**: `ClientToolbarPanel`, `NavigationButton`,
`PluginPanel`, `MultiplexingPluginPanel`, the config plugin's panels, every plugin panel and the
`ui/components/**` helpers all ship in the asset dex and execute against the hand-written
`java.awt`/`javax.swing` implementation in `core/`. Nothing about that code was ported; what was
implemented is the JDK surface it needs, plus a bridge that lets the Android app present it.

## 1. Threading: one Swing dispatch thread

`SwingEdt` (`core/.../bridge/SwingEdt.java`) owns one daemon thread named `Swing-EDT-rlmobile`
with a `LinkedBlockingDeque<Runnable>`:

- `SwingEdt.ensureStarted()`, `post`, `invokeAndWait`, `isDispatchThread`, `dispatchQueued`,
  `pumpUntil(BooleanSupplier)`; `AWTBridge` is the façade the rest of `core/` uses
  (`AWTBridge.post`, `AWTBridge.invokeAndWait`, `AWTBridge.isUiThread`, `AWTBridge.swingThread`).
- `MainActivity.onCreate` registers it as the runtime's UI thread:
  `AWTBridge.registerUiThread(SwingEdt.ensureStarted(), SwingEdt::post)`. Everything RuneLite
  routes "to the EDT" — `PluginManager.startPlugins`' assertion,
  `SwingUtilities.invokeLater/invokeAndWait`, the plugin lifecycle — lands there.
- The **Android main thread is not the EDT**. It presents bitmaps and forwards touches/keys; a
  blocking plugin or a modal Swing dialog cannot freeze the Android UI.
- `pumpUntil` is a *nested* dispatch loop: while `JOptionPane.showConfirmDialog` blocks the
  Swing thread, queued tasks (the tap that clicks OK, an incoming sidebar repaint) are still
  dispatched. `EventQueue.createSecondaryLoop().enter()` drains the same queue
  (via `SwingEdt.dispatchQueued()`).
- An exception in a queued task is printed and swallowed; the thread survives it, like the
  desktop EDT does.

## 2. The stage: `UiBridge`

The app owns the pixels, the core owns the component tree. `UiBridge` is the contract
(`core/.../bridge/UiBridge.java`), implemented on the app side by `SwingSidebarView` (the
drawer body, and the port's `UiBridge.Host`), which also drives `SwingWindowLayer` (the
full-screen window layer):

| Direction | API | Notes |
|---|---|---|
| app → core | `setScreenSize(w,h,density)` | display size; the window layer centres on it |
| app → core | `setSidebarBounds(logicalW, logicalH, scale)` | stage geometry; `scale` is the integer UI scale (see §5) |
| app → core | `setSidebarVisible(boolean)` | the drawer opened/closed: activates/deactivates the selected panel |
| app → core | `paintSidebar(int[] argb, int deviceW, int deviceH)` | dispatch-thread only (marshals); clears the buffer, validates the root, paints the tree and the popup layer |
| app → core | `paintWindow(...)`, `windowLogicalSize()`, `hasVisibleWindow()` | the top-most virtual window, its title bar drawn by the core |
| app → core | `dispatchMouse/Wheel/Key` | constructed and dispatched on the dispatch thread |
| core → app | `Host.sidebarDirty()` / `Host.windowDirty()` | repaint requests (coalesced by the views to ≤ 1 per 16 ms) |
| core → app | `Host.textInputRequested(boolean)` | show/hide the keyboard bar |
| core → app | `Host.toast(String)`, `Host.closeSidebarRequested()` | tooltip/unsupported notice; the strip's own toggle |

`hostshims/.../ClientUI.java` publishes the sidebar tree
(`UiBridge.setSidebarRoot(root)`, `setSidebarActivation(this)`) during `init()`, which
`RuneLiteHost.initSidebar()` calls on the dispatch thread right after the injector is up.

**Popups** (menus, combo drop-downs, tooltips, the colour picker) are not native windows on this
port: `PopupLayer` (`core/.../bridge/PopupLayer.java`) holds a stack of
`(component, stageX, stageY)` entries, `paintSidebar`/`paintWindow` paint them after the base
layer and `UiBridge`'s hit-testing asks the layer first, so a popup always wins over the content
underneath.

## 3. Paint and layout

- `Component` owns position/size, the parent link, focus/enabled state, listener lists and event
  dispatch; `Container` owns the child list in Z-order, `addImpl` (which feeds the layout
  manager its constraint), `invalidate/validate/validateTree/doLayout`, the layout-aware
  preferred/minimum/maximum sizes and `paint` → `paintComponents` (each child translated and
  clipped to its own bounds).
- `Component.paint` paints the component alone (`paintSelf`) and `Container.paint` adds the
  children; `JComponent.paintComponent` must call `paintSelf`, never `super.paint`, or every
  level paints its subtree twice (measured on the device: 195k paints per frame, 5 s per
  sidebar frame instead of 15 ms).
- `JComponent` is the Swing layer: a `ComponentUI` delegate (`paint` → `ui.update(g, this)`),
  `paintComponent`/`paintBorder`/`paintChildren`, borders (`Border.getBorderInsets` feeding
  `getInsets`), client properties, tooltips, the `InputMap`/`ActionMap` key bindings and
  property change notifications.
- `repaint()` → `RepaintManager.markDirty(component, rect)` → walks to the paint root: the
  sidebar root marks the drawer dirty, a visible `Window` marks the window layer dirty, anything
  else (every component of the **game client**) is a **no-op**. That is the invariant that keeps
  the game's own `repaint()` calls harmless.
- `revalidate()` invalidates and marks the layer dirty; the stage paint validates the root
  before painting, so a panel that adds a row re-lays out and repaints in one pass.
- Layout managers implemented with the JDK algorithms: `BorderLayout`, `GridLayout`, `FlowLayout`,
  `CardLayout`, `GridBagLayout`, `BoxLayout`/`Box`, `GroupLayout`, `JViewport.ViewportLayout`.
  `GroupLayout` is the one a plugin panel (Kourend library) needs; its solver is a simplified but
  correct two-pass implementation.

## 4. Events, focus and input

- Mouse/key events are dispatched to the deepest visible component (`UiBridge` hit-tests the
  tree, popups first) and **bubble to the parent while unconsumed, for `JComponent` targets
  only** — the game client's heavyweight components stay the single recipient of their events.
- ENTERED/EXITED are synthesized as the pointer moves (rollover, button hover).
- `KeyboardFocusManager` holds the focus owner; a tap on a focusable component focuses it, text
  components fire `Host.textInputRequested`, and `UiBridge.focusOwnerIsTextInput()` is what makes
  `MainActivity` route the keyboard bar's keys into Swing (`UiBridge.dispatchKey`) instead of the
  game.
- Key routing order: registered `KeyEventDispatcher`s → the focus owner (its `InputMap`
  bindings, then its key listeners) → the window (ancestor/window-condition bindings). That is
  what makes Ctrl+Z in the Notes panel and Enter in a dialog work.
- A long press in the drawer is a BUTTON3 press (`isPopupTrigger`), which is how Swing menus
  open; a two-finger tap is the same thing.

## 5. UI scale and text

The sidebar is rendered at an **integer** UI scale so 1 px borders stay crisp:
`uiScale = clamp(round(drawerWidthPx / 272), 1, 3)` (rounding, not flooring: the drawer should
use the largest scale it can afford — the 12 px logical UI font at 2× reads too small on a phone), `logicalW/H = round(contentPx / uiScale)`,
and the stage root is painted through a `Graphics` scaled by `uiScale`. Rasterisation happens at
device resolution: `Graphics.drawString` passes the effective scale to
`TextBridge.drawString(..., float scale)` and `AndroidTextRenderer` rasterises the glyphs at
`logicalSize × scale`. **Font sizes and every `FontMetrics` answer stay logical**, so all layout
arithmetic (preferred sizes, text wrapping) is scale-independent and the self-test can assert
"preferred size stays logical while the glyphs double".

`javax.swing.UiScale` is the single place that holds the factor; `UiBridge.setSidebarBounds`
sets it.

## 6. What is deliberately not implemented

- Antialiasing of shapes (text is antialiased by the platform rasteriser); strokes are drawn as
  parallel traces and dashes render solid.
- Non-linear `Paint`s: `GradientPaint` is rasterised, anything else falls back to the last solid
  colour.
- `JFileChooser`: configuration is stored, `show*Dialog` calls `UiBridge.notifyUnsupported`
  (logged once per feature + a toast) and returns `CANCEL_OPTION`.
- The system clipboard is in-memory (paste between apps is out of scope); copy/paste inside the
  app works.
- `JTree`/`JTable` support construction, model wiring, row painting, expand/collapse and
  selection — not inline editing or sorting.
- Real LAFs: the port ships one dark theme seeded into `UIManager`'s defaults; `setLookAndFeel`
  is accepted and ignored.

## 7. Self-tests

`SwingSelfTest` (app dex) runs 22 named cases on the Swing thread from
`RuneLiteHost.startPlugins`, right after `GraphicsSelfTest`. `GraphicsSelfTest` logs
`GFX SELFTEST PASS` / `GFX SELFTEST FAIL <case>`; `SwingSelfTest` logs one line per case plus
`SWING SELFTEST PASS (22 cases)` / `SWING SELFTEST FAIL <case>: <detail>`. A failure is appended to
`status` as `gfx self-test FAIL: <detail>` or `swing self-test FAIL: <detail>` (shown in the
launcher's Host section). The cases cover the paint pipeline, each layout manager the shell uses,
the widget set, popups, the modal dialog's nested loop, the UI scale and the theme. The `optionpane`
case puts a dialog on screen, so the boot run skips it (`onScreenCaseNames()`; the conformance pass
skips them too) — run it explicitly with `SwingSelfTest.runCase("optionpane")`. It is the only
automated check of the Swing surface on a release build: run it after any change to
`core/src/main/java/{java,javax}`.
