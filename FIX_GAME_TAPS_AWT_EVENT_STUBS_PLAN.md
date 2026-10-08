# Fix game taps (AWT event stubs) and per-plugin config discovery

## Context

Two defects reported after the side panel landed:

1. Taps no longer act on the game: the client's own world list / "Play Now" cannot be clicked (independent of whether the sidebar drawer is open).
2. The Config tab reports `config unavailable: java.lang.NoSuchMethodException: net.runelite.client.plugins.account.AccountPlugin.getConfig()` for plugins.

Root causes, both established by reading the repo and the running device (not hypotheses):

- **Input.** logcat shows the taps *do* reach the client — `D/RuneLiteMobile: Dispatch mouse id=501 ... to ti` (501/502/500 = PRESSED/RELEASED/CLICKED, dispatched by `MainActivity.emitPoint` to the input target `ti`, the game canvas) — and each dispatch then dies with `W/RuneLiteMobile: Caused by: java.lang.NoSuchMethodError: No virtual method getPoint()Ljava/awt/Point; in class Ljava/awt/event/MouseEvent;`. The side panel is therefore **not** swallowing touches (the strip/container `SidePanel.java:200-218` consumes only inside the handle/drawer bounds and `emitPoint`'s dispatch log proves delivery). The client's mouse path calls members our `java.awt.event` stubs never declared, and the first throw aborts the dispatch (`emitPoint` catches `Throwable` and only logs).
- **Config.** `SidePanel.showConfigFor` (`SidePanel.java:465`) calls `plugin.getClass().getMethod("getConfig")`, a method no RuneLite plugin has. RuneLite exposes a plugin's config as an `@ConfigGroup`-annotated interface (`javap net.runelite.client.plugins.agility.AgilityPlugin` → `private net.runelite.client.plugins.agility.AgilityConfig config;` plus `AgilityConfig getConfig(ConfigManager)`; `AgilityConfig` is `@ConfigGroup("agility")`, `RetentionPolicy.RUNTIME`, `extends net.runelite.client.config.Config`). Some plugins have no config at all (`AccountPlugin` only injects `ConfigManager`) and must show the muted message, not an error.

The member list below is the exact set the client references in `java.awt.event.*`, extracted from the constant pools of `android/build/temp-jars/client-cleaned.jar`, `android/build/temp-jars/injected-client-cleaned.jar`, `android/build/rl-jars/*.jar` and the hub dex. Only the *missing* ones are listed.

## Approach

### 1. Make the AWT event stubs satisfy the client's actual references

`core/src/main/java/java/awt/event/InputEvent.java` — rebase on the real AWT hierarchy so `getSource()`/`getID()`/`consume()`/`isConsumed()`/`paramString()` come from `AWTEvent`/`EventObject` instead of being duplicated or absent:

- `public class InputEvent extends java.awt.AWTEvent`; delete its own `consumed` field, `isConsumed()`, `consume()` (inherited from `AWTEvent.java:56-62`).
- Constructor: `public InputEvent(Object source, int id, long when, int modifiers) { super(source, id); this.when = when; this.modifiers = modifiers; }`
- `public int getModifiersEx() { return modifiers; }`
- `public static String getModifiersExText(int modifiers)` → comma-joined names for `SHIFT_DOWN_MASK`/`CTRL_DOWN_MASK`/`META_DOWN_MASK`/`ALT_DOWN_MASK` plus the three `BUTTON*_DOWN_MASK`s, `""` when nothing is down.
- Keep `getWhen()`, `getModifiers()`, `isControlDown()`, `isAltDown()`, `isShiftDown()`, `isMetaDown()`.

`MouseEvent.java`:

- Constructor becomes `public MouseEvent(java.awt.Component source, int id, long when, int modifiers, int x, int y, int clickCount, boolean popupTrigger, int button)` → `super(source, id, when, modifiers)`. Delete the local `source` field, the `getSource()` override and `getID()` (all inherited now).
- `public java.awt.Component getComponent() { return (java.awt.Component) getSource(); }`
- `public java.awt.Point getPoint() { return new java.awt.Point(x, y); }` (fixes the on-device crash; `java.awt.Point` already has public `x`/`y` and `Point(int,int)`).
- `@Override public int getModifiersEx()` → `super.getModifiersEx()` or, for `MOUSE_PRESSED`/`MOUSE_DRAGGED`, that value OR-ed with the down-mask of `getButton()` (`BUTTON1→BUTTON1_DOWN_MASK`, `BUTTON2→BUTTON2_DOWN_MASK`, `BUTTON3→BUTTON3_DOWN_MASK`, otherwise nothing).

`MouseWheelEvent.java`:

- Constructor becomes `public MouseWheelEvent(java.awt.Component source, int id, long when, int modifiers, int x, int y, int clickCount, boolean popupTrigger, int scrollType, int scrollAmount, int wheelRotation)` → `super(source, id, when, modifiers, x, y, clickCount, popupTrigger, NOBUTTON)`. Keep `getScrollType/getScrollAmount/getWheelRotation`; drop any `getID()` override.

`KeyEvent.java`:

- Constructor → `super(source, id, when, modifiers)`; make `keyCode`/`keyChar` non-final; drop `getID()`.
- Add `public int getExtendedKeyCode() { return keyCode; }`, `public void setKeyCode(int keyCode)`, `public void setKeyChar(char keyChar)`.
- Add `public static String getKeyText(int keyCode)`: a switch over the VK_* constants already declared in this file (`VK_ENTER`→"Enter", `VK_ESCAPE`→"Escape", `VK_SPACE`→"Space", `VK_TAB`→"Tab", `VK_BACK_SPACE`→"Backspace", `VK_CLEAR`→"Clear", `VK_DELETE`→"Delete", `VK_SHIFT`→"Shift", `VK_CONTROL`→"Ctrl"), else `"Key " + keyCode`.
- `@Override public String paramString() { return super.paramString() + ",keyCode=" + keyCode + ",keyChar=" + keyChar; }`

`WindowEvent.java`: constructor becomes `public WindowEvent(java.awt.Window source, int id)` (real AWT signature the client calls; no call site in this repo).

`MainActivity.java` needs **no** change for these: both event construction sites already pass a `java.awt.Component` (`emitPoint`, `MainActivity.java:2297`; `dispatchMouseWheel`, `:2967`) and `dispatchKeyCode` (`:3039`) keeps passing `Object`.

### 2. Resolve a plugin's config interface the way RuneLite does

Add to `android/src/main/java/org/runelite/mobile/host/RuneLiteHost.java` (next to `pluginName`, reusing `clientLoader()`):

```java
/** The config interface a plugin uses, or null when it has none. */
public static Class<?> pluginConfigClass(Object plugin) {
    if (plugin == null) return null;
    Class<?> group;
    try {
        group = clientLoader().loadClass("net.runelite.client.config.ConfigGroup");
    } catch (Throwable t) {
        return null;
    }
    for (Class<?> c = plugin.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (f.getType().isAnnotationPresent(group)) return f.getType();
        }
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (m.getParameterCount() <= 1 && m.getReturnType().isAnnotationPresent(group)) {
                return m.getReturnType();
            }
        }
    }
    return null;
}
```

In `SidePanel.showConfigFor`, replace the `plugin.getClass().getMethod("getConfig").invoke(plugin)` lookup (`SidePanel.java:465-469`) with:

```java
Class<?> iface = RuneLiteHost.pluginConfigClass(plugin);
if (iface == null) {
    configList.addView(label("this plugin has no configuration", 12f, 0xFFB0B0B0));
    return;
}
```

Leave the rest of the method (proxy via `ConfigManager.getConfig(Class)`, `getConfigDescriptor(Config)`, item form, `configItemMethods`) unchanged.

## Critical files & anchors

- `core/src/main/java/java/awt/event/InputEvent.java` — class declaration + constructors; the missing `getModifiersEx()`/`getModifiersExText()` live here.
- `core/src/main/java/java/awt/event/MouseEvent.java:29-47` — constructor, `getSource`/`getID` removal, and the three new members (`getPoint`, `getComponent`, `getModifiersEx`).
- `core/src/main/java/java/awt/event/KeyEvent.java:25-35` — constructor + `keyCode`/`keyChar` mutability + the new members.
- `core/src/main/java/java/awt/event/MouseWheelEvent.java:14-23` and `WindowEvent.java:17` — constructor signatures.
- `android/src/main/java/org/runelite/mobile/SidePanel.java:465-469` — the failing config lookup; `android/src/main/java/org/runelite/mobile/host/RuneLiteHost.java:480` (`pluginName`) — insertion point for `pluginConfigClass`.

## Verification

Prerequisite for all device steps: a plugged-in device, `local.properties` SDK as usual, and network (the build downloads the RuneLite artifacts).

1. `./gradlew :android:verifyHostLinks` (repo root) — the repo's ASM checker resolves every client-jar reference against the built stubs. **Acceptance: it prints `# missing: 0`.** For each line it prints instead, add that member to the named stub with the JDK's signature (the line gives owner, name and descriptor) and implement it as a plain store/return.
2. `./gradlew :android:assembleRelease && adb install -r android/build/outputs/apk/release/android-release.apk`
3. Input proof: launch the app, tap the launcher's green **Play** (this hides the launcher overlay — `launcherScroll` is set `GONE` in `onPlayClicked`), then `adb logcat -c`. Screenshot, find "Play Now"/a world row in the 765x503 game frame, tap it with `adb shell input tap X Y` where `X = 2244 * x_frame / 765`, `Y = 1008 * y_frame / 503` (the surface is stretched to the screen, `dstRect` = full surface, so the mapping is linear). Acceptance: `adb logcat -d | grep -c NoSuchMethodError` is `0`, `Dispatch mouse` lines still appear, and a before/after `adb exec-out screencap -p` pair shows the client leaving the world-list screen.
4. Config proof: open the panel (`btnPanel` in the top row, right of the gear button) → Plugins → tap a plugin that has a config (e.g. "Agility") → the Config tab lists its items; tap one with none (e.g. "Account") → the muted "this plugin has no configuration" line and `adb logcat -d | grep -c NoSuchMethodException` is `0`; change a value, `adb shell am force-stop org.runelite.mobile`, relaunch → the value is still set (this exercises `RuneLiteHost.flushConfig()`).

## Assumptions & contingencies

- `@ConfigGroup` retention/type target verified on the client jar (RUNTIME, TYPE), and `AgilityConfig extends net.runelite.client.config.Config`; `pluginConfigClass` therefore needs no proxy invocation and no `setAccessible`.
- If a plugin that RuneLite's desktop shows a config for resolves to `null`, do **not** invent a second lookup: keep the muted "this plugin has no configuration" line and record the plugin name in `AGENTS.md` as a known gap.
- If step 1 reports `# missing: 0` but a tap still throws `NoSuchMethodError`, take the member name straight from the logcat `Caused by:` line, add it to the stub named there with the JDK signature, and re-run steps 1-3.
- If `getModifiersEx()` on a released event matters to some plugin, note that the value follows `modifiers` (the port passes `0`) plus the button mask only for PRESSED/DRAGGED; revisit only if a concrete plugin misbehaves.
