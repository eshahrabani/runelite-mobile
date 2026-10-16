package javax.swing;

import java.awt.Component;
import org.runelite.mobile.bridge.PopupLayer;

/**
 * Swing's {@code PopupFactory}: the single point every popup in the UI is created from.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop}. The JDK's
 * factory picks between a heavyweight native popup window, a medium-weight
 * {@code JWindow} and a lightweight component in the enclosing root pane; the port has
 * no native windows, so every popup is the same thing: a component registered in
 * {@link PopupLayer}, the stage's overlay stack. The weight flags are therefore stored
 * and reported faithfully (RuneLite's {@code ClientUI} calls
 * {@code JPopupMenu.setDefaultLightWeightPopupEnabled(false)} at start-up) but they do
 * not change the port's behaviour — the in-stage popup is what the whole menu, combo-box
 * and tooltip system uses.
 *
 * <p>Port deviation: the JDK's popup coordinates are screen coordinates; the port's
 * screen is the stage being painted, so the numbers {@link JPopupMenu} passes in are
 * stage-logical (see {@link Popup}).
 */
public class PopupFactory {

    /** A popup painted by the enclosing stage (the only kind the port can build). */
    public static final int LIGHT_WEIGHT_POPUP = 0;
    /** Kept for signature parity with the JDK; the port always builds the light one. */
    public static final int MEDIUM_WEIGHT_POPUP = 1;
    /** Kept for signature parity with the JDK; the port always builds the light one. */
    public static final int HEAVY_WEIGHT_POPUP = 2;

    private static PopupFactory sharedInstance = new PopupFactory();
    private static boolean defaultLightWeightPopupEnabled = true;

    private int popupType = LIGHT_WEIGHT_POPUP;

    public PopupFactory() {
    }

    /**
     * Replaces the shared factory. Null is rejected, exactly as the JDK rejects it — the
     * menu system has no fallback factory.
     */
    public static void setSharedInstance(PopupFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("PopupFactory can not be null");
        }
        sharedInstance = factory;
    }

    public static PopupFactory getSharedInstance() {
        return sharedInstance;
    }

    /**
     * Whether popups are lightweight unless a component opts out. Stored and reported;
     * the port paints every popup in the stage, so both answers behave identically.
     */
    public static void setDefaultLightWeightPopupEnabled(boolean enable) {
        defaultLightWeightPopupEnabled = enable;
    }

    public static boolean isDefaultLightWeightPopupEnabled() {
        return defaultLightWeightPopupEnabled;
    }

    /**
     * Sets the weight used by the next {@link #getPopup(Component, Component, int, int)}
     * call. The JDK's {@code JPopupMenu} drives this before asking the UI for a popup.
     */
    public void setPopupType(int type) {
        popupType = type;
    }

    public int getPopupType() {
        return popupType;
    }

    /** Creates a popup for {@code contents} at the stage position {@code (x, y)}. */
    public Popup getPopup(Component owner, Component contents, int x, int y) {
        return getPopup(owner, contents, x, y, popupType == LIGHT_WEIGHT_POPUP);
    }

    /**
     * The JDK's {@code boolean} overload — public here because the port's runtime links
     * against the pre-compiled RuneLite jars, whose call site was generated from the
     * stub's public declaration (the JDK itself declares it {@code protected}).
     */
    public Popup getPopup(Component owner, Component contents, int x, int y,
                          boolean isHeavyWeightPopup) {
        if (contents == null) {
            throw new IllegalArgumentException("Popup contents must be non-null");
        }
        return new LayerPopup(owner, contents, x, y);
    }

    /** The port's popup: a component in the stage's overlay stack. */
    private static final class LayerPopup extends Popup {

        LayerPopup(Component owner, Component contents, int x, int y) {
            super(owner, contents, x, y);
        }

        @Override
        public void show() {
            PopupLayer.show(getContents(), getX(), getY());
        }

        @Override
        public void hide() {
            PopupLayer.hide(getContents());
        }
    }
}
