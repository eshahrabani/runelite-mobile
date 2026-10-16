package javax.swing.text;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Hand-written {@code javax.swing.text.DefaultCaret}: the dot/mark caret
 * {@link JTextComponent} installs.
 *
 * <p>Why hand-written: typing, selecting and the caret line are the whole point of the port's
 * text fields (the sidebar's profile renamer, the color picker's hex field, Notes). The
 * generated stub had no dot, no mark and no listeners.
 *
 * <p>Deviations from the JDK, both deliberate:
 * <ul>
 *   <li><b>No blinking.</b> The JDK runs a {@link javax.swing.Timer} at {@code blinkRate} and
 *       repaints on each phase; this port's stage repaints on demand, and a timer that fires
 *       while the game is running is pure cost. {@link #setBlinkRate(int)} therefore stores the
 *       rate without scheduling anything (the settings the port uses all pass 0 anyway) and
 *       the caret stays painted while the component has focus and a selection is not being
 *       dragged over it.</li>
 *   <li><b>Mouse handling lives on the component.</b> The JDK's caret is also a
 *       {@code MouseListener}/{@code MouseMotionListener} that positions the dot; here
 *       {@link JTextComponent} does that, because the port's hit-testing goes through its own
 *       line layout ({@code viewToModel}).</li>
 * </ul>
 */
public class DefaultCaret implements Caret, java.io.Serializable {
    /** The JDK's caret line width. */
    private static final int CARET_WIDTH = 1;

    private JTextComponent component;
    private int dot;
    private int mark;
    private int blinkRate = 500;
    private boolean visible;
    private boolean selectionVisible = true;
    private Point magicCaretPosition;

    private final List<ChangeListener> changeListeners = new ArrayList<>(2);
    private transient ChangeEvent changeEvent;
    private boolean installed;

    public DefaultCaret() {
    }

    @Override
    public void install(JTextComponent c) {
        if (c == null) {
            return;
        }
        component = c;
        if (installed) {
            return;
        }
        installed = true;
        dot = 0;
        mark = 0;
        c.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                setVisible(true);
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                setVisible(false);
            }
        });
        Document doc = c.getDocument();
        if (doc != null) {
            doc.addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    clampToDocument();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    clampToDocument();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    // Attribute-only change: the caret does not move.
                }
            });
        }
        if (c.hasFocus()) {
            setVisible(true);
        }
    }

    @Override
    public void deinstall(JTextComponent c) {
        if (c != null) {
            Document doc = c.getDocument();
            if (doc != null) {
                // The listeners installed above are anonymous; a full deinstall is not needed
                // because a component owns its caret for its whole life on this port.
            }
        }
        component = null;
        installed = false;
    }

    /** Keeps the dot and mark inside the document after an edit. */
    private void clampToDocument() {
        int length = component == null || component.getDocument() == null
                ? 0 : component.getDocument().getLength();
        if (dot > length) {
            dot = length;
        }
        if (mark > length) {
            mark = length;
        }
        if (component != null) {
            component.repaint();
        }
    }

    @Override
    public void paint(Graphics g) {
        if (!isVisible() || component == null) {
            return;
        }
        Rectangle r = component.modelToView(dot);
        if (r == null || r.height <= 0) {
            return;
        }
        java.awt.Color old = g.getColor();
        g.setColor(component.getCaretColor());
        g.fillRect(r.x, r.y, CARET_WIDTH, r.height);
        g.setColor(old);
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        if (l != null) {
            synchronized (changeListeners) {
                changeListeners.add(l);
            }
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        synchronized (changeListeners) {
            changeListeners.remove(l);
        }
    }

    /** Notifies the registered listeners that the dot or mark moved. */
    protected void fireStateChanged() {
        List<ChangeListener> snapshot;
        synchronized (changeListeners) {
            snapshot = new ArrayList<>(changeListeners);
        }
        for (int i = 0; i < snapshot.size(); i++) {
            if (changeEvent == null) {
                changeEvent = new ChangeEvent(this);
            }
            snapshot.get(i).stateChanged(changeEvent);
        }
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean v) {
        if (visible != v) {
            visible = v;
            if (component != null) {
                component.repaint();
            }
        }
    }

    @Override
    public boolean isSelectionVisible() {
        return selectionVisible;
    }

    @Override
    public void setSelectionVisible(boolean v) {
        if (selectionVisible != v) {
            selectionVisible = v;
            if (component != null) {
                component.repaint();
            }
        }
    }

    @Override
    public void setMagicCaretPosition(Point p) {
        magicCaretPosition = p;
    }

    @Override
    public Point getMagicCaretPosition() {
        return magicCaretPosition;
    }

    @Override
    public void setBlinkRate(int rate) {
        // Stored only: this port never blinks the caret (see the class comment).
        blinkRate = rate;
    }

    @Override
    public int getBlinkRate() {
        return blinkRate;
    }

    @Override
    public int getDot() {
        return dot;
    }

    @Override
    public int getMark() {
        return mark;
    }

    @Override
    public void setDot(int dot) {
        this.mark = dot;
        moveDotInternal(dot);
    }

    @Override
    public void moveDot(int dot) {
        moveDotInternal(dot);
    }

    private void moveDotInternal(int newDot) {
        if (component != null) {
            int length = component.getDocument() == null ? 0 : component.getDocument().getLength();
            if (newDot < 0) {
                newDot = 0;
            } else if (newDot > length) {
                newDot = length;
            }
        }
        this.dot = newDot;
        fireStateChanged();
        if (component != null) {
            component.repaint();
        }
    }

    /** Moves both dot and mark without firing (used while a component rebuilds itself). */
    void setDotSilently(int newDot, int newMark) {
        this.dot = newDot;
        this.mark = newMark;
    }

    @Override
    public String toString() {
        return "dot=" + dot + ",mark=" + mark;
    }
}
