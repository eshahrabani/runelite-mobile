package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import javax.swing.plaf.ComponentUI;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;

/**
 * Swing {@code JComponent}: the base of every widget the sidebar is built from.
 *
 * <p>Owns the paint pipeline ({@code paintComponent} → {@code paintBorder} →
 * {@code paintChildren}, or the installed {@code ComponentUI}'s
 * {@code update}/{@code paint}), the client-property map, borders, tooltips, the
 * InputMap/ActionMap key bindings and the property-change plumbing. Subclasses install
 * their UI delegate in the constructor (there is no {@code UIManager}-driven LAF
 * lookup at construction time on this port) and may replace it later via
 * {@link #setUI}.
 *
 * <p>{@link #paintImmediately(int, int, int, int)} paints a sub-rectangle right away
 * into the stage buffer; the colour picker's tone panels rely on it while dragging.
 */
public abstract class JComponent extends java.awt.Container
        implements javax.swing.TransferHandler.HasGetTransferHandler, java.io.Serializable {

    /** Marks the frame that a component is being painted in, to catch paint recursion. */
    private static final Object PAINTING = new Object();

    private ComponentUI ui;
    private javax.swing.border.Border border;
    private final Map<Object, Object> clientProperties = new HashMap<>();
    private PropertyChangeSupport changeSupport;
    private String toolTipText;
    private InputMap inputMap;
    private ActionMap actionMap;
    private boolean doubleBuffered = true;
    private boolean painting;
    private boolean validateRoot;
    private float alignmentX = 0.5f;
    private float alignmentY = 0.5f;
    private Dimension preferredSize;
    private Dimension minimumSize;
    private Dimension maximumSize;

    protected JComponent() {
        super();
        setOpaque(false);
    }

    // ------------------------------------------------------------------ UI

    public ComponentUI getUI() {
        return ui;
    }

    public void setUI(ComponentUI newUI) {
        ComponentUI old = this.ui;
        if (old == newUI) {
            return;
        }
        if (old != null) {
            old.uninstallUI(this);
        }
        this.ui = newUI;
        if (newUI != null) {
            newUI.installUI(this);
        }
        revalidate();
        repaint();
    }

    /** Re-resolves the UI from the current look and feel defaults. */
    public void updateUI() {
        ComponentUI newUI = UIManager.getUI(this);
        if (newUI != null) {
            setUI(newUI);
        }
    }

    // --------------------------------------------------------------- paint

    @Override
    public void paint(Graphics g) {
        if (painting) {
            // Re-entrant paint (a component that repaints itself from inside its own
            // paint): the JDK drops it; painting twice corrupts the frame.
            return;
        }
        painting = true;
        try {
            ComponentUI delegate = ui;
            if (delegate != null) {
                delegate.update(g, this);
            } else {
                paintComponent(g);
                paintBorder(g);
                paintChildren(g);
            }
        } finally {
            painting = false;
        }
    }

    /** Paints this component's own content (background first when it is opaque). */
    protected void paintComponent(Graphics g) {
        // paintSelf, not super.paint: Container.paint would paint the children too (see
        // Component.paintSelf).
        paintSelf(g);
    }

    protected void paintBorder(Graphics g) {
        javax.swing.border.Border b = border;
        if (b != null) {
            b.paintBorder(this, g, 0, 0, getWidth(), getHeight());
        }
    }

    @Override
    public void paintComponents(Graphics g) {
        paintChildren(g);
    }

    /** Paints the children, clipped and translated, in Z-order. */
    protected void paintChildren(Graphics g) {
        for (int i = 0; i < getComponentCount(); i++) {
            java.awt.Component child = getComponent(i);
            if (!child.isVisible() || child.getWidth() <= 0 || child.getHeight() <= 0) {
                continue;
            }
            Graphics gc = g.create();
            gc.translate(child.getX(), child.getY());
            gc.clipRect(0, 0, child.getWidth(), child.getHeight());
            child.paint(gc);
            gc.dispose();
        }
    }

    /**
     * Paints the given rectangle of this component's subtree immediately.
     *
     * <p>The stage's paint root routes dirty rects here; {@code RepaintManager} calls it
     * from the sidebar/window paint when only a sub-rectangle changed.
     */
    public void paintImmediately(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        RepaintManager.currentManager(this).paintImmediately(this, x, y, w, h);
    }

    public void paintImmediately(java.awt.Rectangle r) {
        if (r != null) {
            paintImmediately(r.x, r.y, r.width, r.height);
        }
    }

    /** True while this component's {@code paint} is running. */
    public boolean isPainting() {
        return painting;
    }

    // ------------------------------------------------------------- layout

    @Override
    public void revalidate() {
        if (SwingUtilities.isEventDispatchThread()) {
            invalidate();
            RepaintManager.currentManager(this).deferValidate(this);
        } else {
            // Swing's contract: a foreign-thread revalidate is posted to the EDT.
            SwingUtilities.invokeLater(this::revalidate);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    /** A validate root lays out its whole subtree when it is validated. */
    public boolean isValidateRoot() {
        return validateRoot;
    }

    public void setValidateRoot(boolean validateRoot) {
        this.validateRoot = validateRoot;
    }

    @Override
    public java.awt.Insets getInsets() {
        javax.swing.border.Border b = border;
        if (b != null) {
            java.awt.Insets insets = b.getBorderInsets(this);
            if (insets != null) {
                return insets;
            }
        }
        return new java.awt.Insets(0, 0, 0, 0);
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return preferredSize;
        }
        ComponentUI delegate = ui;
        if (delegate != null) {
            Dimension d = delegate.getPreferredSize(this);
            if (d != null) {
                return d;
            }
        }
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return minimumSize;
        }
        ComponentUI delegate = ui;
        if (delegate != null) {
            Dimension d = delegate.getMinimumSize(this);
            if (d != null) {
                return d;
            }
        }
        return super.getMinimumSize();
    }

    @Override
    public Dimension getMaximumSize() {
        if (isMaximumSizeSet()) {
            return maximumSize;
        }
        ComponentUI delegate = ui;
        if (delegate != null) {
            Dimension d = delegate.getMaximumSize(this);
            if (d != null) {
                return d;
            }
        }
        return super.getMaximumSize();
    }

    public void setPreferredSize(Dimension preferredSize) {
        this.preferredSize = preferredSize;
        revalidate();
    }

    public boolean isPreferredSizeSet() {
        return preferredSize != null;
    }

    public void setMinimumSize(Dimension minimumSize) {
        this.minimumSize = minimumSize;
        revalidate();
    }

    public boolean isMinimumSizeSet() {
        return minimumSize != null;
    }

    public void setMaximumSize(Dimension maximumSize) {
        this.maximumSize = maximumSize;
        revalidate();
    }

    public boolean isMaximumSizeSet() {
        return maximumSize != null;
    }

    public float getAlignmentX() {
        return alignmentX;
    }

    public float getAlignmentY() {
        return alignmentY;
    }

    public void setAlignmentX(float alignmentX) {
        this.alignmentX = alignmentX;
    }

    public void setAlignmentY(float alignmentY) {
        this.alignmentY = alignmentY;
    }

    // ------------------------------------------------------ borders/opaque

    public javax.swing.border.Border getBorder() {
        return border;
    }

    public void setBorder(javax.swing.border.Border border) {
        javax.swing.border.Border old = this.border;
        this.border = border;
        firePropertyChange("border", old, border);
        revalidate();
        repaint();
    }

    @Override
    public void setOpaque(boolean opaque) {
        boolean old = isOpaque();
        super.setOpaque(opaque);
        firePropertyChange("opaque", old, opaque);
        if (old != opaque) {
            repaint();
        }
    }

    public boolean isDoubleBuffered() {
        return doubleBuffered;
    }

    public void setDoubleBuffered(boolean aFlag) {
        this.doubleBuffered = aFlag;
    }

    // ------------------------------------------------------ client props

    public Object getClientProperty(Object key) {
        synchronized (clientProperties) {
            return clientProperties.get(key);
        }
    }

    public void putClientProperty(Object key, Object value) {
        if (key == null) {
            return;
        }
        Object old;
        synchronized (clientProperties) {
            if (value == null) {
                old = clientProperties.remove(key);
            } else {
                old = clientProperties.put(key, value);
            }
        }
        firePropertyChange(key.toString(), old, value);
        repaint();
    }

    // ------------------------------------------------------- tooltips

    public void setToolTipText(String text) {
        String old = toolTipText;
        this.toolTipText = text;
        if (text != null) {
            ToolTipManager.sharedInstance().registerComponent(this);
        } else {
            ToolTipManager.sharedInstance().unregisterComponent(this);
        }
        firePropertyChange("toolTipText", old, text);
    }

    public String getToolTipText() {
        return toolTipText;
    }

    /** Tooltip for the given mouse position; {@link #getToolTipText()} by default. */
    public String getToolTipText(java.awt.event.MouseEvent event) {
        return getToolTipText();
    }

    // -------------------------------------------------- keys and actions

    public javax.swing.InputMap getInputMap(int condition) {
        if (condition == WHEN_FOCUSED) {
            if (inputMap == null) {
                inputMap = new InputMap();
            }
            return inputMap;
        }
        return UIManager.getInputMap(this, condition);
    }

    public javax.swing.InputMap getInputMap() {
        return getInputMap(WHEN_FOCUSED);
    }

    public void setInputMap(int condition, javax.swing.InputMap map) {
        if (condition == WHEN_FOCUSED) {
            inputMap = map;
        }
    }

    public void setInputMap(javax.swing.InputMap map) {
        setInputMap(WHEN_FOCUSED, map);
    }

    public ActionMap getActionMap() {
        if (actionMap == null) {
            actionMap = new ActionMap();
        }
        return actionMap;
    }

    public void setActionMap(ActionMap am) {
        this.actionMap = am;
    }

    /** Constant for key bindings active while the component has focus. */
    public static final int WHEN_FOCUSED = 0;
    public static final int WHEN_ANCESTOR_OF_FOCUSED_COMPONENT = 1;
    public static final int WHEN_IN_FOCUSED_WINDOW = 2;
    public static final int UNDEFINED_CONDITION = -1;

    public void registerKeyboardAction(ActionListener anAction, String command, KeyStroke aKeyStroke, int aCondition) {
        if (aCondition == UNDEFINED_CONDITION) {
            aCondition = WHEN_FOCUSED;
        }
        getInputMap(aCondition).put(aKeyStroke, command);
        getActionMap().put(command, new ActionListenerAction(anAction, command));
    }

    public void registerKeyboardAction(ActionListener anAction, KeyStroke aKeyStroke, int aCondition) {
        registerKeyboardAction(anAction, anAction.toString(), aKeyStroke, aCondition);
    }

    public void unregisterKeyboardAction(KeyStroke aKeyStroke) {
        for (int condition : new int[] {WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW}) {
            InputMap map = condition == WHEN_FOCUSED ? inputMap : UIManager.getInputMap(this, condition, false);
            if (map != null) {
                Object key = map.get(aKeyStroke);
                if (key != null) {
                    map.remove(aKeyStroke);
                    ActionMap am = condition == WHEN_FOCUSED ? actionMap : null;
                    if (am != null) {
                        am.remove(key);
                    }
                }
            }
        }
    }

    /**
     * Runs the action bound to {@code ks} in the given condition's InputMap, if any.
     * Called by the port's key dispatch before the key reaches the component's listeners.
     */
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        if (!isEnabled() || ks == null) {
            return false;
        }
        InputMap map = getInputMap(condition);
        ActionMap am = getActionMap();
        if (map == null || am == null) {
            return false;
        }
        Object binding = map.get(ks);
        if (binding == null) {
            return false;
        }
        Action action = am.get(binding);
        if (action == null || !action.isEnabled()) {
            return false;
        }
        String command = (String) action.getValue(Action.ACTION_COMMAND_KEY);
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                command != null ? command : String.valueOf(binding), e.getWhen(), e.getModifiers());
        action.actionPerformed(event);
        return true;
    }

    /**
     * Walks this component and its ancestors looking for a binding in the
     * {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} and {@code WHEN_IN_FOCUSED_WINDOW}
     * maps — the JDK's focus-manager order for keys the focus owner did not consume.
     */
    public boolean processKeyBindingForWindow(KeyStroke ks, KeyEvent e) {
        for (java.awt.Component c = this; c != null; c = c.getParent()) {
            if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                int condition = jc == this ? WHEN_FOCUSED : WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
                if (jc.processKeyBinding(ks, e, condition, true)
                        || jc.processKeyBinding(ks, e, WHEN_IN_FOCUSED_WINDOW, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        // Key bindings run before the component's own key listeners (JDK order).
        if (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_RELEASED) {
            KeyStroke ks = KeyStroke.getKeyStrokeForEvent(e);
            boolean pressed = e.getID() == KeyEvent.KEY_PRESSED;
            if (processKeyBinding(ks, e, WHEN_FOCUSED, pressed)) {
                e.consume();
                return;
            }
        }
        super.processKeyEvent(e);
    }

    // --------------------------------------------------- property change

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport == null) {
            changeSupport = new PropertyChangeSupport(this);
        }
        changeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport != null) {
            changeSupport.removePropertyChangeListener(listener);
        }
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (changeSupport == null) {
            changeSupport = new PropertyChangeSupport(this);
        }
        changeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (changeSupport != null) {
            changeSupport.removePropertyChangeListener(propertyName, listener);
        }
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return changeSupport == null ? new PropertyChangeListener[0] : changeSupport.getPropertyChangeListeners();
    }

    public PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return changeSupport == null ? new PropertyChangeListener[0]
                : changeSupport.getPropertyChangeListeners(propertyName);
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (changeSupport == null || (oldValue != null && newValue != null && oldValue.equals(newValue))) {
            return;
        }
        changeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    protected void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        if (changeSupport == null || oldValue == newValue) {
            return;
        }
        changeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    protected void firePropertyChange(String propertyName, int oldValue, int newValue) {
        if (changeSupport == null || oldValue == newValue) {
            return;
        }
        changeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    // ------------------------------------------------------------ events

    @Override
    public void addNotify() {
        super.addNotify();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
    }

    /**
     * The part of this component that is inside its enclosing viewport (the JDK's
     * {@code getVisibleRect}); a component outside any viewport is fully visible.
     */
    public java.awt.Rectangle getVisibleRect() {
        java.awt.Rectangle rect = new java.awt.Rectangle(0, 0, getWidth(), getHeight());
        computeVisibleRect(rect);
        return rect;
    }

    public void computeVisibleRect(java.awt.Rectangle visibleRect) {
        java.awt.Component child = this;
        for (java.awt.Component p = getParent(); p != null; p = p.getParent()) {
            if (p instanceof JViewport) {
                JViewport viewport = (JViewport) p;
                java.awt.Rectangle visible = viewport.getViewRect();
                visible.translate(-child.getX(), -child.getY());
                java.awt.Rectangle own = new java.awt.Rectangle(0, 0, getWidth(), getHeight());
                visibleRect.setBounds(visible.intersection(own));
                return;
            }
            child = p;
        }
        visibleRect.setBounds(0, 0, getWidth(), getHeight());
    }

    /** Asks the enclosing viewport to scroll the rectangle into view. */
    public void scrollRectToVisible(java.awt.Rectangle aRect) {
        java.awt.Component child = this;
        for (java.awt.Component p = getParent(); p != null; p = p.getParent()) {
            if (p instanceof JViewport) {
                JViewport viewport = (JViewport) p;
                java.awt.Rectangle target = new java.awt.Rectangle(aRect);
                target.translate(-child.getX(), -child.getY());
                viewport.scrollToVisible(target);
                return;
            }
            child = p;
        }
    }

    /** True when this component's children do not overlap (a paint optimization hint). */
    public boolean isOptimizedDrawingEnabled() {
        return true;
    }

    /** The key under which the UI delegate class is registered in the defaults table. */
    public String getUIClassID() {
        return "ComponentUI";
    }

    public JPopupMenu getComponentPopupMenu() {
        Object value = getClientProperty("componentPopupMenu");
        return value instanceof JPopupMenu ? (JPopupMenu) value : null;
    }

    public void setComponentPopupMenu(JPopupMenu popup) {
        putClientProperty("componentPopupMenu", popup);
    }

    /** Pointer position in this component's coordinates, or null when it is elsewhere. */
    public java.awt.Point getMousePosition(boolean allowChildren) {
        java.awt.Point p = getMousePosition();
        if (p == null || (!allowChildren && p.x >= 0 && p.y >= 0
                && p.x < getWidth() && p.y < getHeight() && false)) {
            return p;
        }
        return p;
    }

    /** Wraps a plain {@link ActionListener} as an {@link Action} for keyboard bindings. */
    private static final class ActionListenerAction extends AbstractAction {
        private final ActionListener delegate;

        ActionListenerAction(ActionListener delegate, String command) {
            this.delegate = delegate;
            putValue(ACTION_COMMAND_KEY, command);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            delegate.actionPerformed(e);
        }
    }
}
