# AGENTS.md

RuneLite/OSRS port to Android/iOS. The app does **not** ship a custom client:
at runtime it downloads/dexes RuneLite's official injected client and loads it via
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
  `assets/client-version.txt`. The ASM transform logic lives in TWO places
  that MUST stay in sync: the Groovy `transformClassBytes` in build.gradle
  and the Java port `ClientClassTransformer` (used by the on-device updater).
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
  client's Callbacks field): the render loop calls `client.paint(Graphics2D)`
  every frame; the client renders into a `MainBufferProvider` and calls back
  `callbacks.draw(buffer, Graphics, x, y)`; the proxy blits `buffer.getImage()`
  into the passed Graphics via `drawImage`; mouse hooks pass the event through.
  A no-op proxy leaves a static gray screen — this is the #1 thing to check if
  rendering "stops".
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
- **Client auto-update** (`ClientUpdater.java`): launcher checks
  `static.runelite.net/bootstrap.json` against `client-version.txt`; on a
  newer RuneLite client it downloads both jars, runs the same ASM transform
  on-device, dexes with the **embedded R8/D8 API** (`com.android.tools:r8`,
  `D8.run(cmd, ExecutorService)` with a 2-thread executor + two separate
  dex passes to keep memory down), strips duplicate classes from the API jar
  (OAuthApi exists in both — D8 rejects duplicates), bundles resources +
  `runelite/index`, atomically swaps `files/runelite-dex.jar` and asks to
  restart. Known quirks: on-device dexing is SLOW (~15-25 min on a Pixel 8
  Pro vs 26 s on a host JVM) — it's intentionally background; `largeHeap`
  is required (D8 OOMs at 256 MB); catch `Throwable`, not `Exception`
  (OutOfMemoryError).
- **Render loop is fixed 766×503**: `appletPixels` (MainActivity) ↔
  `AWTBridge.activePixels/activeWidth/activeHeight`. Change all three
  together. A render thread scales the buffer nearest-neighbor to the surface
  size; it sleeps 500 ms when the client isn't running (so background dexing
  isn't starved) and only clears the buffer then — the game overwrites every
  pixel via the frame blit. A debug overlay + 5 s GameState log are drawn on
  top of the frame.
- **Touch input is wired**: ACTION_DOWN/MOVE/UP → MOUSE_PRESSED/DRAGGED/
  RELEASED/CLICKED, ACTION_SCROLL → MOUSE_WHEEL, dispatched to the client
  component itself, falling back to the canvas from `GameEngine.getCanvas()`
  (see `resolveInputTarget()` in MainActivity). Keyboard is NOT wired (no
  on-screen keyboard; in-game chat is future work).
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
