package javax.swing;

import java.awt.Adjustable;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ScrollBarUI;

/**
 * Swing {@code JScrollBar}: the model-backed scroll bar {@link JScrollPane} wires to a
 * {@link JViewport}.
 *
 * <p>Hand-written rather than generated: the shipped jars ask a real scroll bar for its
 * model and value ({@code PluginListPanel} restores {@code getVerticalScrollBar()
 * .setValue(...)} between panel rebuilds), and the port's {@link JScrollPane} drives the
 * viewport through those values. The generated file declared only the members the jars
 * reference; every one of them is kept here, and the rest of the JDK's surface is added so
 * the type behaves like the desktop one. The marker sentence is removed so the generator
 * leaves this file alone.
 *
 * <p>Port deviations (all forced by the missing {@code java.desktop}):
 * <ul>
 *   <li>No accessibility support: the port's {@code javax.accessibility.Accessible} is an
 *       empty marker interface and {@code AccessibleContext} carries no API.</li>
 *   <li>The JDK's {@code setRequestFocusEnabled(false)} becomes {@code setFocusable(false)}
 *       — the port's {@code Component} has no "request focus enabled" flag, and a touch
 *       UI never wants the scroll bar to take the keyboard focus.</li>
 *   <li>The default UI comes from {@code UIManager} when the look and feel registers
 *       {@code ScrollBarUI}, and from {@link javax.swing.plaf.basic.BasicScrollBarUI}
 *       otherwise (the port seeds no {@code ScrollBarUI} key, so the fallback is what
 *       runs; a look and feel that registers one still wins).</li>
 * </ul>
 */
public class JScrollBar extends JComponent implements Adjustable, javax.accessibility.Accessible {

    private static final String uiClassID = "ScrollBarUI";

    /**
     * All model changes are treated as though the user moved the knob: the model's
     * {@code ChangeEvent} is forwarded as an {@link AdjustmentEvent} (JDK behaviour, kept
     * for the plugins whose {@code AdjustmentListener}s poll the value).
     */
    private final ChangeListener fwdAdjustmentEvents = new ModelListener();

    /** The model that holds the scroll bar's minimum, maximum, extent and value. */
    protected BoundedRangeModel model;

    /** VERTICAL or HORIZONTAL. */
    protected int orientation;

    /** Amount a unit (line/arrow) scroll moves the value by. */
    protected int unitIncrement;

    /** Amount a block (page) scroll moves the value by. */
    protected int blockIncrement;

    private List<AdjustmentListener> adjustmentListeners;

    /** Creates a vertical scroll bar with value 0, extent 10, minimum 0 and maximum 100. */
    public JScrollBar() {
        this(VERTICAL);
    }

    /** Creates a scroll bar of the given orientation with the same default range. */
    public JScrollBar(int orientation) {
        this(orientation, 0, 10, 0, 100);
    }

    /**
     * Creates a scroll bar with the given orientation and initial range. The extent is the
     * size of the viewable area, also known as the "visible amount".
     *
     * @throws IllegalArgumentException if orientation is neither VERTICAL nor HORIZONTAL
     */
    public JScrollBar(int orientation, int value, int extent, int min, int max) {
        checkOrientation(orientation);
        this.unitIncrement = 1;
        this.blockIncrement = (extent == 0) ? 1 : extent;
        this.orientation = orientation;
        this.model = new DefaultBoundedRangeModel(value, extent, min, max);
        this.model.addChangeListener(fwdAdjustmentEvents);
        setFocusable(false);
        updateUI();
    }

    private void checkOrientation(int orientation) {
        switch (orientation) {
            case VERTICAL:
            case HORIZONTAL:
                break;
            default:
                throw new IllegalArgumentException("orientation must be one of: VERTICAL, HORIZONTAL");
        }
    }

    /** Installs a scroll bar UI; the parameter is narrowed like the JDK's. */
    public void setUI(ScrollBarUI ui) {
        super.setUI(ui);
    }

    /** The installed {@link ScrollBarUI}, or null when none is installed. */
    @Override
    public ScrollBarUI getUI() {
        return (ScrollBarUI) super.getUI();
    }

    /**
     * Re-resolves the UI from the look and feel, falling back to
     * {@code BasicScrollBarUI} when no {@code ScrollBarUI} is registered (see the class
     * javadoc).
     */
    @Override
    public void updateUI() {
        ComponentUI newUI = UIManager.getUI(this);
        if (!(newUI instanceof ScrollBarUI)) {
            newUI = new javax.swing.plaf.basic.BasicScrollBarUI();
        }
        setUI((ScrollBarUI) newUI);
    }

    /** {@code "ScrollBarUI"} — the key {@code UIManager} resolves this component's UI by. */
    @Override
    public String getUIClassID() {
        return uiClassID;
    }

    @Override
    public int getOrientation() {
        return orientation;
    }

    /** Sets the orientation, firing an "orientation" property change and revalidating. */
    public void setOrientation(int orientation) {
        checkOrientation(orientation);
        int oldValue = this.orientation;
        this.orientation = orientation;
        firePropertyChange("orientation", oldValue, orientation);
        if (oldValue != orientation) {
            revalidate();
        }
    }

    public BoundedRangeModel getModel() {
        return model;
    }

    /** Replaces the model, moving the adjustment listener over to the new one. */
    public void setModel(BoundedRangeModel newModel) {
        BoundedRangeModel oldModel = model;
        if (model != null) {
            model.removeChangeListener(fwdAdjustmentEvents);
        }
        model = newModel;
        if (model != null) {
            model.addChangeListener(fwdAdjustmentEvents);
        }
        firePropertyChange("model", oldModel, model);
        revalidate();
    }

    /**
     * The amount a unit scroll (arrow click, wheel notch) moves the value by. A
     * {@link JScrollPane} widens this to the view's {@code Scrollable} answer when the view
     * knows better (see {@link JScrollPane}).
     */
    public int getUnitIncrement(int direction) {
        return unitIncrement;
    }

    public void setUnitIncrement(int unitIncrement) {
        int oldValue = this.unitIncrement;
        this.unitIncrement = unitIncrement;
        firePropertyChange("unitIncrement", oldValue, unitIncrement);
    }

    /** The amount a block scroll (track/page click, page key) moves the value by. */
    public int getBlockIncrement(int direction) {
        return blockIncrement;
    }

    public void setBlockIncrement(int blockIncrement) {
        int oldValue = this.blockIncrement;
        this.blockIncrement = blockIncrement;
        firePropertyChange("blockIncrement", oldValue, blockIncrement);
    }

    /** {@link Adjustable} compatibility: the same value as {@code getUnitIncrement(1)}. */
    @Override
    public int getUnitIncrement() {
        return unitIncrement;
    }

    /** {@link Adjustable} compatibility: the same value as {@code getBlockIncrement(1)}. */
    @Override
    public int getBlockIncrement() {
        return blockIncrement;
    }

    @Override
    public int getValue() {
        return getModel().getValue();
    }

    /** Forwards the value to the model; the model fires the change events. */
    @Override
    public void setValue(int value) {
        getModel().setValue(value);
    }

    /** The model's extent: how much of the scrolled content is visible. */
    @Override
    public int getVisibleAmount() {
        return getModel().getExtent();
    }

    @Override
    public void setVisibleAmount(int extent) {
        getModel().setExtent(extent);
    }

    @Override
    public int getMinimum() {
        return getModel().getMinimum();
    }

    @Override
    public void setMinimum(int minimum) {
        getModel().setMinimum(minimum);
    }

    /** The model's maximum; the largest value the bar can reach is {@code maximum - extent}. */
    @Override
    public int getMaximum() {
        return getModel().getMaximum();
    }

    @Override
    public void setMaximum(int maximum) {
        getModel().setMaximum(maximum);
    }

    /** True while the user is dragging the thumb (or holding the track). */
    public boolean getValueIsAdjusting() {
        return getModel().getValueIsAdjusting();
    }

    public void setValueIsAdjusting(boolean b) {
        getModel().setValueIsAdjusting(b);
    }

    /**
     * Sets all four model properties at once, forcing the arguments to obey
     * {@code minimum <= value <= value+extent <= maximum}.
     */
    public void setValues(int newValue, int newExtent, int newMin, int newMax) {
        getModel().setRangeProperties(newValue, newExtent, newMin, newMax, getModel().getValueIsAdjusting());
    }

    /**
     * Adds an adjustment listener. Every model change is delivered as an
     * {@link AdjustmentEvent} whose type is {@code TRACK}: a {@code BoundedRangeModel}
     * does not record what caused a value change (JDK behaviour, kept deliberately).
     */
    @Override
    public void addAdjustmentListener(AdjustmentListener l) {
        if (l == null) {
            return;
        }
        if (adjustmentListeners == null) {
            adjustmentListeners = new ArrayList<>();
        }
        if (!adjustmentListeners.contains(l)) {
            adjustmentListeners.add(l);
        }
    }

    @Override
    public void removeAdjustmentListener(AdjustmentListener l) {
        if (adjustmentListeners != null) {
            adjustmentListeners.remove(l);
        }
    }

    /** The registered adjustment listeners, in registration order. */
    public AdjustmentListener[] getAdjustmentListeners() {
        return adjustmentListeners == null
                ? new AdjustmentListener[0]
                : adjustmentListeners.toArray(new AdjustmentListener[0]);
    }

    /** Notifies every adjustment listener of a model change. */
    protected void fireAdjustmentValueChanged(int id, int type, int value) {
        fireAdjustmentValueChanged(id, type, value, getValueIsAdjusting());
    }

    private void fireAdjustmentValueChanged(int id, int type, int value, boolean isAdjusting) {
        if (adjustmentListeners == null || adjustmentListeners.isEmpty()) {
            return;
        }
        AdjustmentEvent e = null;
        for (AdjustmentListener listener : new ArrayList<>(adjustmentListeners)) {
            if (e == null) {
                e = new AdjustmentEvent(this, id, type, value, isAdjusting);
            }
            listener.adjustmentValueChanged(e);
        }
    }

    /** Rigid along the scrolling axis, flexible across it. */
    @Override
    public Dimension getMinimumSize() {
        Dimension pref = getPreferredSize();
        if (orientation == VERTICAL) {
            return new Dimension(pref.width, 5);
        }
        return new Dimension(5, pref.height);
    }

    /** {@inheritDoc} */
    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        if (getOrientation() == VERTICAL) {
            return new Dimension(pref.width, Short.MAX_VALUE);
        }
        return new Dimension(Short.MAX_VALUE, pref.height);
    }

    /** Enables/disables this bar and its arrow buttons together. */
    @Override
    public void setEnabled(boolean x) {
        super.setEnabled(x);
        for (Component child : getComponents()) {
            child.setEnabled(x);
        }
    }

    /**
     * Debug rendering of the bar's increments and orientation. (The port's
     * {@code Component} has no {@code paramString}, so this stands alone rather than
     * appending to a superclass one as the JDK does.)
     */
    protected String paramString() {
        String orientationString = (orientation == HORIZONTAL ? "HORIZONTAL" : "VERTICAL");
        return "blockIncrement=" + blockIncrement
                + ",orientation=" + orientationString
                + ",unitIncrement=" + unitIncrement;
    }

    /**
     * Forwards the model's {@code ChangeEvent} to the adjustment listeners (the JDK's
     * compatibility bridge between the two notification channels).
     */
    private class ModelListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent e) {
            Object source = e.getSource();
            if (source instanceof BoundedRangeModel) {
                BoundedRangeModel changedModel = (BoundedRangeModel) source;
                fireAdjustmentValueChanged(AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
                        AdjustmentEvent.TRACK, changedModel.getValue(), changedModel.getValueIsAdjusting());
            }
        }
    }
}
