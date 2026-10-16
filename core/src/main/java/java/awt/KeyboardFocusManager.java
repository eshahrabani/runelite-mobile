package java.awt;

import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AWT {@code KeyboardFocusManager}: the port's focus owner and key routing.
 *
 * <p>There is no native focus, so the port decides: a tap into a focusable component
 * (or a text component that asks for focus) sets the focus owner, and the keyboard bar's
 * keys are delivered to it. {@link #dispatchKeyEvent(KeyEvent)} walks the registered
 * {@link KeyEventDispatcher}s first (the JDK's order), then the focus owner, then the
 * active window — which is where a {@code JOptionPane}'s buttons get their Enter key.
 */
public abstract class KeyboardFocusManager implements KeyEventDispatcher, KeyEventPostProcessor {
    private static KeyboardFocusManager instance;

    private final List<KeyEventDispatcher> dispatchers = new CopyOnWriteArrayList<>();
    private final List<KeyEventPostProcessor> postProcessors = new CopyOnWriteArrayList<>();

    private volatile Component focusOwner;
    private volatile Window activeWindow;
    private final List<java.beans.PropertyChangeListener> propertyChangeListeners = new ArrayList<>();

    /** The focus manager for this port; created on first use. */
    public static KeyboardFocusManager getCurrentKeyboardFocusManager() {
        KeyboardFocusManager current = instance;
        if (current == null) {
            synchronized (KeyboardFocusManager.class) {
                if (instance == null) {
                    instance = new DefaultKeyboardFocusManager();
                }
                current = instance;
            }
        }
        return current;
    }

    public static void setCurrentKeyboardFocusManager(KeyboardFocusManager newManager) {
        instance = newManager;
    }

    /** The component holding the focus, or null. */
    public Component getFocusOwner() {
        return focusOwner;
    }

    public Component getPermanentFocusOwner() {
        return focusOwner;
    }

    public Window getActiveWindow() {
        return activeWindow;
    }

    public Window getFocusedWindow() {
        return activeWindow;
    }

    public void setActiveWindow(Window window) {
        Window old = this.activeWindow;
        if (old == window) {
            return;
        }
        this.activeWindow = window;
        firePropertyChange("activeWindow", old, window);
    }

    /**
     * Gives the focus to {@code component}, firing the focus-lost/focus-gained pair on
     * the old and new owners (which is what text components listen for to show a caret).
     */
    public void requestFocus(Component component) {
        if (component == null || !component.isFocusable() || component == focusOwner) {
            return;
        }
        if (component instanceof Container && !((Container) component).isVisible()) {
            return;
        }
        Component old = focusOwner;
        focusOwner = component;
        if (old != null) {
            old.dispatchEvent(new FocusEvent(old, FocusEvent.FOCUS_LOST, false, component));
        }
        component.dispatchEvent(new FocusEvent(component, FocusEvent.FOCUS_GAINED, false, old));
        if (component instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) component).repaint();
        }
        if (old instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) old).repaint();
        }
        org.runelite.mobile.bridge.UiBridge.focusOwnerChanged(old, component);
    }

    /** Moves the focus to the next focusable component (Swing's {@code transferFocus}). */
    public void nextFocus(Component current) {
        Container root = current == null ? null : current.getParent();
        if (root == null) {
            return;
        }
        List<Component> focusable = new ArrayList<>();
        collectFocusable(root, focusable);
        int index = focusable.indexOf(current);
        if (focusable.size() > 1) {
            requestFocus(focusable.get((index + 1) % focusable.size()));
        }
    }

    private void collectFocusable(Container container, List<Component> out) {
        for (Component c : container.getComponents()) {
            if (!c.isVisible() || !c.isEnabled() || !c.isFocusable()) {
                continue;
            }
            if (c instanceof javax.swing.JComponent && !((javax.swing.JComponent) c).isFocusable()) {
                continue;
            }
            out.add(c);
            if (c instanceof Container) {
                collectFocusable((Container) c, out);
            }
        }
    }

    // ------------------------------------------------------- key dispatch

    public void addKeyEventDispatcher(KeyEventDispatcher dispatcher) {
        if (dispatcher != null) {
            dispatchers.add(dispatcher);
        }
    }

    public void removeKeyEventDispatcher(KeyEventDispatcher dispatcher) {
        dispatchers.remove(dispatcher);
    }

    public void addKeyEventPostProcessor(KeyEventPostProcessor processor) {
        if (processor != null) {
            postProcessors.add(processor);
        }
    }

    public void removeKeyEventPostProcessor(KeyEventPostProcessor processor) {
        postProcessors.remove(processor);
    }

    /**
     * Routes a key event: registered dispatchers → focus owner → active window's root
     * pane (so dialog buttons can act on Enter/Space without owning the focus).
     * Returns true when a consumer handled it.
     */
    public boolean dispatchKeyEvent(KeyEvent e) {
        for (KeyEventDispatcher dispatcher : dispatchers) {
            if (dispatcher.dispatchKeyEvent(e)) {
                return true;
            }
        }
        boolean dispatched = dispatchEvent(e);
        if (!dispatched) {
            for (KeyEventPostProcessor processor : postProcessors) {
                if (processor.postProcessKeyEvent(e)) {
                    dispatched = true;
                    break;
                }
            }
        }
        return dispatched;
    }

    /** Delivers the event to the focus owner, then the active window. */
    public boolean dispatchEvent(AWTEvent e) {
        if (!(e instanceof KeyEvent)) {
            return false;
        }
        KeyEvent ke = (KeyEvent) e;
        Component target = focusOwner;
        if (target == null) {
            Window window = activeWindow;
            target = window == null ? null : window;
        }
        if (target == null) {
            return false;
        }
        if (target instanceof javax.swing.JComponent) {
            javax.swing.JComponent jc = (javax.swing.JComponent) target;
            if (ke.getID() == KeyEvent.KEY_PRESSED) {
                javax.swing.KeyStroke ks = javax.swing.KeyStroke.getKeyStrokeForEvent(ke);
                if (jc.processKeyBindingForWindow(ks, ke)) {
                    return true;
                }
            }
        }
        target.dispatchEvent(ke);
        return true;
    }

    public boolean postProcessKeyEvent(KeyEvent e) {
        return false;
    }

    // -------------------------------------------------------- properties

    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        synchronized (propertyChangeListeners) {
            propertyChangeListeners.add(listener);
        }
    }

    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        synchronized (propertyChangeListeners) {
            propertyChangeListeners.remove(listener);
        }
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (oldValue != null && newValue != null && oldValue.equals(newValue)) {
            return;
        }
        java.beans.PropertyChangeListener[] listeners;
        synchronized (propertyChangeListeners) {
            listeners = propertyChangeListeners.toArray(new java.beans.PropertyChangeListener[0]);
        }
        if (listeners.length == 0) {
            return;
        }
        java.beans.PropertyChangeEvent event = new java.beans.PropertyChangeEvent(this, propertyName, oldValue, newValue);
        for (java.beans.PropertyChangeListener l : listeners) {
            l.propertyChange(event);
        }
    }

    /** The port's only implementation; the class is abstract because the JDK's is. */
    private static final class DefaultKeyboardFocusManager extends KeyboardFocusManager {
    }
}
