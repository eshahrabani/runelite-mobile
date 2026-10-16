package org.runelite.mobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.runelite.mobile.bridge.AWTBridge;
import org.runelite.mobile.bridge.UiBridge;

import java.awt.event.MouseEvent;

/**
 * The window layer: the core's virtual Swing windows (the colour picker, plugin dialogs)
 * painted into an ARGB bitmap and shown centred on the whole screen, above the game
 * surface and the drawer.
 *
 * <p>RuneLite's modal dialogs are real Swing {@code Window}s; while one is up the core
 * routes <em>all</em> input to it ({@link UiBridge#dispatchMouse} picks the window layer
 * automatically), so this view is a full-screen, transparent, touch-consuming layer:
 * nothing below it receives the touches a dialog swallowed. It is {@code GONE} unless
 * {@link UiBridge#hasVisibleWindow()} holds, which is what {@link #refresh()} maintains
 * — that call arrives from the Swing thread (via {@link SwingSidebarView}'s
 * {@code windowDirty()}), so everything here re-enters the Android main thread first.
 *
 * <p>The window is painted at an integer-ish scale ({@link UiBridge#setWindowBounds}) and
 * centred like a desktop dialog; touches are mapped back through the same offset, and
 * the {@value UiBridge#WINDOW_TITLE_HEIGHT}-logical-px title bar is part of those
 * coordinates, which is also how the core turns a tap on the close glyph into the
 * window's close.
 */
@SuppressLint("ClickableViewAccessibility")
public class SwingWindowLayer extends View {

    private static final int MIN_SCALE = 1;
    private static final int MAX_SCALE = 3;

    /** At most one render per frame; further requests coalesce into the next one. */
    private static final long FRAME_INTERVAL_MS = 16L;

    /** Reference size of a RuneLite dialog ({@code RuneliteColorPicker}), in logical px. */
    private static final float REFERENCE_WIDTH = 410f;
    private static final float REFERENCE_HEIGHT = 380f;
    /** Leave this much of the screen around the window. */
    private static final float SCREEN_FRACTION = 0.9f;

    private final Paint blit = new Paint();

    /** The stage: one bitmap of the whole screen, the window drawn in its top-left. */
    private Bitmap stage;
    private int[] pixels;
    private int screenWidth;
    private int screenHeight;

    /** Window geometry, in logical units plus the scale it is presented at. */
    private int contentWidth;
    private int contentHeight;
    private float windowScale = 1f;
    /** Where the window's top-left corner lands on the screen (device px). */
    private int dstLeft;
    private int dstTop;
    /** Gesture anchor in window-logical units (the core hit-tests in those). */
    private float lastLogicalX;
    private float lastLogicalY;

    /** Coalescing state; only ever touched on the Android main thread. */
    private boolean renderPosted;
    /** One paint in flight on the Swing thread; marks that arrive meanwhile coalesce. */
    private boolean painting;
    private boolean dirtyWhilePainting;
    private long lastRenderUptimeMs;

    /** Guards the refresh hop, which is requested from the Swing thread. */
    private final Object refreshLock = new Object();
    private boolean refreshPosted;

    public SwingWindowLayer(Context context) {
        super(context);
        blit.setFilterBitmap(false);
        blit.setDither(false);
        setVisibility(GONE);
    }

    // ------------------------------------------------------------------ stage

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        screenWidth = w;
        screenHeight = h;
        pixels = new int[w * h];
        // Alpha stays on: only the window rectangle is drawn, and it must not carry a
        // black surround (the core zero-fills the buffer, i.e. fully transparent).
        stage = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        // The geometry is only now known; refresh() re-reads the window and renders.
        refresh();
    }

    /** A window appeared, resized, repainted or closed. Safe from any thread. */
    public void refresh() {
        synchronized (refreshLock) {
            if (refreshPosted) {
                return;   // the queued hop already reads the current state
            }
            refreshPosted = true;
        }
        post(this::applyRefresh);
    }

    /** The main-thread half of {@link #refresh()}: show/hide, then render. */
    private void applyRefresh() {
        synchronized (refreshLock) {
            refreshPosted = false;
        }
        boolean visible = UiBridge.hasVisibleWindow();
        setVisibility(visible ? VISIBLE : GONE);
        if (visible) {
            postRender();
        }
    }

    /** Queues one render at most {@link #FRAME_INTERVAL_MS} after the previous one. */
    private void postRender() {
        if (renderPosted) {
            return;
        }
        renderPosted = true;
        long delay = FRAME_INTERVAL_MS - (SystemClock.uptimeMillis() - lastRenderUptimeMs);
        postDelayed(this::runRender, Math.max(0L, delay));
    }

    private void runRender() {
        renderPosted = false;
        lastRenderUptimeMs = SystemClock.uptimeMillis();
        render();
    }

    /** Paints the core's window into {@link #pixels} and blits it. Main thread. */
    private void render() {
        Bitmap bitmap = stage;
        int[] buffer = pixels;
        if (bitmap == null || buffer == null || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }
        if (!applyWindowGeometry()) {
            return;
        }
        if (painting) {
            dirtyWhilePainting = true;
            return;
        }
        painting = true;
        final int width = screenWidth;
        final int height = screenHeight;
        // Asynchronous on purpose: the main thread must never wait for a Swing paint (see
        // SwingSidebarView.render).
        AWTBridge.post(() -> {
            try {
                UiBridge.paintWindow(buffer, width, height);
            } catch (Throwable t) {
                android.util.Log.e("RuneLiteMobile", "window layer paint failed", t);
            }
            post(() -> {
                if (width == screenWidth && height == screenHeight) {
                    bitmap.setPixels(buffer, 0, width, 0, 0, width, height);
                    invalidate();
                }
                painting = false;
                if (dirtyWhilePainting) {
                    dirtyWhilePainting = false;
                    postRender();
                }
            });
        });
    }

    /**
     * Re-reads the window's logical size (it can change while it is up), recomputes the
     * presentation scale and the centring offset and publishes both to the core.
     * Returns false when there is no window after all.
     */
    private boolean applyWindowGeometry() {
        int[] size = UiBridge.windowLogicalSize();
        if (size == null) {
            setVisibility(GONE);
            return false;
        }
        float sidebarScale = UiBridge.sidebarScale();
        if (sidebarScale <= 0f) {
            sidebarScale = MIN_SCALE;
        }
        // The window never renders larger than the drawer does, and always leaves a
        // tenth of the screen free on each side.
        float scale = Math.min(sidebarScale, Math.min(
            (float) Math.floor(SCREEN_FRACTION * screenWidth / REFERENCE_WIDTH),
            (float) Math.floor(SCREEN_FRACTION * screenHeight / REFERENCE_HEIGHT)));
        windowScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        contentWidth = size[0];
        contentHeight = size[1];
        dstLeft = (screenWidth - Math.round(contentWidth * windowScale)) / 2;
        dstTop = (screenHeight - Math.round(contentHeight * windowScale)) / 2;
        UiBridge.setWindowBounds(contentWidth, contentHeight, windowScale);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bitmap = stage;
        if (bitmap == null) {
            return;
        }
        int w = Math.round(contentWidth * windowScale);
        int h = Math.round(contentHeight * windowScale);
        if (w <= 0 || h <= 0) {
            return;
        }
        blit.setFilterBitmap(false);
        // 1:1 copy of the window's pixels: the core painted them at exactly this scale.
        canvas.drawBitmap(bitmap, new Rect(0, 0, w, h),
            new Rect(dstLeft, dstTop, dstLeft + w, dstTop + h), blit);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                remember(event);
                dispatch(lastLogicalX, lastLogicalY, MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, 0);
                dispatch(lastLogicalX, lastLogicalY, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 1);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                remember(event);
                dispatch(lastLogicalX, lastLogicalY, MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1, 1);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                remember(event);
                dispatch(lastLogicalX, lastLogicalY, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 1);
                dispatch(lastLogicalX, lastLogicalY, MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 1);
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                dispatch(lastLogicalX, lastLogicalY, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 1);
                return true;
            }
            default:
                // Every event is consumed: the layer is the dialog's modality gate,
                // nothing below it (game, drawer) may see the gesture.
                return true;
        }
    }

    /** Maps a touch to the window's logical coordinates (title bar included). */
    private void remember(MotionEvent event) {
        lastLogicalX = (event.getX() - dstLeft) / windowScale;
        lastLogicalY = (event.getY() - dstTop) / windowScale;
    }

    private void dispatch(float logicalX, float logicalY, int id, int button, int clickCount) {
        UiBridge.dispatchMouse((int) logicalX, (int) logicalY, id, button, clickCount, 0,
            System.currentTimeMillis());
    }
}
