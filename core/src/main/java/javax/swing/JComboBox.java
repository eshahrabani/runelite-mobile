package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.runelite.mobile.bridge.UiBridge;

/**
 * Swing {@code JComboBox}: a closed cell painted through a {@link ListCellRenderer} plus a
 * drop-down {@link JList}.
 *
 * <p>Hand-written because the port's configuration panels are built from real combo boxes.
 * The popup is not a native window: it is a {@code JList} shown through
 * {@link PopupFactory} (the port's factory registers contents in the stage overlay), so the
 * stage paints it above the sidebar and hit-tests it first. A tap on a row selects it
 * (firing exactly one {@code ItemEvent} and one {@code ActionEvent}); a tap anywhere else
 * inside the enclosing root closes it.
 *
 * <p>Scope: model accessors, selection, item/action listeners, renderer, row counts and
 * the popup. Inline editing is not implemented — {@link #setEditable(boolean)} stores the
 * flag and an editable box paints as a plain field with no completion and no editor.
 */
public class JComboBox<E> extends JComponent
        implements java.awt.ItemSelectable, ListDataListener, ActionListener,
        javax.accessibility.Accessible {

    private static final long serialVersionUID = 2887414602784700434L;

    private ComboBoxModel<E> dataModel;
    private ListCellRenderer<? super E> renderer = new DefaultListCellRenderer();
    private final List<ItemListener> itemListeners = new ArrayList<>();
    private final List<ActionListener> actionListeners = new ArrayList<>();
    private int maximumRowCount = 8;
    private int minimumRowCount = 0;
    private boolean editable;

    /** The drop-down row list; also the "list" a renderer sees for the closed cell. */
    private final JList<E> popupList = new JList<E>();
    /** The in-stage popup showing {@link #popupList}, or null when closed. */
    private Popup popup;
    private boolean popupVisible;
    private Component dismissRoot;
    private final MouseAdapter dismissListener = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            hidePopup();
        }
    };

    public JComboBox() {
        this(new DefaultComboBoxModel<E>());
    }

    public JComboBox(E[] items) {
        this(new DefaultComboBoxModel<E>(items));
    }

    public JComboBox(ComboBoxModel<E> model) {
        setOpaque(true);
        Color bg = UIManager.getColor("ComboBox.background");
        Color fg = UIManager.getColor("ComboBox.foreground");
        if (bg != null) {
            setBackground(bg);
        }
        if (fg != null) {
            setForeground(fg);
        }
        java.awt.Font f = UIManager.getFont("ComboBox.font");
        if (f != null) {
            setFont(f);
        }
        // A tap in the popup selects the row through the popup list's own selection model;
        // that change becomes the combo's selection and fires the ItemEvent.
        popupList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int index = popupList.getSelectedIndex();
                    if (index >= 0) {
                        setSelectedIndex(index);
                    }
                }
            }
        });
        // Tapping the already-selected row produces no selection change, so close here.
        popupList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                hidePopup();
            }
        });
        setModel(model);
        syncPopupList();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled() && !editable) {
                    togglePopup();
                    e.consume();
                }
            }
        });
    }

    // ------------------------------------------------------------------ model

    public void setModel(ComboBoxModel<E> model) {
        if (this.dataModel != null) {
            this.dataModel.removeListDataListener(this);
        }
        this.dataModel = model == null ? new DefaultComboBoxModel<E>() : model;
        this.dataModel.addListDataListener(this);
        popupList.setModel(this.dataModel);
        revalidate();
        repaint();
    }

    public ComboBoxModel<E> getModel() {
        return dataModel;
    }

    public void setSelectedItem(Object anObject) {
        Object old = dataModel.getSelectedItem();
        if (old != null ? old.equals(anObject) : anObject == null) {
            return;
        }
        // The model fires CONTENTS_CHANGED, which drives selectedItemChanged().
        dataModel.setSelectedItem(anObject);
    }

    public Object getSelectedItem() {
        return dataModel.getSelectedItem();
    }

    public void setSelectedIndex(int index) {
        if (index == -1) {
            setSelectedItem(null);
        } else if (index >= 0 && index < dataModel.getSize()) {
            setSelectedItem(dataModel.getElementAt(index));
        }
    }

    public int getSelectedIndex() {
        Object selected = dataModel.getSelectedItem();
        int n = dataModel.getSize();
        for (int i = 0; i < n; i++) {
            if (Objects.equals(selected, dataModel.getElementAt(i))) {
                return i;
            }
        }
        return -1;
    }

    public int getItemCount() {
        return dataModel.getSize();
    }

    public E getItemAt(int index) {
        return dataModel.getElementAt(index);
    }

    @Override
    public Object[] getSelectedObjects() {
        Object selected = dataModel.getSelectedItem();
        return selected == null ? null : new Object[] {selected};
    }

    // -------------------------------------------------------------- renderer

    public void setRenderer(ListCellRenderer<? super E> renderer) {
        this.renderer = renderer;
        if (popupList != null) {
            popupList.setCellRenderer(renderer);
        }
        repaint();
    }

    public ListCellRenderer<? super E> getRenderer() {
        return renderer;
    }

    // --------------------------------------------------------------- popup

    public void setMaximumRowCount(int maximumRowCount) {
        this.maximumRowCount = Math.max(0, maximumRowCount);
    }

    public int getMaximumRowCount() {
        return maximumRowCount;
    }

    public void setMinimumRowCount(int minimumRowCount) {
        this.minimumRowCount = Math.max(0, minimumRowCount);
    }

    public int getMinimumRowCount() {
        return minimumRowCount;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        repaint();
    }

    public boolean isEditable() {
        return editable;
    }

    public void setPopupVisible(boolean visible) {
        if (visible) {
            showPopup();
        } else {
            hidePopup();
        }
    }

    public boolean isPopupVisible() {
        return popupVisible;
    }

    public void showPopup() {
        if (popupVisible || editable || dataModel.getSize() == 0) {
            return;
        }
        syncPopupList();
        popupList.setSelectedIndex(getSelectedIndex());

        int rows = Math.min(dataModel.getSize(), Math.max(minimumRowCount, maximumRowCount));
        rows = Math.max(1, rows);
        int rowHeight = Math.max(1, rendererHeight());
        int width = Math.max(getWidth(), measurePopupWidth() + arrowWidth());
        popupList.setSize(width, rows * rowHeight);

        Point origin = stageLocation(this);
        int px = origin.x;
        int py = origin.y + getHeight();
        // UiBridge maps a popup's pointer position relative to the component's own
        // location and (for a window) subtracts the title-bar height, so the component's
        // location has to mirror that; the popup itself paints at the stage position.
        int offset = isInWindow(this) ? UiBridge.WINDOW_TITLE_HEIGHT : 0;
        popupList.setLocation(px, py - offset);
        popup = PopupFactory.getSharedInstance().getPopup(this, popupList, px, py);
        popup.show();
        popupVisible = true;
        installDismissListener();
        repaint();
    }

    public void hidePopup() {
        if (!popupVisible) {
            return;
        }
        if (popup != null) {
            popup.hide();
            popup = null;
        }
        popupVisible = false;
        removeDismissListener();
        repaint();
    }

    private void togglePopup() {
        if (popupVisible) {
            hidePopup();
        } else {
            showPopup();
        }
    }

    private void installDismissListener() {
        Component root = topLevelComponent(this);
        if (root == null || root == this) {
            return;
        }
        dismissRoot = root;
        root.addMouseListener(dismissListener);
    }

    private void removeDismissListener() {
        if (dismissRoot != null) {
            dismissRoot.removeMouseListener(dismissListener);
            dismissRoot = null;
        }
    }

    // -------------------------------------------------------------- defaults

    /** Keeps the popup list's renderer/colours in step with the combo's. */
    private void syncPopupList() {
        popupList.setCellRenderer(renderer);
        popupList.setFont(getFont());
        popupList.setForeground(getForeground());
        popupList.setBackground(getBackground());
        popupList.setSelectionBackground(popupSelectionBackground());
        popupList.setSelectionForeground(popupSelectionForeground());
    }

    private Color popupSelectionBackground() {
        Color c = UIManager.getColor("ComboBox.selectionBackground");
        return c != null ? c : getBackground();
    }

    private Color popupSelectionForeground() {
        Color c = UIManager.getColor("ComboBox.selectionForeground");
        return c != null ? c : getForeground();
    }

    // -------------------------------------------------------------- listeners

    @Override
    public void addItemListener(ItemListener aListener) {
        if (aListener != null) {
            itemListeners.add(aListener);
        }
    }

    @Override
    public void removeItemListener(ItemListener aListener) {
        itemListeners.remove(aListener);
    }

    public void addActionListener(ActionListener aListener) {
        if (aListener != null) {
            actionListeners.add(aListener);
        }
    }

    public void removeActionListener(ActionListener aListener) {
        actionListeners.remove(aListener);
    }

    /** The closed cell paints through the renderer; the arrow is drawn on the right. */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth();
        int h = getHeight();
        int arrow = editable ? 0 : arrowWidth();
        Component cell = rendererComponent(dataModel.getSelectedItem(), getSelectedIndex(), false);
        if (cell != null && w > 0 && h > 0) {
            Graphics g2 = g.create();
            try {
                g2.clipRect(0, 0, Math.max(0, w - arrow), h);
                cell.setBounds(0, 0, Math.max(0, w - arrow), h);
                cell.setVisible(true);
                cell.paint(g2);
            } finally {
                g2.dispose();
            }
        }
        if (arrow > 0 && h > 0) {
            paintArrow(g, w - arrow, h);
        }
    }

    private void paintArrow(Graphics g, int boxX, int boxHeight) {
        int cx = boxX + arrowWidth() / 2;
        int cy = boxHeight / 2;
        g.setColor(getForeground() != null ? getForeground() : Color.GRAY);
        int[] xs = {cx - 4, cx + 4, cx};
        int[] ys = {cy - 2, cy - 2, cy + 3};
        g.fillPolygon(xs, ys, 3);
    }

    private static int arrowWidth() {
        return 18;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        int width = arrowWidth();
        int height = Math.max(1, rendererHeight());
        int n = dataModel.getSize();
        for (int i = 0; i < n; i++) {
            Component cell = rendererComponent(dataModel.getElementAt(i), i, false);
            Dimension d = cell == null ? null : cell.getPreferredSize();
            if (d != null) {
                width += d.width;
                height = Math.max(height, d.height);
            }
        }
        if (n == 0) {
            width += 60;
        }
        return new Dimension(width, height);
    }

    @Override
    public void setPreferredSize(Dimension preferredSize) {
        super.setPreferredSize(preferredSize);
    }

    @Override
    public void setToolTipText(String text) {
        super.setToolTipText(text);
    }

    private Component rendererComponent(Object value, int index, boolean isSelected) {
        ListCellRenderer<? super E> r = renderer;
        if (r == null) {
            r = new DefaultListCellRenderer();
        }
        @SuppressWarnings("unchecked")
        E v = (E) value;
        // The JDK hands the combo's list box to the renderer for both the closed cell and
        // the popup rows; popupList is that list here, and it carries the combo's colours.
        return r.getListCellRendererComponent(popupList, v, index, isSelected, false);
    }

    private int rendererHeight() {
        Component cell = rendererComponent(dataModel.getSelectedItem(),
                Math.max(0, getSelectedIndex()), false);
        if (cell != null) {
            Dimension d = cell.getPreferredSize();
            if (d != null && d.height > 0) {
                return d.height;
            }
        }
        FontMetrics fm = getFontMetrics(getFont());
        return fm == null ? 16 : fm.getHeight() + 4;
    }

    private int measurePopupWidth() {
        int width = 0;
        int n = dataModel.getSize();
        for (int i = 0; i < n; i++) {
            Component cell = rendererComponent(dataModel.getElementAt(i), i, false);
            Dimension d = cell == null ? null : cell.getPreferredSize();
            if (d != null) {
                width = Math.max(width, d.width);
            }
        }
        return Math.max(width, 60);
    }

    // ------------------------------------------------------------ ListData

    @Override
    public void contentsChanged(ListDataEvent e) {
        selectedItemChanged();
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        repaint();
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        repaint();
    }

    /** The JDK's one-per-change hook: hide the popup, then notify listeners. */
    private void selectedItemChanged() {
        hidePopup();
        fireItemEvent(dataModel.getSelectedItem(), java.awt.event.ItemEvent.SELECTED);
        fireActionEvent();
        repaint();
    }

    private void fireItemEvent(Object item, int stateChange) {
        if (itemListeners.isEmpty()) {
            return;
        }
        java.awt.event.ItemEvent event = new java.awt.event.ItemEvent(
                this, java.awt.event.ItemEvent.ITEM_STATE_CHANGED, item, stateChange);
        for (ItemListener l : new ArrayList<>(itemListeners)) {
            l.itemStateChanged(event);
        }
    }

    private void fireActionEvent() {
        if (actionListeners.isEmpty()) {
            return;
        }
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                Integer.toString(Math.max(0, getSelectedIndex())));
        for (ActionListener l : new ArrayList<>(actionListeners)) {
            l.actionPerformed(event);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // JComboBox is an ActionListener so the UI can route the popup's actions here;
        // there is no UI delegate on this port, so nothing arrives.
    }

    // ---------------------------------------------------------- geometry utils

    private static Component topLevelComponent(Component c) {
        Component top = c;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        return top;
    }

    private static boolean isInWindow(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof java.awt.Window) {
                return true;
            }
        }
        return false;
    }

    /** The component's origin in stage coordinates (window content gets the title bar). */
    private static Point stageLocation(Component c) {
        Point p = c.getLocationOnScreen();
        if (isInWindow(c)) {
            p.y += UiBridge.WINDOW_TITLE_HEIGHT;
        }
        return p;
    }

}
