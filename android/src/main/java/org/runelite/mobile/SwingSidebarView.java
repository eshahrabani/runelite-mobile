package org.runelite.mobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import org.runelite.mobile.bridge.AWTBridge;
import org.runelite.mobile.bridge.UiBridge;

import java.awt.event.MouseEvent;

/**
 * The drawer body: RuneLite's <em>real</em> Swing sidebar, painted by the core into an
 * ARGB bitmap and shown here as a plain {@link View}.
 *
 * <p>The app owns the pixels and the input, the core owns the component tree (see
 * {@link UiBridge}): this view reports its size and scale once the drawer lays it out,
 * asks the core to repaint into {@link #pixels} whenever the sidebar says it is dirty
 * ({@link UiBridge.Host#sidebarDirty()}, delivered on the Swing thread) and forwards
 * every touch as AWT mouse events, converted from device pixels to the sidebar root's
 * logical coordinate space.
 *
 * <p>While the core has no sidebar ({@link UiBridge#hasSidebar()} is false) nothing is
 * drawn, so the drawer's runtime-status {@code TextView} placeholder stays visible.
 *
 * <p>Gestures (the same mapping the game surface uses, see {@code MainActivity}):
 * one finger is the mouse — the press is held back until the gesture is known, so a
 * scroll or drag can never click what it started on: a tap sends press + release + click
 * at the touch-down point, a drag sends the press there and streams the drag, and two
 * taps within {@value #DOUBLE_TAP_MS} ms are a double click (Swing's
 * {@code getClickCount() == 2}). A finger held for {@value #LONG_PRESS_MS} ms is a right
 * click (which is also how a Swing tooltip is surfaced, as a {@code Toast}); two fingers
 * are the wheel, and a two-finger tap is a right click. Key events are fed in by
 * {@code MainActivity} while a Swing text component owns the focus.
 */
@SuppressLint("ClickableViewAccessibility")
public class SwingSidebarView extends View implements UiBridge.Host {

    private static final String TAG = "RuneLiteMobile";

    /** Device debugging of the drawer render path; off in shipped builds. */
    private static final boolean DEBUG = false;

    /** What the drawer/app has to do when the Swing sidebar asks for it. */
    public interface Callback {
        /** A Swing text component took ({@code true}) or lost ({@code false}) the focus. */
        void onTextInputRequested(boolean show);

        /** RuneLite's own sidebar toggle was clicked: the app closes the drawer. */
        void onCloseSidebarRequested();
    }

    /**
     * {@code net.runelite.client.ui.ClientUI.SIDEBAR_LOGICAL_WIDTH}, hard-coded because
     * {@code ClientUI} lives in the asset dex, which this dex cannot see. The drawer is
     * sized so that this many logical pixels fit; everything else follows from it
     * ({@link SidePanel#onScreenWidthChanged} widens the drawer to keep the scale from
     * clipping the sidebar).
     */
    static final int SIDEBAR_LOGICAL_WIDTH = 272;

    /** The integer scale the core renders the sidebar at (1 px borders stay 1 px). */
    static final int MIN_SCALE = 1;
    static final int MAX_SCALE = 3;

    /** At most one render per frame; further dirty marks coalesce into the next one. */
    private static final long FRAME_INTERVAL_MS = 16L;

    /** A finger still after this long is a right click. */
    private static final long LONG_PRESS_MS = 400L;
    /** Two fingers that both lift within this long, without moving, are a tap. */
    private static final long TWO_FINGER_TAP_MS = 400L;
    /** Travel that turns a touch into a drag (and cancels the long press). */
    private static final float DRAG_SLOP_DP = 10f;
    /** Two-finger centroid travel that produces at least one wheel notch. */
    private static final float WHEEL_STEP_DP = 24f;
    /** Device pixels of two-finger travel per wheel rotation unit. */
    private static final float WHEEL_PIXELS_PER_UNIT = 8f;
    /** Two taps within this long (and within {@link #DOUBLE_TAP_SLOP_DP}) are a double click. */
    private static final long DOUBLE_TAP_MS = 350L;
    /** How far apart the two taps of a double click may land. */
    private static final float DOUBLE_TAP_SLOP_DP = 24f;

    private final Callback callback;
    private final Paint blit = new Paint();
    private final Object renderLock = new Object();
    /** Display density: the gesture thresholds are in dp (never changes). */
    private final float density;

    /** The window layer this view hands {@link UiBridge.Host#windowDirty()} to. */
    private volatile SwingWindowLayer windowLayer;

    /** The stage: one bitmap of the sidebar, at the drawer's device size. */
    private Bitmap stage;
    private int[] pixels;
    private int contentWidthPx;
    private int contentHeightPx;
    private float uiScale = 1f;

    /** Coalescing state, guarded by {@link #renderLock}: a render is queued. */
    private boolean renderPosted;
    private long lastRenderUptimeMs;
    /** The drawer is closed: nothing to paint, the next open repaints in full. */
    private volatile boolean suspended;
    /** One paint in flight on the Swing thread; marks that arrive meanwhile coalesce. */
    private boolean painting;
    private boolean dirtyWhilePainting;

    // Touch gesture state (Android main thread only).
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private float downX;
    private float downY;
    private boolean pressed;
    private boolean dragged;
    private boolean twoFinger;
    /** The gesture started over a scrollable panel: a vertical drag scrolls it. */
    private boolean scrollGesture;
    private float lastScrollY;
    private boolean twoFingerTap;
    private boolean longPressFired;
    private float twoFingerLastY;
    private float wheelAccumPx;
    /** The left button is down: a MOUSE_PRESSED was delivered for this gesture. */
    private boolean pressSent;
    /** When the last tap ended and where it went down, for double-click detection. */
    private long lastTapMs;
    private float lastTapX;
    private float lastTapY;
    private final Runnable longPress = this::fireLongPress;

    public SwingSidebarView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        this.density = context.getResources().getDisplayMetrics().density;
        blit.setFilterBitmap(false);
        blit.setDither(false);
    }

    /** The window layer (the colour picker and other virtual dialogs). */
    public void setWindowLayer(SwingWindowLayer layer) {
        windowLayer = layer;
    }

    // ------------------------------------------------------------------ stage

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        contentWidthPx = w;
        contentHeightPx = h;
        // The integer scale keeps the core's 1 px borders exactly 1 px: fractional
        // scaling would smear them, and core's rasteriser is hard-edged. Flooring (rather
        // than rounding) picks the largest scale the content width actually fits: rounding
        // can pick a scale whose 272-px logical width is wider than the drawer, which cuts
        // the right-hand tab strip off -- the narrow portrait drawer is exactly that case.
        int fitted = w / SIDEBAR_LOGICAL_WIDTH;
        float scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, fitted));
        uiScale = scale;
        final int logicalW = Math.round(w / scale);
        final int logicalH = Math.round(h / scale);
        pixels = new int[w * h];
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        // The core fills every pixel (the stage background is opaque), so the blit
        // must not blend: hasAlpha=false is exactly that contract.
        bitmap.setHasAlpha(false);
        stage = bitmap;
        // The core's answer to setSidebarBounds is a sidebarDirty() (it re-lays out
        // the root), which comes back as a postRender() here, so this is all it takes.
        try {
            AWTBridge.invokeAndWait(() -> UiBridge.setSidebarBounds(logicalW, logicalH, scale));
        } catch (Throwable t) {
            Log.e(TAG, "sidebar bounds failed", t);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bitmap = stage;
        if (bitmap == null || !UiBridge.hasSidebar()) {
            // Nothing to show: the drawer's placeholder TextView shows through.
            return;
        }
        blit.setFilterBitmap(false);
        canvas.drawBitmap(bitmap, null,
            new Rect(0, 0, contentWidthPx, contentHeightPx), blit);
    }

    /**
     * Marks the sidebar dirty. Called from the Swing thread ({@link #sidebarDirty()}),
     * never blocks. At most one render is queued at a time and renders are at least
     * {@link #FRAME_INTERVAL_MS} apart, so a plugin that repaints in a tight loop cannot
     * outrun the device.
     */
    private void postRender() {
        synchronized (renderLock) {
            if (suspended) {
                // The drawer is closed; setSuspended(false) repaints everything.
                return;
            }
            if (renderPosted || contentWidthPx <= 0 || contentHeightPx <= 0) {
                return;
            }
            renderPosted = true;
            long delay = FRAME_INTERVAL_MS - (SystemClock.uptimeMillis() - lastRenderUptimeMs);
            postDelayed(this::runRender, Math.max(0L, delay));
        }
    }

    private void runRender() {
        synchronized (renderLock) {
            renderPosted = false;
            lastRenderUptimeMs = SystemClock.uptimeMillis();
        }
        render();
    }

    /**
     * Hands one paint to the Swing dispatch thread and blits the result when it lands.
     *
     * <p><b>Never blocks the Android main thread.</b> A synchronous
     * {@code invokeAndWait} here made the drawer's own touches and the keyboard wait for a
     * whole sidebar paint: with a heavy panel (the Configuration list is ~120 rows) that is
     * hundreds of milliseconds per frame and the platform reports the app as not responding.
     * Instead the paint runs asynchronously, the main thread keeps serving input, and a
     * dirty mark that arrives mid-paint simply schedules one more frame.
     */
    private void render() {
        Bitmap bitmap = stage;
        int[] buffer = pixels;
        int w = contentWidthPx;
        int h = contentHeightPx;
        if (bitmap == null || buffer == null || w <= 0 || h <= 0) {
            return;
        }
        synchronized (renderLock) {
            if (painting) {
                dirtyWhilePainting = true;
                return;
            }
            painting = true;
        }
        final Bitmap target = bitmap;
        final int width = w;
        final int height = h;
        AWTBridge.post(() -> {
            if (DEBUG) {
                org.runelite.mobile.host.StackSampler.sample(Thread.currentThread(), 5, 3000);
            }
            try {
                UiBridge.paintSidebar(buffer, width, height);
            } catch (Throwable t) {
                Log.e(TAG, "sidebar paint failed", t);
            }
            post(() -> {
                if (stage == target && contentWidthPx == width && contentHeightPx == height) {
                    bitmap.setPixels(buffer, 0, width, 0, 0, width, height);
                    invalidate();
                }
                boolean again;
                synchronized (renderLock) {
                    painting = false;
                    again = dirtyWhilePainting;
                    dirtyWhilePainting = false;
                }
                if (again) {
                    postRender();
                }
            });
        });
    }

    /**
     * The drawer's own state, reported on every open/close transition. Nothing is
     * rendered while it is closed; the first open repaints in full, which is also what
     * makes the dirty marks that arrived while closed visible.
     */
    public void setSuspended(boolean value) {
        if (suspended == value) {
            return;
        }
        suspended = value;
        if (!value) {
            postRender();
        }
    }

    // ------------------------------------------------------------- UiBridge.Host

    @Override
    public void sidebarDirty() {
        postRender();
    }

    @Override
    public void windowDirty() {
        SwingWindowLayer layer = windowLayer;
        if (layer != null) {
            layer.refresh();
        }
    }

    @Override
    public void textInputRequested(final boolean show) {
        // Called from the Swing thread; the keyboard bar is an Android view tree, so the
        // hop to the view's own thread is mandatory (touching it from the Swing thread
        // throws CalledFromWrongThreadException).
        if (callback != null) {
            post(() -> callback.onTextInputRequested(show));
        }
    }

    @Override
    public void toast(final String message) {
        if (message == null) {
            return;
        }
        // Swing thread -> Android main thread.
        post(() -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void closeSidebarRequested() {
        // Swing thread (RuneLite's own sidebar toggle): the drawer is an Android view.
        if (callback != null) {
            post(callback::onCloseSidebarRequested);
        }
    }

    // -------------------------------------------------------------------- keys

    /**
     * Delivers one AWT key event to the focused Swing component. Called by
     * {@code MainActivity} while {@link UiBridge#focusOwnerIsTextInput()} holds.
     */
    public void dispatchKey(int id, int keyCode, char keyChar) {
        UiBridge.dispatchKey(id, keyCode, keyChar, 0, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                activePointerId = event.getPointerId(0);
                downX = event.getX(0);
                downY = event.getY(0);
                pressed = true;
                dragged = false;
                longPressFired = false;
                twoFinger = false;
                twoFingerTap = false;
                twoFingerLastY = 0f;
                wheelAccumPx = 0f;
                scrollGesture = UiBridge.isScrollableAt(logicalX(downX), logicalY(downY));
                lastScrollY = downY;
                pressSent = false;
                postDelayed(longPress, LONG_PRESS_MS);
                // A mouse always moves before it presses: Swing components pick their
                // hover state (and menus the row under the pointer) from the move.
                // The press itself is deferred: the client acts on mouse *down*, so an
                // early press would activate what is under the finger before a scroll or
                // long press could take the gesture over. ACTION_MOVE and ACTION_UP decide
                // which gesture this is and send the press accordingly.
                dispatchMouseAt(downX, downY, MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, 0);
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                // Two fingers are never a press: the gesture becomes a wheel / tap.
                cancelLongPressTimer();
                twoFinger = true;
                twoFingerTap = !dragged && !longPressFired;
                lastTapMs = 0L;
                twoFingerLastY = centroidY(event);
                wheelAccumPx = 0f;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (twoFinger) {
                    // A surviving finger after the wheel does not become the mouse.
                    if (event.getPointerCount() >= 2) {
                        twoFingerLastY = handleTwoFingerMove(event, twoFingerLastY);
                    }
                    return true;
                }
                float slop = DRAG_SLOP_DP * density;
                if (scrollGesture) {
                    // A touch device has no wheel, and RuneLite's panels have no drag
                    // semantics of their own: dragging inside a scrollable panel scrolls it.
                    scrollByDrag(event.getY());
                    if (!dragged && Math.hypot(event.getX() - downX, event.getY() - downY) > slop) {
                        dragged = true;
                        lastTapMs = 0L;
                        cancelLongPressTimer();
                    }
                    return true;
                }
                if (Math.hypot(event.getX() - downX, event.getY() - downY) > slop) {
                    if (!dragged) {
                        dragged = true;
                        lastTapMs = 0L;
                        cancelLongPressTimer();
                        if (!longPressFired) {
                            // The gesture is a drag, not a tap: send the held-off press at the
                            // touch-down point and let the drag stream follow it (sliders,
                            // scroll-bar thumbs) -- what a desktop mouse sends. After a long
                            // press (a right click) no left button is sent at all.
                            pressSent = true;
                            dispatchMouseAt(downX, downY, MouseEvent.MOUSE_PRESSED,
                                MouseEvent.BUTTON1, 1);
                        }
                    }
                    if (pressSent) {
                        dispatchMouseAt(event.getX(), event.getY(), MouseEvent.MOUSE_DRAGGED,
                            MouseEvent.BUTTON1, 1);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                cancelLongPressTimer();
                if (twoFinger) {
                    if (twoFingerTap
                        && System.currentTimeMillis() - event.getDownTime() <= TWO_FINGER_TAP_MS) {
                        float x = firstFingerX(event);
                        float y = firstFingerY(event);
                        dispatchMouseAt(x, y, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, 1);
                        dispatchMouseAt(x, y, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3, 1);
                    }
                    // The lift belongs to the two-finger gesture: close the left press
                    // that ACTION_DOWN sent, but do not turn it into a left click.
                    closeLeftPress();
                } else if (longPressFired) {
                    // The gesture already became a right click (and possibly a popup):
                    // close the left press, but do not turn the lift into a click.
                    closeLeftPress();
                } else if (pressed) {
                    if (!dragged) {
                        // A tap: press, release and click all at the touch-down point, so the
                        // whole gesture acts on what the finger went down on (a list row, a tab,
                        // a button) and not on whatever it drifted over. The click count makes a
                        // quick second tap a double click (Swing's `getClickCount() == 2`).
                        int count = nextTapClickCount(downX, downY);
                        pressSent = true;
                        dispatchMouseAt(downX, downY, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1,
                            count);
                        dispatchMouseAt(downX, downY, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1,
                            count);
                        dispatchMouseAt(downX, downY, MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1,
                            count);
                    } else if (pressSent) {
                        // The drag delivered the press: close it where the finger is. A
                        // scrolled gesture sent no press, so it sends nothing here either.
                        dispatchMouseAt(event.getX(), event.getY(), MouseEvent.MOUSE_RELEASED,
                            MouseEvent.BUTTON1, 1);
                    }
                }
                resetGesture();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // The wheel belongs to the gesture as a whole: keep two-finger mode
                // (and the tap candidate) until the last finger lifts.
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                cancelLongPressTimer();
                closeLeftPress();
                resetGesture();
                return true;
            }
            default:
                return true;
        }
    }

    /** Turns a vertical drag inside a scrollable panel into wheel notches. */
    private void scrollByDrag(float y) {
        float step = WHEEL_STEP_DP * density;
        float travel = y - lastScrollY;
        if (Math.abs(travel) < step) {
            return;
        }
        lastScrollY = y;
        // Dragging down moves the content down = scrolls up = negative AWT rotation.
        int rotation = (int) Math.round(-travel / (WHEEL_STEP_DP * density));
        if (rotation == 0) {
            rotation = travel > 0 ? -1 : 1;
        }
        UiBridge.dispatchWheel(logicalX(downX), logicalY(downY), rotation, 1, 0,
            System.currentTimeMillis());
    }

    /**
     * Routes a two-finger move: the vertical centroid travel is the wheel. Returns the
     * centroid Y to remember for the next move.
     */
    private float handleTwoFingerMove(MotionEvent event, float lastY) {
        float cy = centroidY(event);
        wheelAccumPx += cy - lastY;
        float step = WHEEL_STEP_DP * density;
        if (Math.abs(wheelAccumPx) >= step) {
            float travel = wheelAccumPx;
            wheelAccumPx = 0f;
            // The gesture turned into a scroll: it is not a two-finger tap any more.
            twoFingerTap = false;
            // Dragging down scrolls up (AWT: a negative rotation moves the view up).
            int rotation = (int) (-travel / WHEEL_PIXELS_PER_UNIT);
            if (rotation == 0) {
                rotation = travel > 0 ? -1 : 1;
            }
            UiBridge.dispatchWheel(logicalX(firstFingerX(event)), logicalY(firstFingerY(event)),
                rotation, 1, 0, System.currentTimeMillis());
        }
        return cy;
    }

    /**
     * A finger that has not moved for {@link #LONG_PRESS_MS} is a right click — the
     * Swing context menu (or popup) for whatever the pointer is over. The lift is then
     * swallowed by the {@code longPressFired} branch above, so that is the only click
     * the gesture produces.
     */
    private void fireLongPress() {
        if (!pressed || twoFinger || dragged) {
            return;
        }
        longPressFired = true;
        lastTapMs = 0L;
        int x = logicalX(downX);
        int y = logicalY(downY);
        dispatchMouseAt(downX, downY, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, 1);
        dispatchMouseAt(downX, downY, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3, 1);
        // A long press is also how a desktop user hovers: surface the tooltip.
        String tip = UiBridge.tooltipAt(x, y);
        if (tip != null && !tip.isEmpty()) {
            Toast.makeText(getContext(), tip, Toast.LENGTH_SHORT).show();
        }
    }

    /** Releases the left button if a press is still open, without a click. */
    private void closeLeftPress() {
        if (pressed && pressSent) {
            dispatchMouseAt(downX, downY, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 1);
        }
    }

    /**
     * The click count for a tap that ended at {@code (x, y)} now: 2 when it followed a tap
     * within {@link #DOUBLE_TAP_MS} and {@link #DOUBLE_TAP_SLOP_DP}, else 1. Swing acts on a
     * double click through {@code MouseEvent.getClickCount()} (RuneLite's world switcher hops on
     * {@code == 2}), so a double tap has to carry the count. Public for {@code SwingSelfTest}.
     */
    public static int tapClickCount(long now, float x, float y, long lastTapMs, float lastTapX,
                                    float lastTapY, float slopPx) {
        if (lastTapMs <= 0L || now - lastTapMs > DOUBLE_TAP_MS) {
            return 1;
        }
        return Math.hypot(x - lastTapX, y - lastTapY) <= slopPx ? 2 : 1;
    }

    /** Records this tap and returns its click count; a triple tap starts a new pair. */
    private int nextTapClickCount(float x, float y) {
        long now = System.currentTimeMillis();
        int count = tapClickCount(now, x, y, lastTapMs, lastTapX, lastTapY,
            DOUBLE_TAP_SLOP_DP * density);
        lastTapMs = count == 2 ? 0L : now;
        lastTapX = x;
        lastTapY = y;
        return count;
    }

    private void resetGesture() {
        scrollGesture = false;
        pressed = false;
        dragged = false;
        twoFinger = false;
        twoFingerTap = false;
        longPressFired = false;
        pressSent = false;
        wheelAccumPx = 0f;
    }

    private void cancelLongPressTimer() {
        removeCallbacks(longPress);
    }

    private void dispatchMouseAt(float viewX, float viewY, int id, int button, int clickCount) {
        UiBridge.dispatchMouse(logicalX(viewX), logicalY(viewY), id, button, clickCount, 0,
            System.currentTimeMillis());
    }

    private int logicalX(float viewX) {
        return (int) (viewX / uiScale);
    }

    private int logicalY(float viewY) {
        return (int) (viewY / uiScale);
    }

    /** The mean Y of the first two pointers (the fingers that make the gesture). */
    private static float centroidY(MotionEvent event) {
        return (event.getY(0) + event.getY(1)) / 2f;
    }

    /** The position of the finger that started the gesture. */
    private float firstFingerX(MotionEvent event) {
        int index = event.findPointerIndex(activePointerId);
        return event.getX(index < 0 ? 0 : index);
    }

    private float firstFingerY(MotionEvent event) {
        int index = event.findPointerIndex(activePointerId);
        return event.getY(index < 0 ? 0 : index);
    }
}
