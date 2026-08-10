package org.runelite.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
    private long lastStateLog = 0;
    private long lastDrawLog = 0;

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

    // ── Login WebView ───────────────────────────────────────────────────────
    private WebView loginWebView;
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
    // Jagex account login (two-leg OAuth in the WebView)
    // ═══════════════════════════════════════════════════════════════════════

    private void startJagexLogin() {
        if (loginActive) return;
        loginActive = true;
        loginStage = LoginStage.LEG1;
        ensureLoginWebView();
        leg1Verifier = JagexOAuthClient.generateVerifier();
        String challenge = JagexOAuthClient.createChallenge(leg1Verifier);
        String state = JagexOAuthClient.randomToken(16);
        String nonce = JagexOAuthClient.randomToken(16);
        String url = JagexOAuthClient.buildLauncherAuthorizeUrl(state, nonce, challenge);
        Log.i(TAG, "[1] Navigating to Jagex authorize (leg 1)");
        tvLoginStatus.setText("Opening Jagex sign-in...");
        loginOverlay.setVisibility(View.VISIBLE);
        loginWebView.loadUrl(url);
    }

    /** Lazy WebView creation: instantiating it eagerly spawns a renderer process. */
    private void ensureLoginWebView() {
        if (loginWebView != null) return;
        loginWebView = new WebView(this);
        loginWebView.getSettings().setJavaScriptEnabled(true);
        loginWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleLoginNavigation(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                runOnUiThread(() -> {
                    if (loginActive) {
                        tvLoginStatus.setText("Loading...");
                    }
                });
            }
        });
        FrameLayout.LayoutParams wvParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        loginOverlay.addView(loginWebView, wvParams);
    }

    private void startConsentLeg() {
        loginStage = LoginStage.LEG2;
        leg2State = JagexOAuthClient.randomToken(16);
        leg2Nonce = JagexOAuthClient.randomToken(16);
        String url = JagexOAuthClient.buildConsentAuthorizeUrl(leg2State, leg2Nonce);
        Log.i(TAG, "[4] Navigating to consent authorize (leg 2)");
        tvLoginStatus.setText("Confirming account...");
        loginWebView.loadUrl(url);
    }

    private boolean handleLoginNavigation(String url) {
        if (!loginActive) return false;

        switch (loginStage) {
            case LEG1: {
                if (!url.startsWith(JagexOAuthClient.LAUNCHER_REDIRECT_URI) && !url.startsWith("jagex:")) {
                    return false;
                }
                String code = parseUrlParam(url, "code");
                if (code == null || code.isEmpty()) {
                    failLogin("Login failed: no authorization code in the redirect.");
                    return true;
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
                return true;
            }
            case LEG2: {
                if (!url.startsWith("http://localhost") && !url.startsWith("http://127.0.0.1")) {
                    return false;
                }
                String idToken = parseFragmentParam(url, "id_token");
                if (idToken == null || idToken.isEmpty()) {
                    failLogin("Login failed: Jagex did not return a consent token.");
                    return true;
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
                return true;
            }
            default:
                return false;
        }
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
        loginWebView.stopLoading();
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

    private static String parseFragmentParam(String url, String key) {
        int idx = url.indexOf('#');
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
            boolean useExisting = localJarFile.exists() && localJarFile.length() > 0
                && !versionIsOlderThanAsset();
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
                    switch (methodName) {
                        case "error":
                            Log.e(TAG, "Callbacks.error: " + args[0], (Throwable) args[1]);
                            return null;
                        case "draw": {
                            // Frame blit: copy the rendered game buffer into the graphics
                            if (args.length >= 2 && args[0] != null && args[1] instanceof java.awt.Graphics) {
                                try {
                                    Object image = args[0].getClass().getMethod("getImage").invoke(args[0]);
                                    if (image instanceof java.awt.Image) {
                                        java.awt.Image img = (java.awt.Image) image;
                                        long now = System.currentTimeMillis();
                                        if (now - lastDrawLog > 5000) {
                                            lastDrawLog = now;
                                            Log.d(TAG, "callbacks.draw: " + img.getWidth() + "x" + img.getHeight());
                                        }
                                        ((java.awt.Graphics) args[1]).drawImage(img, 0, 0, null);
                                    }
                                } catch (Exception e) {
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
                Toast.makeText(this, "Loader Error: " + finalMsg, Toast.LENGTH_LONG).show();
            });
        }
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
            int x = (int) (event.getX() * GAME_W / v.getWidth());
            int y = (int) (event.getY() * GAME_H / v.getHeight());
            long when = System.currentTimeMillis();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    pointerDown = true;
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_PRESSED, x, y, when);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (pointerDown) {
                        dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_DRAGGED, x, y, when);
                    } else {
                        dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_MOVED, x, y, when);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_RELEASED, x, y, when);
                    dispatchMouseEvent(java.awt.event.MouseEvent.MOUSE_CLICKED, x, y, when);
                    pointerDown = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    pointerDown = false;
                    break;
                case MotionEvent.ACTION_SCROLL: {
                    int rotation = -(int) Math.round(event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                    if (rotation != 0) {
                        dispatchMouseWheel(x, y, rotation, when);
                    }
                    break;
                }
            }
            return true;
        });
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
        java.awt.Component target = resolveInputTarget();
        if (target == null) return;
        if (id != java.awt.event.MouseEvent.MOUSE_MOVED) {
            Log.d(TAG, "Dispatch mouse id=" + id + " at (" + x + "," + y + ") to " + target.getClass().getSimpleName());
        }
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
                        java.awt.Graphics2D bridgeGraphics = new java.awt.Graphics2D(appletPixels, GAME_W, GAME_H);
                        clientInstance.paint(bridgeGraphics);
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
                            destPixels[destRowOffset + x] = appletPixels[srcRowOffset + srcX];
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
                            Log.i(TAG, "GameState: " + state + " loginIndex: " + loginIdx);
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
        if (loginWebView != null) {
            loginWebView.destroy();
        }
    }
}
