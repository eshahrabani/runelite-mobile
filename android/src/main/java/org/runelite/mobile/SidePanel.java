package org.runelite.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.runelite.mobile.bridge.UiBridge;
import org.runelite.mobile.host.RuneLiteHost;

/**
 * Native side panel: the mobile chrome around RuneLite's real Swing sidebar.
 *
 * <p>A collapsible drawer on the right edge. The drawer body is RuneLite's own Swing
 * sidebar, rendered by a hand-written Swing runtime in {@code core/} into an ARGB bitmap
 * and presented by {@link SwingSidebarView} -- the app no longer reimplements plugins,
 * config or the host tab natively. While the runtime is not up, a status line stands in
 * for the sidebar body.
 *
 * <p>The right-edge column (the drawer handle and the keyboard toggle) is always present
 * and consumes {@code handleWidthPx} of screen width; an open drawer adds
 * {@code drawerWidthPx}. The game surface is <em>inset</em> by that sum -- the host reads
 * {@link #occupiedWidthPx()} and resizes the surface (see {@link Listener}) -- instead of
 * the drawer floating over a full-size game. The column and the drawer swallow touches
 * inside their own bounds, and the game's own input mapping accounts for the inset, so no
 * tap is lost.
 */
public final class SidePanel {

    /** Same preferences file the launcher uses for its own UI state. */
    private static final String PREFS_NAME = "RuneLiteMobilePrefs";
    private static final String PREFS_OPEN = "sidePanelOpen";

    /** Callbacks the host uses to follow the drawer (game-area inset, launcher, keyboard). */
    public interface Listener {
        /** Drawer opened or closed: the game-area inset changed. */
        void onOpenChanged(boolean open);

        /** The drawer header's launcher shortcut was tapped. */
        void onShowLauncherRequested();

        /** The keyboard toggle at the foot of the right-edge column was tapped. */
        void onKeyboardToggleRequested();
    }

    private final Activity activity;
    private final FrameLayout root;
    /**
     * The drawer width is the single knob for the Swing UI scale: the drawer is
     * {@code min(42% of screen, 380 dp)} wide and {@link SwingSidebarView} maps that onto
     * RuneLite's 272-logical-px sidebar (and thumbnails its bitmaps to a scale that does
     * not exceed the drawer), so widening or narrowing this rule retunes {@code uiScale}.
     */
    private final int drawerWidthPx;
    private final int handleWidthPx;
    private final float density;
    private final SharedPreferences prefs;

    private final FrameLayout container;
    private final LinearLayout column;
    private final LinearLayout drawer;
    private final TextView handle;
    private final FrameLayout content;
    private final TextView statusText;

    /** Set by {@link #setSwingSidebarView(View)} once the host has built the runtime. */
    private SwingSidebarView swingSidebarView;

    private boolean open;
    private boolean available = true;
    private Listener listener;

    public SidePanel(Activity activity, FrameLayout rootLayout) {
        this.activity = activity;
        this.root = rootLayout;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        this.density = metrics.density;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.drawerWidthPx = Math.min((int) (0.42f * metrics.widthPixels), (int) (380 * density));
        this.handleWidthPx = (int) (44 * density);

        container = new FrameLayout(activity);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        container.setLayoutParams(containerParams);
        container.setClipChildren(false);

        // Right-edge column: the port's only always-present chrome. It holds the drawer
        // handle (top, fills the remaining height) and the keyboard toggle (foot); the
        // host insets the game surface by the column + drawer width, so it takes screen
        // space instead of covering the game.
        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(UiTheme.corners(UiTheme.SURFACE_ALT, UiTheme.GOLD_DIM, 1.5f,
            new float[]{14, 14, 0, 0, 0, 0, 14, 14}, density));
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
            handleWidthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        column.setLayoutParams(columnParams);

        handle = new TextView(activity);
        handle.setTextSize(20f);
        handle.setTextColor(UiTheme.GOLD);
        handle.setGravity(Gravity.CENTER);
        handle.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // A touch listener, not a click listener: a TextView with an OnClickListener
        // consumes the down event itself, but the column's swallowing listener would
        // never see it -- and a listener is what keeps the strip out of the game.
        handle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                toggle();
            }
            return true;
        });
        column.addView(handle);

        TextView keyboardToggle = new TextView(activity);
        keyboardToggle.setText("⌨");
        keyboardToggle.setTextSize(16f);
        keyboardToggle.setTextColor(UiTheme.TEXT);
        keyboardToggle.setGravity(Gravity.CENTER);
        keyboardToggle.setBackground(UiTheme.rounded(UiTheme.SURFACE, UiTheme.GOLD_DIM, 1f, 10f, density));
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(
            handleWidthPx, (int) (48 * density));
        keyboardParams.bottomMargin = (int) (8 * density);
        keyboardToggle.setLayoutParams(keyboardParams);
        keyboardToggle.setOnClickListener(v -> {
            if (listener != null) {
                listener.onKeyboardToggleRequested();
            }
        });
        column.addView(keyboardToggle);

        container.addView(column);

        // Drawer.
        drawer = new LinearLayout(activity);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackground(UiTheme.corners(UiTheme.SURFACE, UiTheme.BORDER, 1.5f,
            new float[]{16, 16, 0, 0, 0, 0, 16, 16}, density));
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(
            drawerWidthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        drawerParams.rightMargin = handleWidthPx;
        drawer.setLayoutParams(drawerParams);
        drawer.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));

        // Header: the brand and the launcher shortcut (the floating gear button the
        // host used to draw over the game is gone).
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = new TextView(activity);
        brand.setText("RuneLite");
        brand.setTextSize(15f);
        brand.setTextColor(UiTheme.GOLD);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(brand);
        TextView launcherButton = new TextView(activity);
        launcherButton.setText("⌂");
        launcherButton.setTextSize(16f);
        launcherButton.setTextColor(UiTheme.TEXT);
        launcherButton.setGravity(Gravity.CENTER);
        launcherButton.setBackground(UiTheme.rounded(UiTheme.SURFACE_ALT, UiTheme.GOLD_DIM, 1f, 10f, density));
        launcherButton.setLayoutParams(new LinearLayout.LayoutParams(
            (int) (34 * density), (int) (34 * density)));
        launcherButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShowLauncherRequested();
            }
        });
        header.addView(launcherButton);
        drawer.addView(header);

        // Body: the Swing sidebar (added later by setSwingSidebarView) with a status
        // line that stands in for it while the runtime is not up.
        content = new FrameLayout(activity);
        content.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusText = new TextView(activity);
        statusText.setTextSize(12f);
        statusText.setTextColor(UiTheme.TEXT_MUTED);
        statusText.setPadding(0, (int) (8 * density), 0, 0);
        content.addView(statusText, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        drawer.addView(content);

        // The column and the drawer swallow touches so a tap never walks the
        // character. Child views get their events first, so the handle and the
        // keyboard toggle keep working.
        View.OnTouchListener swallow = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                return false;
            }
            return true;
        };
        drawer.setOnTouchListener(swallow);
        column.setOnTouchListener(swallow);

        container.addView(drawer);
        root.addView(container);

        open = prefs.getBoolean(PREFS_OPEN, false);
        updateStatus();
        applyState();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Hands the drawer its Swing sidebar body. Called by the host once the RuneLite
     * runtime and its render loop are up; may be called at any time after construction
     * (it is how the view is added, since the host builds it after the drawer).
     */
    public void setSwingSidebarView(View view) {
        if (view == null) {
            return;
        }
        if (swingSidebarView != null) {
            content.removeView(swingSidebarView);
        }
        swingSidebarView = (SwingSidebarView) view;
        // Behind the status line: when the runtime is not up the sidebar paints nothing
        // and the status text must stay visible.
        content.addView(swingSidebarView, 0, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swingSidebarView.setSuspended(!open);
        updateStatus();
    }

    /**
     * Shows or hides the whole right-edge chrome. Hiding also closes the drawer, and
     * the trailing notification is what applies the inset when the drawer was restored
     * <em>open</em> from {@code sidePanelOpen} and becomes available again.
     */
    public void setAvailable(boolean available) {
        this.available = available;
        container.setVisibility(available ? View.VISIBLE : View.GONE);
        if (!available) {
            close();
        }
        if (listener != null) {
            listener.onOpenChanged(open);
        }
    }

    /** Screen pixels the chrome hides from the game: the column, plus the drawer when open. */
    public int occupiedWidthPx() {
        return handleWidthPx + (open ? drawerWidthPx : 0);
    }

    public void toggle() {
        open = !open;
        applyState();
        prefs.edit().putBoolean(PREFS_OPEN, open).apply();
    }

    public void open() {
        if (!open) {
            toggle();
        }
    }

    public void close() {
        if (open) {
            toggle();
        }
    }

    public boolean isOpen() {
        return open;
    }

    private void applyState() {
        drawer.setVisibility(open ? View.VISIBLE : View.GONE);
        // Sync both sides of the bridge on every transition: the Swing sidebar only
        // renders/publishes while visible, and only needs to composite while open.
        UiBridge.setSidebarVisible(open);
        if (swingSidebarView != null) {
            swingSidebarView.setSuspended(!open);
        }
        if (open) {
            updateStatus();
        }
        handle.setText(open ? "›" : "‹");
        if (listener != null) {
            listener.onOpenChanged(open);
        }
    }

    /** The body is the Swing sidebar; the status line stands in only while it is not up. */
    private void updateStatus() {
        boolean ready = UiBridge.hasSidebar();
        statusText.setVisibility(ready ? View.GONE : View.VISIBLE);
        if (!ready) {
            statusText.setText("RuneLite runtime not running: " + RuneLiteHost.status());
        }
    }
}
