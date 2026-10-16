package javax.swing;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swing {@code ToolTipManager}: tooltip bookkeeping.
 *
 * <p>Tooltips are not shown on hover on a touch device: the app's long-press handler asks
 * {@link org.runelite.mobile.bridge.UiBridge#tooltipAt(int, int)} for the text under the
 * finger and raises it as a toast. This class therefore only keeps the registration set
 * and the delays a plugin may configure, so {@code setToolTipText} and the delay setters
 * behave as documented instead of throwing.
 */
public final class ToolTipManager extends MouseAdapter implements java.awt.event.MouseMotionListener {
    private static ToolTipManager sharedInstance;

    private final Set<JComponent> registered = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int initialDelay = 750;
    private int dismissDelay = 4000;
    private int reshowDelay = 500;
    private boolean lightWeightPopupEnabled = true;

    public ToolTipManager() {
        // Public for the JDK's ABI; use sharedInstance().
    }

    public static ToolTipManager sharedInstance() {
        ToolTipManager instance = sharedInstance;
        if (instance == null) {
            synchronized (ToolTipManager.class) {
                if (sharedInstance == null) {
                    sharedInstance = new ToolTipManager();
                }
                instance = sharedInstance;
            }
        }
        return instance;
    }

    public void registerComponent(JComponent component) {
        if (component != null) {
            registered.add(component);
        }
    }

    public void unregisterComponent(JComponent component) {
        registered.remove(component);
    }

    public Set<JComponent> registeredComponents() {
        return Collections.unmodifiableSet(registered);
    }

    public void setInitialDelay(int milliseconds) {
        initialDelay = Math.max(0, milliseconds);
    }

    public int getInitialDelay() {
        return initialDelay;
    }

    public void setDismissDelay(int milliseconds) {
        dismissDelay = Math.max(0, milliseconds);
    }

    public int getDismissDelay() {
        return dismissDelay;
    }

    public void setReshowDelay(int milliseconds) {
        reshowDelay = Math.max(0, milliseconds);
    }

    public int getReshowDelay() {
        return reshowDelay;
    }

    public void setLightWeightPopupEnabled(boolean aFlag) {
        lightWeightPopupEnabled = aFlag;
    }

    public boolean isLightWeightPopupEnabled() {
        return lightWeightPopupEnabled;
    }

    public void setEnabled(boolean flag) {
    }

    public boolean isEnabled() {
        return true;
    }

    public void dismissAll() {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // The port raises tooltips from the long-press path, not from hover.
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }
}
