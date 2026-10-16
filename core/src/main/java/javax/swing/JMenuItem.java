package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing's {@code JMenuItem}: one row of a {@link JPopupMenu}.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the
 * generated stub did nothing: RuneLite builds its sidebar and plugin popup menus out of
 * menu items ({@code ClientUI}'s tab-strip menu, {@code ClientToolbarPanel}'s navigation
 * buttons, {@code PluginListItem}, the XP tracker and the world hopper), so a tap has to
 * fire the item's action and close the menu.
 *
 * <p>Implemented like the JDK's {@code BasicMenuItemUI}: the row fills with
 * {@code MenuItem.background} (falling back to {@code Menu.background} and then the
 * panel colour), an armed row fills with {@code MenuItem.selectionBackground} and paints
 * its text in {@code MenuItem.selectionForeground}, a disabled row dims its text, and
 * the accelerator is right-aligned in a dimmer colour. The row height is the font height
 * plus {@value #ROW_PAD_Y} px above and below (never below {@value #MIN_ROW_HEIGHT} px),
 * and the width is the text plus the accelerator plus {@value #TEXT_PAD} px of leading
 * padding and the border insets.
 *
 * <p>Port deviations from the JDK, all deliberate:
 * <ul>
 * <li>The label lives in this class instead of {@code AbstractButton}, because the
 *     port's {@code AbstractButton} is being written for push buttons at the same time
 *     and its text storage is not part of the interface the pre-compiled RuneLite jars
 *     link against. {@code getText}/{@code setText} are overridden here, so every caller
 *     (including {@code setAction} once the port's {@code AbstractButton} implements
 *     it) goes through this field and the two never disagree.</li>
 * <li>{@link #paint} paints the row itself. A {@code JComponent} routes its painting
 *     through a UI delegate, and the port's {@code AbstractButton} installs a button
 *     look and feel — a menu row must not depend on whether that happened.</li>
 * <li>Items are text-only: the RuneLite jars only reference
 *     {@code JMenuItem()} and {@code JMenuItem(String)}, so an icon slot would be dead
 *     weight next to {@code AbstractButton}'s own icon machinery.</li>
 * <li>No mnemonic underline is painted (the JDK draws one when the menu is opened with
 *     the keyboard); the mnemonic is stored and reported.</li>
 * </ul>
 */
public class JMenuItem extends AbstractButton implements javax.accessibility.Accessible, MenuElement {

    /** Left/right padding of a row inside its border. */
    private static final int TEXT_PAD = 8;
    /** Gap between the label and the right-aligned accelerator text. */
    private static final int ACCEL_GAP = 12;
    /** Vertical padding above and below the text. */
    private static final int ROW_PAD_Y = 3;
    /** Smallest row height, so a menu still looks like a menu with a tiny font. */
    private static final int MIN_ROW_HEIGHT = 20;

    private String text;
    private KeyStroke accelerator;
    private String actionCommand;
    private int mnemonic = KeyEvent.VK_UNDEFINED;
    private boolean armed;
    private final List<ActionListener> actionListeners = new ArrayList<>();

    public JMenuItem() {
        super();
    }

    public JMenuItem(String text) {
        super();
        setText(text);
    }

    // ----------------------------------------------------------- model state

    /**
     * The item's label. Overrides {@code AbstractButton} so that the port has exactly
     * one place a menu label lives (see the class comment). No {@code @Override} yet:
     * the port's {@code AbstractButton} declares it only once its push-button slice
     * lands, and a marker would not compile until then.
     */
    public String getText() {
        return text;
    }

    public void setText(String text) {
        String old = this.text;
        this.text = text;
        if (old == null ? text != null : !old.equals(text)) {
            firePropertyChange("text", old, text);
            revalidate();
            repaint();
        }
    }

    /** The keystroke shown right-aligned in the row; never handled by the port's menus. */
    public KeyStroke getAccelerator() {
        return accelerator;
    }

    public void setAccelerator(KeyStroke accelerator) {
        KeyStroke old = this.accelerator;
        this.accelerator = accelerator;
        if (old == null ? accelerator != null : !old.equals(accelerator)) {
            firePropertyChange("accelerator", old, accelerator);
            revalidate();
            repaint();
        }
    }

    /** The keyboard mnemonic (stored; the port paints no underline — see the class doc). */
    public int getMnemonic() {
        return mnemonic;
    }

    public void setMnemonic(int mnemonic) {
        this.mnemonic = mnemonic;
    }

    public void setMnemonic(char mnemonic) {
        setMnemonic((int) mnemonic);
    }

    /** True while the row is highlighted (pointer inside it, or keyboard selection). */
    public boolean isArmed() {
        return armed;
    }

    public void setArmed(boolean armed) {
        if (this.armed == armed) {
            return;
        }
        this.armed = armed;
        firePropertyChange("armed", !armed, armed);
        repaint();
    }

    /** The command carried by the fired {@code ACTION_PERFORMED} event. */
    public String getActionCommand() {
        return actionCommand != null ? actionCommand : text;
    }

    public void setActionCommand(String actionCommand) {
        this.actionCommand = actionCommand;
    }

    @Override
    public String getUIClassID() {
        return "MenuItemUI";
    }

    // ----------------------------------------------------------- listeners

    /**
     * Adds an action listener. Declared here (and not only on {@code AbstractButton})
     * because the pre-compiled RuneLite jars call it on {@code JMenuItem} and because
     * this class fires the action itself. Kept without an {@code @Override} marker: the
     * port's {@code AbstractButton} only gains the same member with its own slice.
     */
    public void addActionListener(ActionListener listener) {
        if (listener != null && !actionListeners.contains(listener)) {
            actionListeners.add(listener);
        }
    }

    public void removeActionListener(ActionListener listener) {
        actionListeners.remove(listener);
    }

    public ActionListener[] getActionListeners() {
        return actionListeners.toArray(new ActionListener[0]);
    }

    /** Fires the item's action — the JDK's {@code AbstractButton} hook. */
    protected void fireActionPerformed(ActionEvent event) {
        for (ActionListener listener : new ArrayList<>(actionListeners)) {
            listener.actionPerformed(event);
        }
    }

    /**
     * Activates the item: fires {@code ACTION_PERFORMED} and closes the popup it lives
     * in (the JDK's {@code MenuItemHandler} does the closing). No {@code @Override} yet —
     * the port's {@code AbstractButton} gains {@code doClick} with its own slice.
     */
    public void doClick() {
        if (!isEnabled()) {
            return;
        }
        fireActionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                getActionCommand(), System.currentTimeMillis(), 0));
        JPopupMenu popup = enclosingPopup();
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
        }
    }

    /** The popup this item is a child of, or null when it is not in one. */
    protected JPopupMenu enclosingPopup() {
        for (Component p = getParent(); p != null; p = p.getParent()) {
            if (p instanceof JPopupMenu) {
                return (JPopupMenu) p;
            }
        }
        return null;
    }

    // ------------------------------------------------------- input handling

    @Override
    protected void processMouseEvent(MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_ENTERED:
                if (isEnabled()) {
                    setArmed(true);
                }
                break;
            case MouseEvent.MOUSE_EXITED:
                setArmed(false);
                break;
            case MouseEvent.MOUSE_PRESSED:
                if (isEnabled() && event.getButton() == MouseEvent.BUTTON1) {
                    setArmed(true);
                }
                break;
            case MouseEvent.MOUSE_RELEASED:
                // The JDK fires on release inside the row, so dragging off an item
                // cancels it.
                if (isEnabled() && event.getButton() == MouseEvent.BUTTON1) {
                    boolean inside = contains(event.getX(), event.getY());
                    setArmed(inside);
                    if (inside) {
                        doClick();
                    }
                }
                break;
            default:
                break;
        }
        super.processMouseEvent(event);
    }

    // -------------------------------------------------------------- MenuElement

    @Override
    public MenuElement[] getSubElements() {
        return new MenuElement[0];
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void menuSelectionChanged(boolean isIncluded) {
        if (!isIncluded) {
            setArmed(false);
        }
    }

    /**
     * The manager's route into this item. The port's items are also dispatched directly
     * (a tap becomes a mouse event on the item through {@code PopupLayer} hit-testing);
     * this only keeps the highlight in step when the event arrives through the path.
     */
    @Override
    public void processMouseEvent(MouseEvent event, MenuElement[] path, MenuSelectionManager manager) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_ENTERED:
                if (isEnabled()) {
                    setArmed(true);
                }
                break;
            case MouseEvent.MOUSE_EXITED:
                setArmed(false);
                break;
            default:
                break;
        }
    }

    /** {@code ENTER}/{@code SPACE} activate the item when the path routes keys here. */
    @Override
    public void processKeyEvent(KeyEvent event, MenuElement[] path, MenuSelectionManager manager) {
        if (event.getID() != KeyEvent.KEY_PRESSED || !isEnabled()) {
            return;
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) {
            doClick();
            event.consume();
        }
    }

    // --------------------------------------------------------------- geometry

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Insets insets = getInsets();
        Font font = getFont();
        FontMetrics fm = font != null ? getFontMetrics(font) : null;
        String label = text != null ? text : "";
        int textWidth = fm != null ? fm.stringWidth(label) : 0;
        int textHeight = fm != null ? fm.getHeight() : MIN_ROW_HEIGHT - 2 * ROW_PAD_Y;
        int acceleratorWidth = 0;
        if (accelerator != null) {
            acceleratorWidth = ACCEL_GAP + acceleratorWidth();
        }
        int width = insets.left + TEXT_PAD + textWidth + acceleratorWidth + TEXT_PAD + insets.right;
        int height = insets.top + Math.max(MIN_ROW_HEIGHT, textHeight + 2 * ROW_PAD_Y) + insets.bottom;
        return new Dimension(width, height);
    }

    private int acceleratorWidth() {
        Font font = acceleratorFont();
        if (font == null) {
            return 0;
        }
        return getFontMetrics(font).stringWidth(acceleratorText());
    }

    /** The accelerator's display font ({@code menu.acceleratorFont} when the LAF has one). */
    private Font acceleratorFont() {
        Object font = UIManager.get("MenuItem.acceleratorFont");
        if (font instanceof Font) {
            return (Font) font;
        }
        return getFont();
    }

    /**
     * The JDK's accelerator text: modifier names then the key name, from
     * {@code KeyEvent.getKeyText}. {@code KeyEvent.getKeyModifiersText} does not exist in
     * this port's runtime, so the modifier names are spelled here.
     */
    private String acceleratorText() {
        if (accelerator == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int modifiers = accelerator.getModifiers();
        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
            sb.append("Ctrl+");
        }
        if ((modifiers & KeyEvent.ALT_DOWN_MASK) != 0) {
            sb.append("Alt+");
        }
        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("Shift+");
        }
        if ((modifiers & KeyEvent.META_DOWN_MASK) != 0) {
            sb.append("Meta+");
        }
        sb.append(KeyEvent.getKeyText(accelerator.getKeyCode()));
        return sb.toString();
    }

    // ---------------------------------------------------------------- painting

    /**
     * Paints the whole row: background, label, accelerator, border — see the class
     * comment for why this does not go through a UI delegate.
     */
    @Override
    public void paint(Graphics g) {
        if (isPainting()) {
            // Painting twice from inside one paint corrupts the frame (JComponent's rule).
            return;
        }
        Color background = getItemBackground();
        Color foreground = getItemForeground();
        boolean highlighted = armed && isEnabled();
        if (highlighted) {
            background = selectionBackground(background);
            foreground = selectionForeground(foreground);
        }
        g.setColor(background);
        g.fillRect(0, 0, getWidth(), getHeight());

        Font font = getFont();
        FontMetrics fm = font != null ? g.getFontMetrics(font) : null;
        Insets insets = getInsets();
        int height = getHeight();
        g.setFont(font);

        int acceleratorWidth = accelerator == null ? 0 : ACCEL_GAP + acceleratorWidth();
        String label = text != null ? text : "";
        if (!label.isEmpty() && fm != null) {
            Graphics textGraphics = g.create();
            textGraphics.clipRect(insets.left, insets.top,
                    Math.max(0, getWidth() - acceleratorWidth - insets.left - insets.right),
                    Math.max(0, height - insets.top - insets.bottom));
            textGraphics.setColor(isEnabled() ? foreground : blended(foreground, background));
            textGraphics.drawString(label, insets.left + TEXT_PAD,
                    (height + fm.getAscent() - fm.getDescent()) / 2);
            textGraphics.dispose();
        }

        if (accelerator != null) {
            Font accelFont = acceleratorFont();
            FontMetrics afm = accelFont != null ? g.getFontMetrics(accelFont) : fm;
            String accel = acceleratorText();
            if (afm != null && accel != null && !accel.isEmpty()) {
                int accelRight = getWidth() - insets.right - TEXT_PAD;
                Graphics accelGraphics = g.create();
                accelGraphics.clipRect(Math.max(0, accelRight - acceleratorWidth), insets.top,
                        acceleratorWidth, Math.max(0, height - insets.top - insets.bottom));
                accelGraphics.setFont(accelFont);
                accelGraphics.setColor(isEnabled()
                        ? acceleratorForeground(foreground, background)
                        : blended(foreground, background));
                accelGraphics.drawString(accel, accelRight - afm.stringWidth(accel),
                        (height + afm.getAscent() - afm.getDescent()) / 2);
                accelGraphics.dispose();
            }
        }
        paintBorder(g);
    }

    /** The row's base fill; {@link JMenu} overrides it when it sits in a menu bar. */
    protected Color getItemBackground() {
        Color color = UIManager.getColor("MenuItem.background");
        if (color == null) {
            color = UIManager.getColor("Menu.background");
        }
        if (color == null) {
            color = UIManager.getColor("Panel.background");
        }
        if (color == null) {
            color = getBackground();
        }
        return color != null ? color : Color.LIGHT_GRAY;
    }

    /** The row's text colour. */
    protected Color getItemForeground() {
        Color color = UIManager.getColor("MenuItem.foreground");
        if (color == null) {
            color = UIManager.getColor("Menu.foreground");
        }
        if (color == null) {
            color = getForeground();
        }
        return color != null ? color : Color.BLACK;
    }

    /** The armed row's fill ({@code MenuItem.selectionBackground}, (77,77,77) in the port). */
    protected Color selectionBackground(Color fallback) {
        Color color = UIManager.getColor("MenuItem.selectionBackground");
        return color != null ? color : fallback.brighter();
    }

    /** The armed row's text colour. */
    protected Color selectionForeground(Color fallback) {
        Color color = UIManager.getColor("MenuItem.selectionForeground");
        return color != null ? color : Color.WHITE;
    }

    /** The accelerator's colour: dimmer than the label (the JDK uses a lighter shade). */
    private Color acceleratorForeground(Color foreground, Color background) {
        Color color = UIManager.getColor("MenuItem.acceleratorForeground");
        return color != null ? color : blended(foreground, background);
    }

    /** Half-way between the two colours; used for disabled and accelerator text. */
    static Color blended(Color a, Color b) {
        return new Color((a.getRed() + b.getRed()) / 2,
                (a.getGreen() + b.getGreen()) / 2,
                (a.getBlue() + b.getBlue()) / 2);
    }
}
