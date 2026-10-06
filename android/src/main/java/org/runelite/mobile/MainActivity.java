package org.runelite.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
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
    private static final int GAME_W = 766;
    private static final int GAME_H = 503;
    private static final String PREFS_NAME = "RuneLiteMobilePrefs";

    // ── Rendering ───────────────────────────────────────────────────────────
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private AWTBridge awtBridge;
    private volatile boolean isRunning = false;
    private Bitmap renderBitmap;
    private Paint debugPaint;
    private Paint highlightPaint;
    private static java.awt.Component clientInstance;
    private static Object clientObject;
    private static Class<?> clientClass;
    private Thread renderThread;
    private final int[] appletPixels = new int[GAME_W * GAME_H];
    private boolean pointerDown = false;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private long lastMouseWhen = 0;
    private long lastStateLog = 0;
    private long lastDrawLog = 0;
    private long drawCount = 0;
    private volatile Object bufferProvider;
    private Button kbButton;
    private LinearLayout kbBar;
    private EditText kbEdit;
    private String kbPrevText = "";
    private final Object renderLock = new Object();
    private final int[] frameScratch = new int[GAME_W * GAME_H];
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
    private TextView btnManual;
    private LinearLayout manualPanel;
    private EditText etSessionId;
    private EditText etCharacterId;
    private EditText etDisplayName;
    private Switch swJxMode;
    private ProgressBar updateProgress;
    private TextView tvUpdateText;
    private Button btnSettings;

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
    private boolean updateDialogShown = false;

    // Loader status displayed on the launcher / rendering canvas
    private String loaderStatus = "Idle";
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

        // Bind AWTBridge active pixels to appletPixels
        AWTBridge.activePixels = appletPixels;
        AWTBridge.activeWidth = GAME_W;
        AWTBridge.activeHeight = GAME_H;

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);

        debugPaint = new Paint();
        debugPaint.setColor(Color.GREEN);
        debugPaint.setTextSize(28f);

        highlightPaint = new Paint();
        highlightPaint.setColor(Color.YELLOW);
        highlightPaint.setTextSize(32f);

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

        // 3. Build UI: SurfaceView + launcher screen + login WebView overlay
        buildLauncherUi();
        setContentView(rootLayout);

        // 4. Touch input dispatch into the game client
        setupTouchInput();

        // 5. Check for a newer RuneLite client in the background
        startUpdateCheckAsync();

        // 5b. Pre-register the game dex with ART so dex2oat can AOT it. This
        // device has dalvik.vm.usejit=false (JIT disabled), so without AOT the
        // game runs interpreted (~0.2 fps). The DexClassLoader construction
        // mirrors bootstrapGameClient exactly, which lets `cmd package compile
        // --secondary-dex` record the class-loader context before login.
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

        final float density = getResources().getDisplayMetrics().density;
        final int dp = (int) (density * 5);

        // ── Launcher panel ──
        launcherScroll = new ScrollView(this);
        launcherScroll.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        launcherPanel = new LinearLayout(this);
        launcherPanel.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(0xF01E1E24);
        panelBg.setCornerRadius(30 * density);
        panelBg.setStroke(3, 0xFF3F3F4F);
        launcherPanel.setBackground(panelBg);

        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
            (int) (380 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.CENTER;
        panelLp.topMargin = (int) (60 * density);
        panelLp.bottomMargin = (int) (60 * density);
        launcherPanel.setLayoutParams(panelLp);
        launcherPanel.setPadding((int) (30 * density), (int) (35 * density), (int) (30 * density), (int) (30 * density));

        tvTitle = new TextView(this);
        tvTitle.setText("RuneLite Mobile");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        launcherPanel.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("RuneLite client for Old School RuneScape");
        tvSubtitle.setTextColor(0xFF9A9AA8);
        tvSubtitle.setTextSize(12f);
        tvSubtitle.setGravity(Gravity.CENTER);
        tvSubtitle.setPadding(0, dp * 2, 0, dp * 5);
        launcherPanel.addView(tvSubtitle);

        tvSignedInAs = new TextView(this);
        tvSignedInAs.setTextColor(0xFF7CD47C);
        tvSignedInAs.setTextSize(15f);
        tvSignedInAs.setGravity(Gravity.CENTER);
        tvSignedInAs.setPadding(0, 0, 0, dp * 2);
        launcherPanel.addView(tvSignedInAs);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(0xFFB8B8C8);
        tvStatus.setTextSize(12f);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, dp * 4);
        launcherPanel.addView(tvStatus);

        // Update progress — kept near the top so it's visible without scrolling
        updateProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        updateProgress.setVisibility(View.GONE);
        launcherPanel.addView(updateProgress);

        tvUpdateText = new TextView(this);
        tvUpdateText.setTextColor(0xFFB8B8C8);
        tvUpdateText.setTextSize(12f);
        tvUpdateText.setGravity(Gravity.CENTER);
        tvUpdateText.setVisibility(View.GONE);
        launcherPanel.addView(tvUpdateText);

        btnSignIn = styledButton("Sign in with Jagex Account", new int[]{0xFF4F46E5, 0xFF7C3AED}, 16f);
        btnSignIn.setOnClickListener(v -> startJagexLogin());
        launcherPanel.addView(btnSignIn);

        btnPlay = styledButton("Play", new int[]{0xFF16A34A, 0xFF15803D}, 18f);
        btnPlay.setOnClickListener(v -> onPlayClicked());
        launcherPanel.addView(btnPlay);

        btnSwitchCharacter = linkButton("Switch character");
        btnSwitchCharacter.setOnClickListener(v -> startJagexLogin());
        launcherPanel.addView(btnSwitchCharacter);

        btnSignOut = linkButton("Sign out");
        btnSignOut.setTextColor(0xFFE07A7A);
        btnSignOut.setOnClickListener(v -> confirmSignOut());
        launcherPanel.addView(btnSignOut);

        tvVersion = new TextView(this);
        tvVersion.setTextColor(0xFF70707E);
        tvVersion.setTextSize(11f);
        tvVersion.setGravity(Gravity.CENTER);
        tvVersion.setPadding(0, dp * 3, 0, 0);
        launcherPanel.addView(tvVersion);

        btnCheckUpdates = linkButton("Check for updates");
        btnCheckUpdates.setOnClickListener(v -> checkForUpdates(true));
        launcherPanel.addView(btnCheckUpdates);

        btnManual = linkButton("Manual session tokens (advanced)");
        btnManual.setTextColor(0xFF66667A);
        btnManual.setOnClickListener(v ->
            manualPanel.setVisibility(manualPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        launcherPanel.addView(btnManual);

        manualPanel = new LinearLayout(this);
        manualPanel.setOrientation(LinearLayout.VERTICAL);
        manualPanel.setVisibility(View.GONE);
        launcherPanel.addView(manualPanel);

        swJxMode = new Switch(this);
        swJxMode.setText("Enable Jagex Account Mode");
        swJxMode.setTextColor(0xFFFFFFFF);
        swJxMode.setChecked(signedIn);
        manualPanel.addView(swJxMode);

        etSessionId = createStyledEditText("JX_SESSION_ID (jagexSessionId)", sessionId);
        manualPanel.addView(etSessionId);

        etCharacterId = createStyledEditText("JX_CHARACTER_ID (characterId)", characterId);
        manualPanel.addView(etCharacterId);

        etDisplayName = createStyledEditText("JX_DISPLAY_NAME", displayName);
        manualPanel.addView(etDisplayName);

        Button btnSave = styledButton("Save & Apply", new int[]{0xFF4F46E5, 0xFF7C3AED}, 15f);
        btnSave.setOnClickListener(v -> saveManualCredentials());
        manualPanel.addView(btnSave);

        launcherScroll.addView(launcherPanel);
        rootLayout.addView(launcherScroll);

        // ── Floating settings button (visible while the game runs) ──
        btnSettings = new Button(this);
        btnSettings.setText("⚙");
        btnSettings.setTextColor(0xFFFFFFFF);
        btnSettings.setTextSize(16f);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xBB2E2E3E);
        btnBg.setCornerRadius(15 * density);
        btnBg.setStroke(2, 0xFF4F4F5F);
        btnSettings.setBackground(btnBg);
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.TOP | Gravity.RIGHT;
        btnParams.topMargin = (int) (40 * density);
        btnParams.rightMargin = (int) (40 * density);
        btnSettings.setLayoutParams(btnParams);
        btnSettings.setPadding((int) (28 * density), (int) (12 * density), (int) (28 * density), (int) (12 * density));
        btnSettings.setOnClickListener(v ->
            launcherScroll.setVisibility(launcherScroll.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        btnSettings.setVisibility(View.GONE);
        rootLayout.addView(btnSettings);

        // ── Soft keyboard bridge: the game has no IME of its own, so a
        //    floating "KB" button opens an EditText whose keystrokes are
        //    forwarded into the client as AWT KeyEvents (display-name, chat) ──
        float density2 = getResources().getDisplayMetrics().density;
        kbButton = new Button(this);
        kbButton.setText("KB");
        kbButton.setTextSize(14f);
        GradientDrawable kbBg = new GradientDrawable();
        kbBg.setColor(0xAA2E2E3E);
        kbBg.setCornerRadius(10 * density2);
        kbBg.setStroke(2, 0xFF4F4F5F);
        kbButton.setBackground(kbBg);
        FrameLayout.LayoutParams kbBtnParams = new FrameLayout.LayoutParams(
            (int) (52 * density2), (int) (44 * density2));
        kbBtnParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        kbBtnParams.topMargin = (int) (8 * density2);
        kbButton.setLayoutParams(kbBtnParams);
        kbButton.setVisibility(View.GONE);
        kbButton.setOnClickListener(v -> toggleKeyboardBar());
        rootLayout.addView(kbButton);

        kbBar = new LinearLayout(this);
        kbBar.setOrientation(LinearLayout.HORIZONTAL);
        kbBar.setPadding((int) (6 * density2), (int) (6 * density2), (int) (6 * density2), (int) (6 * density2));
        kbBar.setVisibility(View.GONE);
        kbEdit = new EditText(this);
        kbEdit.setSingleLine(true);
        kbEdit.setTextSize(16f);
        kbEdit.setHint("Type here...");
        kbEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        kbEdit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
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
        Button kbEnter = new Button(this);
        kbEnter.setText("Enter");
        kbEnter.setOnClickListener(v -> dispatchKeyCode(java.awt.event.KeyEvent.VK_ENTER, '\n'));
        Button kbClose = new Button(this);
        kbClose.setText("Hide");
        kbClose.setOnClickListener(v -> toggleKeyboardBar());
        kbBar.addView(kbEdit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        kbBar.addView(kbEnter, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        kbBar.addView(kbClose, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams kbBarParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        kbBarParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        kbBar.setLayoutParams(kbBarParams);
        rootLayout.addView(kbBar);

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

        updateLauncherUi();
    }

    private Button styledButton(String text, int[] gradient, float textSize) {
        final float density = getResources().getDisplayMetrics().density;
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(textSize);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, gradient);
        bg.setCornerRadius(20 * density);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (10 * density);
        btn.setLayoutParams(lp);
        btn.setPadding(0, (int) (16 * density), 0, (int) (16 * density));
        return btn;
    }

    private TextView linkButton(String text) {
        final float density = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF8F8FBF);
        tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, (int) (12 * density), 0, 0);
        return tv;
    }

    private EditText createStyledEditText(String hint, String value) {
        final float density = getResources().getDisplayMetrics().density;
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(0xFF666677);
        et.setText(value);
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(14f);
        et.setSingleLine(true);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFF0F0F14);
        etBg.setCornerRadius(15 * density);
        etBg.setStroke(2, 0xFF2F2F3F);
        et.setBackground(etBg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (10 * density);
        et.setLayoutParams(params);
        et.setPadding((int) (30 * density), (int) (20 * density), (int) (30 * density), (int) (20 * density));
        return et;
    }

    private void updateLauncherUi() {
        if (signedIn && !sessionId.isEmpty()) {
            tvSignedInAs.setText("Signed in as " + (displayName.isEmpty() ? characterId : displayName));
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
        if (!current.equals(installed)) {
            tvVersion.setText("Client v" + installed + " (APK ships v" + current + ")");
        } else {
            tvVersion.setText("Client v" + current);
        }
        swJxMode.setChecked(signedIn);
        etSessionId.setText(sessionId);
        etCharacterId.setText(characterId);
        etDisplayName.setText(displayName);
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
            loaderStatus = "Login failed";
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
        new Thread(() -> {
            String latest = ClientUpdater.fetchLatestClientVersion();
            if (latest == null) {
                return;
            }
            String installed = ClientUpdater.installedClientVersion(this);
            if (installed.equals("unknown") || latest.equals(installed)) {
                return;
            }
            runOnUiThread(() -> {
                if (!updateDialogShown) {
                    showUpdateDialog(latest, installed);
                }
            });
        }, "UpdateCheck").start();
    }

    private void checkForUpdates(boolean manual) {
        tvUpdateText.setVisibility(View.VISIBLE);
        tvUpdateText.setText("Checking for updates...");
        new Thread(() -> {
            String latest = ClientUpdater.fetchLatestClientVersion();
            String installed = ClientUpdater.installedClientVersion(this);
            runOnUiThread(() -> {
                if (latest == null) {
                    tvUpdateText.setText("Could not check for updates (network error).");
                    return;
                }
                tvUpdateText.setText("");
                tvUpdateText.setVisibility(View.GONE);
                if (latest.equals(installed)) {
                    Toast.makeText(this, "Client is up to date (v" + installed + ")", Toast.LENGTH_SHORT).show();
                } else {
                    showUpdateDialog(latest, installed);
                }
            });
        }, "UpdateCheck").start();
    }

    private void showUpdateDialog(String latest, String installed) {
        updateDialogShown = true;
        new AlertDialog.Builder(this)
            .setTitle("RuneLite client update available")
            .setMessage("A new RuneLite client is available (v" + installed + " -> v" + latest + ").\n\n"
                + "Download and install it now? The install runs in the background (dexing "
                + "takes about 20-30 minutes on-device — progress is shown here); you'll be "
                + "asked to restart the game when it's ready. This keeps the game working "
                + "after weekly OSRS updates.")
            .setPositiveButton("Update now", (d, w) -> runClientUpdate(latest))
            .setNegativeButton("Later", null)
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
        new Thread(() -> {
            try {
                ClientUpdater.runUpdate(this, version, (stage, percent, etaMillis) -> runOnUiThread(() -> {
                    updateProgress.setProgress(percent);
                    tvUpdateText.setText(stage + "\n" + percent + "% · " + formatEta(etaMillis));
                }));
                runOnUiThread(() -> {
                    updateProgress.setVisibility(View.GONE);
                    tvUpdateText.setVisibility(View.GONE);
                    btnCheckUpdates.setEnabled(true);
                    updateLauncherUi();
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
        btnSettings.setVisibility(View.VISIBLE);
        kbButton.setVisibility(View.VISIBLE);
        tvStatus.setText("Starting game...");
        new Thread(this::bootstrapGameClient, "GameClientBootstrapper").start();
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
                                // Frame blit: copy the rendered game buffer into the graphics
                                if (args.length >= 2 && args[0] != null && args[1] instanceof java.awt.Graphics) {
                                    try {
                                        bufferProvider = args[0];
                                        bufferProviderStatic = args[0];
                                        repointSceneRasterizerOnce();
                                        Object image = args[0].getClass().getMethod("getImage").invoke(args[0]);
                                        if (image instanceof java.awt.Image) {
                                            java.awt.Image img = (java.awt.Image) image;
                                            long now = System.currentTimeMillis();
                                            drawCount++;
                                            if (now - lastDrawLog > 2000) {
                                                long elapsed = now - lastDrawLog;
                                                long n = drawCount;
                                                lastDrawLog = now;
                                                drawCount = 0;
                                                int[] imgPx = img.getPixels();
                                                StringBuilder pb = new StringBuilder(" px="
                                                    + (imgPx == null ? "null" : System.identityHashCode(imgPx) + "(" + imgPx.length + ")"));
                                                if (imgPx != null && imgPx.length >= 765 * 503) {
                                                    int[] s = {0, 100 * 765 + 100, 250 * 765 + 380, 300 * 765 + 200, 400 * 765 + 200, 380 * 765 + 300};
                                                    for (int si = 0; si < s.length; si++) {
                                                        if (s[si] < imgPx.length) {
                                                            pb.append(String.format(",%08X", imgPx[s[si]]));
                                                        }
                                                    }
                                                }
                                                Log.d(TAG, "callbacks.draw: " + img.getWidth() + "x" + img.getHeight()
                                                    + " fps=" + ((n * 1000.0) / elapsed) + " (" + n + " in " + elapsed + "ms)" + pb);
                                            }
                                            synchronized (renderLock) {
                                                ((java.awt.Graphics) args[1]).drawImage(img, 0, 0, null);
                                            }
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
                                return args[0];
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

            updateStatus("RUNNING: Injected Client Active!");
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
                launcherScroll.setVisibility(View.VISIBLE);
                btnSettings.setVisibility(View.GONE);
                kbButton.setVisibility(View.GONE);
                kbBar.setVisibility(View.GONE);
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
        this.loaderStatus = status;
        Log.i(TAG, "Status Update: " + status);
        runOnUiThread(() -> {
            if (tvStatus != null && loginStage == LoginStage.IDLE) {
                tvStatus.setText(status);
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
                    pointerDown = true;
                    int x = toGameX(v, event.getX());
                    int y = toGameY(v, event.getY());
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_PRESSED, x, y, when);
                    lastMouseX = x;
                    lastMouseY = y;
                    lastMouseWhen = when;
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    int motionId = pointerDown
                        ? java.awt.event.MouseEvent.MOUSE_DRAGGED
                        : java.awt.event.MouseEvent.MOUSE_MOVED;
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        emitSegment(motionId,
                            toGameX(v, event.getHistoricalX(i)),
                            toGameY(v, event.getHistoricalY(i)),
                            wallFor(event, event.getHistoricalEventTime(i)));
                    }
                    emitSegment(motionId, toGameX(v, event.getX()), toGameY(v, event.getY()), when);
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    int x = toGameX(v, event.getX());
                    int y = toGameY(v, event.getY());
                    emitSegment(pointerDown
                            ? java.awt.event.MouseEvent.MOUSE_DRAGGED
                            : java.awt.event.MouseEvent.MOUSE_MOVED,
                        x, y, when);
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_RELEASED, x, y, when);
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_CLICKED, x, y, when);
                    pointerDown = false;
                    lastMouseX = -1;
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    pointerDown = false;
                    lastMouseX = -1;
                    break;
                case MotionEvent.ACTION_SCROLL: {
                    int rotation = -(int) Math.round(event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                    if (rotation != 0) {
                        dispatchMouseWheel(toGameX(v, event.getX()), toGameY(v, event.getY()), rotation, when);
                    }
                    break;
                }
            }
            return true;
        });
    }

    private int toGameX(android.view.View v, float raw) {
        return (int) (raw * GAME_W / v.getWidth());
    }

    private int toGameY(android.view.View v, float raw) {
        return (int) (raw * GAME_H / v.getHeight());
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
    private void emitSegment(int id, int x, int y, long when) {
        try {
            if (lastMouseX < 0) {
                emitPoint(id, x, y, when);
            } else {
                long[] pts = org.runelite.mobile.MousePath.expand(
                    lastMouseX, lastMouseY, lastMouseWhen, x, y, when);
                for (int i = 0; i < pts.length; i += 3) {
                    emitPoint(id, (int) pts[i], (int) pts[i + 1], pts[i + 2]);
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
            emitPoint(id, x, y, when);
        } catch (Throwable t) {
            Log.w(TAG, "dispatchMouseEvent failed", t);
        }
    }

    /**
     * Constructs and dispatches a single synthesized mouse event to the
     * resolved client component. Guarded so a client listener throw cannot
     * unwind the calling thread.
     */
    private void emitPoint(int id, int x, int y, long when) {
        try {
            java.awt.Component target = resolveInputTarget();
            if (target == null) return;
            java.awt.event.MouseEvent ev = new java.awt.event.MouseEvent(
                target, id, when, 0, x, y, 1, false, 1);
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
    private volatile boolean rasterizerRepointed = false;

    /**
     * The desktop client re-points the 3D rasterizer at the display buffer
     * during its resize path; on this port that call never happens, so the
     * scene renders into a 65536-element scratch array that is never shown.
     * Replicate the call once the buffer exists (fh.ap -> fq.de sets the
     * rasterizer's pixel target + width/height).
     */
    private void repointSceneRasterizerOnce() {
        if (rasterizerRepointed) return;
        rasterizerRepointed = true;
        try {
            ClassLoader cl = clientClass != null ? clientClass.getClassLoader() : null;
            if (cl == null || bufferProviderStatic == null) {
                rasterizerRepointed = false;
                return;
            }
            java.lang.reflect.Field faz = findFieldInChain(bufferProviderStatic.getClass(), "az");
            faz.setAccessible(true);
            Object img = faz.get(bufferProviderStatic);
            if (!(img instanceof java.awt.Image)) {
                rasterizerRepointed = false;
                return;
            }
            int[] dispPx = ((java.awt.Image) img).getPixels();
            Class<?> fh = cl.loadClass("fh");
            Class<?> yw = cl.loadClass("yw");
            java.lang.reflect.Method eu = yw.getMethod("eu", int[].class, int.class, int.class, float[].class);
            float[] floatBuf = null;
            try {
                floatBuf = (float[]) yw.getField("ad").get(null);
            } catch (Exception ignored) {}
            eu.invoke(null, dispPx, GAME_W - 1, GAME_H, floatBuf);
            java.lang.reflect.Field fae = fh.getDeclaredField("ae");
            fae.setAccessible(true);
            fae.set(null, dispPx);
            for (String inst : new String[]{"aj", "af", "az"}) {
                try {
                    java.lang.reflect.Field fi = fh.getDeclaredField(inst);
                    fi.setAccessible(true);
                    Object rasterizer = fi.get(null);
                    if (rasterizer != null) {
                        java.lang.reflect.Field faa = findFieldInChain(rasterizer.getClass(), "aa");
                        faa.setAccessible(true);
                        faa.set(rasterizer, dispPx);
                    }
                } catch (Exception ignored) {}
            }
            Log.i(TAG, "Repointed scene rasterizer to display buffer "
                + System.identityHashCode(dispPx) + "(" + dispPx.length + ") yw.aj="
                + (yw.getField("aj").get(null) != null ? System.identityHashCode(yw.getField("aj").get(null)) : "null"));
        } catch (Throwable t) {
            rasterizerRepointed = false;
            Log.w(TAG, "Repoint rasterizer failed: " + t);
        }
    }

    /**
     * The software renderer draws the 3D scene into a small square scene
     * buffer (fh.aj.aa, 65536 = 256x256) and the client never scales it up
     * into the display buffer (the RuneLite runtime/GPU plugin does that on
     * desktop). Upscale the scene buffer into the display right before the
     * blit; the 2D UI redraws over it every frame so this is safe timing-wise.
     */
    private void compositeSceneBuffer(java.awt.Image displayImage) {
        try {
            ClassLoader cl = clientClass != null ? clientClass.getClassLoader() : null;
            if (cl == null) return;
            Class<?> fh = cl.loadClass("fh");
            java.lang.reflect.Field faj = fh.getDeclaredField("aj");
            faj.setAccessible(true);
            Object rasterizer = faj.get(null);
            if (rasterizer == null) return;
            java.lang.reflect.Field faa = findFieldInChain(rasterizer.getClass(), "aa");
            faa.setAccessible(true);
            int[] scene = (int[]) faa.get(rasterizer);
            int[] disp = displayImage.getPixels();
            if (scene == null || disp == null) return;
            int srcW = 256;
            int srcH = 256;
            try {
                java.lang.reflect.Field fw = findFieldInChain(rasterizer.getClass(), "ab");
                fw.setAccessible(true);
                java.lang.reflect.Field fh2 = findFieldInChain(rasterizer.getClass(), "af");
                fh2.setAccessible(true);
                int w = fw.getInt(rasterizer);
                int h = fh2.getInt(rasterizer);
                if (w > 0 && w <= 1024 && h > 0 && h <= 1024 && w * h <= scene.length) {
                    srcW = w;
                    srcH = h;
                }
            } catch (Exception ignored) {}
            int dispW = displayImage.getWidth();
            int dispH = displayImage.getHeight();
            for (int y = 0; y < dispH; y++) {
                int srcY = y * srcH / dispH;
                int srcRow = srcY * srcW;
                int dstRow = y * dispW;
                for (int x = 0; x < dispW; x++) {
                    int srcX = x * srcW / dispW;
                    if (srcRow + srcX < scene.length) {
                        disp[dstRow + x] = scene[srcRow + srcX];
                    }
                }
            }
            if (!sceneCompositeLogged) {
                sceneCompositeLogged = true;
                Log.i(TAG, "SceneComposite: src=" + scene.length + " (" + srcW + "x" + srcH
                    + ") -> " + dispW + "x" + dispH);
            }
        } catch (Throwable t) {
            Log.w(TAG, "SceneComposite failed: " + t);
        }
    }

    private volatile boolean sceneCompositeLogged = false;

    private volatile boolean tileLayoutLogged = false;

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
     * Diagnostic: samples of the display buffer (what drawImage blits) and the
     * applet bridge (what the render thread scales to the surface), to locate
     * where the composited scene gets lost.
     */
    private static String dumpBridgeContent() {
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
        if (ap != null && ap.length >= GAME_W * GAME_H) {
            int[] s = new int[]{0, 100 * GAME_W + 100, 250 * GAME_W + 380, 300 * GAME_W + 200, 400 * GAME_W + 200};
            sb.append(" appletSamp=");
            for (int i = 0; i < s.length; i++) {
                sb.append(String.format("%08X,", ap[s[i]]));
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
     * Delivers AWT KeyEvents (KEY_PRESSED + KEY_TYPED + KEY_RELEASED) for the
     * given text into every key listener registered on the client component or
     * its canvas. Mirrors desktop AWT: printable chars go through KEY_TYPED
     * with the char; the code is the upper-case VK for letters, else the char.
     */
    private void dispatchKeyText(String text) {
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

    /** Delivers a special-key event (ENTER, BACK_SPACE, ESCAPE, ...). */
    private void dispatchKeyCode(int keyCode, char keyChar) {
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
        awtBridge = new AWTBridge(width, height);
        renderBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
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
        while (isRunning) {
            if (clientInstance == null) {
                // Nothing to render yet - idle so background work (updates,
                // dexing) isn't starved by a 60fps no-op loop.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
                continue;
            }
            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null && awtBridge != null) {
                    // Only clear the applet when the game isn't running - the game
                    // overwrites every pixel each frame via the callbacks.draw blit.
                    if (clientInstance == null) {
                        int stoneGray = 0xFF2B2824;
                        java.util.Arrays.fill(appletPixels, stoneGray);
                    }

                    if (clientInstance != null) {
                        // The client thread delivers completed frames via the
                        // callbacks.draw proxy (drawImage under renderLock). We
                        // must NOT call client.paint() here: it blits the game's
                        // LIVE frame buffer while the client thread is rendering
                        // the next frame into it, producing torn frames.
                        synchronized (renderLock) {
                            System.arraycopy(appletPixels, 0, frameScratch, 0, appletPixels.length);
                        }
                    } else {
                        System.arraycopy(appletPixels, 0, frameScratch, 0, appletPixels.length);
                    }

                    int[] destPixels = awtBridge.getRawPixels();
                    int destW = awtBridge.getWidth();
                    int destH = awtBridge.getHeight();

                    for (int y = 0; y < destH; y++) {
                        int srcY = y * GAME_H / destH;
                        int destRowOffset = y * destW;
                        int srcRowOffset = srcY * GAME_W;
                        for (int x = 0; x < destW; x++) {
                            int srcX = x * GAME_W / destW;
                            destPixels[destRowOffset + x] = frameScratch[srcRowOffset + srcX];
                        }
                    }

                    renderBitmap.setPixels(awtBridge.getRawPixels(), 0, awtBridge.getWidth(), 0, 0, awtBridge.getWidth(), awtBridge.getHeight());
                    canvas.drawBitmap(renderBitmap, 0, 0, null);

                    canvas.drawText("RuneLite Mobile (AWT Bridge Active)", 40, 70, debugPaint);
                    canvas.drawText(loaderStatus, 40, 130, highlightPaint);

                    // Diagnostic: report the game's state machine position periodically
                    long now = System.currentTimeMillis();
                    if (now - lastStateLog > 5000 && clientObject != null) {
                        lastStateLog = now;
                        try {
                            Class<?> clientInterface = clientClass.getClassLoader().loadClass("net.runelite.api.Client");
                            Object state = clientInterface.getMethod("getGameState").invoke(clientObject);
                            Object loginIdx = clientInterface.getMethod("getLoginIndex").invoke(clientObject);
                            StringBuilder sb = new StringBuilder("GameState: " + state + " loginIndex: " + loginIdx);
                            for (String probe : new String[]{"getWorld", "getWorldHost", "getCurrentLoginField"}) {
                                try {
                                    Object v = clientInterface.getMethod(probe).invoke(clientObject);
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
            try {
                Thread.sleep(16);
            } catch (InterruptedException ignored) {}
        }
    }

    @Override
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
