package org.runelite.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.runelite.mobile.bridge.AWTBridge;
import org.runelite.mobile.bridge.SwingEdt;
import org.runelite.mobile.bridge.TextBridge;
import org.runelite.mobile.bridge.UiBridge;
import org.runelite.mobile.host.PluginConformance;
import org.runelite.mobile.host.RuneLiteHost;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import dalvik.system.DexClassLoader;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "RuneLiteMobile";
    private static final int GAME_W = 765;
    private static final int GAME_H = 503;
    /**
     * Client frame-pacing target (Client.setUnlockedFpsTarget). The default
     * card-deck clock presents once per 20 ms cycle batch, i.e. once per up to
     * 10 cycles, which decouples the presented rate from the rendered rate; the
     * unlocked clock sleeps to a 1e9/FPS_TARGET boundary instead.
     */
    private static final int FPS_TARGET = 60;
    private static final String PREFS_NAME = "RuneLiteMobilePrefs";
    /**
     * Upper bound on the client frame's pixel count. The client software-renders this
     * frame (and the port's blit copies it once per presented frame), so the fill mode
     * picks the coarsest pixel scale that stays inside this budget: at the apparent
     * scale of a 503-px-tall frame the Pixel 8 Pro's 2244x1008 surface would ask for
     * 565k px, which is the target; a much taller surface backs off instead of paying
     * a linear cost in frame time.
     */
    private static final int FILL_PIXEL_BUDGET = 700_000;

    // ── Rendering ───────────────────────────────────────────────────────────
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private volatile boolean isRunning = false;
    /** Render-thread only: frame buffer bitmap, rebuilt when the frame size changes. */
    private Bitmap renderBitmap;
    /** Surface size in px, read by the render thread. */
    private volatile int surfaceW, surfaceH;
    /** Centred fit of the live client frame inside the surface; read by the UI thread. */
    private volatile int fitLeft, fitTop, fitW, fitH;
    /** Frame counter bumped by the callbacks.draw blit; the render thread waits on it. */
    private long frameSeq, lastDrawnSeq;
    /** Render-thread only; the frame source rect of the current renderBitmap. */
    private final Rect srcRect = new Rect();
    /** Render-thread only. */
    private final Rect renderDst = new Rect();
    /** Render-thread only: the geometry renderBitmap/srcRect/fit* were built for. */
    private int geoW, geoH, geoSurfaceW, geoSurfaceH;
    private final Paint scalePaint = new Paint();
    /** Render-thread only; paints the letterbox bars. */
    private final Paint barPaint = new Paint();
    private static java.awt.Component clientInstance;
    private static Object clientObject;
    private static Class<?> clientClass;
    private Thread renderThread;
    /**
     * The display buffer the client draws into (core {@code Component.getGraphics()}
     * builds a {@code Graphics2D} over {@link AWTBridge#activePixels}), and the client
     * frame size it holds. Both are swapped together under {@code renderLock}: the
     * client caches that Graphics per frame-buffer instance, so the array and the
     * bridge dimensions must change before the client asks for a new one (i.e. before
     * its next frame after a resize).
     */
    private volatile int[] appletPixels = new int[GAME_W * GAME_H];
    private volatile int frameW = GAME_W, frameH = GAME_H;
    /** Requested frame size, written by the UI thread, applied on the client thread. */
    private volatile int pendingFrameW = GAME_W, pendingFrameH = GAME_H;
    /**
     * Game fill (the launcher's Display card): the client renders a device-aspect
     * frame in its own resizable display mode instead of the fixed 765x503 one.
     * Persisted; a setup that fails turns it off for the session only (the pref stays,
     * so a later client build -- with different obfuscated internals -- can succeed).
     */
    private volatile boolean fillEnabled = true;
    /** Sidebar mode: true = the drawer floats over a full-width game, false = it insets it. */
    private volatile boolean sidebarOverlap = true;
    /** False until {@link #setupClientDisplay()} ran for the current client instance. */
    private volatile boolean displaySetupDone;
    /** Display card status: "off", "ok" or "unavailable: &lt;reason&gt;". */
    private volatile String fillStatus = "off";
    /**
     * Minimum spacing between display-mode re-asserts. The client applies its own stored
     * display setting whenever player settings arrive (login, a settings packet) or the
     * settings interface opens, which reverts the fill frame; re-applying our mode is
     * cheap but not free (it reallocates the client's frame buffer), so a client that
     * resets the mode repeatedly is given up on rather than fought frame by frame.
     */
    private static final long FILL_REASSERT_MIN_MS = 5_000;
    /** When the display mode was last re-asserted ({@code applyPendingFrameSize}). */
    private long lastFillReassert;
    private boolean pointerDown = false;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private long lastMouseWhen = 0;
    /** A still finger held this long is a right click (the official mobile client's long press). */
    private static final long LONG_PRESS_MS = 400L;
    /** The scheduled long press, or null when none is pending. */
    private Runnable longPress;
    private int touchDownX = -1;
    private int touchDownY = -1;
    private long touchDownWhen = 0;
    /** A leftover finger after a camera gesture: drop its events until it lifts. */
    private boolean suppressUntilUp = false;
    /** No two-finger gesture decided yet. */
    private static final int TWO_NONE = 0, TWO_ROTATE = 1, TWO_ZOOM = 2;
    /** View-pixel movement that locks the gesture: rotate = centroid moved, zoom = span changed. */
    private static final float ROTATE_LOCK_DP = 10f, ZOOM_LOCK_DP = 14f;
    /** Pinch span change (dp) per synthesized mouse-wheel notch, and the per-event step clamp. */
    private static final float ZOOM_DP_PER_NOTCH = 28f;
    private static final int ZOOM_MAX_NOTCHES = 3;
    /** Both fingers down and up within this time, with no rotation/zoom lock: a two-finger tap. */
    private static final long TWO_TAP_MAX_MS = 400L;
    /** The decided two-finger mode for the gesture in flight (one of {@code TWO_*}). */
    private int twoFingerMode = TWO_NONE;
    /** A single-finger camera drag (the official mobile client's rotate gesture) is in progress. */
    private boolean oneFingerDrag;
    /** The finger's down point in view pixels, for the one-finger drag threshold. */
    private float touchDownViewX, touchDownViewY;
    private float gestureSpan0, gestureSpanLast, gestureCx0, gestureCy0, zoomRemainder;
    /** When the second finger went down (the two-finger tap time bound measures from it). */
    private long gestureStartWhen;
    /** View density, for the dp gesture thresholds. */
    private float uiDensity = 1f;
    /** Value of the client's camera-drag setting to restore when the gesture ends; null = untouched. */
    private Boolean forcedCameraSetting;
    private long lastStateLog = 0;
    private long lastDrawLog = 0;
    private long drawCount = 0;
    private boolean loggedRenderableDraw = false;

    // ── Conformance instrumentation (read by PluginConformance) ─────────────
    /**
     * The client asks {@code callbacks.draw(Renderable, boolean)} for every world actor
     * (mesh, shadow, overhead text, hint arrow). {@code entityDrawCalls} counts those
     * questions, {@code entityDrawDenied} the ones RuneLite answered with "no" — which is
     * how a hiding plugin (Entity Hider) becomes observable. {@code calls == 0} means the
     * client never asks, i.e. nothing in the scene can be vetoed at all.
     */
    private static volatile long entityDrawCalls;
    private static volatile long entityDrawDenied;
    private static volatile long playerDrawCalls;
    private static volatile long npcDrawCalls;
    /** {@code net.runelite.api.Player}/{@code NPC}, resolved once through the client loader. */
    private static volatile Class<?> playerInterface;
    private static volatile Class<?> npcInterface;
    /** Event-bus {@code post} calls since boot, keyed by the event object's class name. */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> eventPosts =
        new java.util.concurrent.ConcurrentHashMap<>();
    private LinearLayout kbBar;
    private EditText kbEdit;
    private String kbPrevText = "";
    private final Object renderLock = new Object();
    private long lastListenerLog = 0;
    private Runnable loginReqTicker;

    // ── Launcher UI ─────────────────────────────────────────────────────────
    private FrameLayout rootLayout;
    private ScrollView launcherScroll;
    private LinearLayout launcherPanel;
    private TextView tvTitle;
    private TextView tvSignedInAs;
    private TextView tvStatus;
    private TextView tvVersion;
    private Button btnSignIn;
    private Button btnPlay;
    private TextView btnSwitchCharacter;
    private TextView btnSignOut;
    private TextView btnCheckUpdates;
    /** The launcher's Host section: its rows and its two action pills. */
    private LinearLayout hostRows;
    private TextView btnHostConformance;
    private TextView btnHostRefresh;
    /** The launcher's Display section: game fill + sidebar mode. */
    private LinearLayout displayRows;
    private TextView btnFillMode;
    private TextView btnSidebarMode;
    private TextView btnManual;
    private LinearLayout manualPanel;
    private EditText etSessionId;
    private EditText etCharacterId;
    private EditText etDisplayName;
    private Switch swJxMode;
    private ProgressBar updateProgress;
    private TextView tvUpdateText;
    /** Shown while the client boots (and after a restart); hidden on the first presented frame. */
    private FrameLayout bootOverlay;
    private TextView tvBootStatus;
    private volatile boolean firstFramePresented;
    private SidePanel sidePanel;
    /** The drawer body: the core's Swing sidebar, presented as a bitmap. */
    private SwingSidebarView swingSidebarView;

    // ── Login (browser-based OAuth) ────────────────────────────────────────
    private LocalCallbackServer callbackServer;
    private FrameLayout loginOverlay;
    private TextView tvLoginStatus;

    // ── Session state ───────────────────────────────────────────────────────
    private String sessionId = "";
    private String characterId = "";
    private String displayName = "";
    private String oauthAccessToken = "";
    private String oauthRefreshToken = "";
    private long oauthExpiresAt = 0;
    private boolean signedIn = false;

    private enum LoginStage { IDLE, LEG1, LEG2, EXCHANGING, DONE }
    private LoginStage loginStage = LoginStage.IDLE;
    private String leg1Verifier;
    private String leg2State;
    private String leg2Nonce;
    private boolean loginActive = false;
    /** Version whose update dialog the user already declined, so a *new* version still asks. */
    private String declinedVersion;

    // ── Client-update polling: the "weekly OSRS update" window.
    //    A player can sit on the launcher (or in the game) while RuneLite and CI publish the
    //    client for a new game revision, and the game is unplayable until it is installed.
    //    So: re-check every UPDATE_POLL_MS while the activity is in front, keep a visible
    //    status line, and offer the update the moment a newer client appears. A whole CI run
    //    costs ~190 s (measured 2026-10-16: Android job 177 s, publish 7 s), so a 2-minute
    //    poll bounds publish -> offered at ~2 min, and publish -> installed at ~3-4 min. ──
    private static final long UPDATE_POLL_MS = 120_000;
    private TextView tvClientStatus;
    private final android.os.Handler updatePollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updatePollTask = this::startUpdateCheckAsync;
    private boolean updatePollActive;
    private boolean updateInProgress;
    private String lastVersionCheckStamp;

    private String codebase = "";
    private final Map<String, String> appletParameters = new HashMap<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Emulate the desktop JVM properties Android lacks as early as possible:
        // the embedded R8 dexer reads java.version and misbehaves with "0".
        System.setProperty("java.vendor", "Eclipse Adoptium");
        System.setProperty("java.version", "11.0.22");
        System.setProperty("user.home", getFilesDir().getAbsolutePath());
        System.setProperty("jagex.userhome", getFilesDir().getAbsolutePath());
        // The game's bundled BouncyCastle TLS (used for its own HTTPS requests)
        // fails its handshake on Android. This flag makes the client use the
        // standard platform TLS stack instead (verified in the client: qk ctor).
        System.setProperty("jagex.disableBouncyCastle", "true");

        // Bind AWTBridge active pixels to the client frame buffer (the fixed frame
        // until the Display card's fill mode sizes the client's canvas).
        publishFrameSize(GAME_W, GAME_H);

        // slf4j-simple is the logging binding (logback is not shipped); INFO reaches
        // logcat as System.err, which is where RuneLite's own log.info/warn end up.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

        // The UI thread for the RuneLite runtime is the Swing event dispatch thread:
        // the port runs RuneLite's real Swing shell (the drawer's sidebar and the
        // core's virtual windows), so PluginManager's "must be the EDT" assertion and
        // SwingUtilities.invokeAndWait route there. The Android main thread only
        // presents the core's bitmaps and forwards input -- which is what keeps a
        // blocking plugin (or a modal Swing dialog) from freezing the Android UI.
        AWTBridge.registerUiThread(SwingEdt.ensureStarted(), SwingEdt::post);

        // The core's stage: how big the screen is (the window layer centres its
        // dialogs on it), and that the drawer -- and with it RuneLite's sidebar --
        // starts closed.
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        UiBridge.setScreenSize(displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.density);
        UiBridge.setSidebarVisible(false);

        // Text: overlay text is rasterised by the platform (Typeface/Paint) behind the
        // core-side TextBridge, because core cannot reference android.* and cannot ship a
        // TTF rasteriser. The fonts land in the cache dir because Typeface.Builder only
        // reads files.
        AndroidTextRenderer.setFontDir(new File(getCacheDir(), "fonts"));
        TextBridge.renderer = new AndroidTextRenderer();

        // Sideloaded Plugin Hub plugins live in files/plugins (see MobilePluginHub).
        RuneLiteHost.setAppContext(getApplicationContext());

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);

        uiDensity = getResources().getDisplayMetrics().density;

        // Letterbox bars behind the game frame (the game keeps its 765:503 aspect).
        barPaint.setColor(UiTheme.BG_DEEP);

        // Nearest-neighbour upscale of the 765x503 frame (today's look: the
        // previous per-pixel loop sampled the same way).
        scalePaint.setFilterBitmap(false);
        scalePaint.setDither(false);

        // Bridge Desktop.browse() to the Android browser for in-game links
        java.awt.Desktop.openUrlHandler = url -> {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url));
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Log.w(TAG, "Could not open URL: " + url, e);
            }
        };

        // 1. Import credentials.properties from local/external files storage if it exists
        importCredentialsFile();

        // 2. Load saved session state
        loadSessionState();
        loadDisplayPrefs();

        // 3. Build UI: SurfaceView + launcher screen + login WebView overlay
        buildLauncherUi();
        setContentView(rootLayout);

        // 4. Touch input dispatch into the game client
        setupTouchInput();

        // 5. Check for a newer RuneLite client in the background
        startUpdateCheckAsync();

        // 5b. Pre-register the game dex with ART so dex2oat can AOT it. This
        // device has dalvik.vm.usejit=false (JIT disabled), so without AOT the
        // game runs interpreted. The DexClassLoader construction mirrors
        // bootstrapGameClient exactly, which records the class-loader context
        // before login. AOT is an operator step (the app has no root and cannot
        // run `pm compile` itself), re-run after every client-jar update AND
        // after every APK install: an install lands in a new /data/app/~~…==/
        // dir, whose path+checksums are part of the class-loader context the
        // odex is keyed to, so ART discards the `speed` odex and falls back to
        // the `verify` vdex (interpreted, ~10x slower).
        //   adb shell cmd package compile -m speed -f org.runelite.mobile                  # app dex
        //   adb shell cmd package compile -m speed -f --secondary-dex org.runelite.mobile  # client dex
        //   adb shell pm art dump org.runelite.mobile   # expect [status=speed] twice
        // The `--secondary-dex` pass must be LAST: compiling the package without it
        // rewrites the app's oat state and drops the client dex back to `verify`, which
        // is the interpreted case this comment is about.
        // The installed APK must NOT be debuggable: ART Service rewrites `-m
        // speed` to `verify` for debuggable packages, and a verify-only odex
        // still executes interpreted. Use the release build (signed with the
        // debug key, so `install -r` keeps app data).
        try {
            File dexJar = new File(getFilesDir(), ClientUpdater.DEX_ASSET_NAME);
            if (dexJar.exists() && dexJar.length() > 0) {
                File dexOut = getDir("dex", MODE_PRIVATE);
                new DexClassLoader(dexJar.getAbsolutePath(), dexOut.getAbsolutePath(), null, getClassLoader());
                Log.i(TAG, "Pre-registered game dex classloader for ART dexopt");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Pre-register dex failed: " + t.getMessage());
        }

        int aot = ClientUpdater.clientDexAotStatus(this);
        if (aot != ClientUpdater.AOT_OK && aot != ClientUpdater.AOT_UNKNOWN) {
            Log.w(TAG, "Client dex is NOT AOT-compiled: " + ClientUpdater.clientDexAotText(this)
                + " -- dalvik.vm.usejit=false, so the game runs interpreted (~10x slower)");
        } else {
            Log.i(TAG, "Client dex AOT: " + ClientUpdater.clientDexAotText(this));
        }

        // 6. JIT sanity benchmark: proves whether ART is compiling hot code in
        // this process (JIT'd: 50M float ops ~100-300ms; interpret-only: seconds).
        Thread benchThread = new Thread(() -> {
            try {
                Thread.sleep(3000);
                int[] arr = new int[1 << 20];
                int r = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < 5_000_000; i++) r += Float.floatToRawIntBits((i - r) * 1.3f) >>> 31;
                long warmFloatNs = System.nanoTime() - t0;
                t0 = System.nanoTime();
                for (int i = 0; i < 50_000_000; i++) r += Float.floatToRawIntBits((i - r) * 1.3f) >>> 31;
                long hotFloatNs = System.nanoTime() - t0;
                t0 = System.nanoTime();
                for (int i = 0; i < 100_000_000; i++) r += arr[i & 0xFFFFF] + i * 7;
                long hotIntNs = System.nanoTime() - t0;
                t0 = System.nanoTime();
                for (int i = 0; i < 20_000_000; i++) arr[i & 0xFFFFF] = arr[i & 0xFFFFF] + (Float.floatToRawIntBits((i - r) * 1.3f) >>> 3);
                long scanNs = System.nanoTime() - t0;
                Log.i(TAG, "BENCH warmFloat(5M)=" + (warmFloatNs / 1e6) + "ms hotFloat(50M)=" + (hotFloatNs / 1e6)
                    + "ms hotInt(100M)=" + (hotIntNs / 1e6) + "ms scanline(20M)=" + (scanNs / 1e6) + "ms chk=" + r);
            } catch (Throwable e) {
                Log.e(TAG, "BENCH failed", e);
            }
        }, "BenchThread");
        benchThread.setPriority(Thread.MIN_PRIORITY);
        benchThread.start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Launcher UI
    // ═══════════════════════════════════════════════════════════════════════

    private void buildLauncherUi() {
        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        rootLayout.addView(surfaceView);

        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final float density = metrics.density;
        final int dp = (int) (density * 5);

        // ── Launcher panel ──
        launcherScroll = new ScrollView(this);
        launcherScroll.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // Scrim over the (idle) game surface. fillViewport + a MATCH_PARENT wrapper is
        // what centres the card on both axes when it fits and scrolls when it does not.
        launcherScroll.setFillViewport(true);
        launcherScroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xEE1C1610, 0xEE0B0906}));

        FrameLayout launcherWrap = new FrameLayout(this);
        launcherWrap.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        launcherPanel = new LinearLayout(this);
        launcherPanel.setOrientation(LinearLayout.VERTICAL);
        launcherPanel.setBackground(UiTheme.rounded(0xF71E1A16, UiTheme.GOLD_DIM, 2f, 20f, density));

        int cardWidth = Math.min((int) (420 * density), (int) (0.92f * metrics.widthPixels));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
            cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        int cardMargin = (int) (24 * density);
        panelLp.setMargins(cardMargin, cardMargin, cardMargin, cardMargin);
        launcherPanel.setLayoutParams(panelLp);
        launcherPanel.setPadding((int) (28 * density), (int) (24 * density), (int) (28 * density), (int) (24 * density));

        tvTitle = new TextView(this);
        tvTitle.setText("RuneLite Mobile");
        tvTitle.setTextColor(UiTheme.GOLD);
        tvTitle.setTextSize(26f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setLetterSpacing(0.02f);
        tvTitle.setGravity(Gravity.CENTER);
        launcherPanel.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Old School RuneScape");
        tvSubtitle.setTextColor(UiTheme.TEXT_MUTED);
        tvSubtitle.setTextSize(12f);
        tvSubtitle.setGravity(Gravity.CENTER);
        tvSubtitle.setPadding(0, dp * 2, 0, dp * 3);
        launcherPanel.addView(tvSubtitle);

        View divider = new View(this);
        divider.setBackground(UiTheme.rounded(UiTheme.GOLD_DIM, 0, 0, 0, density));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) density));
        dividerParams.bottomMargin = (int) (12 * density);
        launcherPanel.addView(divider, dividerParams);

        tvSignedInAs = new TextView(this);
        tvSignedInAs.setTextColor(UiTheme.TEXT);
        tvSignedInAs.setTextSize(15f);
        tvSignedInAs.setGravity(Gravity.CENTER);
        tvSignedInAs.setBackground(UiTheme.rounded(UiTheme.SURFACE_ALT, UiTheme.BORDER, 1f, 12f, density));
        tvSignedInAs.setPadding((int) (14 * density), (int) (14 * density),
            (int) (14 * density), (int) (14 * density));
        launcherPanel.addView(tvSignedInAs);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(UiTheme.TEXT_MUTED);
        tvStatus.setTextSize(12f);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, dp * 3, 0, dp * 2);
        launcherPanel.addView(tvStatus);

        // Update progress — kept near the top so it's visible without scrolling
        updateProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        updateProgress.setVisibility(View.GONE);
        launcherPanel.addView(updateProgress);

        tvUpdateText = new TextView(this);
        tvUpdateText.setTextColor(UiTheme.TEXT_MUTED);
        tvUpdateText.setTextSize(12f);
        tvUpdateText.setGravity(Gravity.CENTER);
        tvUpdateText.setVisibility(View.GONE);
        launcherPanel.addView(tvUpdateText);

        btnSignIn = styledButton("Sign in with Jagex Account",
            new int[]{0xFFFFD75E, 0xFFD6A419}, 0xFF231A05, 16f);
        btnSignIn.setOnClickListener(v -> startJagexLogin());
        launcherPanel.addView(btnSignIn);

        btnPlay = styledButton("Play", new int[]{0xFF4CBB5A, 0xFF2E7D32}, 0xFFFFFFFF, 18f);
        btnPlay.setOnClickListener(v -> onPlayClicked());
        launcherPanel.addView(btnPlay);

        btnSwitchCharacter = linkButton("Switch character");
        btnSwitchCharacter.setOnClickListener(v -> startJagexLogin());
        launcherPanel.addView(btnSwitchCharacter);

        btnSignOut = linkButton("Sign out");
        btnSignOut.setTextColor(UiTheme.RED);
        btnSignOut.setOnClickListener(v -> confirmSignOut());
        launcherPanel.addView(btnSignOut);

        tvVersion = new TextView(this);
        tvVersion.setTextColor(UiTheme.TEXT_DIM);
        tvVersion.setTextSize(11f);
        tvVersion.setGravity(Gravity.CENTER);
        tvVersion.setPadding(0, dp * 3, 0, 0);
        launcherPanel.addView(tvVersion);

        btnCheckUpdates = linkButton("Check for updates");
        btnCheckUpdates.setOnClickListener(v -> checkForUpdates(true));
        launcherPanel.addView(btnCheckUpdates);

        // Update/health line: what the client-update poll found, and -- during a weekly OSRS
        // update -- the one-tap way out of a client the game refuses. See updateClientStatus.
        tvClientStatus = new TextView(this);
        tvClientStatus.setTextColor(UiTheme.TEXT_DIM);
        tvClientStatus.setTextSize(11f);
        tvClientStatus.setGravity(Gravity.CENTER);
        tvClientStatus.setPadding(dp, dp * 2, dp, dp * 2);
        launcherPanel.addView(tvClientStatus);

        // ── Host diagnostics: what the old drawer's Host tab reported. The drawer is
        //    RuneLite's own Swing sidebar now, so this lives where a failed boot and
        //    the "Check for updates" button already are -- the launcher screen. ──
        LinearLayout hostCard = new LinearLayout(this);
        hostCard.setOrientation(LinearLayout.VERTICAL);
        hostCard.setBackground(UiTheme.rounded(UiTheme.SURFACE, UiTheme.BORDER, 1f, 12f, density));
        hostCard.setPadding((int) (14 * density), (int) (12 * density),
            (int) (14 * density), (int) (12 * density));
        LinearLayout.LayoutParams hostCardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hostCardParams.topMargin = (int) (12 * density);
        launcherPanel.addView(hostCard, hostCardParams);

        TextView tvHostTitle = new TextView(this);
        tvHostTitle.setText("Host");
        tvHostTitle.setTextColor(UiTheme.GOLD);
        tvHostTitle.setTextSize(14f);
        tvHostTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        hostCard.addView(tvHostTitle);

        hostRows = new LinearLayout(this);
        hostRows.setOrientation(LinearLayout.VERTICAL);
        hostCard.addView(hostRows);

        btnHostConformance = pillAction("Run plugin conformance");
        btnHostConformance.setLayoutParams(fullWidthParams((int) (6 * density)));
        btnHostConformance.setOnClickListener(v -> requestPluginConformance());
        hostCard.addView(btnHostConformance);

        btnHostRefresh = pillAction("Refresh");
        btnHostRefresh.setLayoutParams(fullWidthParams((int) (6 * density)));
        btnHostRefresh.setOnClickListener(v -> refreshHostCard());
        hostCard.addView(btnHostRefresh);

        refreshHostCard();

        // ── Display: game fill (a device-aspect client frame) and the sidebar mode. ──
        LinearLayout displayCard = new LinearLayout(this);
        displayCard.setOrientation(LinearLayout.VERTICAL);
        displayCard.setBackground(UiTheme.rounded(UiTheme.SURFACE, UiTheme.BORDER, 1f, 12f, density));
        displayCard.setPadding((int) (14 * density), (int) (12 * density),
            (int) (14 * density), (int) (12 * density));
        LinearLayout.LayoutParams displayCardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        displayCardParams.topMargin = (int) (12 * density);
        launcherPanel.addView(displayCard, displayCardParams);

        TextView tvDisplayTitle = new TextView(this);
        tvDisplayTitle.setText("Display");
        tvDisplayTitle.setTextColor(UiTheme.GOLD);
        tvDisplayTitle.setTextSize(14f);
        tvDisplayTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        displayCard.addView(tvDisplayTitle);

        displayRows = new LinearLayout(this);
        displayRows.setOrientation(LinearLayout.VERTICAL);
        displayCard.addView(displayRows);

        btnFillMode = pillAction("Game fill: on");
        btnFillMode.setLayoutParams(fullWidthParams((int) (6 * density)));
        btnFillMode.setOnClickListener(v -> toggleFillMode());
        displayCard.addView(btnFillMode);

        btnSidebarMode = pillAction("Sidebar: overlap");
        btnSidebarMode.setLayoutParams(fullWidthParams((int) (6 * density)));
        btnSidebarMode.setOnClickListener(v -> toggleSidebarMode());
        displayCard.addView(btnSidebarMode);

        refreshDisplayCard();

        btnManual = linkButton("Manual session tokens (advanced)");
        btnManual.setTextColor(UiTheme.TEXT_DIM);
        btnManual.setOnClickListener(v ->
            manualPanel.setVisibility(manualPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        launcherPanel.addView(btnManual);

        manualPanel = new LinearLayout(this);
        manualPanel.setOrientation(LinearLayout.VERTICAL);
        manualPanel.setVisibility(View.GONE);
        launcherPanel.addView(manualPanel);

        swJxMode = new Switch(this);
        swJxMode.setText("Enable Jagex Account Mode");
        swJxMode.setTextColor(UiTheme.TEXT);
        UiTheme.tintSwitch(swJxMode);
        swJxMode.setChecked(signedIn);
        manualPanel.addView(swJxMode);

        etSessionId = createStyledEditText("JX_SESSION_ID (jagexSessionId)", sessionId);
        manualPanel.addView(etSessionId);

        etCharacterId = createStyledEditText("JX_CHARACTER_ID (characterId)", characterId);
        manualPanel.addView(etCharacterId);

        etDisplayName = createStyledEditText("JX_DISPLAY_NAME", displayName);
        manualPanel.addView(etDisplayName);

        Button btnSave = styledButton("Save & Apply", new int[]{0xFFFFD75E, 0xFFD6A419}, 0xFF231A05, 15f);
        btnSave.setOnClickListener(v -> saveManualCredentials());
        manualPanel.addView(btnSave);

        launcherWrap.addView(launcherPanel);
        launcherScroll.addView(launcherWrap);
        rootLayout.addView(launcherScroll);

        // ── Boot overlay: the client's own progress (the on-canvas debug text is
        //    gone; its information lives here and in the drawer's Host tab). It is
        //    added before the side panel so the column and drawer draw over it. ──
        bootOverlay = new FrameLayout(this);
        bootOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bootOverlay.setBackgroundColor(0xFF0E0C09);
        bootOverlay.setVisibility(View.GONE);

        LinearLayout bootCard = new LinearLayout(this);
        bootCard.setOrientation(LinearLayout.VERTICAL);
        bootCard.setGravity(Gravity.CENTER_HORIZONTAL);
        bootCard.setBackground(UiTheme.rounded(UiTheme.SURFACE, UiTheme.BORDER, 1f, 16f, density));
        bootCard.setPadding((int) (24 * density), (int) (24 * density),
            (int) (24 * density), (int) (24 * density));
        FrameLayout.LayoutParams bootCardParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER);
        bootCard.setLayoutParams(bootCardParams);

        ProgressBar bootSpinner = new ProgressBar(this);
        bootSpinner.setIndeterminate(true);
        bootSpinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(UiTheme.GOLD));
        bootCard.addView(bootSpinner, new LinearLayout.LayoutParams(
            (int) (36 * density), (int) (36 * density)));

        TextView tvBootTitle = new TextView(this);
        tvBootTitle.setText("Starting RuneLite…");
        tvBootTitle.setTextColor(UiTheme.TEXT);
        tvBootTitle.setTextSize(15f);
        tvBootTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBootTitle.setGravity(Gravity.CENTER);
        tvBootTitle.setPadding(0, (int) (12 * density), 0, (int) (6 * density));
        bootCard.addView(tvBootTitle);

        tvBootStatus = new TextView(this);
        tvBootStatus.setTextColor(UiTheme.TEXT_MUTED);
        tvBootStatus.setTextSize(12f);
        tvBootStatus.setGravity(Gravity.CENTER);
        tvBootStatus.setMaxLines(3);
        bootCard.addView(tvBootStatus);

        bootOverlay.addView(bootCard);
        rootLayout.addView(bootOverlay);

        // ── Soft keyboard bridge: the game has no IME of its own, so the keyboard
        //    toggle at the foot of the right-edge column opens an EditText whose
        //    keystrokes are forwarded into the client as AWT KeyEvents ──
        kbBar = new LinearLayout(this);
        kbBar.setOrientation(LinearLayout.HORIZONTAL);
        kbBar.setGravity(Gravity.CENTER_VERTICAL);
        kbBar.setBackground(UiTheme.rounded(UiTheme.SURFACE, UiTheme.GOLD_DIM, 1f, 0f, density));
        kbBar.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
        kbBar.setVisibility(View.GONE);
        kbEdit = new EditText(this);
        kbEdit.setSingleLine(true);
        kbEdit.setTextSize(16f);
        kbEdit.setHint("Type here...");
        kbEdit.setTextColor(UiTheme.TEXT);
        kbEdit.setHintTextColor(UiTheme.TEXT_DIM);
        kbEdit.setBackground(UiTheme.rounded(UiTheme.SURFACE_ALT, UiTheme.BORDER, 1f, 10f, density));
        kbEdit.setPadding((int) (10 * density), (int) (10 * density),
            (int) (10 * density), (int) (10 * density));
        // Not TYPE_TEXT_VARIATION_VISIBLE_PASSWORD: Gboard treats a password field as
        // "must not be shoulder-surfed" and takes over the whole screen in landscape,
        // covering this bar's own Enter/Hide buttons. NO_SUGGESTIONS +
        // NO_PERSONALIZED_LEARNING keep the other half of that variation's behaviour
        // (no autocorrect, no learned words) without the fullscreen takeover.
        kbEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        kbEdit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO
            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        kbEdit.setOnEditorActionListener((v, actionId, event) -> {
            dispatchKeyCode(java.awt.event.KeyEvent.VK_ENTER, '\n');
            return true;
        });
        kbEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String oldText = kbPrevText;
                String newText = s.toString();
                kbPrevText = newText;
                int pf = 0;
                while (pf < oldText.length() && pf < newText.length() && oldText.charAt(pf) == newText.charAt(pf)) pf++;
                int sf = 0;
                while (sf < oldText.length() - pf && sf < newText.length() - pf
                    && oldText.charAt(oldText.length() - 1 - sf) == newText.charAt(newText.length() - 1 - sf)) sf++;
                int removed = oldText.length() - pf - sf;
                String added = newText.substring(pf, newText.length() - sf);
                for (int i = 0; i < removed; i++) {
                    dispatchKeyCode(java.awt.event.KeyEvent.VK_BACK_SPACE, '\b');
                }
                if (added.length() > 0) {
                    dispatchKeyText(added);
                }
            }
        });
        TextView kbEnter = pillAction("Enter");
        kbEnter.setOnClickListener(v -> dispatchKeyCode(java.awt.event.KeyEvent.VK_ENTER, '\n'));
        TextView kbClose = pillAction("Hide");
        kbClose.setOnClickListener(v -> toggleKeyboardBar());
        kbBar.addView(kbEdit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        kbBar.addView(kbEnter);
        kbBar.addView(kbClose);
        // Anchored to the TOP: with the soft keyboard up (Gboard takes the bottom
        // half in landscape) a bottom-anchored bar is covered -- together with the
        // game's own chat input line -- so neither the typed text nor the Enter/Hide
        // buttons would be reachable.
        FrameLayout.LayoutParams kbBarParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        kbBarParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        kbBar.setLayoutParams(kbBarParams);
        rootLayout.addView(kbBar);

        // ── Native side panel: plugins, config and host status. Its right-edge
        //    column is the port's only always-present chrome; the game surface is
        //    inset by the width it occupies (applyGameInsets) instead of being
        //    covered. Built before the login overlay so the overlay stays on top. ──
        sidePanel = new SidePanel(this, rootLayout);
        sidePanel.setListener(new SidePanel.Listener() {
            @Override
            public void onOpenChanged(boolean open) {
                applyGameInsets();
            }

            @Override
            public void onShowLauncherRequested() {
                showLauncher();
            }

            @Override
            public void onKeyboardToggleRequested() {
                toggleKeyboardBar();
            }
        });

        // ── The Swing stage: RuneLite's real sidebar (hosted by the drawer, which
        //    reports its own open/closed state) and the core's virtual windows (the
        //    colour picker, plugin dialogs), both presented as bitmaps the core
        //    paints. The host is installed before the client boots, so no dirty mark
        //    or focus change can be lost, and the keyboard bar it asks for is the
        //    same one the game's input uses. ──
        swingSidebarView = new SwingSidebarView(this, new SwingSidebarView.Callback() {
            @Override
            public void onTextInputRequested(boolean show) {
                showKeyboardBar(show);
            }

            @Override
            public void onCloseSidebarRequested() {
                sidePanel.close();
            }
        });
        SwingWindowLayer windowLayer = new SwingWindowLayer(this);
        windowLayer.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        swingSidebarView.setWindowLayer(windowLayer);
        UiBridge.setHost(swingSidebarView);
        sidePanel.setSwingSidebarView(swingSidebarView);
        // Above the drawer and the game surface (the side panel was added before it),
        // below the login overlay (added after it). It shows itself only while the
        // core has a visible window up.
        rootLayout.addView(windowLayer);

        // ── Login WebView overlay (WebView created lazily in startJagexLogin —
        //    instantiating it eagerly spawns a background renderer process) ──
        loginOverlay = new FrameLayout(this);
        loginOverlay.setBackgroundColor(0xCC000000);
        loginOverlay.setVisibility(View.GONE);

        tvLoginStatus = new TextView(this);
        tvLoginStatus.setTextColor(0xFF9A9AA8);
        tvLoginStatus.setTextSize(12f);
        tvLoginStatus.setGravity(Gravity.CENTER);
        tvLoginStatus.setPadding(0, (int) (40 * density), 0, 0);
        loginOverlay.addView(tvLoginStatus);

        TextView tvCloseLogin = new TextView(this);
        tvCloseLogin.setText("Cancel login");
        tvCloseLogin.setTextColor(0xFF888899);
        tvCloseLogin.setTextSize(14f);
        tvCloseLogin.setGravity(Gravity.CENTER);
        tvCloseLogin.setPadding(0, (int) (20 * density), 0, (int) (30 * density));
        tvCloseLogin.setOnClickListener(v -> cancelLogin());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        closeParams.gravity = Gravity.BOTTOM;
        loginOverlay.addView(tvCloseLogin, closeParams);

        rootLayout.addView(loginOverlay);

        // The app starts on the launcher screen: the right-edge chrome (and a drawer
        // restored open from prefs) must not float over it.
        sidePanel.setAvailable(false);
        updateLauncherUi();
    }

    private Button styledButton(String text, int[] gradient, int textColour, float textSize) {
        final float density = getResources().getDisplayMetrics().density;
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(textColour);
        btn.setTextSize(textSize);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, gradient);
        bg.setCornerRadius(14 * density);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (10 * density);
        btn.setLayoutParams(lp);
        btn.setPadding(0, (int) (16 * density), 0, (int) (16 * density));
        UiTheme.ripple(btn);
        return btn;
    }

    /** A small gold-on-stone pill: the keyboard bar's action buttons. */
    private TextView pillAction(String text) {
        final float density = getResources().getDisplayMetrics().density;
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(UiTheme.GOLD);
        view.setTextSize(12f);
        view.setGravity(Gravity.CENTER);
        view.setBackground(UiTheme.rounded(UiTheme.SURFACE_ALT, UiTheme.GOLD_DIM, 1f, 10f, density));
        view.setPadding((int) (10 * density), (int) (10 * density),
            (int) (10 * density), (int) (10 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (6 * density);
        view.setLayoutParams(lp);
        UiTheme.ripple(view);
        return view;
    }

    private TextView linkButton(String text) {
        final float density = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(UiTheme.TEXT_MUTED);
        tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, (int) (12 * density), 0, 0);
        return tv;
    }

    private EditText createStyledEditText(String hint, String value) {
        final float density = getResources().getDisplayMetrics().density;
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(UiTheme.TEXT_DIM);
        et.setText(value);
        et.setTextColor(UiTheme.TEXT);
        et.setTextSize(14f);
        et.setSingleLine(true);
        et.setBackground(UiTheme.rounded(UiTheme.SURFACE_ALT, UiTheme.BORDER, 1f, 10f, density));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (10 * density);
        et.setLayoutParams(params);
        et.setPadding((int) (14 * density), (int) (14 * density), (int) (14 * density), (int) (14 * density));
        return et;
    }

    private void updateLauncherUi() {
        if (signedIn && !sessionId.isEmpty()) {
            tvSignedInAs.setText("●  Signed in as " + (displayName.isEmpty() ? characterId : displayName));
            tvSignedInAs.setVisibility(View.VISIBLE);
            btnSignIn.setVisibility(View.GONE);
            btnPlay.setVisibility(View.VISIBLE);
            btnSwitchCharacter.setVisibility(View.VISIBLE);
            btnSignOut.setVisibility(View.VISIBLE);
            tvStatus.setText(sessionId.isEmpty() ? "Session not loaded" : "Ready to play");
        } else {
            tvSignedInAs.setVisibility(View.GONE);
            btnSignIn.setVisibility(View.VISIBLE);
            btnPlay.setVisibility(View.GONE);
            btnSwitchCharacter.setVisibility(View.GONE);
            btnSignOut.setVisibility(View.GONE);
            tvStatus.setText("Sign in with a Jagex account to play");
        }
        String current = ClientUpdater.currentClientVersion(this);
        String installed = ClientUpdater.installedClientVersion(this);
        int aot = ClientUpdater.clientDexAotStatus(this);
        boolean aotBad = aot == ClientUpdater.AOT_STALE || aot == ClientUpdater.AOT_MISSING;
        tvVersion.setText(current.equals(installed)
            ? "Client v" + current
            : "Client v" + installed + " (APK ships v" + current + ")");
        if (aotBad) {
            tvVersion.append("\nNOT AOT-COMPILED - expect ~5 fps (Host section on this screen)");
        }
        float density = getResources().getDisplayMetrics().density;
        tvVersion.setTextColor(aotBad ? UiTheme.RED : UiTheme.TEXT_DIM);
        tvVersion.setTextSize(aotBad ? 12f : 11f);
        tvVersion.setBackground(aotBad
            ? UiTheme.rounded(0x22E57373, UiTheme.RED, 1f, 8f, density)
            : null);
        tvVersion.setPadding(0, aotBad ? (int) (8 * density) : 0, 0, aotBad ? (int) (8 * density) : 0);
        swJxMode.setChecked(signedIn);
        etSessionId.setText(sessionId);
        etCharacterId.setText(characterId);
        etDisplayName.setText(displayName);
        refreshHostCard();
        refreshDisplayCard();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Launcher Host section

    /**
     * Fills the Host card with the live runtime state. Called from
     * {@link #updateLauncherUi()}, i.e. whenever the launcher becomes visible.
     */
    private void refreshHostCard() {
        if (hostRows == null) {
            return;
        }
        hostRows.removeAllViews();
        hostRows.addView(hostRow("client version", RuneLiteHost.clientVersion()));
        hostRows.addView(hostRow("runtime", RuneLiteHost.isRunning() ? "running" : "not running"));
        hostRows.addView(hostRow("frame", frameW + "x" + frameH + (fillEnabled ? " (fill)" : " (fixed)")));
        hostRows.addView(hostRow("plugin index", RuneLiteHost.indexSize() + " classes"));
        hostRows.addView(hostRow("active plugins", String.valueOf(RuneLiteHost.activePluginCount())));
        hostRows.addView(hostRow("status", RuneLiteHost.status()));
        Throwable failure = RuneLiteHost.failure();
        hostRows.addView(hostRow("last error", failure == null ? "none" : String.valueOf(failure)));
        // The sidebar bootstrap is separate from the injector's (it is RuneLite's own
        // ClientUI, not the port's shim), so it gets its own row.
        Throwable sidebarFailure = RuneLiteHost.sidebarFailure();
        hostRows.addView(sidebarFailure == null
            ? hostRow("sidebar", "ok")
            : hostText("sidebar: " + sidebarFailure, 11f, UiTheme.RED));
        List<String> pluginFailures = RuneLiteHost.pluginFailures();
        hostRows.addView(hostRow("plugin load failures", String.valueOf(pluginFailures.size())));
        for (String entry : pluginFailures) {
            hostRows.addView(hostText("  " + entry, 10f, UiTheme.RED));
        }
        int aot = ClientUpdater.clientDexAotStatus(this);
        boolean aotBad = aot == ClientUpdater.AOT_STALE || aot == ClientUpdater.AOT_MISSING;
        String aotText = ClientUpdater.clientDexAotText(this);
        hostRows.addView(aotBad
            ? hostText("client AOT: " + aotText, 11f, UiTheme.RED)
            : hostRow("client AOT", aotText));
        hostRows.addView(hostRow("on-device dexer", "unavailable"));
        hostRows.addView(hostRow("conformance", PluginConformance.isRunning()
            ? "running…" : PluginConformance.lastSummary()));
        if (btnHostConformance != null) {
            btnHostConformance.setEnabled(!PluginConformance.isRunning() && RuneLiteHost.isRunning());
        }
    }

    /** Starts a plugin conformance pass (single-flight inside {@link PluginConformance}). */
    private void requestPluginConformance() {
        PluginConformance.run(this);
        Toast.makeText(this, "conformance running — report: "
            + PluginConformance.reportFile(this).getName(), Toast.LENGTH_LONG).show();
        refreshHostCard();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Launcher Display section

    /**
     * Fills the Display card with the live frame geometry. Called from
     * {@link #updateLauncherUi()} (i.e. whenever the launcher becomes visible) and from
     * both toggles, so the card always shows what the client is actually doing.
     */
    private void refreshDisplayCard() {
        if (displayRows == null) {
            return;
        }
        displayRows.removeAllViews();
        if (!fillEnabled) {
            displayRows.addView(hostRow("game", "off — letterbox " + GAME_W + "x" + GAME_H));
        } else if (fillStatus.startsWith("unavailable")) {
            displayRows.addView(hostText("game: " + fillStatus, 11f, UiTheme.RED));
            displayRows.addView(hostRow("fallback", "letterbox " + frameW + "x" + frameH));
        } else if ("off".equals(fillStatus)) {
            // The pref is on but no client has applied it yet (the launcher screen).
            displayRows.addView(hostRow("game", "on — device-aspect client frame (at boot)"));
        } else {
            displayRows.addView(hostRow("game", "on — resizable " + frameW + "x" + frameH
                + " (" + ((frameW * frameH) / 1000) + "k px)"));
        }
        displayRows.addView(hostRow("sidebar", sidebarOverlap
            ? "overlap — the drawer floats over the game" : "resize — the game is inset"));
        if (btnFillMode != null) {
            btnFillMode.setText(fillEnabled ? "Game fill: off" : "Game fill: on");
        }
        if (btnSidebarMode != null) {
            btnSidebarMode.setText(sidebarOverlap ? "Sidebar: resize" : "Sidebar: overlap");
        }
    }

    /** One {@code label: value} row of the Host card. */
    private View hostRow(String label, String value) {
        return hostText(label + ": " + value, 11f, UiTheme.TEXT_MUTED);
    }

    // ── Device diagnostics: on-demand thread dump ──────────────────────────

    /** The adb drop file that asks for a thread dump (app-specific external files dir). */
    static File stackDumpRequestFile(android.content.Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir == null ? new File(context.getFilesDir(), "stackdump.request")
            : new File(dir, "stackdump.request");
    }

    /** The file a thread dump is written to, next to the request file. */
    static File stackDumpFile(android.content.Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir == null ? new File(context.getFilesDir(), "stackdump.txt")
            : new File(dir, "stackdump.txt");
    }

    /**
     * Consumes {@link #stackDumpRequestFile} if it is there and writes every thread's
     * Java stack to {@link #stackDumpFile}, one frame per line. The release APK is not
     * debuggable and the adb shell cannot signal it, so this is how a hung client thread
     * (the "frozen world" case) is localised on the device. Called from both the render
     * loop's 5 s tick and the main-thread 1 s tick, because a hang can block either one.
     */
    private void consumeStackDumpRequest() {
        File request = stackDumpRequestFile(this);
        if (!request.isFile() || !request.delete()) {
            return;
        }
        File out = stackDumpFile(this);
        Log.i(TAG, "stack dump requested via " + request.getAbsolutePath() + " -> " + out.getAbsolutePath());
        try (java.io.PrintWriter writer = new java.io.PrintWriter(out, "UTF-8")) {
            writer.println("# thread dump " + new java.util.Date());
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                Thread thread = entry.getKey();
                writer.println("\"" + thread.getName() + "\" state=" + thread.getState()
                    + " prio=" + thread.getPriority() + " daemon=" + thread.isDaemon());
                for (StackTraceElement frame : entry.getValue()) {
                    writer.println("    at " + frame);
                }
            }
            writer.flush();
        } catch (Exception e) {
            Log.w(TAG, "stack dump failed", e);
        }
    }

    private TextView hostText(String text, float size, int colour) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(colour);
        final float density = getResources().getDisplayMetrics().density;
        view.setPadding(0, (int) (3 * density), 0, (int) (3 * density));
        return view;
    }

    /** Full-width action-pill params (the launcher card is a column, not a bar). */
    private static LinearLayout.LayoutParams fullWidthParams(int topMarginPx) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMarginPx;
        return params;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Session persistence
    // ═══════════════════════════════════════════════════════════════════════

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void loadSessionState() {
        SharedPreferences prefs = prefs();
        signedIn = prefs.getBoolean("jx_mode_enabled", false);
        sessionId = prefs.getString("jx_session_id", "");
        characterId = prefs.getString("jx_character_id", "");
        displayName = prefs.getString("jx_display_name", "");
        oauthAccessToken = prefs.getString("oauth_access_token", "");
        oauthRefreshToken = prefs.getString("oauth_refresh_token", "");
        oauthExpiresAt = prefs.getLong("oauth_expires_at", 0);
        if (!signedIn || sessionId.isEmpty()) {
            signedIn = false;
        }
    }

    /**
     * The Display card's two settings: game fill (device-aspect client frame) and the
     * sidebar mode. Both are read here -- before {@code buildLauncherUi()} -- so the
     * launcher renders the stored state and {@code bootstrapGameClient} applies it.
     */
    private void loadDisplayPrefs() {
        SharedPreferences prefs = prefs();
        fillEnabled = prefs.getBoolean("gameFill", true);
        sidebarOverlap = prefs.getBoolean("sidebarOverlap", true);
    }

    private void saveSessionState(String newSessionId, String newCharacterId, String newDisplayName) {
        sessionId = newSessionId;
        characterId = newCharacterId;
        displayName = newDisplayName;
        signedIn = !newSessionId.isEmpty();
        prefs().edit()
            .putBoolean("jx_mode_enabled", signedIn)
            .putString("jx_session_id", sessionId)
            .putString("jx_character_id", characterId)
            .putString("jx_display_name", displayName)
            .putString("oauth_access_token", oauthAccessToken)
            .putString("oauth_refresh_token", oauthRefreshToken)
            .putLong("oauth_expires_at", oauthExpiresAt)
            .apply();
        writeCredentialsFile();
        updateLauncherUi();
    }

    /**
     * Writes the client's native credentials file (<user.home>/credentials.properties,
     * JX_* keys) so the client can read the session even if env vars are missed.
     */
    private void writeCredentialsFile() {
        File f = new File(getFilesDir(), "credentials.properties");
        try {
            Properties props = new Properties();
            props.setProperty("JX_SESSION_ID", sessionId);
            props.setProperty("JX_CHARACTER_ID", characterId);
            props.setProperty("JX_DISPLAY_NAME", displayName);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                props.store(fos, "RuneLite Mobile session");
            }
            Log.i(TAG, "Wrote credentials.properties (" + f.length() + " bytes)");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write credentials.properties", e);
        }
    }

    private void importCredentialsFile() {
        File filesDirFile = new File(getFilesDir(), "credentials.properties");
        File extFilesDirFile = null;
        try {
            extFilesDirFile = new File(getExternalFilesDir(null), "credentials.properties");
        } catch (Exception ignored) {}

        File targetFile = null;
        if (filesDirFile.exists()) {
            targetFile = filesDirFile;
        } else if (extFilesDirFile != null && extFilesDirFile.exists()) {
            targetFile = extFilesDirFile;
        }

        if (targetFile != null) {
            Log.i(TAG, "Found credentials.properties file: " + targetFile.getAbsolutePath() + ". Importing...");
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(targetFile)) {
                props.load(fis);
                String sId = firstNonEmpty(props.getProperty("JX_SESSION_ID"), props.getProperty("jagexSessionId"));
                String cId = firstNonEmpty(props.getProperty("JX_CHARACTER_ID"), props.getProperty("characterId"));
                String dName = firstNonEmpty(props.getProperty("JX_DISPLAY_NAME"), props.getProperty("displayName"));
                if (sId != null && !sId.isEmpty()) {
                    sessionId = sId;
                    characterId = cId != null ? cId : "";
                    displayName = dName != null ? dName : "";
                    signedIn = true;
                    prefs().edit()
                        .putBoolean("jx_mode_enabled", true)
                        .putString("jx_session_id", sessionId)
                        .putString("jx_character_id", characterId)
                        .putString("jx_display_name", displayName)
                        .apply();
                    Log.i(TAG, "Successfully imported credentials.properties.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse credentials.properties file", e);
            }
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private void saveManualCredentials() {
        String sId = etSessionId.getText().toString().trim();
        String cId = etCharacterId.getText().toString().trim();
        String dName = etDisplayName.getText().toString().trim();
        boolean enabled = swJxMode.isChecked();
        if (enabled && sId.isEmpty()) {
            Toast.makeText(this, "Session ID is required", Toast.LENGTH_SHORT).show();
            return;
        }
        oauthAccessToken = "";
        oauthRefreshToken = "";
        oauthExpiresAt = 0;
        saveSessionState(enabled ? sId : "", cId, dName);
        manualPanel.setVisibility(View.GONE);
        Toast.makeText(this, enabled ? "Session saved" : "Session cleared", Toast.LENGTH_SHORT).show();
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
            .setTitle("Sign out")
            .setMessage("Sign out of " + (displayName.isEmpty() ? "this account" : displayName) + "?")
            .setPositiveButton("Sign out", (d, w) -> signOut())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void signOut() {
        prefs().edit().clear().apply();
        sessionId = "";
        characterId = "";
        displayName = "";
        signedIn = false;
        File creds = new File(getFilesDir(), "credentials.properties");
        //noinspection ResultOfMethodCallIgnored
        creds.delete();
        if (clientInstance != null) {
            try {
                Method stop = clientClass.getMethod("stop");
                stop.invoke(clientObject);
                Log.i(TAG, "Stopped game client");
            } catch (Exception e) {
                Log.w(TAG, "Could not stop game client: " + e.getMessage());
            }
        }
        Toast.makeText(this, "Signed out. Restarting...", Toast.LENGTH_SHORT).show();
        // Relaunch the app cleanly: schedule a fresh start, then kill this process
        // (the game client's threads can't be reliably torn down in-process).
        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        android.content.Intent restart = new android.content.Intent(this, MainActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0, restart,
            android.app.PendingIntent.FLAG_IMMUTABLE);
        try {
            am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 400, pi);
        } catch (Exception e) {
            Log.w(TAG, "Could not schedule restart: " + e.getMessage());
        }
        Process.killProcess(Process.myPid());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Jagex account login (two-leg OAuth in the system browser)
    // ═══════════════════════════════════════════════════════════════════════
    //
    // Jagex's Cloudflare bot protection blocks embedded WebViews (the UA-CH
    // client hints advertise the "Android WebView" runtime, which no WebView
    // API can change), so the interactive login happens in the device's real
    // browser where it passes. Leg 1's auth code returns via an intent filter
    // on the launcher redirect URL; leg 2's consent id_token returns via the
    // URL fragment of http://localhost, captured by LocalCallbackServer.

    private void startJagexLogin() {
        if (loginActive) return;
        loginActive = true;
        loginStage = LoginStage.LEG1;
        if (!startCallbackServer()) {
            failLogin("Login failed: could not start the local login callback.");
            return;
        }
        leg1Verifier = JagexOAuthClient.generateVerifier();
        String challenge = JagexOAuthClient.createChallenge(leg1Verifier);
        String state = JagexOAuthClient.randomToken(16);
        String nonce = JagexOAuthClient.randomToken(16);
        String url = JagexOAuthClient.buildLauncherAuthorizeUrl(state, nonce, challenge);
        Log.i(TAG, "[1] Opening browser for Jagex authorize (leg 1)");
        tvLoginStatus.setText("Signing in — check your browser");
        loginOverlay.setVisibility(View.VISIBLE);
        openInBrowser(url);
    }

    /** The launcher redirect comes back via the https URL or the "jagex:" scheme. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = intent.getDataString();
        if (url == null) {
            Log.w(TAG, "Redirect intent without a URL");
            return;
        }
        Log.i(TAG, "[intent] " + url);
        if (loginActive && loginStage == LoginStage.LEG1 && url.startsWith("jagex:")) {
            handleLeg1Scheme(url.substring("jagex:".length()));
        } else if (loginActive && loginStage == LoginStage.LEG1
            && url.startsWith(JagexOAuthClient.LAUNCHER_REDIRECT_URI)) {
            handleLeg1Redirect(url);
        } else {
            Log.w(TAG, "Ignoring redirect outside active leg-1 login: stage=" + loginStage);
        }
    }

    /** The launcher page hands the code via the "jagex:code=...,state=..." scheme. */
    private void handleLeg1Scheme(String params) {
        String code = null;
        for (String pair : params.split("[,&]")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = pair.substring(0, eq);
            String v = pair.substring(eq + 1);
            try {
                v = java.net.URLDecoder.decode(v, "UTF-8");
            } catch (Exception ignored) {
            }
            if ("code".equals(k)) {
                code = v;
            }
        }
        if (code == null || code.isEmpty()) {
            failLogin("Login failed: no authorization code in the launcher redirect.");
            return;
        }
        handleLeg1Code(code);
    }

    private void handleLeg1Redirect(String url) {
        if (loginStage != LoginStage.LEG1) {
            Log.w(TAG, "Ignoring leg-1 redirect outside leg 1: stage=" + loginStage);
            return;
        }
        String code = parseUrlParam(url, "code");
        if (code == null || code.isEmpty()) {
            failLogin("Login failed: no authorization code in the redirect.");
            return;
        }
        handleLeg1Code(code);
    }

    private void handleLeg1Code(String code) {
        if (loginStage != LoginStage.LEG1) {
            Log.w(TAG, "Ignoring leg-1 code outside leg 1: stage=" + loginStage);
            return;
        }
        loginStage = LoginStage.EXCHANGING;
        final String verifier = leg1Verifier;
        Log.i(TAG, "[2] Captured leg-1 code (len=" + code.length() + ")");
        new Thread(() -> {
            try {
                JagexOAuthClient.Tokens tokens = JagexOAuthClient.exchangeCode(code, verifier);
                String provider = tokens.idToken != null ? JagexOAuthClient.loginProvider(tokens.idToken) : "";
                Log.i(TAG, "[3] Token exchange OK; login_provider=" + provider);
                if ("runescape".equals(provider)) {
                    failLogin("This is a legacy RuneScape account. RuneLite Mobile only supports Jagex accounts.");
                    return;
                }
                oauthAccessToken = tokens.accessToken != null ? tokens.accessToken : "";
                oauthRefreshToken = tokens.refreshToken != null ? tokens.refreshToken : "";
                oauthExpiresAt = tokens.expiresAtMillis;
                runOnUiThread(MainActivity.this::startConsentLeg);
            } catch (Exception e) {
                Log.e(TAG, "Token exchange failed", e);
                failLogin("Login failed: token exchange error. Check your network and try again.");
            }
        }, "JagexTokenExchange").start();
    }

    /** Start the loopback callback server the consent leg redirects to. */
    private boolean startCallbackServer() {
        stopCallbackServer();
        callbackServer = new LocalCallbackServer(fragment ->
            runOnUiThread(() -> handleConsentFragment(fragment)));
        if (!callbackServer.start()) {
            Log.e(TAG, "Could not bind the localhost consent callback port 80");
            callbackServer = null;
            return false;
        }
        return true;
    }

    private void stopCallbackServer() {
        if (callbackServer != null) {
            callbackServer.stop();
            callbackServer = null;
        }
    }

    private void openInBrowser(String url) {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)));
        } catch (Exception e) {
            Log.e(TAG, "No browser available to open " + url, e);
            failLogin("Login failed: could not open your browser.");
        }
    }

    private void startConsentLeg() {
        loginStage = LoginStage.LEG2;
        leg2State = JagexOAuthClient.randomToken(16);
        leg2Nonce = JagexOAuthClient.randomToken(16);
        String url = JagexOAuthClient.buildConsentAuthorizeUrl(leg2State, leg2Nonce);
        Log.i(TAG, "[4] Opening browser for consent authorize (leg 2)");
        tvLoginStatus.setText("Confirming account — check your browser");
        openInBrowser(url);
    }

    /** Consent id_token arrives in the URL fragment, POSTed back by the callback page. */
    private void handleConsentFragment(String fragment) {
        if (!loginActive || loginStage != LoginStage.LEG2) {
            Log.w(TAG, "Ignoring consent callback outside leg 2: stage=" + loginStage);
            return;
        }
        String state = parsePairs(fragment, "state", true);
        if (leg2State == null || !leg2State.equals(state)) {
            Log.w(TAG, "Consent state mismatch: got=" + state);
            failLogin("Login failed: consent state mismatch. Try again.");
            return;
        }
        String idToken = parsePairs(fragment, "id_token", true);
        if (idToken == null || idToken.isEmpty()) {
            failLogin("Login failed: Jagex did not return a consent token.");
            return;
        }
        loginStage = LoginStage.DONE;
        Log.i(TAG, "[5/6] Captured consent id_token (len=" + idToken.length() + ")");
        new Thread(() -> {
            try {
                String newSessionId = JagexOAuthClient.createSession(idToken);
                List<JagexOAuthClient.Account> accounts = JagexOAuthClient.listAccounts(newSessionId);
                Log.i(TAG, "[7/8] Session created; accounts: " + accounts.size());
                runOnUiThread(() -> onLoginSucceeded(newSessionId, accounts));
            } catch (Exception e) {
                Log.e(TAG, "Session creation failed", e);
                failLogin("Login failed: could not create a game session. Try again.");
            }
        }, "JagexSession").start();
    }

    private void onLoginSucceeded(String newSessionId, List<JagexOAuthClient.Account> accounts) {
        if (accounts.isEmpty()) {
            failLogin("No game characters found on this account.");
            return;
        }
        if (accounts.size() == 1) {
            selectCharacter(newSessionId, accounts.get(0));
            return;
        }
        String[] names = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            JagexOAuthClient.Account account = accounts.get(i);
            names[i] = account.displayName.isEmpty() ? "Character " + account.accountId : account.displayName;
        }
        new AlertDialog.Builder(this)
            .setTitle("Choose a character")
            .setItems(names, (dialog, which) -> selectCharacter(newSessionId, accounts.get(which)))
            .setNegativeButton("Cancel", (d, w) -> cancelLogin())
            .setOnCancelListener(d -> cancelLogin())
            .show();
    }

    private void selectCharacter(String newSessionId, JagexOAuthClient.Account account) {
        loginActive = false;
        loginStage = LoginStage.IDLE;
        loginOverlay.setVisibility(View.GONE);
        saveSessionState(newSessionId, account.accountId, account.displayName);
        Toast.makeText(this, "Signed in", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Login complete. Session len=" + newSessionId.length() + ", character=" + characterId);
    }

    private void cancelLogin() {
        loginActive = false;
        loginStage = LoginStage.IDLE;
        loginOverlay.setVisibility(View.GONE);
        stopCallbackServer();
        tvLoginStatus.setText("");
    }

    private void failLogin(String message) {
        Log.e(TAG, "Login failed: " + message);
        runOnUiThread(() -> {
            if (!loginActive) return;
            loginActive = false;
            loginStage = LoginStage.IDLE;
            loginOverlay.setVisibility(View.GONE);
            tvStatus.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private static String parseUrlParam(String url, String key) {
        int idx = url.indexOf('?');
        if (idx < 0) return null;
        return parsePairs(url.substring(idx + 1), key, false);
    }

    private static String parsePairs(String pairs, String key, boolean fragment) {
        for (String pair : pairs.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            if (k.equals(key)) {
                try {
                    return java.net.URLDecoder.decode(v, "UTF-8");
                } catch (Exception e) {
                    return v;
                }
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Client updates
    // ═══════════════════════════════════════════════════════════════════════

    private void startUpdateCheckAsync() {
        updatePollHandler.removeCallbacks(updatePollTask);
        new Thread(() -> {
            String latest = ClientUpdater.fetchAvailableClientVersion();
            String installed = ClientUpdater.installedClientVersion(this);
            runOnUiThread(() -> {
                updateClientStatus(latest, installed);
                boolean newer = latest != null && installed != null && !installed.equals("unknown")
                    && compareVersions(latest, installed) > 0;
                // Only pop the dialog over the launcher: mid-game it would interrupt play, and
                // the status line already carries the same information for the way back.
                boolean launcherVisible = launcherScroll != null
                    && launcherScroll.getVisibility() == View.VISIBLE;
                if (newer && launcherVisible && !latest.equals(declinedVersion) && !updateInProgress) {
                    showUpdateDialog(latest, installed);
                }
                if (updatePollActive) {
                    updatePollHandler.postDelayed(updatePollTask, UPDATE_POLL_MS);
                }
            });
        }, "UpdateCheck").start();
    }

    /**
     * Launcher status line for the client-update state -- the guide through a weekly OSRS
     * update, when the game can refuse the installed client until the new one lands. While
     * RuneLite/CI have not published yet the line says so and keeps retrying; as soon as a
     * newer client exists it becomes a one-tap install.
     */
    private void updateClientStatus(String latest, String installed) {
        if (tvClientStatus == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        if (latest == null) {
            tvClientStatus.setText("Can't reach the update server"
                + (lastVersionCheckStamp == null ? "" : " (last checked " + lastVersionCheckStamp + ")")
                + "\nIf the game says the client is out of date, a new build is on its way -- retrying every 2 min.");
            tvClientStatus.setTextColor(UiTheme.RED);
            tvClientStatus.setBackground(UiTheme.rounded(0x22E57373, UiTheme.RED, 1f, 8f, density));
            tvClientStatus.setOnClickListener(null);
            return;
        }
        lastVersionCheckStamp = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(new java.util.Date());
        int cmp = compareVersions(latest, installed);
        String from = installed == null || installed.equals("unknown") ? "installed" : installed;
        if (cmp > 0) {
            tvClientStatus.setText("Update available: v" + from + " -> v" + latest
                + " (checked " + lastVersionCheckStamp + ")\nTap to download and install now.");
            tvClientStatus.setTextColor(0xFFFFD75E);
            tvClientStatus.setBackground(UiTheme.rounded(0x22FFD75E, 0xFFFFD75E, 1f, 8f, density));
            tvClientStatus.setOnClickListener(v -> showUpdateDialog(latest, installed));
        } else if (cmp < 0) {
            tvClientStatus.setText("Client v" + installed + " is newer than the published v" + latest
                + " -- nothing to do (checked " + lastVersionCheckStamp + ").");
            tvClientStatus.setTextColor(UiTheme.TEXT_MUTED);
            tvClientStatus.setBackground(null);
            tvClientStatus.setOnClickListener(null);
        } else {
            tvClientStatus.setText("Client v" + installed + " is up to date (checked "
                + lastVersionCheckStamp + ").");
            tvClientStatus.setTextColor(UiTheme.TEXT_DIM);
            tvClientStatus.setBackground(null);
            tvClientStatus.setOnClickListener(null);
        }
    }

    private void checkForUpdates(boolean manual) {
        tvUpdateText.setVisibility(View.VISIBLE);
        tvUpdateText.setText("Checking for updates...");
        new Thread(() -> {
            String latest = ClientUpdater.fetchAvailableClientVersion();
            String installed = ClientUpdater.installedClientVersion(this);
            runOnUiThread(() -> {
                if (latest == null) {
                    tvUpdateText.setText("Could not check for updates (network error).");
                    return;
                }
                tvUpdateText.setText("");
                tvUpdateText.setVisibility(View.GONE);
                updateClientStatus(latest, installed);
                int cmp = compareVersions(latest, installed);
                if (cmp == 0) {
                    Toast.makeText(this, "Client is up to date (v" + installed + ")", Toast.LENGTH_SHORT).show();
                } else if (cmp < 0) {
                    // The published release is older than what is installed (a rollback, or a
                    // hand-installed client): never offer that as an update.
                    Toast.makeText(this, "Installed client v" + installed + " is newer than the published v"
                        + latest + " -- nothing to do", Toast.LENGTH_LONG).show();
                } else {
                    showUpdateDialog(latest, installed);
                }
            });
        }, "UpdateCheck").start();
    }

    private void showUpdateDialog(String latest, String installed) {
        new AlertDialog.Builder(this)
            .setTitle("RuneLite client update available")
            .setMessage("A new game client is available (v" + installed + " -> v" + latest + ").\n\n"
                + "Download and install it now? (~6 MB, takes seconds.) "
                + "The old client keeps working if you decline -- but after a weekly OSRS "
                + "update the game may refuse it until this is installed.")
            .setPositiveButton("Update now", (d, w) -> runClientUpdate(latest))
            .setNegativeButton("Later", (d, w) -> declinedVersion = latest)
            .setCancelable(true)
            .show();
    }

    private void runClientUpdate(String version) {
        updateProgress.setVisibility(View.VISIBLE);
        updateProgress.setMax(100);
        updateProgress.setProgress(0);
        tvUpdateText.setVisibility(View.VISIBLE);
        tvUpdateText.setText("Downloading update...");
        btnCheckUpdates.setEnabled(false);
        updateInProgress = true;
        new Thread(() -> {
            try {
                ClientUpdater.downloadAndInstall(this, version, (stage, percent, etaMillis) -> runOnUiThread(() -> {
                    updateProgress.setProgress(percent);
                    tvUpdateText.setText(stage + "\n" + percent + "% · " + formatEta(etaMillis));
                }));
                runOnUiThread(() -> {
                    updateProgress.setVisibility(View.GONE);
                    tvUpdateText.setVisibility(View.GONE);
                    btnCheckUpdates.setEnabled(true);
                    updateInProgress = false;
                    declinedVersion = null;
                    updateLauncherUi();
                    startUpdateCheckAsync();   // the status line now reads "up to date"
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Client updated")
                        .setMessage("RuneLite client updated to v" + version + ". Restart the game now to use it?")
                        .setPositiveButton("Restart now", (d, w) -> restartClient())
                        .setNegativeButton("Not now", null)
                        .show();
                });
            } catch (Throwable e) {
                Log.e(TAG, "Client update failed", e);
                runOnUiThread(() -> {
                    updateProgress.setVisibility(View.GONE);
                    tvUpdateText.setText("Update failed: " + e.getMessage());
                    btnCheckUpdates.setEnabled(true);
                    updateInProgress = false;
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Update failed")
                        .setMessage("The update could not be installed:\n" + e.getMessage()
                            + "\n\nThe old client is still installed. You can try again, or if the game stops "
                            + "connecting after a weekly OSRS update, update the app instead.")
                        .setPositiveButton("Try again", (d, w) -> runClientUpdate(version))
                        .setNegativeButton("OK", null)
                        .show();
                });
            }
        }, "ClientUpdate").start();
    }

    private static String formatEta(long etaMillis) {
        if (etaMillis <= 0) return "< 1 min";
        long seconds = (etaMillis + 999) / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long rem = seconds % 60;
        return (minutes < 60 ? "~" + minutes + "m" : (minutes / 60) + "h " + (minutes % 60) + "m")
            + (rem > 0 ? " " + rem + "s" : "") + " left";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Game bootstrap
    // ═══════════════════════════════════════════════════════════════════════

    private void onPlayClicked() {
        if (!signedIn || sessionId.isEmpty()) {
            Toast.makeText(this, "Sign in with a Jagex account first", Toast.LENGTH_SHORT).show();
            startJagexLogin();
            return;
        }
        launchGame();
    }

    private void launchGame() {
        launcherScroll.setVisibility(View.GONE);
        // The right-edge column (and with it the keyboard toggle) comes back here.
        sidePanel.setAvailable(true);
        firstFramePresented = false;   // a restarted/resumed client hides the overlay again
        bootOverlay.setVisibility(View.VISIBLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tvStatus.setText("Starting game...");
        new Thread(this::bootstrapGameClient, "GameClientBootstrapper").start();
    }

    /**
     * Returns to the launcher screen: the drawer header's ⌂ button, and the path a
     * failed boot takes. Hides the right-edge chrome as well -- the drawer must not
     * float over the launcher.
     */
    private void showLauncher() {
        launcherScroll.setVisibility(View.VISIBLE);
        kbBar.setVisibility(View.GONE);
        bootOverlay.setVisibility(View.GONE);
        sidePanel.setAvailable(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateLauncherUi();
    }

    /**
     * Insets the game surface and the keyboard bar by the right-edge chrome width.
     *
     * <p>Two modes (the launcher's Display card): <b>overlap</b>, the drawer floats over
     * a full-width game (that is the mode's point -- the resizable layout's minimap and
     * tab panel live under the drawer), and <b>resize</b>, today's behaviour, where the
     * surface is inset and the client re-renders at the smaller size. The keyboard bar
     * sits under the drawer in both modes, so its action pills stay clear of the chrome
     * either way -- it is chrome, not game.
     */
    private void applyGameInsets() {
        int chrome = sidePanel != null ? sidePanel.occupiedWidthPx() : 0;
        insetRight(surfaceView, sidebarOverlap ? 0 : chrome);
        insetRight(kbBar, chrome);
    }

    private static void insetRight(View view, int px) {
        if (view == null) {
            return;
        }
        android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) {
            return;   // both callers are rootLayout children
        }
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
        if (flp.rightMargin == px) {
            return;
        }
        flp.rightMargin = px;
        view.setLayoutParams(flp);
    }

    private void hideBootOverlay() {
        if (bootOverlay != null) {
            bootOverlay.setVisibility(View.GONE);
        }
    }

    /**
     * Stops the running client (if any) and boots a fresh one. Used after a
     * client update or character switch.
     */
    private void restartClient() {
        if (clientInstance != null) {
            try {
                Method stop = clientClass.getMethod("stop");
                stop.invoke(clientObject);
            } catch (Exception e) {
                Log.w(TAG, "Could not stop old client: " + e.getMessage());
            }
            clientInstance = null;
            clientObject = null;
            clientClass = null;
        }
        launchGame();
    }

    private void bootstrapGameClient() {
        try {
            if (clientInstance != null) {
                Log.i(TAG, "Client already running globally, skipping bootstrap");
                updateStatus("RUNNING: Injected Client Active!");
                runOnUiThread(() -> Toast.makeText(this, "Game client resumed", Toast.LENGTH_SHORT).show());
                return;
            }

            // ── Step 0: Emulate the desktop JVM environment the client expects ──
            // Truthful fingerprint: the OS stays Android's real "Linux"/arch/kernel.
            // Only properties Android doesn't provide are supplied:
            //  - user.home/jagex.userhome: writable app dir (client writes cache + credentials here)
            //  - java.version/vendor: the client was compiled for a Java 11 desktop JVM and
            //    misparses Android's "0" version string
            System.setProperty("user.home", getFilesDir().getAbsolutePath());
            System.setProperty("jagex.userhome", getFilesDir().getAbsolutePath());
            System.setProperty("java.vendor", "Eclipse Adoptium");
            System.setProperty("java.version", "11.0.22");
            Log.i(TAG, "JVM environment emulated (truthful OS fingerprint, no spoofing)");

            // ── Step 1: Apply Jagex Launcher session tokens ──
            if (signedIn && !sessionId.isEmpty()) {
                updateStatus("Applying Jagex Account session...");
                try {
                    // Android's Os.setenv() updates the native C environ, which
                    // System.getenv() re-reads (b/201665416).
                    android.system.Os.setenv("JX_SESSION_ID", sessionId, true);
                    android.system.Os.setenv("JX_CHARACTER_ID", characterId, true);
                    android.system.Os.setenv("JX_DISPLAY_NAME", displayName, true);
                    System.setProperty("JX_SESSION_ID", sessionId);
                    System.setProperty("JX_CHARACTER_ID", characterId);
                    System.setProperty("JX_DISPLAY_NAME", displayName);
                    // Legacy-path vars must NOT be set (client takes the wrong login branch)
                    android.system.Os.unsetenv("JX_ACCESS_TOKEN");
                    android.system.Os.unsetenv("JX_REFRESH_TOKEN");
                    Log.i(TAG, "Jagex Account environment applied; JX_SESSION_ID len=" + sessionId.length());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to apply env tokens", e);
                }
            }

            // ── Step 2: Fetch and parse jav_config.ws parameters ──
            updateStatus("Parsing Jagex jav_config.ws...");
            URL configUrl = new URL("https://oldschool.runescape.com/jav_config.ws");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(configUrl.openStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("param=")) {
                        String[] parts = line.substring(6).split("=", 2);
                        if (parts.length == 2) {
                            appletParameters.put(parts[0], parts[1]);
                        }
                    } else {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            if (parts[0].equals("codebase")) {
                                codebase = parts[1];
                            }
                            appletParameters.put(parts[0], parts[1]);
                        }
                    }
                }
            }
            Log.i(TAG, "Parsed " + appletParameters.size() + " parameters. Codebase: " + codebase);

            // ── Step 3: Locate the dexed client ──
            // Prefer the files-dir copy (runtime-updated) unless the APK asset is newer.
            updateStatus("Loading dexed injected client...");
            File localJarFile = new File(getFilesDir(), ClientUpdater.DEX_ASSET_NAME);
            long apkTime = 0;
            try {
                apkTime = new File(getPackageCodePath()).lastModified();
            } catch (Exception ignored) {}
            boolean useExisting = localJarFile.exists() && localJarFile.length() > 0
                && !versionIsOlderThanAsset()
                && localJarFile.lastModified() >= apkTime;
            if (!useExisting) {
                if (localJarFile.exists() && !localJarFile.delete()) {
                    Log.w(TAG, "Failed to delete stale dex JAR");
                }
                try (InputStream is = getAssets().open(ClientUpdater.DEX_ASSET_NAME);
                     FileOutputStream os = new FileOutputStream(localJarFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
                // Keep the copy's timestamp at the APK's install time. The bytes are identical
                // to the asset, so ART keeps using the `speed` odex (its class-loader context
                // is keyed to the dex checksum) -- but ClientUpdater.clientDexAotStatus()
                // compares mtimes, and a copy stamped "now" would make a perfectly good odex
                // report as stale on the launcher after every Play.
                if (apkTime > 0) {
                    localJarFile.setLastModified(apkTime);
                }
                // The files-dir jar is now the asset build, so record that version: otherwise
                // the launcher keeps reporting the old one, re-copies the asset on every boot,
                // and the update check nags for a client that is already installed.
                try (FileOutputStream vf = new FileOutputStream(new File(getFilesDir(), ClientUpdater.VERSION_FILE_NAME))) {
                    vf.write(ClientUpdater.currentClientVersion(this).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception e) {
                    Log.w(TAG, "Could not record the asset client version", e);
                }
            }
            Log.i(TAG, "Using dexed client: " + localJarFile.getAbsolutePath() + " (" + localJarFile.length() + " bytes)");

            // ART refuses to load dex files that are writable by others
            // ("Writable dex file ... is not allowed").
            localJarFile.setWritable(true, true);
            localJarFile.setReadOnly();

            // ── Step 4: Initialize DexClassLoader ──
            File dexOutputDir = getDir("dex", MODE_PRIVATE);
            DexClassLoader dexClassLoader = new DexClassLoader(
                localJarFile.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                getClassLoader()
            );

            // ── Step 5: Instantiate the OSRS client ──
            updateStatus("Instantiating OSRS injected client...");
            clientClass = dexClassLoader.loadClass("client");
            org.runelite.mobile.TileCompositor.setClassLoader(dexClassLoader);
            clientObject = clientClass.getDeclaredConstructor().newInstance();
            clientInstance = (java.awt.Component) clientObject;
            clientInstance.setSize(GAME_W, GAME_H);
            Log.i(TAG, "Client instantiated: " + clientClass.getName() + " extends " + clientClass.getSuperclass().getName());

            // ── Step 6: Bind ClientConfiguration via the public GameEngine interface ──
            // (net.runelite.api.GameEngine.setConfiguration) - no obfuscated field names.
            updateStatus("Binding client configuration...");
            Class<?> configInterface = dexClassLoader.loadClass("net.runelite.api.ClientConfiguration");
            final URL codebaseUrl;
            try {
                codebaseUrl = new URL(codebase);
            } catch (Exception e) {
                throw new RuntimeException("Invalid codebase URL: " + codebase, e);
            }

            Object configProxy = Proxy.newProxyInstance(
                dexClassLoader,
                new Class<?>[]{configInterface},
                (Object proxy, Method method, Object[] args) -> {
                    String methodName = method.getName();
                    try {
                        switch (methodName) {
                            case "getCodeBase":
                                return codebaseUrl;
                            case "getParameter": {
                                String paramName = (String) args[0];
                                String value = appletParameters.get(paramName);
                                Log.d(TAG, "ClientConfig.getParameter(" + paramName + ") -> "
                                    + (value != null ? value.substring(0, Math.min(value.length(), 40)) : "null"));
                                return value;
                            }
                            case "onError":
                                Log.e(TAG, "ClientConfig.onError: " + args[0]);
                                return null;
                            case "toString":
                                return "MobileClientConfiguration";
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                Log.w(TAG, "ClientConfig: unhandled method: " + methodName);
                                return null;
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "ClientConfig." + methodName + " failed", t);
                        return defaultValue(method.getReturnType());
                    }
                }
            );

            Class<?> gameEngineClass = dexClassLoader.loadClass("net.runelite.api.GameEngine");
            Method setConfigurationMethod = gameEngineClass.getMethod("setConfiguration", configInterface);
            setConfigurationMethod.invoke(clientObject, configProxy);
            Log.i(TAG, "ClientConfiguration bound via GameEngine.setConfiguration");

            // ── Step 6.5: Bind Callbacks proxy by TYPE (obfuscation-proof) ──
            // The injected client hands the runtime its rendered frame via
            // callbacks.draw(MainBufferProvider, Graphics, x, y) - we blit it into
            // the applet graphics here (this is what desktop RuneLite's runtime does).
            // Mouse hooks return the (possibly plugin-modified) event; pass through.
            Class<?> callbacksInterface = dexClassLoader.loadClass("net.runelite.api.hooks.Callbacks");
            Object callbacksProxy = Proxy.newProxyInstance(
                dexClassLoader,
                new Class<?>[]{callbacksInterface},
                (Object proxy, Method method, Object[] args) -> {
                    String methodName = method.getName();
                    Class<?> returnType = method.getReturnType();
                    try {
                        switch (methodName) {
                            case "error":
                                Log.e(TAG, "Callbacks.error: " + args[0], (Throwable) args[1]);
                                return null;
                            case "draw": {
                                // Callbacks declares two `draw` methods:
                                //   boolean draw(Renderable, boolean)                    -> "may the client draw it?"
                                //   void    draw(MainBufferProvider, Graphics, int, int) -> the frame blit below
                                // The client draws every world actor (mesh, ground shadow, spot-anims,
                                // overhead name/level, hint arrow) inside `if (callbacks.draw(actor, true))`,
                                // so any non-true return suppresses all world entities. Match on the API
                                // signature, not on argument classes.
                                if (method.getParameterCount() == 2 && method.getParameterTypes()[1] == boolean.class) {
                                    Object hostHooks = RuneLiteHost.hooks();
                                    if (hostHooks != null) {
                                        // RenderCallbackManager.addEntity(): lets plugins veto/observe
                                        // entity rendering. Hooks answers true unless a plugin says no.
                                        Object renderable = args.length > 0 ? args[0] : null;
                                        boolean allowed = Boolean.TRUE.equals(
                                            hooksMethod(hostHooks, method).invoke(hostHooks, args));
                                        countEntityDraw(renderable, allowed);
                                        return allowed;
                                    }
                                    if (!loggedRenderableDraw) {
                                        loggedRenderableDraw = true;
                                        Log.i(TAG, "Callbacks.draw(Renderable,boolean) -> true");
                                    }
                                    countEntityDraw(args.length > 0 ? args[0] : null, true);
                                    return Boolean.TRUE;
                                }
                                // Frame blit: copy the rendered game buffer into the graphics
                                if (args.length >= 2 && args[0] != null && args[1] instanceof java.awt.Graphics) {
                                    try {
                                        Object provider = args[0];
                                        bufferProviderStatic = provider;
                                        // Display mode + canvas size are the client's own state;
                                        // both are applied here, on the client thread between two
                                        // frames, so neither can race the renderer. Order matters:
                                        // the display mode decides whether the client clamps the
                                        // canvas to 765x503.
                                        boolean frameChanged = false;
                                        if (!displaySetupDone) {
                                            displaySetupDone = true;
                                            setupClientDisplay();
                                            frameChanged = applyPendingFrameSize();
                                        } else {
                                            frameChanged = applyPendingFrameSize();
                                        }
                                        // The client's frame buffer is the authority on the frame
                                        // size. A size the host did not ask for (the client resized
                                        // itself) is adopted here; the Graphics this frame drew
                                        // through still targets the previous array, so that frame is
                                        // not published and the next one -- which fetches a Graphics
                                        // over the new array -- is. A frame that just resized is
                                        // exempt: the provider in flight is the *old* buffer by
                                        // construction.
                                        boolean publish = !frameChanged;
                                        int[] psz = bufferProviderSize(provider);
                                        if (!frameChanged && psz != null
                                            && (psz[0] != frameW || psz[1] != frameH)) {
                                            publishFrameSize(psz[0], psz[1]);
                                            invalidateProviderGraphics(provider);
                                            publish = false;
                                        }
                                        // Bind the rasterizer only while the buffer in flight is the
                                        // published one. On a resize frame the provider is the
                                        // pre-resize buffer: re-binding it re-points the rasterizer's
                                        // clip at the old size, while the client's own resize path
                                        // has already moved the target array to the new buffer, and
                                        // the next scene paint then writes past its end
                                        // (ArrayIndexOutOfBoundsException out of yw.fb -- the client
                                        // thread dies in its own error path and the world freezes).
                                        // The client's resize path leaves the rasterizer consistent,
                                        // so the resize frame needs no bind; the next frame binds the
                                        // new buffer.
                                        if (psz != null && psz[0] == frameW && psz[1] == frameH) {
                                            bindSceneRasterizerToDisplay(provider);
                                        }
                                        Object image = provider.getClass().getMethod("getImage").invoke(provider);
                                        if (image instanceof java.awt.Image) {
                                            java.awt.Image img = (java.awt.Image) image;
                                            logFrameDiagnostics(img);
                                            Object hostHooks = RuneLiteHost.hooks();
                                            long blitStart = System.nanoTime();
                                            synchronized (renderLock) {
                                                if (hostHooks != null) {
                                                    // RuneLite's Hooks renders the plugin overlays into the
                                                    // client's own frame image (mainBufferProvider.getImage())
                                                    // and then blits that image into args[1] -- which is the
                                                    // Graphics over appletPixels, the buffer the render thread
                                                    // presents. Delegating is what puts plugins on screen; the
                                                    // lock/frameSeq handshake stays here so the render thread
                                                    // still presents exactly one client frame per draw.
                                                    hooksMethod(hostHooks, method).invoke(hostHooks, args);
                                                } else {
                                                    ((java.awt.Graphics) args[1]).drawImage(img, 0, 0, null);
                                                }
                                                if (publish) {
                                                    // Hand the completed frame to the render thread
                                                    // (it waits for frameSeq to change).
                                                    frameSeq++;
                                                    renderLock.notifyAll();
                                                }
                                            }
                                            if (publish && !firstFramePresented) {
                                                // A frame reached the surface: the boot overlay's job is done.
                                                firstFramePresented = true;
                                                runOnUiThread(MainActivity.this::hideBootOverlay);
                                            }
                                            lastBlitNanos = System.nanoTime() - blitStart;
                                        }
                                    } catch (Throwable e) {
                                        Log.w(TAG, "callbacks.draw failed", e);
                                    }
                                }
                                return returnType.equals(boolean.class) ? Boolean.FALSE : null;
                            }
                            case "mousePressed":
                            case "mouseReleased":
                            case "mouseClicked":
                            case "mouseMoved":
                            case "mouseDragged":
                            case "mouseEntered":
                            case "mouseExited":
                            case "mouseWheelMoved":
                            case "keyPressed":
                            case "keyReleased":
                            case "keyTyped":
                                // RuneLite's Hooks run MouseManager/KeyManager (plugin input
                                // hooks) and return the event the client should process.
                                Object hostHooks = RuneLiteHost.hooks();
                                if (hostHooks != null) {
                                    return hooksMethod(hostHooks, method).invoke(hostHooks, args);
                                }
                                return args[0];
                            case "drawInterface": {
                                // Which interface the client is drawing, and how many
                                // ABOVE_WIDGETS overlays are registered for it: that layer is
                                // rendered from renderAfterInterface, so an overlay only
                                // appears when its interface is drawn.
                                if (args.length >= 1 && args[0] instanceof Integer) {
                                    lastInterfaceDrawn = (Integer) args[0];
                                    lastInterfaceOverlays = interfaceOverlayCount(lastInterfaceDrawn);
                                }
                                Object interfaceHooks = RuneLiteHost.hooks();
                                if (interfaceHooks != null) {
                                    return hooksMethod(interfaceHooks, method).invoke(interfaceHooks, args);
                                }
                                return null;
                            }
                            case "post":
                            case "postDeferred":
                            case "tick":
                            case "tickEnd":
                            case "frame":
                            case "serverTick":
                            case "drawScene":
                            case "drawAboveOverheads":
                            case "drawLayer":
                                // Event-bus delivery and the overlay render passes. Delegating is
                                // what makes plugins see game events and draw on every layer.
                                // `post` and `postDeferred` both reach the bus (deferred ones at
                                // the end of the client tick), so both are counted: the
                                // conformance run needs to tell "the plugin subscribes but the
                                // event never fires" from "the subscriber never got registered".
                                // What this cannot see are the events RuneLite's own Hooks posts
                                // straight to the bus (GameTick/BeforeRender are built inside
                                // Hooks, never through this proxy).
                                if (("post".equals(methodName) || "postDeferred".equals(methodName))
                                    && args.length > 0 && args[0] != null) {
                                    countEventPost(args[0].getClass().getName());
                                }
                                Object eventHooks = RuneLiteHost.hooks();
                                if (eventHooks != null) {
                                    return hooksMethod(eventHooks, method).invoke(eventHooks, args);
                                }
                                return returnType.equals(boolean.class) ? Boolean.FALSE : null;
                            case "isRuneLiteClientOutdated":
                                return Boolean.FALSE;
                            case "openUrl":
                                if (args[0] instanceof String && java.awt.Desktop.openUrlHandler != null) {
                                    java.awt.Desktop.openUrlHandler.accept((String) args[0]);
                                }
                                return null;
                            default:
                                if (returnType.equals(boolean.class)) {
                                    return false;
                                }
                                if (returnType.isPrimitive()) {
                                    return 0;
                                }
                                return null;
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "callbacks." + methodName + " failed", t);
                        return defaultValue(returnType);
                    }
                }
            );

            Field callbacksField = findFieldByType(clientClass, "net.runelite.api.hooks.Callbacks");
            if (callbacksField != null) {
                callbacksField.setAccessible(true);
                callbacksField.set(clientObject, callbacksProxy);
                Log.i(TAG, "Callbacks proxy bound (field " + callbacksField.getName() + ")");
            } else {
                Log.e(TAG, "Could not find a Callbacks field on the client class!");
            }

            // ── Step 6.6: Inject the ScheduledExecutorService the game expects ──            // The game reads client.tk (ScheduledExecutorService) to run the
            // post-terms-accept login task (mw.af). Desktop RuneLite populates it
            // via Guice injectMembers(); here we set it by type, obfuscation-proof.
            Field executorField = findFieldByType(clientClass, "java.util.concurrent.ScheduledExecutorService");
            if (executorField != null) {
                executorField.setAccessible(true);
                executorField.set(clientObject, java.util.concurrent.Executors.newScheduledThreadPool(1));
                Log.i(TAG, "ScheduledExecutorService injected into client field " + executorField.getName());
            } else {
                Log.e(TAG, "Could not find ScheduledExecutorService field on the client class!");
            }

            // ── Step 6.7: Install the OtlTokenRequester (one-time-login token) ──
            // The game's login flow (client.hv) checks client.qs; if it is null it
            // skips the OTL fetch and sends the /play login request with no token,
            // which the server rejects with HTTP 400 ("Failed to login"). Desktop
            // RuneLite injects a requester that POSTs the JX session to
            // auth.jagex.com/game-session/v1/tokens and returns the OTL token.
            installOtlTokenRequester();
    // ── Step 7: Initialize and start the game client ──
            // GameEngine.initialize() calls setSize(GAME_FIXED_SIZE) + init() + start()
            updateStatus("Initializing game client...");
            Method initializeMethod = gameEngineClass.getMethod("initialize");
            initializeMethod.invoke(clientObject);
            Log.i(TAG, "client.initialize() completed");

            // Unlock the client's frame pacing so it presents at FPS_TARGET
            // instead of once per 20 ms catch-up batch (the default clock's
            // `mo.xg` sets `mo.bd` once per up-to-10-cycle run). Order matters:
            // setUnlockedFps first, setUnlockedFpsTarget second (turning
            // unlocked fps off clears the target).
            try {
                Class<?> clientIface = dexClassLoader.loadClass("net.runelite.api.Client");
                clientIface.getMethod("setUnlockedFps", boolean.class).invoke(clientObject, true);
                clientIface.getMethod("setUnlockedFpsTarget", int.class).invoke(clientObject, FPS_TARGET);
                Log.i(TAG, "unlocked fps target=" + FPS_TARGET);
            } catch (Throwable t) {
                Log.w(TAG, "unlocked fps unavailable", t);
            }

            // The client frame geometry -- the display mode and the canvas size -- is
            // applied on the client thread at the first frame (see the callbacks.draw
            // handler): the client is between two frames there, so the display-mode
            // flip, the RuneLite ResizeableChanged post and the canvas resize cannot
            // race the renderer.
            displaySetupDone = false;
            requestGeometry();

            updateStatus("RUNNING: Injected Client Active!");

            // ── Step 8: Start the RuneLite runtime on top of the live client ──
            // The plugin API (PluginManager, EventBus, ConfigManager, OverlayManager and
            // the Hooks Callbacks implementation the proxy above delegates to) lives in
            // the same asset dex. It boots on its own thread because it performs one
            // blocking HTTP fetch (runelite.config) before handing the plugin lifecycle
            // to the UI thread.
            RuneLiteHost.setClientVersion(ClientUpdater.installedClientVersion(this));
            Thread hostThread = new Thread(
                () -> RuneLiteHost.start(clientObject, dexClassLoader), "RuneLiteHost");
            hostThread.setDaemon(true);
            hostThread.start();

            runOnUiThread(() -> Toast.makeText(this, "Game client initialized!", Toast.LENGTH_LONG).show());
        } catch (Throwable e) {
            Log.e(TAG, "Loader failed to bootstrap client", e);
            String msg = e.getMessage();
            if (e.getCause() != null) {
                msg += " | Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
            }
            final String finalMsg = msg;
            updateStatus("FAILED: " + e.getClass().getSimpleName() + " - " + msg);
            runOnUiThread(() -> {
                tvStatus.setText("Game failed to start: " + finalMsg);
                showLauncher();
                Toast.makeText(this, "Loader Error: " + finalMsg, Toast.LENGTH_LONG).show();
            });
        }
    }


            /**
     * The game expects the runtime to supply an OtlTokenRequester that turns the
     * JX session into a one-time login token for the /play login request. We
     * implement it against auth.jagex.com/game-session/v1/tokens (verified: the
     * endpoint returns HTTP 200 {"token": "..."} for a fresh session).
     */
    private void installOtlTokenRequester() {
        try {
            ClassLoader cl = clientClass.getClassLoader();
            Class<?> requesterIface = cl.loadClass("com.jagex.oldscape.pub.OtlTokenRequester");
            Class<?> responseIface = cl.loadClass("com.jagex.oldscape.pub.OtlTokenResponse");
            final Method isSuccess = responseIface.getMethod("isSuccess");
            final Method getToken = responseIface.getMethod("getToken");

            Object requester = Proxy.newProxyInstance(cl, new Class<?>[]{requesterIface},
                (Object proxy, Method method, Object[] args) -> {
                    if (!method.getName().equals("request") || args == null || args.length < 4) {
                        return null;
                    }
                    final java.net.URL playUrl = (java.net.URL) args[1];
                    @SuppressWarnings("unchecked")
                    final java.util.Map<String, String> headers = (java.util.Map<String, String>) args[2];
                    Log.d(TAG, "OTL request() invoked: url=" + playUrl + " arg0=" + args[0]
                        + " headers=" + headers + " body=" + args[3]);
                    return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            String token = fetchOtlToken(playUrl, headers);
                            if (token == null) {
                                Log.w(TAG, "OTL request failed");
                                return responseProxy(responseIface, isSuccess, getToken, false, null);
                            }
                            Log.i(TAG, "OTL token obtained (" + token.length() + " chars)");
                            return responseProxy(responseIface, isSuccess, getToken, true, token);
                        } catch (Throwable e) {
                            Log.w(TAG, "OTL request error", e);
                            return responseProxy(responseIface, isSuccess, getToken, false, null);
                        }
                    }, java.util.concurrent.Executors.newSingleThreadExecutor());
                });

            // Find the setter by parameter type (obfuscation-proof).
            Method setter = null;
            for (Method m : clientClass.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == requesterIface) {
                    setter = m;
                    break;
                }
            }
            if (setter == null) {
                Log.e(TAG, "Could not find OtlTokenRequester setter on client");
                return;
            }
            setter.invoke(clientObject, requester);
            Log.i(TAG, "OtlTokenRequester installed via " + setter.getName());
        } catch (Throwable e) {
            Log.w(TAG, "OtlTokenRequester install failed: " + e.getMessage());
        }
    }

    private String fetchOtlToken(java.net.URL playUrl, java.util.Map<String, String> headers) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL otlUrl = new java.net.URL(playUrl.getProtocol(), playUrl.getHost(),
                playUrl.getPort(), "/game-session/v1/tokens");
            conn = (java.net.HttpURLConnection) otlUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + sessionId);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            String body = new org.json.JSONObject().put("accountId", characterId).toString();
            conn.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "OTL endpoint HTTP " + code);
                return null;
            }
            java.io.InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            org.json.JSONObject resp = new org.json.JSONObject(out.toString("UTF-8"));
            return resp.optString("token", null);
        } catch (Throwable e) {
            Log.w(TAG, "OTL HTTP error", e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Object responseProxy(Class<?> iface, Method isSuccess, Method getToken,
                                 boolean success, String token) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
            (Object proxy, Method method, Object[] args) -> {
                if (method.equals(isSuccess)) {
                    return success;
                }
                if (method.equals(getToken)) {
                    return token;
                }
                return null;
            });
    }

    private Field findFieldByType(Class<?> clazz, String typeName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().getName().equals(typeName)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /** Default value for a proxy-invocation return type (proxy methods must never throw). */
    private static Object defaultValue(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t.isPrimitive()) return 0;
        return null;
    }

    /** True when the APK-bundled client version is newer than the files-dir one. */
    private boolean versionIsOlderThanAsset() {
        String installed = ClientUpdater.installedClientVersion(this);
        String asset = ClientUpdater.currentClientVersion(this);
        if (installed.equals("unknown") || asset.equals("unknown") || installed.equals(asset)) {
            return false;
        }
        return compareVersions(installed, asset) < 0;
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parseInt(pa[i]) : 0;
            int vb = i < pb.length ? parseInt(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateStatus(String status) {
        Log.i(TAG, "Status Update: " + status);
        runOnUiThread(() -> {
            if (tvStatus != null && loginStage == LoginStage.IDLE) {
                tvStatus.setText(status);
            }
            if (tvBootStatus != null) {
                tvBootStatus.setText(status);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Touch input -> game client listeners
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchInput() {
        surfaceView.setOnTouchListener((v, event) -> {
            if (clientInstance == null) {
                return true;
            }
            int action = event.getActionMasked();
            long when = System.currentTimeMillis();
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    if (suppressUntilUp) {
                        break;
                    }
                    int x = toGameX(event.getX());
                    int y = toGameY(event.getY());
                    // A mouse always moves before it presses. The client's own
                    // menus (the world list) pick the row from the *hover*
                    // position, so without this move a tap acts on wherever the
                    // previous gesture left the cursor.
                    lastMouseX = x;
                    lastMouseY = y;
                    lastMouseWhen = when;
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_MOVED, x, y, when);
                    // No left-button event is sent yet. The gesture decides which one it
                    // is: a quick drag rotates the camera, a slow drag becomes a
                    // left-button drag (interface items), a tap sends press+release+click
                    // on lift, and a still finger held past LONG_PRESS_MS is a right click.
                    // Deferring the press is what makes the long press possible at all --
                    // the client acts on mouse *down*, so an early press would walk or
                    // attack before any long-press timer could fire.
                    pointerDown = false;
                    oneFingerDrag = false;
                    touchDownViewX = event.getX();
                    touchDownViewY = event.getY();
                    touchDownX = x;
                    touchDownY = y;
                    touchDownWhen = when;
                    scheduleLongPress();
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (suppressUntilUp) {
                        break;
                    }
                    if (event.getPointerCount() >= 2) {
                        handleTwoFingerMove(event, when);
                        break;
                    }
                    if (oneFingerDrag) {
                        // Camera rotate (the official mobile client's one-finger drag):
                        // feed the client's own middle-button drag path.
                        for (int i = 0; i < event.getHistorySize(); i++) {
                            emitSegment(java.awt.event.MouseEvent.MOUSE_DRAGGED,
                                toGameX(event.getHistoricalX(i)),
                                toGameY(event.getHistoricalY(i)),
                                wallFor(event, event.getHistoricalEventTime(i)),
                                java.awt.event.MouseEvent.BUTTON2);
                        }
                        emitSegment(java.awt.event.MouseEvent.MOUSE_DRAGGED,
                            toGameX(event.getX()), toGameY(event.getY()), when,
                            java.awt.event.MouseEvent.BUTTON2);
                        break;
                    }
                    // Any movement past the rotate threshold is the camera, whenever it
                    // happens: an earlier version also required it to arrive inside a
                    // 250 ms window and turned later movement into a left-button drag
                    // instead, which stole slow rotations (the first MOVE of a fast drag
                    // can arrive after the window, because the app's UI thread is busy
                    // rendering the game) and walked the character. A press is therefore
                    // only ever sent for a tap, on lift.
                    if (!pointerDown
                        && Math.hypot(event.getX() - touchDownViewX, event.getY() - touchDownViewY)
                            > ROTATE_LOCK_DP * uiDensity) {
                        cancelLongPress();
                        oneFingerDrag = true;
                        startCameraDrag(event.getX(), event.getY(), when, "one-finger drag");
                        break;
                    }
                    int motionId = pointerDown
                        ? java.awt.event.MouseEvent.MOUSE_DRAGGED
                        : java.awt.event.MouseEvent.MOUSE_MOVED;
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        emitSegment(motionId,
                            toGameX(event.getHistoricalX(i)),
                            toGameY(event.getHistoricalY(i)),
                            wallFor(event, event.getHistoricalEventTime(i)),
                            java.awt.event.MouseEvent.BUTTON1);
                    }
                    emitSegment(motionId, toGameX(event.getX()), toGameY(event.getY()), when,
                        java.awt.event.MouseEvent.BUTTON1);
                    break;
                }
                case MotionEvent.ACTION_POINTER_DOWN: {
                    if (suppressUntilUp) {
                        break;
                    }
                    // Second finger: cancel the held-off press (nothing has been
                    // sent, so this gesture can never walk or attack), or release
                    // a left press that was already in flight. A one-finger camera
                    // drag hands over to the two-finger router.
                    cancelLongPress();
                    if (pointerDown) {
                        dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_RELEASED, lastMouseX, lastMouseY, when);
                        pointerDown = false;
                    }
                    if (oneFingerDrag) {
                        endCameraDrag(event.getX(), event.getY(), when);
                        oneFingerDrag = false;
                    }
                    if (twoFingerMode == TWO_NONE) {
                        gestureSpan0 = gestureSpanLast = pointerSpan(event);
                        gestureCx0 = event.getX(0) / 2f + event.getX(1) / 2f;
                        gestureCy0 = event.getY(0) / 2f + event.getY(1) / 2f;
                        gestureStartWhen = when;
                        zoomRemainder = 0;
                        lastMouseX = lastMouseY = -1;
                    }
                    break;
                }
                case MotionEvent.ACTION_POINTER_UP: {
                    if (twoFingerMode == TWO_NONE && event.getPointerCount() == 2
                        && when - gestureStartWhen <= TWO_TAP_MAX_MS) {
                        // Both fingers came and went without crossing the rotation/zoom lock:
                        // a two-finger tap, i.e. the right click (the client's own context
                        // menu). The held-off left press was cancelled when the second finger
                        // arrived, so this is the only click the gesture produces. The point is
                        // the midpoint of both fingers -- where the tap was aimed.
                        int rx = centroidX(event, -1, -1);
                        int ry = centroidY(event, -1, -1);
                        emitRightClick(rx, ry, when);
                        Log.i(TAG, "two-finger tap -> right click at " + rx + "," + ry);
                        suppressUntilUp = true;
                        pointerDown = false;
                        lastMouseX = -1;
                        break;
                    }
                    if (twoFingerMode != TWO_NONE) {
                        // Release the middle button at the surviving finger and
                        // ignore that finger's remaining events until it lifts.
                        if (twoFingerMode == TWO_ROTATE) {
                            int sx = centroidX(event, -1, event.getActionIndex());
                            int sy = centroidY(event, -1, event.getActionIndex());
                            emitPoint(java.awt.event.MouseEvent.MOUSE_RELEASED, sx, sy, when,
                                java.awt.event.MouseEvent.BUTTON2);
                        }
                        restoreCameraDragSetting();
                        twoFingerMode = TWO_NONE;
                        pointerDown = false;
                        lastMouseX = -1;
                        suppressUntilUp = true;
                    }
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    cancelLongPress();
                    if (twoFingerMode != TWO_NONE) {
                        if (twoFingerMode == TWO_ROTATE) {
                            emitPoint(java.awt.event.MouseEvent.MOUSE_RELEASED,
                                centroidX(event, -1, -1), centroidY(event, -1, -1), when,
                                java.awt.event.MouseEvent.BUTTON2);
                        }
                        restoreCameraDragSetting();
                        twoFingerMode = TWO_NONE;
                        pointerDown = false;
                        lastMouseX = -1;
                        suppressUntilUp = false;
                        break;
                    }
                    if (oneFingerDrag) {
                        // The tap was cancelled when the drag started: end the camera
                        // drag, send no left-button event at all.
                        endCameraDrag(event.getX(), event.getY(), when);
                        oneFingerDrag = false;
                        pointerDown = false;
                        lastMouseX = -1;
                        break;
                    }
                    if (suppressUntilUp) {
                        // The long press already turned this gesture into a right click.
                        suppressUntilUp = false;
                        pointerDown = false;
                        lastMouseX = -1;
                        break;
                    }
                    int x = toGameX(event.getX());
                    int y = toGameY(event.getY());
                    if (!pointerDown) {
                        // A tap: the press was deferred, so send it (at the down point)
                        // now, followed by the usual release/click sequence.
                        beginTapPress();
                    }
                    emitSegment(pointerDown
                            ? java.awt.event.MouseEvent.MOUSE_DRAGGED
                            : java.awt.event.MouseEvent.MOUSE_MOVED,
                        x, y, when, java.awt.event.MouseEvent.BUTTON1);
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_RELEASED, x, y, when);
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_CLICKED, x, y, when);
                    pointerDown = false;
                    lastMouseX = -1;
                    break;
                }
                case MotionEvent.ACTION_CANCEL: {
                    cancelLongPress();
                    if (twoFingerMode != TWO_NONE) {
                        if (twoFingerMode == TWO_ROTATE) {
                            emitPoint(java.awt.event.MouseEvent.MOUSE_RELEASED,
                                centroidX(event, -1, -1), centroidY(event, -1, -1), when,
                                java.awt.event.MouseEvent.BUTTON2);
                        }
                        restoreCameraDragSetting();
                    }
                    if (oneFingerDrag) {
                        endCameraDrag(event.getX(), event.getY(), when);
                    }
                    oneFingerDrag = false;
                    twoFingerMode = TWO_NONE;
                    suppressUntilUp = false;
                    pointerDown = false;
                    lastMouseX = -1;
                    break;
                }
                case MotionEvent.ACTION_SCROLL: {
                    int rotation = -(int) Math.round(event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                    if (rotation != 0) {
                        dispatchMouseWheel(toGameX(event.getX()), toGameY(event.getY()), rotation, when);
                    }
                    break;
                }
            }
            return true;
        });
    }

    private void scheduleLongPress() {
        cancelLongPress();
        longPress = this::fireLongPress;
        surfaceView.postDelayed(longPress, LONG_PRESS_MS);
    }

    /** Drops the pending long press, if one is still scheduled. */
    private void cancelLongPress() {
        if (longPress != null) {
            surfaceView.removeCallbacks(longPress);
            longPress = null;
        }
    }

    /**
     * A finger that has not moved for {@link #LONG_PRESS_MS} is a right click: the
     * client's context menu for whatever the cursor is over, with no left-button
     * event ever sent for the gesture. The lift is then swallowed by
     * {@code suppressUntilUp} so the menu is the only thing that happens.
     */
    private void fireLongPress() {
        longPress = null;
        if (oneFingerDrag || twoFingerMode != TWO_NONE || suppressUntilUp || pointerDown) {
            return;
        }
        suppressUntilUp = true;
        int x = lastMouseX < 0 ? touchDownX : lastMouseX;
        int y = lastMouseY < 0 ? touchDownY : lastMouseY;
        emitRightClick(x, y, System.currentTimeMillis());
        Log.i(TAG, "long press -> right click at " + x + "," + y);
    }

    /**
     * Sends the held-off single-finger press, unless a two-finger gesture or a
     * leftover finger superseded it.
     */
    private void beginTapPress() {
        if (oneFingerDrag || twoFingerMode != TWO_NONE || suppressUntilUp) {
            return;
        }
        pointerDown = true;
        dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_MOVED, touchDownX, touchDownY, touchDownWhen);
        dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_PRESSED, touchDownX, touchDownY, touchDownWhen);
    }

    /**
     * Routes a two-finger move: the gesture is undecided until the centroid
     * moves (rotate) or the span changes (zoom), and the mode never changes back
     * within one gesture. Both are fed to the client's own input handlers -- a
     * middle-button drag for the rotation, a mouse wheel for the pinch (the
     * client routes a wheel to the camera zoom in the world and to list
     * scrolling while an interface is open, so one stream covers both).
     */
    private void handleTwoFingerMove(MotionEvent event, long when) {
        float cx = event.getX(0) / 2f + event.getX(1) / 2f;
        float cy = event.getY(0) / 2f + event.getY(1) / 2f;
        float span = pointerSpan(event);
        if (twoFingerMode == TWO_NONE) {
            float moved = (float) Math.hypot(cx - gestureCx0, cy - gestureCy0);
            float zoomed = Math.abs(span - gestureSpan0);
            if (zoomed > ZOOM_LOCK_DP * uiDensity && zoomed > moved) {
                twoFingerMode = TWO_ZOOM;
                gestureSpanLast = span;
            } else if (moved > ROTATE_LOCK_DP * uiDensity) {
                twoFingerMode = TWO_ROTATE;
                startCameraDrag(cx, cy, when, "two-finger gesture");
                return;
            } else {
                return;
            }
        }
        if (twoFingerMode == TWO_ROTATE) {
            // The client's camera drag tracks the cursor; feed it the two-finger
            // centroid as a middle-button drag.
            for (int i = 0; i < event.getHistorySize(); i++) {
                emitSegment(java.awt.event.MouseEvent.MOUSE_DRAGGED,
                    centroidX(event, i, -1),
                    centroidY(event, i, -1),
                    wallFor(event, event.getHistoricalEventTime(i)),
                    java.awt.event.MouseEvent.BUTTON2);
            }
            emitSegment(java.awt.event.MouseEvent.MOUSE_DRAGGED,
                centroidX(event, -1, -1),
                centroidY(event, -1, -1),
                when,
                java.awt.event.MouseEvent.BUTTON2);
            return;
        }
        zoomRemainder += (span - gestureSpanLast) / (ZOOM_DP_PER_NOTCH * uiDensity);
        gestureSpanLast = span;
        int steps = (int) zoomRemainder;
        zoomRemainder -= steps;
        if (steps > ZOOM_MAX_NOTCHES) {
            steps = ZOOM_MAX_NOTCHES;
        } else if (steps < -ZOOM_MAX_NOTCHES) {
            steps = -ZOOM_MAX_NOTCHES;
        }
        if (steps != 0) {
            // Fingers apart (steps > 0) must zoom IN: the client's wheel rotation sign is
            // the opposite of the pinch's span change (verified on the device -- dispatching
            // `steps` unchanged zoomed out on a spread pinch).
            dispatchMouseWheel(toGameX(cx), toGameY(cy), -steps, when);
            Log.i(TAG, "pinch zoom " + steps + " notch(es)");
        }
    }

    /**
     * Starts the client's own camera drag with a middle-button press at the
     * centroid. A mouse always moves before it presses, and the client measures
     * its first drag delta against the <em>published</em> cursor position --
     * which still holds the finger's position when the gesture started. Leaving
     * it there makes the first drag rotate by (centroid - that position): the
     * one-time camera jump at the start of a rotation.
     */
    private void startCameraDrag(float viewX, float viewY, long when, String why) {
        forceCameraDragSetting();
        int cx = toGameX(viewX);
        int cy = toGameY(viewY);
        lastMouseX = -1;
        lastMouseY = -1;
        emitPoint(java.awt.event.MouseEvent.MOUSE_MOVED, cx, cy, when,
            java.awt.event.MouseEvent.BUTTON1);
        lastMouseX = cx;
        lastMouseY = cy;
        lastMouseWhen = when;
        emitPoint(java.awt.event.MouseEvent.MOUSE_PRESSED, cx, cy, when,
            java.awt.event.MouseEvent.BUTTON2);
        Log.i(TAG, why + " -> rotate");
    }

    /**
     * A two-finger tap is the right click: the client's own context menu. The
     * cursor is moved to the point first for the same reason the left tap moves
     * it -- the client builds the menu from the <em>hovered</em> target, not from
     * the position embedded in the press.
     */
    private void emitRightClick(int x, int y, long when) {
        emitPoint(java.awt.event.MouseEvent.MOUSE_MOVED, x, y, when,
            java.awt.event.MouseEvent.BUTTON1);
        emitPoint(java.awt.event.MouseEvent.MOUSE_PRESSED, x, y, when,
            java.awt.event.MouseEvent.BUTTON3);
        emitPoint(java.awt.event.MouseEvent.MOUSE_RELEASED, x, y, when,
            java.awt.event.MouseEvent.BUTTON3);
        emitPoint(java.awt.event.MouseEvent.MOUSE_CLICKED, x, y, when,
            java.awt.event.MouseEvent.BUTTON3);
    }

    /** Ends a camera drag: the middle button goes up and the client setting is restored. */
    private void endCameraDrag(float viewX, float viewY, long when) {
        emitPoint(java.awt.event.MouseEvent.MOUSE_RELEASED, toGameX(viewX), toGameY(viewY), when,
            java.awt.event.MouseEvent.BUTTON2);
        restoreCameraDragSetting();
    }

    /** Euclidean distance between the first two pointers in view pixels; 0 with fewer than two. */
    private float pointerSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        return (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
    }

    /**
     * Mean x of the active pointers (at most the first two) in game coordinates,
     * either for historical sample {@code sample} or, with {@code sample < 0},
     * the current position. {@code excludeIndex} skips one pointer (the one
     * lifting, on ACTION_POINTER_UP).
     */
    private int centroidX(MotionEvent event, int sample, int excludeIndex) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < event.getPointerCount() && i < 2; i++) {
            if (i == excludeIndex) {
                continue;
            }
            sum += sample < 0 ? event.getX(i) : event.getHistoricalX(i, sample);
            n++;
        }
        return n == 0 ? 0 : toGameX(sum / n);
    }

    private int centroidY(MotionEvent event, int sample, int excludeIndex) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < event.getPointerCount() && i < 2; i++) {
            if (i == excludeIndex) {
                continue;
            }
            sum += sample < 0 ? event.getY(i) : event.getHistoricalY(i, sample);
            n++;
        }
        return n == 0 ? 0 : toGameY(sum / n);
    }

    /**
     * Forces the client's "camera drag" setting true for the duration of a
     * two-finger gesture: with it false the client treats a middle press as a
     * left click instead of rotating the camera. {@code bn.hc} is a
     * version-specific internal name (re-derive it on a client bump).
     */
    private void forceCameraDragSetting() {
        if (forcedCameraSetting != null) {
            return;
        }
        try {
            java.lang.reflect.Field f = clientClass.getClassLoader().loadClass("bn").getDeclaredField("hc");
            f.setAccessible(true);
            boolean current = f.getBoolean(null);
            if (!current) {
                f.setBoolean(null, true);
                forcedCameraSetting = Boolean.FALSE;
            }
            Log.i(TAG, "camera drag setting bn.hc=" + current + (current ? "" : " (forced true for this gesture)"));
        } catch (Throwable t) {
            Log.w(TAG, "camera drag setting unavailable", t);
        }
    }

    /** Restores the camera-drag setting a gesture forced, if any. */
    private void restoreCameraDragSetting() {
        if (forcedCameraSetting == null) {
            return;
        }
        try {
            java.lang.reflect.Field f = clientClass.getClassLoader().loadClass("bn").getDeclaredField("hc");
            f.setAccessible(true);
            f.setBoolean(null, forcedCameraSetting);
            Log.i(TAG, "camera drag setting bn.hc restored to " + forcedCameraSetting);
        } catch (Throwable t) {
            Log.w(TAG, "camera drag setting restore failed", t);
        }
        forcedCameraSetting = null;
    }

    /**
     * Maps a raw view x to a game pixel inside the letterbox fit. A point in a
     * bar clamps to the nearest game pixel instead of being dropped: a finger
     * that lands in a bar and drags into the game must keep its gesture stream
     * (dropping it would break the two-finger camera gesture when a finger
     * strays into a bar).
     */
    private int toGameX(float raw) {
        int w = fitW;
        if (w <= 0) {
            return 0;
        }
        int x = (int) ((raw - fitLeft) * frameW / w);
        return x < 0 ? 0 : Math.min(x, frameW - 1);
    }

    private int toGameY(float raw) {
        int h = fitH;
        if (h <= 0) {
            return 0;
        }
        int y = (int) ((raw - fitTop) * frameH / h);
        return y < 0 ? 0 : Math.min(y, frameH - 1);
    }

    /**
     * Wall-clock time for a sample taken at {@code eventTime}. MotionEvent
     * times are uptimeMillis, so only the offset from the current event is
     * applied to the wall clock.
     */
    private long wallFor(MotionEvent event, long eventTime) {
        return System.currentTimeMillis() - (event.getEventTime() - eventTime);
    }

    /**
     * Expands the segment from the last emitted point to (x,y) via
     * {@link org.runelite.mobile.MousePath} and dispatches every point, so the
     * game sees a continuous motion stream rather than single jumps.
     */
    private void emitSegment(int id, int x, int y, long when, int button) {
        try {
            if (lastMouseX < 0) {
                emitPoint(id, x, y, when, button);
            } else {
                long[] pts = org.runelite.mobile.MousePath.expand(
                    lastMouseX, lastMouseY, lastMouseWhen, x, y, when);
                for (int i = 0; i < pts.length; i += 3) {
                    emitPoint(id, (int) pts[i], (int) pts[i + 1], pts[i + 2], button);
                }
            }
            lastMouseX = x;
            lastMouseY = y;
            lastMouseWhen = when;
        } catch (Throwable t) {
            Log.w(TAG, "emitSegment failed", t);
        }
    }

    /**
     * The game client attaches its mouse listeners to the game canvas
     * (tq.sp / td). Fall back to the client component if the canvas has none.
     */
    private java.awt.Component resolveInputTarget() {
        if (clientInstance == null) return null;
        if (clientInstance.getMouseListeners().length > 0
            || clientInstance.getMouseMotionListeners().length > 0
            || clientInstance.getMouseWheelListeners().length > 0) {
            return clientInstance;
        }
        try {
            Class<?> gameEngineClass = clientClass.getClassLoader().loadClass("net.runelite.api.GameEngine");
            Method getCanvas = gameEngineClass.getMethod("getCanvas");
            Object canvas = getCanvas.invoke(clientObject);
            if (canvas instanceof java.awt.Component) {
                return (java.awt.Component) canvas;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve game canvas: " + e.getMessage());
        }
        return clientInstance;
    }

    private void dispatchMouseEvent(int id, int x, int y, long when) {
        try {
            java.awt.Component target = resolveInputTarget();
            if (target == null) return;
            if (id != java.awt.event.MouseEvent.MOUSE_MOVED) {
            Log.d(TAG, "Dispatch mouse id=" + id + " at (" + x + "," + y + ") to " + target.getClass().getSimpleName());
        }
        if (id == java.awt.event.MouseEvent.MOUSE_PRESSED) {
            long now = System.currentTimeMillis();
            if (now - lastListenerLog > 2000) {
                lastListenerLog = now;
                Log.d(TAG, "InputTarget listeners: mouse=" + target.getMouseListeners().length
                    + " motion=" + target.getMouseMotionListeners().length
                    + " wheel=" + target.getMouseWheelListeners().length);
            }
            dumpMouseState("press-stored");
            surfaceView.postDelayed(() -> dumpMouseState("press-snapshot"), 300);
        }
        if (id == java.awt.event.MouseEvent.MOUSE_PRESSED) {
            if (loginReqTicker == null) {
                loginReqTicker = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            consumeStackDumpRequest();
                            ClassLoader cl = clientClass.getClassLoader();
                            Object qj = readFieldOn(cl, "client", "qj");
                            Object otl = readFieldOn(cl, "client", "pq");
                            Object qs = readFieldOn(cl, "client", "qs");
                            Object stage = readFieldOn(cl, "client", "cm");
                            StringBuilder sb = new StringBuilder("LoginTick: stage=").append(stage);
                            sb.append(" otl=").append(otl == null ? "null" : "set(" + String.valueOf(otl).length() + ")");
                            sb.append(" requester=").append(qs == null ? "null" : "set");
                            if (qj != null) {
                                String url = readNestedField(qj, "af");
                                sb.append(" qjUrl=").append(url);
                                String resp = readNestedField(qj, "ae");
                                sb.append(" qjState=").append(resp);
                            } else {
                                sb.append(" qj=null");
                            }
                            sb.append(" kk=").append(readFieldOn(cl, "client", "kk"));
                            sb.append(" gx=").append(readFieldOn(cl, "client", "gx"));
                            sb.append(" ci=").append(readFieldOn(cl, "client", "ci"));
                            sb.append(" ru=").append(readFieldOn(cl, "client", "ru"));
                            sb.append(" isGpu=").append(invokeOnClient("isGpu"));
                            sb.append(" dq=").append(readFieldOn(cl, "client", "dq"));
                            sb.append(" mm=").append(readFieldOn(cl, "client", "mm"));
                            sb.append(" player=").append(invokeOnClient("getLocalPlayer") == null ? "null" : "set");
                            sb.append(" cam=").append(invokeOnClient("getCameraX")).append(",")
                                .append(invokeOnClient("getCameraY")).append(",")
                                .append(invokeOnClient("getCameraZ"));
                            try {
                                ClassLoader cl2 = clientClass.getClassLoader();
                                Object camObj = cl2.loadClass("wk").getField("cy").get(null);
                                if (camObj != null) {
                                    java.lang.reflect.Field fap = findFieldInChain(camObj.getClass(), "ap");
                                    fap.setAccessible(true);
                                    sb.append(" vv.ap=").append(fap.getInt(camObj));
                                } else {
                                    sb.append(" vv=null");
                                }
                            } catch (Exception e) {
                                sb.append(" vv=ERR");
                            }
                            sb.append(" fv=").append(readFieldOn(cl, "client", "fv"));
                            sb.append(" fn=").append(readFieldOn(cl, "client", "fn"));
                            sb.append(" my=").append(readFieldOn(cl, "client", "my"));
                            sb.append(" loginIdx=").append(readFieldOn(cl, "bf", "cw"));
                            sb.append(" lh=").append(readFieldOn(cl, "lt", "lh") == null ? "null" : "set(" + String.valueOf(readFieldOn(cl, "lt", "lh")).length() + ")");
                            sb.append(" lz=").append(readFieldOn(cl, "ch", "lz") == null ? "null" : "set");
                            sb.append(" qn=").append(readFieldOn(cl, "client", "qn") == null ? "null" : "set");
                            sb.append(" qt=").append(readFieldOn(cl, "client", "qt") == null ? "null" : "set");
                            sb.append(" qv=").append(readFieldOn(cl, "client", "qv") == null ? "null" : "set");
                            sb.append(" iw=").append(readFieldOn(cl, "client", "iw"));
                            sb.append(" dm=").append(readFieldOn(cl, "client", "dm") == null ? "null" : readFieldOn(cl, "client", "dm").getClass().getSimpleName());
                            sb.append(" cxAz=").append(readFieldOn(cl, "cx", "az") == null ? "null" : readFieldOn(cl, "cx", "az").getClass().getSimpleName());
                            sb.append(" cxAt=").append(readFieldOn(cl, "cx", "at") == null ? "null" : readFieldOn(cl, "cx", "at").getClass().getSimpleName());
                            sb.append(" cxAv=").append(readFieldOn(cl, "cx", "av") == null ? "null" : readFieldOn(cl, "cx", "av").getClass().getSimpleName());
                            sb.append(" fmLn=").append(readFieldOn(cl, "fm", "ln") == null ? "null" : readFieldOn(cl, "fm", "ln").getClass().getSimpleName());
                            sb.append(" ja=").append(readFieldOn(cl, "client", "ja"));
                            sb.append(" jf=").append(readFieldOn(cl, "client", "jf"));
                            sb.append(" ji=").append(readFieldOn(cl, "client", "ji"));
                            sb.append(" jn=").append(readFieldOn(cl, "client", "jn"));
                            sb.append(" vg=").append(readFieldOn(cl, "client", "vg") == null ? "null" : "set");
                            sb.append(" rm=").append(readFieldOn(cl, "client", "rm") == null ? "null" : "set");
                            sb.append(" mo=").append(readFieldOn(cl, "client", "mo") == null ? "null" : "set");
                            sb.append(" ye=").append(readFieldOn(cl, "client", "ye"));
            sb.append(" ").append(dumpPixelArrays(cl));
            sb.append(" ").append(dumpSceneContent(cl));
            sb.append(" ").append(dumpBridgeContent());
            Object vbStore = readFieldOn(cl, "eb", "pw");
                            if (vbStore != null) {
                                sb.append(" vb.ay=").append(readNestedField(vbStore, "ay"));
                                sb.append(" vb.ad=").append(readNestedField(vbStore, "ad"));
                                sb.append(" vb.aw=").append(readNestedField(vbStore, "aw"));
                                sb.append(" vb.ai=").append(readNestedField(vbStore, "ai"));
                                sb.append(" vb.am=").append(readNestedField(vbStore, "am"));
                                sb.append(" vb.ac=").append(readNestedField(vbStore, "ac"));
                                sb.append(" vb.ax=").append(readNestedField(vbStore, "ax"));
                                sb.append(" vb.bp=").append(readNestedField(vbStore, "bp"));
                                sb.append(" vb.ao=").append(readNestedField(vbStore, "ao"));
                                sb.append(" vb.luk=").append(readNestedField(vbStore, "aa") == null ? "null" : "set");
                            } else {
                                sb.append(" vb=null");
                            }
                            Log.i(TAG, sb.toString());
                        } catch (Throwable e) {
                            Log.w(TAG, "LoginTick failed: " + e.getMessage());
                        }
                        surfaceView.postDelayed(this, 1000);
                    }
                };
                surfaceView.postDelayed(loginReqTicker, 1000);
            }
        }
            emitPoint(id, x, y, when, java.awt.event.MouseEvent.BUTTON1);
        } catch (Throwable t) {
            Log.w(TAG, "dispatchMouseEvent failed", t);
        }
    }

    /**
     * Constructs and dispatches a single synthesized mouse event to the
     * resolved client component. Guarded so a client listener throw cannot
     * unwind the calling thread.
     */
    private void emitPoint(int id, int x, int y, long when, int button) {
        try {
            java.awt.Component target = resolveInputTarget();
            if (target == null) return;
            java.awt.event.MouseEvent ev = new java.awt.event.MouseEvent(
                target, id, when, 0, x, y, 1, false, button);
            if (id == java.awt.event.MouseEvent.MOUSE_MOVED || id == java.awt.event.MouseEvent.MOUSE_DRAGGED) {
                for (java.awt.event.MouseMotionListener listener : target.getMouseMotionListeners()) {
                    if (id == java.awt.event.MouseEvent.MOUSE_MOVED) {
                        listener.mouseMoved(ev);
                    } else {
                        listener.mouseDragged(ev);
                    }
                }
            } else {
                for (java.awt.event.MouseListener listener : target.getMouseListeners()) {
                    switch (id) {
                        case java.awt.event.MouseEvent.MOUSE_PRESSED:
                            listener.mousePressed(ev);
                            break;
                        case java.awt.event.MouseEvent.MOUSE_RELEASED:
                            listener.mouseReleased(ev);
                            break;
                        case java.awt.event.MouseEvent.MOUSE_CLICKED:
                            listener.mouseClicked(ev);
                            break;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "emitPoint failed", t);
        }
    }

    /**
     * Diagnostic: reflect the game's mouse-handler state (obfuscation-stable:
     * we look up the class and public static fields by name) plus the RuneLite
     * API view, to see whether a dispatched press reached the game.
     */
    private void dumpMouseState(String why) {
        if (clientObject == null) return;
        try {
            ClassLoader cl = clientClass.getClassLoader();
            Class<?> tz = cl.loadClass("tz");
            StringBuilder sb = new StringBuilder(why + ": aj=" + (tz.getField("aj").get(null) != null));
            String[] names = {"ar", "aw", "ai", "aq", "ac", "aa", "ao", "ab", "ag", "as", "ap", "ad"};
            for (String n : names) {
                try {
                    sb.append(" ").append(n).append("=").append(tz.getField(n).getInt(null));
                } catch (Exception e) {
                    sb.append(" ").append(n).append("=ERR");
                }
            }
            try {
                Class<?> clientIface = cl.loadClass("net.runelite.api.Client");
                Object pos = clientIface.getMethod("getMouseCanvasPosition").invoke(clientObject);
                Object btn = clientIface.getMethod("getMouseCurrentButton").invoke(clientObject);
                sb.append(" apiPos=").append(pos).append(" apiBtn=").append(btn);
            } catch (Exception e) {
                sb.append(" api=ERR");
            }
            sb.append(" jxSession=").append(readStaticField(cl, "at", "lb"));
            sb.append(" jxChar=").append(readStaticField(cl, "ec", "ly"));
            sb.append(" jxToken=").append(readStaticField(cl, "lt", "lh"));
            sb.append(" otlToken=").append(readFieldOn(cl, "client", "pq"));
            sb.append(" otlRequester=").append(readFieldOn(cl, "client", "qs") == null ? "null" : "set");
            Object otlFuture = readFieldOn(cl, "client", "qt");
            if (otlFuture instanceof java.util.concurrent.Future) {
                java.util.concurrent.Future<?> f = (java.util.concurrent.Future<?>) otlFuture;
                sb.append(" otlFuture=").append(f.isDone() ? "done" : (f.isCancelled() ? "cancelled" : "pending"));
            } else {
                sb.append(" otlFuture=").append(otlFuture);
            }
            sb.append(" loginReqUrl=").append(readFieldOn(cl, "client", "qj") == null ? "null" : readNestedField(readFieldOn(cl, "client", "qj"), "af"));
            sb.append(" loginStage=").append(readFieldOn(cl, "client", "cm"));
            Log.d(TAG, "MouseState " + sb);
        } catch (Throwable e) {
            Log.w(TAG, "MouseState dump failed: " + e.getMessage());
        }
    }

    private static String readStaticField(ClassLoader cl, String cls, String field) {
        try {
            java.lang.reflect.Field f = cl.loadClass(cls).getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(null);
            if (v == null) return "null";
            String s = v.toString();
            return s.length() > 8 ? s.substring(0, 8) + "...(" + s.length() + ")" : s;
        } catch (Exception e) {
            return "ERR";
        }
    }

    private static Object readFieldOn(ClassLoader cl, String cls, String field) {
        try {
            java.lang.reflect.Field f = cl.loadClass(cls).getDeclaredField(field);
            f.setAccessible(true);
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                return f.get(null);
            }
            if (clientObject == null) {
                return null;
            }
            return f.get(clientObject);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Diagnostic: identity of the 3D scene's pixel array (fh.ae / the fq
     * instance's aa) vs the buffer image's pixels. If they differ, the scene
     * renders into a scratch array that never reaches the display.
     */
    private static String dumpPixelArrays(ClassLoader cl) {
        StringBuilder sb = new StringBuilder();
        try {
            Class<?> fh = cl.loadClass("fh");
            java.lang.reflect.Field fae = fh.getDeclaredField("ae");
            fae.setAccessible(true);
            int[] scenePx = (int[]) fae.get(null);
            sb.append(" fh.ae=").append(System.identityHashCode(scenePx)).append("(").append(scenePx.length).append(")");
            try {
                java.lang.reflect.Field faj = fh.getDeclaredField("aj");
                faj.setAccessible(true);
                Object aj = faj.get(null);
                if (aj != null) {
                    java.lang.reflect.Field faa = findFieldInChain(aj.getClass(), "aa");
                    faa.setAccessible(true);
                    int[] scenePx2 = (int[]) faa.get(aj);
                    sb.append(" aj.aa=").append(System.identityHashCode(scenePx2)).append("(").append(scenePx2.length).append(")");
                } else {
                    sb.append(" aj=null");
                }
            } catch (Exception e) {
                sb.append(" aj.aa=ERR");
            }
        } catch (Exception e) {
            sb.append(" fh.ae=ERR");
        }
        Object bp = bufferProviderStatic;
        if (bp != null) {
            try {
                java.lang.reflect.Field faz = findFieldInChain(bp.getClass(), "az");
                faz.setAccessible(true);
                Object img = faz.get(bp);
                if (img instanceof java.awt.Image) {
                    int[] dispPx = ((java.awt.Image) img).getPixels();
                    sb.append(" dispPx=").append(System.identityHashCode(dispPx)).append("(").append(dispPx.length).append(")");
                } else {
                    sb.append(" img=null");
                }
            } catch (Exception e) {
                sb.append(" dispPx=ERR");
            }
        } else {
            sb.append(" bp=null");
        }
        try {
            Class<?> yw = cl.loadClass("yw");
            java.lang.reflect.Field faj = yw.getDeclaredField("aj");
            faj.setAccessible(true);
            int[] ywPx = (int[]) faj.get(null);
            sb.append(" yw.aj=").append(ywPx == null ? "null" : System.identityHashCode(ywPx) + "(" + ywPx.length + ")");
            sb.append(" yw.ay=").append(yw.getDeclaredField("ay").getInt(null));
            sb.append(" yw.aq=").append(yw.getDeclaredField("aq").getInt(null));
        } catch (Exception e) {
            sb.append(" yw=ERR");
        }
        return sb.toString();
    }

    private static volatile Object bufferProviderStatic;
    private java.lang.reflect.Field rasterizerTargetField;
    private int[] rasterizerBoundPixels;

    // Palette diagnostics (throttled log): `fq.aq` is the HSL->RGB palette the
    // shaded fills and model faces look up, and `fa.ak` is the per-rasterizer
    // reference to it. The reference must never be re-pointed at a frame buffer.
    private java.lang.reflect.Field paletteStaticField;
    private java.lang.reflect.Field[] paletteSlotFields;
    private java.lang.reflect.Field paletteArrayField;

    private volatile long lastBlitNanos;
    private volatile long lastScaleNanos;

    /** Cached Hooks method lookups: the Callbacks proxy runs at frame rate. */
    private volatile Map<String, Method> hooksMethodCache;

    /**
     * Resolves {@code method} on the RuneLite {@code Hooks} instance. The proxy's Method
     * comes from the child class loader's {@code Callbacks} interface, so the parameter
     * types match the Hooks declaration exactly.
     */
    private Method hooksMethod(Object hooks, Method method) throws NoSuchMethodException {
        Map<String, Method> cache = hooksMethodCache;
        if (cache == null) {
            cache = new java.util.concurrent.ConcurrentHashMap<>();
            hooksMethodCache = cache;
        }
        String key = method.getName() + '/' + method.getParameterCount();
        Method resolved = cache.get(key);
        if (resolved == null) {
            resolved = hooks.getClass().getMethod(method.getName(), method.getParameterTypes());
            cache.put(key, resolved);
        }
        return resolved;
    }

    // ── Conformance instrumentation ─────────────────────────────────────────

    /**
     * Counts one {@code Callbacks.draw(Renderable, boolean)} question. See
     * {@link #entityDrawCalls()} — Entity Hider's veto shows up as {@code denied}.
     */
    private void countEntityDraw(Object renderable, boolean allowed) {
        entityDrawCalls++;
        if (!allowed) {
            entityDrawDenied++;
        }
        Class<?> player = playerInterface;
        if (player == null) {
            ClassLoader loader = RuneLiteHost.clientLoader();
            if (loader == null) {
                return;
            }
            try {
                player = loader.loadClass("net.runelite.api.Player");
                npcInterface = loader.loadClass("net.runelite.api.NPC");
                playerInterface = player;
            } catch (Throwable t) {
                return;
            }
        }
        if (renderable == null) {
            return;
        }
        if (player.isInstance(renderable)) {
            playerDrawCalls++;
        } else if (npcInterface != null && npcInterface.isInstance(renderable)) {
            npcDrawCalls++;
        }
    }

    private static void countEventPost(String eventClass) {
        eventPosts.computeIfAbsent(eventClass, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
    }

    /** How often the client asked whether a world entity may be drawn (since boot). */
    public static long entityDrawCalls() {
        return entityDrawCalls;
    }

    /** How often RuneLite answered that question with "no" (since boot). */
    public static long entityDrawDenied() {
        return entityDrawDenied;
    }

    /** How many of the counted entities were {@code net.runelite.api.Player}s. */
    public static long playerDrawCalls() {
        return playerDrawCalls;
    }

    /** How many of the counted entities were {@code net.runelite.api.NPC}s. */
    public static long npcDrawCalls() {
        return npcDrawCalls;
    }

    /** {@code post} calls the proxy saw for an event class, 0 when it never arrived. */
    public static long eventPostCount(String eventClassName) {
        java.util.concurrent.atomic.AtomicLong n = eventPosts.get(eventClassName);
        return n == null ? 0L : n.get();
    }

    /**
     * Throttled (2 s) frame diagnostic: the client's own frame content, the applet bridge
     * the render thread presents, the rasteriser palette invariant and the blit time.
     * A frozen {@code world=} with a changing {@code bridge=} means the client stopped
     * redrawing; differing values mean the blit is dropping content.
     */
    private void logFrameDiagnostics(java.awt.Image img) {
        long now = System.currentTimeMillis();
        drawCount++;
        if (now - lastDrawLog <= 2000) {
            return;
        }
        long elapsed = now - lastDrawLog;
        long n = drawCount;
        lastDrawLog = now;
        drawCount = 0;
        int[] imgPx = img.getPixels();
        int iw = img.getWidth(), ih = img.getHeight();
        StringBuilder pb = new StringBuilder(" px="
            + (imgPx == null ? "null" : System.identityHashCode(imgPx) + "(" + imgPx.length + ")"));
        if (imgPx != null && iw > 0 && ih > 0 && imgPx.length >= iw * ih) {
            int[] s = {0, 100 * iw + 100, Math.min(ih - 1, 250) * iw + Math.min(iw - 1, 380),
                Math.min(ih - 1, 300) * iw + Math.min(iw - 1, 200), Math.min(ih - 1, 400) * iw + Math.min(iw - 1, 200),
                Math.min(ih - 1, 380) * iw + Math.min(iw - 1, 300)};
            for (int si = 0; si < s.length; si++) {
                if (s[si] < imgPx.length) {
                    pb.append(String.format(",%08X", imgPx[s[si]]));
                }
            }
        }
        pb.append(" world=").append(regionNonZero(imgPx, iw, ih))
            .append("/").append(Integer.toHexString(regionXor(imgPx, iw, ih)));
        int[] bridgePx = AWTBridge.activePixels;
        pb.append(" bridge=").append(regionNonZero(bridgePx, frameW, frameH))
            .append("/").append(Integer.toHexString(regionXor(bridgePx, frameW, frameH)));
        // Top-right strip of the client's own frame: that is where RuneLite's overlays
        // land (the FPS overlay, infoboxes). The game art behind it is static on the
        // welcome screen, so a change in this XOR is overlay pixels appearing.
        int stripW = Math.min(240, iw);
        pb.append(" tr=").append(Integer.toHexString(regionXorRegion(imgPx, iw,
            iw - stripW, 0, iw, Math.min(26, ih))));
        pb.append(" ovl=").append(alwaysOnTopOverlayCount()).append("(").append(overlayNames).append(")");
        pb.append(" iface=").append(lastInterfaceDrawn).append("/").append(lastInterfaceOverlays);
        // Cumulative entity-draw counters: `denied > 0` is a plugin vetoing entity
        // rendering (e.g. Entity Hider). `p=`/`npc=` are the player/NPC subsets.
        pb.append(" entities=").append(entityDrawCalls).append("/").append(entityDrawDenied)
            .append("(p=").append(playerDrawCalls).append(" npc=").append(npcDrawCalls).append(")");
        // RuneLite's FPS overlay paints pure yellow (or red when it is enforcing a
        // limit) at the top-right of the frame; no game art in that strip uses those
        // colours, so a non-zero count is overlay pixels reaching the frame.
        pb.append(" yellow=").append(countColour(imgPx, iw, iw - stripW, 0,
            iw, Math.min(26, ih), 0xFFFFFF00));
        pb.append(" red=").append(countColour(imgPx, iw, iw - stripW, 0,
            iw, Math.min(26, ih), 0xFFFF0000));
        pb.append(" ").append(paletteInvariant())
            .append(" host=").append(RuneLiteHost.isRunning() ? RuneLiteHost.activePluginCount() + "p" : "-")
            .append(" stop=").append(shutdownState())
            .append(" blitMs=").append(String.format("%.1f", lastBlitNanos / 1e6));
        Log.d(TAG, "callbacks.draw: " + iw + "x" + ih
            + " fps=" + ((n * 1000.0) / elapsed) + " (" + n + " in " + elapsed + "ms)" + pb);
    }

    /**
     * Points the software 3D rasterizer's output at the display buffer the app
     * blits.
     *
     * The rasterizer writes its frame through the static `yw.ah` array, which
     * `yw.ef(int[], int, int, float[])` sets together with the clip and the
     * depth array. The client only calls that from its resize path (its own
     * `yi.ab(int)`), which never runs on this port — the frame size is fixed —
     * so without this call the 2D UI lands in the display buffer while the 3D
     * scene is rasterised into the client's original target: a frozen frame
     * with a live minimap.
     *
     * Do NOT retarget `fa.ak` (the per-rasterizer palette reference, initialised
     * from `fq.aq`): it is the 65536-entry HSL->RGB table every shaded fill
     * reads (`var0.ak[hslIndex]`), not a pixel target. Pointing it at the frame
     * makes those lookups return screen pixels — walls take grey/stone from the
     * upper screen while ground, tree trunks and actors go black.
     *
     * Nothing in the public API can retarget the rasterizer, so the client's own
     * internals are reached by name; those names are version-specific and must
     * be re-derived on a client bump (docs/telemetry-assessment.md §6).
     *
     * Idempotent and re-checked per frame: the client can re-point `yw.ah`
     * behind our back, and the display array can be replaced.
     */
    private volatile int lastInterfaceDrawn = -1;
    private volatile int lastInterfaceOverlays = -1;

    /**
     * Number of overlays registered for an interface id ({@code OverlayManager.getForInterface}),
     * i.e. the ABOVE_WIDGETS overlays that {@code renderAfterInterface} will draw.
     */
    private int interfaceOverlayCount(int interfaceId) {
        try {
            Object manager = RuneLiteHost.overlayManager();
            if (manager == null) {
                return -1;
            }
            if (interfaceOverlayMethod == null) {
                interfaceOverlayMethod = manager.getClass().getDeclaredMethod("getForInterface", int.class);
                interfaceOverlayMethod.setAccessible(true);
            }
            Object result = interfaceOverlayMethod.invoke(manager, interfaceId);
            return result instanceof java.util.Collection ? ((java.util.Collection<?>) result).size() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private volatile Method interfaceOverlayMethod;

    /** Counts pixels of an exact ARGB value inside a rectangle (diagnostics only). */
    private static int countColour(int[] px, int width, int x0, int y0, int x1, int y1, int argb) {
        if (px == null) {
            return 0;
        }
        int count = 0;
        for (int y = Math.max(0, y0); y < y1; y++) {
            int row = y * width;
            for (int x = Math.max(0, x0); x < x1 && row + x < px.length; x++) {
                if (px[row + x] == argb) {
                    count++;
                }
            }
        }
        return count;
    }

    /** XOR checksum of a rectangle inside a pixel array (diagnostics only). */
    private static int regionXorRegion(int[] px, int width, int x0, int y0, int x1, int y1) {
        if (px == null) {
            return 0;
        }
        int xor = 0;
        for (int y = Math.max(0, y0); y < y1; y++) {
            int row = y * width;
            for (int x = Math.max(0, x0); x < x1 && row + x < px.length; x++) {
                xor ^= px[row + x];
            }
        }
        return xor;
    }

    /**
     * How many overlays the plugin API has registered for {@code OverlayLayer.ALWAYS_ON_TOP}
     * (the layer {@code Hooks.draw} renders first). A plugin whose overlay is registered but
     * invisible is then distinguishable from a plugin that never registered one.
     */
    private int alwaysOnTopOverlayCount() {
        try {
            Object manager = RuneLiteHost.overlayManager();
            if (manager == null) {
                return -1;
            }
            if (overlayLayerAlwaysOnTop == null) {
                Class<?> layerClass = clientClass.getClassLoader()
                    .loadClass("net.runelite.client.ui.overlay.OverlayLayer");
                // getLayer(OverlayLayer) is package-private in OverlayManager.
                overlayLayerMethod = manager.getClass().getDeclaredMethod("getLayer", layerClass);
                overlayLayerMethod.setAccessible(true);
                overlayLayerConstants = layerClass.getEnumConstants();
            }
            int total = 0;
            StringBuilder names = new StringBuilder();
            for (Object layer : overlayLayerConstants) {
                Object overlays = overlayLayerMethod.invoke(manager, layer);
                if (!(overlays instanceof java.util.Collection)) {
                    continue;
                }
                java.util.Collection<?> collection = (java.util.Collection<?>) overlays;
                total += collection.size();
                names.append(((Enum<?>) layer).name()).append(':').append(collection.size()).append(' ');
            }
            // Master list (OverlayManager.overlays): distinguishes "never added" from
            // "added but not in the layer map".
            Object master = null;
            try {
                java.lang.reflect.Field field = manager.getClass().getDeclaredField("overlays");
                field.setAccessible(true);
                master = field.get(manager);
            } catch (Throwable ignored) {
                // private field layout changed; the layer counts are enough
            }
            if (master instanceof java.util.Collection) {
                names.append(" master=").append(((java.util.Collection<?>) master).size());
            }
            overlayNames = names.toString().trim();
            return total;
        } catch (Throwable t) {
            if (overlayDiagnosticFailure == null) {
                overlayDiagnosticFailure = t.toString();
                Log.w(TAG, "overlay count diagnostic failed", t);
            }
            return -1;
        }
    }

    private volatile Object overlayLayerAlwaysOnTop;
    private volatile Method overlayLayerMethod;
    private volatile Object[] overlayLayerConstants;
    private volatile String overlayNames = "";
    private volatile String overlayDiagnosticFailure;

    private void bindSceneRasterizerToDisplay(Object bufferProvider) {
        try {
            ClassLoader cl = clientClass.getClassLoader();
            Class<?> bufferProviderIface = cl.loadClass("net.runelite.api.BufferProvider");
            int[] pixels = (int[]) bufferProviderIface.getMethod("getPixels").invoke(bufferProvider);
            if (pixels == null) return;

            if (rasterizerTargetField == null) {
                Class<?> rasterizer = cl.loadClass("yw");
                java.lang.reflect.Field target = rasterizer.getField("ah");
                target.setAccessible(true);
                rasterizerTargetField = target;
            }
            if (rasterizerBoundPixels == pixels && rasterizerTargetField.get(null) == pixels) return;

            int[] size = bufferProviderSize(bufferProvider);
            if (size == null) return;
            Class<?> rasterizer = cl.loadClass("yw");
            float[] depth = (float[]) rasterizer.getField("aw").get(null);
            Method bind = rasterizer.getDeclaredMethod("ef", int[].class, int.class, int.class, float[].class);
            bind.setAccessible(true);
            bind.invoke(null, pixels, size[0], size[1], depth);

            rasterizerBoundPixels = pixels;
            Log.i(TAG, "Bound 3D rasterizer target to display buffer "
                + System.identityHashCode(pixels) + "(" + size[0] + "x" + size[1] + ")");
        } catch (Throwable t) {
            Log.w(TAG, "Rasterizer bind failed: " + t);
        }
    }

    /**
     * The frame size a {@code net.runelite.api.BufferProvider} reports, or null when
     * it is not one (or the interface is not loaded yet). The client's own frame
     * buffer is the authority on how big the frame it just drew is, so this is what
     * the host adopts when the client resizes itself.
     */
    private int[] bufferProviderSize(Object provider) {
        if (provider == null || clientClass == null) {
            return null;
        }
        try {
            ClassLoader cl = clientClass.getClassLoader();
            if (bufferProviderIface == null) {
                bufferProviderIface = cl.loadClass("net.runelite.api.BufferProvider");
                bufferProviderWidthMethod = bufferProviderIface.getMethod("getWidth");
                bufferProviderHeightMethod = bufferProviderIface.getMethod("getHeight");
            }
            if (!bufferProviderIface.isInstance(provider)) {
                return null;
            }
            return new int[]{
                (Integer) bufferProviderWidthMethod.invoke(provider),
                (Integer) bufferProviderHeightMethod.invoke(provider)};
        } catch (Throwable t) {
            return null;
        }
    }

    private Class<?> bufferProviderIface;
    private Method bufferProviderWidthMethod;
    private Method bufferProviderHeightMethod;

    /**
     * Swaps the display buffer and the live frame size. Must be called on the client
     * thread (or the UI thread before the client exists) and pairs the array with
     * {@link AWTBridge}'s dimensions under {@code renderLock}, so the render thread's
     * {@code setPixels} can never pair a stride with the wrong array.
     */
    private void publishFrameSize(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        synchronized (renderLock) {
            // Keep the array when the size did not change: it is the client's drawing
            // target and re-allocating it would blank a frame.
            int[] px = (w == frameW && h == frameH && appletPixels != null && appletPixels.length == w * h)
                ? appletPixels
                : new int[w * h];
            appletPixels = px;
            frameW = w;
            frameH = h;
            AWTBridge.activePixels = px;
            AWTBridge.activeWidth = w;
            AWTBridge.activeHeight = h;
        }
    }

    /**
     * Nulls the frame-buffer's cached {@code java.awt.Graphics} (found by type, so it
     * survives obfuscation). The client fetches that Graphics from
     * {@code Component.getGraphics()} -- a snapshot of {@link AWTBridge#activePixels} --
     * exactly once per frame-buffer instance, so after a client-initiated resize it
     * still targets the previous frame size and would paint into the old array forever.
     */
    private static void invalidateProviderGraphics(Object provider) {
        if (provider == null) {
            return;
        }
        try {
            for (Class<?> c = provider.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getType() == java.awt.Graphics.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        if (f.get(provider) != null) {
                            f.set(provider, null);
                        }
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not invalidate the frame buffer's Graphics cache: " + t);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Frame geometry (host side) and client display mode
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Chooses the client frame size the surface should present, and stores it as the
     * pending request for the client thread.
     *
     * <p>Fill mode asks for the surface's own aspect at the apparent UI scale
     * ({@code round(surfaceH / GAME_H)}, i.e. the integer scale the fixed frame would have
     * been presented at), clamped to the client's own floor (765x503, the same bound
     * upstream RuneLite's {@code ClientUI} clamps to) and to {@link #FILL_PIXEL_BUDGET}.
     * When the height floor binds -- the surface is shorter than {@code s} 503-px frames,
     * so the frame cannot follow the scale -- the width is widened to the surface's aspect
     * instead, so the fit still fills the screen; if that would break the budget the frame
     * keeps the scaled width and the fit leaves bars (a centred fit never distorts).
     */
    private void requestGeometry() {
        int w = GAME_W, h = GAME_H;
        if (fillEnabled && surfaceW > 0 && surfaceH > 0) {
            int s = Math.max(1, Math.min(3, Math.round(surfaceH / (float) GAME_H)));
            for (;;) {
                w = Math.max(GAME_W, Math.round(surfaceW / (float) s));
                h = Math.max(GAME_H, Math.round(surfaceH / (float) s));
                if ((long) w * h <= FILL_PIXEL_BUDGET || s >= 4) break;
                s++;
            }
            if (h == GAME_H) {
                // The client's floor bound the height, so the scaled width is too narrow
                // for the surface's aspect (the fit would leave side bars). Widen the
                // frame to the surface's aspect, unless that breaks the pixel budget.
                int aspectW = Math.max(GAME_W, Math.round(surfaceW * (GAME_H / (float) surfaceH)));
                if ((long) aspectW * GAME_H <= FILL_PIXEL_BUDGET) {
                    w = aspectW;
                }
            }
        }
        pendingFrameW = w;
        pendingFrameH = h;
    }

    /** UI thread: flips fill mode, persisted, applied by the client thread's next frame. */
    private void toggleFillMode() {
        fillEnabled = !fillEnabled;
        prefs().edit().putBoolean("gameFill", fillEnabled).apply();
        // The live apply is the boot path: the client thread picks the request up on its
        // next frame. Turning fill on also re-applies the resizable display mode; turning
        // it off only shrinks the frame back to 765x503 (the mode switch is one-way, see
        // ClientDisplay.requestResizable).
        displaySetupDone = false;
        fillStatus = "pending";
        requestGeometry();
        refreshDisplayCard();
    }

    /** UI thread: flips the sidebar mode (overlap vs resize), persisted. */
    private void toggleSidebarMode() {
        sidebarOverlap = !sidebarOverlap;
        prefs().edit().putBoolean("sidebarOverlap", sidebarOverlap).apply();
        // Resizes the game surface, which fires surfaceChanged -> requestGeometry().
        applyGameInsets();
        refreshDisplayCard();
    }

    /**
     * Client thread (from the draw handler, i.e. between two frames): applies the display
     * setup the current fill setting needs. Fill on puts the client into its own resizable
     * display mode -- the client hard-sets its canvas to 765x503 and clamps to it in fixed
     * mode -- so a failure here leaves the frame at 765x503, i.e. the pre-fill behaviour.
     * Fill off touches the client's display state **only** through the frame size: the
     * fixed direction of the mode applier corrupts the client's canvas bookkeeping
     * ({@link ClientDisplay#requestResizable} documents the crash), so the client keeps
     * whatever mode it booted with.
     */
    private void setupClientDisplay() {
        if (clientObject == null || clientClass == null) {
            displaySetupDone = false;   // retry at the next frame
            return;
        }
        ClassLoader cl = clientClass.getClassLoader();
        if (!fillEnabled) {
            fillStatus = "off";
            pendingFrameW = GAME_W;
            pendingFrameH = GAME_H;
            Log.i(TAG, "fill: off (" + GAME_W + "x" + GAME_H + " frame, client display mode untouched)");
            return;
        }
        try {
            // An active RuneLite stretch owns the canvas (it reports the *real* size
            // instead of the requested one); fill mode needs the canvas itself.
            ClientDisplay.disableStretch(clientObject, cl);
            if (!ClientDisplay.requestResizable(clientObject, cl)) {
                failFill("display mode unchanged");
                return;
            }
            fillStatus = "ok";
            Log.i(TAG, "fill: resizable mode applied (isResized=true)");
        } catch (Throwable t) {
            failFill(String.valueOf(t));
        }
    }

    /** Turns fill mode off for this session (the pref stays; the frame falls back). */
    private void failFill(String reason) {
        fillStatus = "unavailable: " + reason;
        fillEnabled = false;
        pendingFrameW = GAME_W;
        pendingFrameH = GAME_H;
        Log.w(TAG, "fill: unavailable: " + reason);
        runOnUiThread(this::refreshDisplayCard);
    }

    /**
     * Client thread: sizes the client's canvas to the pending frame request. Returns
     * true when the frame buffer changed, in which case the frame in flight was drawn
     * into the previous buffer and must not be published (its {@code Graphics} targets
     * the old array); the client's next frame fetches a Graphics over the new one and
     * is presented normally.
     */
    private boolean applyPendingFrameSize() {
        int w = pendingFrameW, h = pendingFrameH;
        if (w == frameW && h == frameH) {
            return false;
        }
        if (clientObject == null || clientClass == null) {
            return false;
        }
        ClassLoader cl = clientClass.getClassLoader();
        // Read the canvas back first, and again after a mode re-assert: the display-mode
        // applier resizes the canvas through the client's own path, and re-running the
        // resize on a canvas that already has the requested size is *not* harmless -- the
        // client's scrambled canvas arithmetic threw NegativeArraySizeException out of the
        // frame-buffer constructor and the client thread died in its own error path.
        if (!requestedCanvas(clientObject, cl, w, h)
            && fillEnabled && !ClientDisplay.isResized(clientObject, cl)) {
            // The client is back in its fixed display mode: it applies its own *stored*
            // display setting whenever player settings arrive (login, a settings packet)
            // or the settings interface opens, and that setting is the account's --
            // fixed for most accounts. Re-assert the mode (which also re-requests the
            // shell's maximum, repairing the canvas bounds) and fall through. Throttled:
            // a client resetting it continuously is given up on instead of fought, since
            // every attempt costs a frame-buffer allocation.
            long now = System.currentTimeMillis();
            if (now - lastFillReassert < FILL_REASSERT_MIN_MS) {
                failFill("display mode reset by the client");
                return false;
            }
            lastFillReassert = now;
            Log.i(TAG, "fill: client reset the display mode, re-asserting");
            setupClientDisplay();
            if (!fillEnabled) {
                return false;   // setupClientDisplay reported why and set the fallback
            }
        }
        if (requestedCanvas(clientObject, cl, w, h)) {
            // The applier (or the client itself) already produced the requested size: just
            // publish it for the presentation.
            if (w == frameW && h == frameH) {
                return false;
            }
            Log.i(TAG, "client canvas already " + w + "x" + h);
            publishFrameSize(w, h);
            return true;
        }
        int[] canvas = ClientDisplay.resizeCanvas(clientObject, cl, w, h);
        if (canvas == null) {
            failFill("canvas resize threw");
            return false;
        }
        Log.i(TAG, "resize client canvas " + canvas[0] + "x" + canvas[1]
            + " (requested " + w + "x" + h + ")");
        if (canvas[0] == frameW && canvas[1] == frameH) {
            // No effect at all: the client clamped the request back to the size it
            // already had, i.e. it is not honouring the request.
            failFill("canvas did not resize");
            return false;
        }
        // Publish *before* the client's next frame: it fetches its destination Graphics
        // (a snapshot of AWTBridge.activePixels) at the start of that frame.
        publishFrameSize(canvas[0], canvas[1]);
        return true;
    }

    /** Whether the client's canvas already has the size the frame request asks for. */
    private boolean requestedCanvas(Object client, ClassLoader cl, int w, int h) {
        int[] canvas = ClientDisplay.canvasSize(client, cl);
        return canvas != null && canvas[0] == w && canvas[1] == h;
    }

    /**
     * Diagnostic: whether the client has armed its own shutdown deadline
     * ({@code ClientDisplay.shutdownDeadline}). A frozen world is a client whose game
     * loop exited on that deadline, so this belongs next to every frame line.
     */
    private String shutdownState() {
        if (clientClass == null) {
            return "-";
        }
        long deadline = ClientDisplay.shutdownDeadline(clientClass.getClassLoader());
        return deadline == 0L ? "clear" : deadline == -1L ? "ERR" : "arm";
    }

    /**
     * Diagnostic: `pal=ok|BAD(<non-zero entries>/65536)` — whether the three
     * rasterizer palette references still point at `fq.aq` and how much of the
     * table is actually built. An all-zero table means the client never ran its
     * palette builder.
     */
    private String paletteInvariant() {
        try {
            ClassLoader cl = clientClass.getClassLoader();
            if (paletteStaticField == null) {
                Class<?> manager = cl.loadClass("fq");
                paletteStaticField = manager.getField("aq");
                String[] slots = {"az", "ah", "an"};
                paletteSlotFields = new java.lang.reflect.Field[slots.length];
                for (int i = 0; i < slots.length; i++) {
                    java.lang.reflect.Field slot = manager.getDeclaredField(slots[i]);
                    slot.setAccessible(true);
                    paletteSlotFields[i] = slot;
                }
                paletteArrayField = findFieldInChain(cl.loadClass("fa"), "ak");
                paletteArrayField.setAccessible(true);
            }
            int[] palette = (int[]) paletteStaticField.get(null);
            int nonZero = 0;
            for (int i = 0; i < palette.length; i++) {
                if (palette[i] != 0) nonZero++;
            }
            boolean ok = true;
            for (java.lang.reflect.Field slot : paletteSlotFields) {
                Object instance = slot.get(null);
                if (instance == null || paletteArrayField.get(instance) != palette) {
                    ok = false;
                    break;
                }
            }
            return "pal=" + (ok ? "ok" : "BAD") + "(" + nonZero + "/" + palette.length + ")";
        } catch (Throwable t) {
            return "pal=ERR";
        }
    }

    private static java.lang.reflect.Field findFieldInChain(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        throw new RuntimeException("field not found: " + name);
    }

        /**
     * Diagnostic: content of the 3D scene buffer, the rasterizer viewport
     * dims, and the terrain tile cache state.
     */
    private static String dumpSceneContent(ClassLoader cl) {
        StringBuilder sb = new StringBuilder();
        try {
            Class<?> fh = cl.loadClass("fh");
            java.lang.reflect.Field faj = fh.getDeclaredField("aj");
            faj.setAccessible(true);
            Object rast = faj.get(null);
            if (rast == null) {
                sb.append(" rast=null");
                return sb.toString();
            }
            int[] px = null;
            try {
                java.lang.reflect.Field faa = findFieldInChain(rast.getClass(), "aa");
                faa.setAccessible(true);
                px = (int[]) faa.get(rast);
            } catch (Exception ignored) {}
            if (px != null && px.length >= 65536) {
                java.util.HashSet<Integer> colors = new java.util.HashSet<>();
                int[] samples = new int[]{0, 16384, 32768, 49152, 65535, 100, 30000};
                sb.append(" scenePx=");
                for (int i = 0; i < samples.length; i++) {
                    sb.append(String.format("%08X,", px[samples[i]]));
                }
                for (int i = 0; i < px.length; i += 17) {
                    colors.add(px[i] & 0x00FFFFFF);
                }
                sb.append("colors=").append(colors.size());
            } else {
                sb.append(" scenePx=null");
            }
            for (String f : new String[]{"ab", "af", "ag"}) {
                try {
                    java.lang.reflect.Field ff = findFieldInChain(rast.getClass(), f);
                    ff.setAccessible(true);
                    sb.append(" r.").append(f).append("=").append(ff.getInt(rast));
                } catch (Exception e) {
                    sb.append(" r.").append(f).append("=ERR");
                }
            }
            try {
                java.lang.reflect.Field fao = findFieldInChain(rast.getClass(), "ao");
                fao.setAccessible(true);
                Object fd = fao.get(rast);
                if (fd != null) {
                    java.lang.reflect.Field fai = findFieldInChain(fd.getClass(), "ai");
                    fai.setAccessible(true);
                    Object ec = fai.get(fd);
                    if (ec != null) {
                        java.lang.reflect.Field faz = findFieldInChain(ec.getClass(), "az");
                        faz.setAccessible(true);
                        Object[] tiles = (Object[]) faz.get(ec);
                        int rendered = 0;
                        int withPx = 0;
                        if (tiles != null) {
                            for (Object t : tiles) {
                                if (t == null) continue;
                                try {
                                    int st = findFieldInChain(t.getClass(), "as").getInt(t);
                                    Object al = findFieldInChain(t.getClass(), "al").get(t);
                                    if (st != -1) rendered++;
                                    if (al != null) withPx++;
                                } catch (Exception ignored) {}
                            }
                        }
                        sb.append(" tiles=").append(tiles == null ? "null" : tiles.length)
                            .append(" rendered=").append(rendered).append(" withPx=").append(withPx);
                    }
                }
            } catch (Exception e) {
                sb.append(" ec=ERR");
            }
        } catch (Exception e) {
            sb.append(" scene=ERR");
        }
        return sb.toString();
    }

    /**
     * Diagnostic: number of non-zero pixels in the left three quarters (the 3D
     * world region) of a frame, or -1 when the buffer is absent.
     */
    private static int regionNonZero(int[] px, int width, int height) {
        if (px == null || width <= 0) return -1;
        int regionWidth = width * 3 / 4;
        int count = 0;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < regionWidth && row + x < px.length; x++) {
                if (px[row + x] != 0) count++;
            }
        }
        return count;
    }

    /**
     * Diagnostic: per-row XOR checksum of the left three quarters (the 3D world
     * region) of a frame, or -1 when the buffer is absent. Rows are folded with
     * a multiply so row order matters.
     */
    private static int regionXor(int[] px, int width, int height) {
        if (px == null || width <= 0) return -1;
        int regionWidth = width * 3 / 4;
        int acc = 0;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int rowXor = 0;
            for (int x = 0; x < regionWidth && row + x < px.length; x++) {
                rowXor ^= px[row + x];
            }
            acc = acc * 31 + rowXor;
        }
        return acc;
    }

    /**
     * Diagnostic: samples of the display buffer (what drawImage blits) and the
     * applet bridge (what the render thread scales to the surface), to locate
     * where the composited scene gets lost.
     */
    private String dumpBridgeContent() {
        StringBuilder sb = new StringBuilder();
        Object bp = bufferProviderStatic;
        if (bp != null) {
            try {
                java.lang.reflect.Field faz = findFieldInChain(bp.getClass(), "az");
                faz.setAccessible(true);
                Object img = faz.get(bp);
                if (img instanceof java.awt.Image) {
                    int[] disp = ((java.awt.Image) img).getPixels();
                    int w = ((java.awt.Image) img).getWidth();
                    int[] s = new int[]{0, 100 * w + 100, 250 * w + 380, 300 * w + 200, 400 * w + 200};
                    sb.append(" dispSamp=");
                    for (int i = 0; i < s.length; i++) {
                        if (s[i] < disp.length) sb.append(String.format("%08X,", disp[s[i]]));
                    }
                }
            } catch (Exception e) {
                sb.append(" dispSamp=ERR");
            }
        }
        int[] ap = AWTBridge.activePixels;
        int fw = frameW, fh = frameH;
        if (ap != null && fw > 0 && fh > 0 && ap.length >= fw * fh) {
            int[] s = new int[]{0, 100 * fw + 100, Math.min(fh - 1, 250) * fw + Math.min(fw - 1, 380),
                Math.min(fh - 1, 300) * fw + Math.min(fw - 1, 200), Math.min(fh - 1, 400) * fw + Math.min(fw - 1, 200)};
            sb.append(" appletSamp=");
            for (int i = 0; i < s.length; i++) {
                if (s[i] < ap.length) {
                    sb.append(String.format("%08X,", ap[s[i]]));
                }
            }
        }
        return sb.toString();
    }

    private static String readNestedField(Object o, String field) {
        try {
            java.lang.reflect.Field f = o.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(o);
            if (v == null) return "null";
            String s = v.toString();
            return s.length() > 60 ? s.substring(0, 60) + "...(" + s.length() + ")" : s;
        } catch (Exception e) {
            return "ERR";
        }
    }

    private static String invokeOnClient(String methodName) {
        try {
            if (clientObject != null && clientClass != null) {
                return String.valueOf(clientClass.getMethod(methodName).invoke(clientObject));
            }
            return "no-client";
        } catch (Exception e) {
            return "ERR";
        }
    }

    private void dispatchMouseWheel(int x, int y, int rotation, long when) {
        java.awt.Component target = resolveInputTarget();
        if (target == null) return;
        java.awt.event.MouseWheelEvent ev = new java.awt.event.MouseWheelEvent(
            target, java.awt.event.MouseWheelEvent.MOUSE_WHEEL, when, 0,
            x, y, 1, false,
            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation);
        for (java.awt.event.MouseWheelListener listener : target.getMouseWheelListeners()) {
            listener.mouseWheelMoved(ev);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Soft keyboard bridge
    // ═══════════════════════════════════════════════════════════════════════

    private void toggleKeyboardBar() {
        boolean show = kbBar.getVisibility() != View.VISIBLE;
        kbBar.setVisibility(show ? View.VISIBLE : View.GONE);
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (show) {
            kbEdit.requestFocus();
            imm.showSoftInput(kbEdit, 0);
        } else {
            imm.hideSoftInputFromWindow(kbEdit.getWindowToken(), 0);
        }
    }

    /**
     * Shows or hides the keyboard bar for the core: a Swing text component took or lost
     * the focus ({@code UiBridge.Host.textInputRequested}), so the sidebar's text fields
     * are typed into through the very same bar as the game's chat line. Idempotent --
     * the core may ask for the state it is already in.
     */
    private void showKeyboardBar(boolean show) {
        if (kbBar == null) {
            return;
        }
        kbBar.setVisibility(show ? View.VISIBLE : View.GONE);
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        if (show) {
            kbEdit.requestFocus();
            imm.showSoftInput(kbEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(kbEdit.getWindowToken(), 0);
        }
    }

    /**
     * Delivers AWT KeyEvents (KEY_PRESSED + KEY_TYPED + KEY_RELEASED) for the
     * given text into every key listener registered on the client component or
     * its canvas. Mirrors desktop AWT: printable chars go through KEY_TYPED
     * with the char; the code is the upper-case VK for letters, else the char.
     */
    private void dispatchKeyText(String text) {
        if (UiBridge.focusOwnerIsTextInput()) {
            // A Swing text component owns the focus: the run is its input, not the
            // game's. One press/typed/release per character, as a desktop EDT sends it.
            SwingSidebarView view = swingSidebarView;
            if (view == null) {
                return;
            }
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                int keyCode = charKeyCode(c);
                view.dispatchKey(java.awt.event.KeyEvent.KEY_PRESSED, keyCode, c);
                view.dispatchKey(java.awt.event.KeyEvent.KEY_TYPED,
                    java.awt.event.KeyEvent.VK_UNDEFINED, c);
                view.dispatchKey(java.awt.event.KeyEvent.KEY_RELEASED, keyCode, c);
            }
            return;
        }
        if (clientInstance == null || clientClass == null) return;
        java.awt.Component target = resolveInputTarget();
        if (target == null) return;
        java.awt.Component canvas = null;
        try {
            Class<?> gameEngineClass = clientClass.getClassLoader().loadClass("net.runelite.api.GameEngine");
            Method getCanvas = gameEngineClass.getMethod("getCanvas");
            Object c = getCanvas.invoke(clientObject);
            if (c instanceof java.awt.Component) canvas = (java.awt.Component) c;
        } catch (Exception ignored) {}
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int keyCode = (c >= 'a' && c <= 'z') ? c - 32 : (c >= 'A' && c <= 'Z') ? c : c;
            deliverKeyEvent(target, canvas, java.awt.event.KeyEvent.KEY_PRESSED, keyCode, c);
            deliverKeyEvent(target, canvas, java.awt.event.KeyEvent.KEY_TYPED, keyCode, c);
            deliverKeyEvent(target, canvas, java.awt.event.KeyEvent.KEY_RELEASED, keyCode, c);
        }
    }

    /** AWT's key code for a printable character: the upper-case VK for letters. */
    private static int charKeyCode(char c) {
        return (c >= 'a' && c <= 'z') ? c - ('a' - 'A') : c;
    }

    /** Delivers a special-key event (ENTER, BACK_SPACE, ESCAPE, ...). */
    private void dispatchKeyCode(int keyCode, char keyChar) {
        if (UiBridge.focusOwnerIsTextInput()) {
            SwingSidebarView view = swingSidebarView;
            if (view == null) {
                return;
            }
            view.dispatchKey(java.awt.event.KeyEvent.KEY_PRESSED, keyCode, keyChar);
            view.dispatchKey(java.awt.event.KeyEvent.KEY_TYPED,
                java.awt.event.KeyEvent.VK_UNDEFINED, keyChar);
            view.dispatchKey(java.awt.event.KeyEvent.KEY_RELEASED, keyCode, keyChar);
            return;
        }
        if (clientInstance == null || clientClass == null) return;
        java.awt.Component target = resolveInputTarget();
        if (target == null) return;
        java.awt.Component canvas = null;
        try {
            Class<?> gameEngineClass = clientClass.getClassLoader().loadClass("net.runelite.api.GameEngine");
            Method getCanvas = gameEngineClass.getMethod("getCanvas");
            Object c = getCanvas.invoke(clientObject);
            if (c instanceof java.awt.Component) canvas = (java.awt.Component) c;
        } catch (Exception ignored) {}
        deliverKeyEvent(target, canvas, java.awt.event.KeyEvent.KEY_PRESSED, keyCode, keyChar);
        deliverKeyEvent(target, canvas, java.awt.event.KeyEvent.KEY_TYPED, keyCode, keyChar);
        deliverKeyEvent(target, canvas, java.awt.event.KeyEvent.KEY_RELEASED, keyCode, keyChar);
    }

    private void deliverKeyEvent(java.awt.Component target, java.awt.Component canvas,
                                 int id, int keyCode, char keyChar) {
        long when = System.currentTimeMillis();
        java.awt.event.KeyEvent ev = new java.awt.event.KeyEvent(target, id, when, 0, keyCode, keyChar);
        java.awt.Component[] components = (canvas != null && canvas != target)
            ? new java.awt.Component[]{target, canvas} : new java.awt.Component[]{target};
        for (java.awt.Component comp : components) {
            for (java.awt.event.KeyListener listener : comp.getKeyListeners()) {
                switch (id) {
                    case java.awt.event.KeyEvent.KEY_PRESSED:
                        listener.keyPressed(ev);
                        break;
                    case java.awt.event.KeyEvent.KEY_TYPED:
                        listener.keyTyped(ev);
                        break;
                    case java.awt.event.KeyEvent.KEY_RELEASED:
                        listener.keyReleased(ev);
                        break;
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Surface / render loop
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        isRunning = true;
        renderThread = new Thread(this::runRenderLoop, "AWTBridge-RenderThread");
        renderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceW = width;
        surfaceH = height;
        // The frame bitmap and the letterbox fit rect are built on the render thread
        // (rebuildRenderGeometry) from the live frame size, which the client owns and
        // fill mode changes; this thread only publishes the new surface size and the
        // frame size the surface asks for.
        requestGeometry();
    }

    /**
     * Render thread: (re)builds the frame bitmap and the fit rect from the live frame
     * and surface sizes. A no-op unless one of them changed.
     *
     * <p>Called under {@code renderLock}, so a resize published by the client thread
     * (publishFrameSize) can never be observed halfway. The frame bitmap is created
     * here rather than in {@code surfaceChanged} because the client frame size is
     * live: the surface may arrive before the client and every frame resize lands
     * here.
     */
    private void rebuildRenderGeometry() {
        int w = frameW, h = frameH;
        int sw = surfaceW, sh = surfaceH;
        if (sw <= 0 || sh <= 0) {
            return;
        }
        if (w == geoW && h == geoH && sw == geoSurfaceW && sh == geoSurfaceH) {
            return;
        }
        geoW = w;
        geoH = h;
        geoSurfaceW = sw;
        geoSurfaceH = sh;
        if (renderBitmap == null || renderBitmap.getWidth() != w || renderBitmap.getHeight() != h) {
            renderBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            // The rasterizer writes 3D pixels with alpha 0. An alpha bitmap would
            // have its alpha-0 pixels dropped by Skia's SRC_OVER blend, leaving the
            // previous surface content visible (the "stale login-screen remnants"
            // symptom); setHasAlpha(false) replaces the old `| 0xFF000000` pass.
            renderBitmap.setHasAlpha(false);
            srcRect.set(0, 0, w, h);
        }
        // Uniform scale, centred: the game keeps its native aspect (a stretch to fill
        // would distort it) and the leftover strips are painted with barPaint.
        float scale = Math.min(sw / (float) w, sh / (float) h);
        fitW = Math.max(1, Math.round(w * scale));
        fitH = Math.max(1, Math.round(h * scale));
        fitLeft = (sw - fitW) / 2;
        fitTop = (sh - fitH) / 2;
        Log.i(TAG, "surface " + sw + "x" + sh + " fit " + fitW + "x" + fitH
            + " at " + fitLeft + "," + fitTop + " (frame " + w + "x" + h + ")");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isRunning = false;
        if (renderThread != null) {
            renderThread.interrupt();
            try {
                renderThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while joining renderThread", e);
            }
            renderThread = null;
        }
    }

    private void runRenderLoop() {
        // The frame is presented with native calls only: one row copy of the
        // client's frame int[] into the bitmap and one scaled drawBitmap.
        // DISPLAY priority keeps this thread scheduled while the client thread
        // saturates the CPU with its software renderer.
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY);
        while (isRunning) {
            long scaleStart;
            synchronized (renderLock) {
                // The client frame size is live (fill mode resizes the client's canvas),
                // so the bitmap and the fit rect are rebuilt here, from the sizes the
                // client thread publishes.
                rebuildRenderGeometry();
                if (renderBitmap == null) {
                    // No surface yet - idle so background work (updates, dexing)
                    // isn't starved.
                    try {
                        renderLock.wait(50);
                    } catch (InterruptedException ignored) {}
                    continue;
                }
                // Wait for the client's next completed frame (delivered by the
                // callbacks.draw proxy). We must NOT present on a timer, nor call
                // client.paint(): that blits the game's LIVE frame buffer while the
                // client thread is rendering the next frame into it (torn frames).
                while (isRunning && frameSeq == lastDrawnSeq) {
                    try {
                        renderLock.wait(100);
                    } catch (InterruptedException ignored) {}
                }
                if (!isRunning) break;
                lastDrawnSeq = frameSeq;
                // A resize can land while we wait; rebuilding keeps the copy below
                // from ever pairing a stride with the wrong frame buffer.
                rebuildRenderGeometry();
                scaleStart = System.nanoTime();
                renderBitmap.setPixels(appletPixels, 0, frameW, 0, 0, frameW, frameH);
            }
            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    // Letterbox: paint the bars, then blit into the centred fit rect.
                    renderDst.set(fitLeft, fitTop, fitLeft + fitW, fitTop + fitH);
                    // The surface buffer is not cleared by the system and bar pixels are
                    // left over from the previous geometry, so repaint the four strips
                    // (empty rects are cheap/skipped).
                    int sw = surfaceW, sh = surfaceH;
                    int l = fitLeft, t = fitTop, r = fitLeft + fitW, b = fitTop + fitH;
                    if (l > 0) {
                        canvas.drawRect(0, 0, l, sh, barPaint);
                        canvas.drawRect(r, 0, sw, sh, barPaint);
                    }
                    if (t > 0) {
                        canvas.drawRect(0, 0, sw, t, barPaint);
                        canvas.drawRect(0, b, sw, sh, barPaint);
                    }
                    // One native capped draw: Skia scales the frame buffer into the fit
                    // rect (nearest-neighbour, see scalePaint) and composites it opaquely
                    // (setHasAlpha(false)).
                    canvas.drawBitmap(renderBitmap, srcRect, renderDst, scalePaint);
                    lastScaleNanos = System.nanoTime() - scaleStart;

                    // Diagnostic: report the game's state machine position periodically
                    long now = System.currentTimeMillis();
                    if (now - lastStateLog > 5000 && clientObject != null) {
                        lastStateLog = now;
                        // adb trigger for the per-plugin conformance pass: the operator
                        // drops conformance.request into the app-specific external files
                        // dir, this consumes it and PluginConformance writes
                        // conformance-report.txt next to it.
                        File conformanceRequest = PluginConformance.requestFile(this);
                        if (conformanceRequest.isFile() && conformanceRequest.delete()) {
                            Log.i(TAG, "conformance requested via " + conformanceRequest.getAbsolutePath());
                            runOnUiThread(() -> PluginConformance.run(this));
                        }
                        consumeStackDumpRequest();
                        try {
                            Class<?> clientInterface = clientClass.getClassLoader().loadClass("net.runelite.api.Client");
                            Object state = clientInterface.getMethod("getGameState").invoke(clientObject);
                            Object loginIdx = clientInterface.getMethod("getLoginIndex").invoke(clientObject);
                            StringBuilder sb = new StringBuilder("GameState: " + state + " loginIndex: " + loginIdx
                                + " scaleMs=" + String.format("%.1f", lastScaleNanos / 1e6));
                            for (String probe : new String[]{"getWorld", "getWorldHost", "getCurrentLoginField",
                                    "getBaseX", "getBaseY", "getPlane", "getCameraX", "getCameraY", "getCameraZ",
                                    "getLocalPlayer", "getMapRegions", "getFPS", "getCanvasWidth", "getCanvasHeight",
                                    "isStretchedEnabled"}) {
                                try {
                                    Object v = clientInterface.getMethod(probe).invoke(clientObject);
                                    if (v instanceof int[]) {
                                        v = ((int[]) v).length + " regions";
                                    }
                                    sb.append(" ").append(probe).append("=").append(v);
                                } catch (Exception e) {
                                    sb.append(" ").append(probe).append("=ERR");
                                }
                            }
                            try {
                                Object worlds = clientInterface.getMethod("getWorldList").invoke(clientObject);
                                if (worlds instanceof Object[]) {
                                    sb.append(" worldList=").append(((Object[]) worlds).length);
                                } else {
                                    sb.append(" worldList=").append(worlds);
                                }
                            } catch (Exception e) {
                                sb.append(" worldList=ERR");
                            }
                            Log.i(TAG, sb.toString());
                        } catch (Exception e) {
                            Log.w(TAG, "GameState poll failed: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in render loop", e);
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        // The activity is in front: keep watching the release feed (see UPDATE_POLL_MS) so a
        // client published during a weekly OSRS update is offered without a relaunch.
        updatePollActive = true;
        startUpdateCheckAsync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        updatePollActive = false;
        updatePollHandler.removeCallbacks(updatePollTask);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Android may kill a backgrounded process without warning, and RuneLite's own
        // config flush is a periodic task: flush on the way out so panel changes (plugin
        // enablement, config edits) survive.
        RuneLiteHost.flushConfig();
    }

    public void onBackPressed() {
        if (loginOverlay.getVisibility() == View.VISIBLE) {
            cancelLogin();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCallbackServer();
    }
}
