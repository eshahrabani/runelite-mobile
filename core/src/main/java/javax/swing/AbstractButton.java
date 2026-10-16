package javax.swing;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Swing {@code AbstractButton}: the shared behaviour of {@link JButton},
 * {@link JToggleButton}, {@link JCheckBox} and {@link JRadioButton}.
 *
 * <p>Hand-written because the sidebar and every config panel are built from these and the
 * full state machine has to work: a mouse press arms and paints, a release inside fires
 * exactly one {@code ActionEvent}, rollover/selection drive the icon lookup, and the model
 * is a real {@link DefaultButtonModel}. The paint itself lives in
 * {@code plaf.basic.BasicButtonUI} (installed by the concrete subclass); this class only
 * holds the state and the event plumbing.
 *
 * <p>The {@link ItemListener} forwarding is delegated to the model, as in the JDK. Item
 * state changes are reported by the model (see {@link DefaultButtonModel} for the
 * reflective {@code ItemEvent} deviation).
 */
public abstract class AbstractButton extends JComponent
        implements java.awt.ItemSelectable, SwingConstants {

    private ButtonModel model;
    private String text = "";
    private Icon icon;
    private Icon pressedIcon;
    private Icon selectedIcon;
    private Icon rolloverIcon;
    private Icon rolloverSelectedIcon;
    private Icon disabledIcon;
    private Icon disabledSelectedIcon;

    private boolean rolloverEnabled = true;
    private boolean contentAreaFilled = true;
    private boolean borderPainted = true;
    private boolean focusPainted = true;

    private int mnemonic = -1;
    private int displayedMnemonicIndex = -1;
    private int iconTextGap = 4;

    private int horizontalAlignment = SwingConstants.CENTER;
    private int verticalAlignment = SwingConstants.CENTER;
    private int horizontalTextPosition = SwingConstants.TRAILING;
    private int verticalTextPosition = SwingConstants.CENTER;

    private Insets margin;
    private int multiClickThreshhold;
    private Action action;

    /** The model's previous state, so {@code stateChanged} can fire the right properties. */
    private boolean lastArmed;
    private boolean lastPressed;
    private boolean lastRollover;
    private boolean lastSelected;

    protected AbstractButton() {
        super();
        setOpaque(false);
    }

    /**
     * Shared constructor tail: called by the concrete subclass after it has installed its
     * model. Applies the initial text/icon and subscribes to the model so state changes
     * repaint and fire the {@code armed}/{@code pressed}/{@code selected} properties.
     */
    protected void init(String text, Icon icon) {
        if (text != null) {
            this.text = text;
        }
        if (icon != null) {
            this.icon = icon;
        }
        attachModel(model);
        if (model.getActionCommand() == null) {
            model.setActionCommand(this.text);
        }
    }

    /** (Re)subscribes to {@code m} and points its event source at this button. */
    private void attachModel(ButtonModel m) {
        m.removeChangeListener(modelChangeListener);
        m.addChangeListener(modelChangeListener);
        if (m instanceof DefaultButtonModel) {
            ((DefaultButtonModel) m).setSource(this);
        }
    }

    private final ChangeListener modelChangeListener = new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
            fireStateChangedFromModel();
        }
    };

    private void fireStateChangedFromModel() {
        boolean armed = model.isArmed();
        boolean pressed = model.isPressed();
        boolean rollover = model.isRollover();
        boolean selected = model.isSelected();
        if (lastArmed != armed) {
            firePropertyChange("armed", lastArmed, armed);
        }
        if (lastPressed != pressed) {
            firePropertyChange("pressed", lastPressed, pressed);
        }
        if (lastRollover != rollover) {
            firePropertyChange("rollover", lastRollover, rollover);
        }
        if (lastSelected != selected) {
            firePropertyChange("selected", lastSelected, selected);
            // The visual state (icon/glyph) depends on selection.
            repaint();
        }
        lastArmed = armed;
        lastPressed = pressed;
        lastRollover = rollover;
        lastSelected = selected;
        repaint();
    }

    // --------------------------------------------------------------- model

    public ButtonModel getModel() {
        return model;
    }

    public void setModel(ButtonModel newModel) {
        if (newModel == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (model == newModel) {
            return;
        }
        model = newModel;
        attachModel(model);
        model.setEnabled(isEnabled());
    }

    // ---------------------------------------------------------------- text

    public String getText() {
        return text;
    }

    public void setText(String text) {
        String old = this.text;
        this.text = text == null ? "" : text;
        String command = model.getActionCommand();
        if (command == null || command.equals(old)) {
            model.setActionCommand(this.text);
        }
        firePropertyChange("text", old, this.text);
        revalidate();
        repaint();
    }

    public int getMnemonic() {
        return mnemonic;
    }

    public void setMnemonic(int mnemonic) {
        int old = this.mnemonic;
        this.mnemonic = mnemonic;
        getModel().setMnemonic(mnemonic);
        firePropertyChange("mnemonic", old, mnemonic);
        revalidate();
    }

    public void setMnemonic(char mnemonic) {
        setMnemonic((int) mnemonic);
    }

    public int getDisplayedMnemonicIndex() {
        return displayedMnemonicIndex;
    }

    public void setDisplayedMnemonicIndex(int index) {
        int old = this.displayedMnemonicIndex;
        this.displayedMnemonicIndex = index;
        firePropertyChange("displayedMnemonicIndex", old, index);
    }

    // --------------------------------------------------------------- icons

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        Icon old = this.icon;
        this.icon = icon;
        firePropertyChange("icon", old, icon);
        revalidate();
        repaint();
    }

    public Icon getPressedIcon() {
        return pressedIcon;
    }

    public void setPressedIcon(Icon icon) {
        Icon old = this.pressedIcon;
        this.pressedIcon = icon;
        firePropertyChange("pressedIcon", old, icon);
        repaint();
    }

    public Icon getSelectedIcon() {
        return selectedIcon;
    }

    public void setSelectedIcon(Icon icon) {
        Icon old = this.selectedIcon;
        this.selectedIcon = icon;
        firePropertyChange("selectedIcon", old, icon);
        repaint();
    }

    public Icon getRolloverIcon() {
        return rolloverIcon;
    }

    public void setRolloverIcon(Icon icon) {
        Icon old = this.rolloverIcon;
        this.rolloverIcon = icon;
        firePropertyChange("rolloverIcon", old, icon);
        repaint();
    }

    public Icon getRolloverSelectedIcon() {
        return rolloverSelectedIcon;
    }

    public void setRolloverSelectedIcon(Icon icon) {
        Icon old = this.rolloverSelectedIcon;
        this.rolloverSelectedIcon = icon;
        firePropertyChange("rolloverSelectedIcon", old, icon);
        repaint();
    }

    public Icon getDisabledIcon() {
        return disabledIcon;
    }

    public void setDisabledIcon(Icon icon) {
        Icon old = this.disabledIcon;
        this.disabledIcon = icon;
        firePropertyChange("disabledIcon", old, icon);
        repaint();
    }

    public Icon getDisabledSelectedIcon() {
        if (disabledSelectedIcon != null) {
            return disabledSelectedIcon;
        }
        if (selectedIcon != null) {
            return selectedIcon;
        }
        return getDisabledIcon();
    }

    public void setDisabledSelectedIcon(Icon icon) {
        Icon old = this.disabledSelectedIcon;
        this.disabledSelectedIcon = icon;
        firePropertyChange("disabledSelectedIcon", old, icon);
        repaint();
    }

    /**
     * The icon to paint for the model's current state, mirroring the JDK's lookup order:
     * disabled-selected → rollover-selected → selected → disabled → rollover → plain.
     */
    public Icon getIconForState() {
        if (model.isPressed() && model.isArmed() && pressedIcon != null) {
            return pressedIcon;
        }
        Icon result = null;
        if (model.isSelected()) {
            if (!model.isEnabled()) {
                result = getDisabledSelectedIcon();
            } else if (model.isRollover()) {
                result = rolloverSelectedIcon;
            }
            if (result == null) {
                result = selectedIcon;
            }
        } else {
            if (!model.isEnabled()) {
                result = disabledIcon;
            } else if (model.isRollover()) {
                result = rolloverIcon;
            }
        }
        return result != null ? result : icon;
    }

    // ------------------------------------------------------- appearance

    public boolean isRolloverEnabled() {
        return rolloverEnabled;
    }

    public void setRolloverEnabled(boolean rolloverEnabled) {
        boolean old = this.rolloverEnabled;
        this.rolloverEnabled = rolloverEnabled;
        firePropertyChange("rolloverEnabled", old, rolloverEnabled);
    }

    public boolean isContentAreaFilled() {
        return contentAreaFilled;
    }

    public void setContentAreaFilled(boolean contentAreaFilled) {
        boolean old = this.contentAreaFilled;
        this.contentAreaFilled = contentAreaFilled;
        firePropertyChange("contentAreaFilled", old, contentAreaFilled);
        repaint();
    }

    public boolean isBorderPainted() {
        return borderPainted;
    }

    public void setBorderPainted(boolean borderPainted) {
        boolean old = this.borderPainted;
        this.borderPainted = borderPainted;
        firePropertyChange("borderPainted", old, borderPainted);
        repaint();
    }

    public boolean isFocusPainted() {
        return focusPainted;
    }

    public void setFocusPainted(boolean focusPainted) {
        boolean old = this.focusPainted;
        this.focusPainted = focusPainted;
        firePropertyChange("focusPainted", old, focusPainted);
        repaint();
    }

    public Insets getMargin() {
        return margin;
    }

    public void setMargin(Insets margin) {
        Insets old = this.margin;
        this.margin = margin;
        firePropertyChange("margin", old, margin);
        revalidate();
        repaint();
    }

    public int getIconTextGap() {
        return iconTextGap;
    }

    public void setIconTextGap(int iconTextGap) {
        int old = this.iconTextGap;
        this.iconTextGap = iconTextGap;
        firePropertyChange("iconTextGap", old, iconTextGap);
        revalidate();
        repaint();
    }

    public int getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public void setHorizontalAlignment(int alignment) {
        this.horizontalAlignment = alignment;
        revalidate();
        repaint();
    }

    public int getVerticalAlignment() {
        return verticalAlignment;
    }

    public void setVerticalAlignment(int alignment) {
        this.verticalAlignment = alignment;
        revalidate();
        repaint();
    }

    public int getHorizontalTextPosition() {
        return horizontalTextPosition;
    }

    public void setHorizontalTextPosition(int textPosition) {
        this.horizontalTextPosition = textPosition;
        revalidate();
        repaint();
    }

    public int getVerticalTextPosition() {
        return verticalTextPosition;
    }

    public void setVerticalTextPosition(int textPosition) {
        this.verticalTextPosition = textPosition;
        revalidate();
        repaint();
    }

    // -------------------------------------------------------- selection

    public boolean isSelected() {
        return model.isSelected();
    }

    public void setSelected(boolean b) {
        model.setSelected(b);
    }

    public String getActionCommand() {
        String command = model.getActionCommand();
        return command == null ? text : command;
    }

    public void setActionCommand(String actionCommand) {
        model.setActionCommand(actionCommand);
    }

    public int getMultiClickThreshhold() {
        return multiClickThreshhold;
    }

    public void setMultiClickThreshhold(long threshhold) {
        if (threshhold < 0) {
            throw new IllegalArgumentException("threshhold must be >= 0");
        }
        this.multiClickThreshhold = (int) threshhold;
    }

    // ------------------------------------------------------------ enabled

    @Override
    public void setEnabled(boolean b) {
        if (isEnabled() == b) {
            return;
        }
        super.setEnabled(b);
        model.setEnabled(b);
        firePropertyChange("enabled", !b, b);
    }

    // ------------------------------------------------------------- click

    /** Performs a programmatic click; see {@link #doClick(int)}. */
    public void doClick() {
        doClick(68);
    }

    /**
     * Performs a programmatic click: arm, press, release. The single
     * {@code ACTION_PERFORMED} (and, for a toggle, the selection flip) is fired by the
     * model on the release, exactly as a mouse click would.
     */
    public void doClick(int pressTime) {
        if (!isEnabled()) {
            return;
        }
        ButtonModel m = getModel();
        m.setArmed(true);
        m.setPressed(true);
        m.setPressed(false);
        m.setArmed(false);
    }

    // ---------------------------------------------------------- listeners

    public void addActionListener(ActionListener l) {
        model.addActionListener(l);
    }

    public void removeActionListener(ActionListener l) {
        model.removeActionListener(l);
    }

    public ActionListener[] getActionListeners() {
        return model instanceof DefaultButtonModel
                ? ((DefaultButtonModel) model).getActionListeners()
                : new ActionListener[0];
    }

    public void addItemListener(ItemListener l) {
        model.addItemListener(l);
    }

    public void removeItemListener(ItemListener l) {
        model.removeItemListener(l);
    }

    /** {@link java.awt.ItemSelectable}: the selected item, or null. */
    @Override
    public Object[] getSelectedObjects() {
        return isSelected() ? new Object[] { this } : null;
    }

    public void addChangeListener(ChangeListener l) {
        model.addChangeListener(l);
    }

    public void removeChangeListener(ChangeListener l) {
        model.removeChangeListener(l);
    }

    public ChangeListener[] getChangeListeners() {
        return model instanceof DefaultButtonModel
                ? ((DefaultButtonModel) model).getChangeListeners()
                : new ChangeListener[0];
    }

    /** Fires an action event to this button's listeners (the model already did; kept for ABI). */
    protected void fireActionPerformed(ActionEvent event) {
        for (ActionListener l : getActionListeners()) {
            l.actionPerformed(event);
        }
    }

    // --------------------------------------------------------------- Action

    public Action getAction() {
        return action;
    }

    public void setAction(Action a) {
        Action old = this.action;
        if (old != null) {
            removeActionListener(old);
            old.removePropertyChangeListener(actionPropertyChangeListener);
        }
        this.action = a;
        if (a != null) {
            addActionListener(a);
            setText((String) a.getValue(Action.NAME));
            setIcon((Icon) a.getValue(Action.SMALL_ICON));
            setEnabled(a.isEnabled());
            a.addPropertyChangeListener(actionPropertyChangeListener);
        }
        firePropertyChange("action", old, a);
    }

    private final PropertyChangeListener actionPropertyChangeListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            configureFromAction();
        }
    };

    private void configureFromAction() {
        if (action == null) {
            return;
        }
        setEnabled(action.isEnabled());
        String name = (String) action.getValue(Action.NAME);
        if (name != null) {
            setText(name);
        }
    }
}
