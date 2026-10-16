package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import org.runelite.mobile.bridge.AWTBridge;

/**
 * Swing's coordinate/EDT/label helpers, hand-written for this port.
 *
 * <p>The desktop JDK implements this class on top of AWT's real event queue, its
 * screen-coordinate space and {@code sun.swing.SwingUtilities2}. None of those exist
 * here: the port's "screen" is the stage (the sidebar bitmap or a virtual window), the
 * event dispatch thread is {@link org.runelite.mobile.bridge.SwingEdt}'s own thread, and
 * text metrics come from {@link FontMetrics} alone. So the coordinate conversions walk
 * the component tree, {@code invokeLater}/{@code invokeAndWait} route to
 * {@link AWTBridge}, and {@link #layoutCompoundLabel} is the real icon+text placement
 * algorithm with the {@code SwingUtilities2} refinements dropped (documented per method).
 */
public class SwingUtilities implements SwingConstants {

    // ------------------------------------------------------------ the EDT

    /**
     * Runs {@code doRun} asynchronously on the port's event dispatch thread (the thread
     * RuneLite's plugins are started on), like the JDK's {@code EventQueue.invokeLater}.
     */
    public static void invokeLater(Runnable doRun) {
        if (doRun == null) {
            return;
        }
        AWTBridge.post(doRun);
    }

    /**
     * Runs {@code doRun} on the dispatch thread and waits for it.
     *
     * <p>Deviation: the JDK throws when called from the dispatch thread; here the task
     * runs inline instead, which is what every caller on this port means (see
     * {@link AWTBridge#invokeAndWait}). {@code InterruptedException} is declared for
     * source compatibility with the desktop signature; it is never thrown.
     */
    public static void invokeAndWait(Runnable doRun) throws InterruptedException {
        if (doRun == null) {
            return;
        }
        AWTBridge.invokeAndWait(doRun);
    }

    /** True when the calling thread is the port's event dispatch thread. */
    public static boolean isEventDispatchThread() {
        return AWTBridge.isUiThread();
    }

    // ------------------------------------------------------------ geometry

    /**
     * Converts a point from {@code source}'s space to {@code destination}'s space.
     *
     * <p>JDK semantics (Java 9+ implementation): the point is moved to the stage by
     * {@link #convertPointToScreen} and back down by {@link #convertPointFromScreen}.
     * A {@code null} end means "the root (window) of the other end"; when neither end is
     * a window the JDK's {@code Error} is raised, exactly as on the desktop. Two
     * components that share a stage root but no window (the sidebar tree) convert
     * correctly because both walks accumulate to the same topmost ancestor.
     */
    public static Point convertPoint(Component source, Point aPoint, Component destination) {
        if (aPoint == null) {
            return null;
        }
        Point p = new Point(aPoint);
        if (source == null && destination == null) {
            return p;
        }
        if (source == null) {
            source = getWindowAncestor(destination);
            if (source == null) {
                throw new Error("Source component not connected to component tree hierarchy");
            }
        }
        convertPointToScreen(p, source);
        if (destination == null) {
            destination = getWindowAncestor(source);
            if (destination == null) {
                throw new Error("Destination component not connected to component tree hierarchy");
            }
        }
        convertPointFromScreen(p, destination);
        return p;
    }

    /** {@link #convertPoint(Component, Point, Component)} with integer coordinates. */
    public static Point convertPoint(Component source, int x, int y, Component destination) {
        return convertPoint(source, new Point(x, y), destination);
    }

    /** The rectangle's origin converted; its size is unchanged (JDK semantics). */
    public static Rectangle convertRectangle(Component source, Rectangle aRectangle, Component destination) {
        Point p = convertPoint(source, new Point(aRectangle.x, aRectangle.y), destination);
        return new Rectangle(p.x, p.y, aRectangle.width, aRectangle.height);
    }

    /**
     * Stage coordinates of the component's origin, added to {@code p}.
     *
     * <p>The port's stage is the coordinate space {@link Component#getLocationOnScreen}
     * reports in, so the two agree. Components whose topmost ancestor is not a window
     * (the sidebar tree) accumulate up to that ancestor and stop, which is the port's
     * equivalent of the desktop's single screen space.
     */
    public static void convertPointToScreen(Point p, Component c) {
        for (Component cur = c; cur != null; cur = cur.getParent()) {
            p.x += cur.getX();
            p.y += cur.getY();
            if (cur instanceof Window) {
                break;
            }
        }
    }

    /** Inverse of {@link #convertPointToScreen}. */
    public static void convertPointFromScreen(Point p, Component c) {
        for (Component cur = c; cur != null; cur = cur.getParent()) {
            p.x -= cur.getX();
            p.y -= cur.getY();
            if (cur instanceof Window) {
                break;
            }
        }
    }

    /** The component's bounds moved to (0,0). */
    public static Rectangle getLocalBounds(Component aComponent) {
        Rectangle b = new Rectangle(aComponent.getBounds());
        b.x = 0;
        b.y = 0;
        return b;
    }

    /** The first {@link Window} ancestor of {@code c}, or {@code null}. */
    public static Window getWindowAncestor(Component c) {
        if (c == null) {
            return null;
        }
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof Window) {
                return (Window) p;
            }
        }
        return null;
    }

    /** JDK alias of {@link #getWindowAncestor}. */
    public static Window windowForComponent(Component c) {
        return getWindowAncestor(c);
    }

    /** The {@link JRootPane} {@code c} belongs to, or {@code null}. */
    public static JRootPane getRootPane(Component c) {
        if (c instanceof RootPaneContainer) {
            return ((RootPaneContainer) c).getRootPane();
        }
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof JRootPane) {
                return (JRootPane) p;
            }
        }
        return null;
    }

    /**
     * The root of {@code c}'s tree: its first {@link Window} ancestor, or {@code null}
     * when the tree is not inside a window (the sidebar's tree on this port).
     */
    public static Component getRoot(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof Window) {
                return p;
            }
        }
        return null;
    }

    /** True when {@code a} is {@code b} or descends from it. */
    public static boolean isDescendingFrom(Component a, Component b) {
        if (a == b) {
            return true;
        }
        for (Container p = a == null ? null : a.getParent(); p != null; p = p.getParent()) {
            if (p == b) {
                return true;
            }
        }
        return false;
    }

    /** Re-targets a mouse event onto {@code destination} with converted coordinates. */
    public static MouseEvent convertMouseEvent(Component source, MouseEvent sourceEvent, Component destination) {
        if (sourceEvent == null) {
            return null;
        }
        Point p = convertPoint(source, new Point(sourceEvent.getX(), sourceEvent.getY()), destination);
        Component newSource = destination != null ? destination : source;
        int modifiers = sourceEvent.getModifiers() | sourceEvent.getModifiersEx();
        if (sourceEvent instanceof MouseWheelEvent) {
            MouseWheelEvent we = (MouseWheelEvent) sourceEvent;
            return new MouseWheelEvent(newSource, we.getID(), we.getWhen(), modifiers, p.x, p.y,
                    we.getClickCount(), we.isPopupTrigger(), we.getScrollType(),
                    we.getScrollAmount(), we.getWheelRotation());
        }
        return new MouseEvent(newSource, sourceEvent.getID(), sourceEvent.getWhen(), modifiers,
                p.x, p.y, sourceEvent.getClickCount(), sourceEvent.isPopupTrigger(),
                sourceEvent.getButton());
    }

    // ------------------------------------------------------------ rectangles

    /** Intersects the two rectangles into {@code dest} (zeroed when they do not overlap). */
    public static Rectangle computeIntersection(int x, int y, int width, int height, Rectangle dest) {
        int x1 = Math.max(x, dest.x);
        int x2 = Math.min(x + width, dest.x + dest.width);
        int y1 = Math.max(y, dest.y);
        int y2 = Math.min(y + height, dest.y + dest.height);
        dest.x = x1;
        dest.y = y1;
        dest.width = x2 - x1;
        dest.height = y2 - y1;
        if (dest.width < 0 || dest.height < 0) {
            dest.x = 0;
            dest.y = 0;
            dest.width = 0;
            dest.height = 0;
        }
        return dest;
    }

    /** Unions the two rectangles into {@code dest}. */
    public static Rectangle computeUnion(int x, int y, int width, int height, Rectangle dest) {
        int x1 = Math.min(x, dest.x);
        int x2 = Math.max(x + width, dest.x + dest.width);
        int y1 = Math.min(y, dest.y);
        int y2 = Math.max(y + height, dest.y + dest.height);
        dest.x = x1;
        dest.y = y1;
        dest.width = x2 - x1;
        dest.height = y2 - y1;
        return dest;
    }

    // ------------------------------------------------------------ text

    /**
     * Width of {@code str} in {@code fm}.
     *
     * <p>The JDK routes this through {@code SwingUtilities2.stringWidth}, which adds
     * font-context/kerning handling. The port's metrics are logical (Android measures
     * with the platform rasteriser), so {@link FontMetrics#stringWidth} is already the
     * same number this method must return.
     */
    public static int computeStringWidth(FontMetrics fm, String str) {
        if (fm == null || str == null || str.isEmpty()) {
            return 0;
        }
        return fm.stringWidth(str);
    }

    /**
     * Positions an icon and a text relative to {@code viewR} and returns the (possibly
     * clipped) text, the JDK's {@code layoutCompoundLabel(JComponent, ...)}.
     *
     * <p>{@code LEADING}/{@code TRAILING} are resolved to {@code LEFT}/{@code RIGHT} for
     * a left-to-right, top-to-bottom component; the port has no
     * {@code ComponentOrientation} (nothing in the shipped client references one), so
     * the desktop's right-to-left mirroring does not exist here.
     */
    public static String layoutCompoundLabel(JComponent c, FontMetrics fm, String text, Icon icon,
                                             int verticalAlignment, int horizontalAlignment,
                                             int verticalTextPosition, int horizontalTextPosition,
                                             Rectangle viewR, Rectangle iconR, Rectangle textR,
                                             int textIconGap) {
        int hAlign = horizontalAlignment;
        int hTextPos = horizontalTextPosition;
        if (horizontalAlignment == LEADING) {
            hAlign = LEFT;
        } else if (horizontalAlignment == TRAILING) {
            hAlign = RIGHT;
        }
        if (horizontalTextPosition == LEADING) {
            hTextPos = LEFT;
        } else if (horizontalTextPosition == TRAILING) {
            hTextPos = RIGHT;
        }
        return layoutCompoundLabelImpl(c, fm, text, icon, verticalAlignment, hAlign,
                verticalTextPosition, hTextPos, viewR, iconR, textR, textIconGap);
    }

    /**
     * Same as {@link #layoutCompoundLabel(JComponent, FontMetrics, String, Icon, int, int,
     * int, int, Rectangle, Rectangle, Rectangle, int)} for callers that have a
     * {@link JLabel} at hand (the signature the shipped client's {@code BasicLabelUI}
     * resolves).
     */
    public static String layoutCompoundLabel(JLabel label, FontMetrics fm, String text, Icon icon,
                                             int verticalAlignment, int horizontalAlignment,
                                             int verticalTextPosition, int horizontalTextPosition,
                                             Rectangle viewR, Rectangle iconR, Rectangle textR,
                                             int gap) {
        return layoutCompoundLabel((JComponent) label, fm, text, icon, verticalAlignment,
                horizontalAlignment, verticalTextPosition, horizontalTextPosition, viewR, iconR,
                textR, gap);
    }

    /**
     * The JDK's 11-argument form, which resolves neither {@code LEADING} nor
     * {@code TRAILING} (they fall through to {@code LEFT}/{@code CENTER}).
     */
    public static String layoutCompoundLabel(FontMetrics fm, String text, Icon icon,
                                             int verticalAlignment, int horizontalAlignment,
                                             int verticalTextPosition, int horizontalTextPosition,
                                             Rectangle viewR, Rectangle iconR, Rectangle textR,
                                             int textIconGap) {
        return layoutCompoundLabelImpl(null, fm, text, icon, verticalAlignment,
                horizontalAlignment, verticalTextPosition, horizontalTextPosition, viewR,
                iconR, textR, textIconGap);
    }

    /**
     * The JDK's {@code layoutCompoundLabelImpl}, without the {@code SwingUtilities2}
     * refinements: there is no HTML view (HTML labels lay themselves out in their UI),
     * no left/right side-bearing correction and no fractional-metric rounding. Text is
     * measured and clipped with {@link FontMetrics} alone, and the clipped string the
     * JDK returns (with a trailing {@code "..."}) is returned here too.
     */
    private static String layoutCompoundLabelImpl(JComponent c, FontMetrics fm, String text, Icon icon,
                                                  int verticalAlignment, int horizontalAlignment,
                                                  int verticalTextPosition, int horizontalTextPosition,
                                                  Rectangle viewR, Rectangle iconR, Rectangle textR,
                                                  int textIconGap) {
        if (viewR == null || iconR == null || textR == null || fm == null) {
            // A component that is not displayable can hand out null metrics on this port;
            // returning the text unchanged is friendlier than the desktop's NPE.
            return text;
        }
        if (icon != null) {
            iconR.width = icon.getIconWidth();
            iconR.height = icon.getIconHeight();
        } else {
            iconR.width = 0;
            iconR.height = 0;
        }

        boolean textIsEmpty = (text == null) || text.isEmpty();
        int gap;
        if (textIsEmpty) {
            textR.width = 0;
            textR.height = 0;
            text = "";
            gap = 0;
        } else {
            gap = icon == null ? 0 : textIconGap;
            int availTextWidth = horizontalTextPosition == CENTER
                    ? viewR.width
                    : viewR.width - (iconR.width + gap);
            if (availTextWidth < 0) {
                availTextWidth = 0;
            }
            textR.width = computeStringWidth(fm, text);
            if (textR.width > availTextWidth) {
                text = clipString(fm, text, availTextWidth);
                textR.width = computeStringWidth(fm, text);
            }
            textR.height = fm.getHeight();
        }

        if (verticalTextPosition == TOP) {
            textR.y = horizontalTextPosition != CENTER ? 0 : -(textR.height + gap);
        } else if (verticalTextPosition == CENTER) {
            textR.y = iconR.height / 2 - textR.height / 2;
        } else {
            textR.y = horizontalTextPosition != CENTER
                    ? iconR.height - textR.height
                    : iconR.height + gap;
        }

        if (horizontalTextPosition == LEFT) {
            textR.x = -(textR.width + gap);
        } else if (horizontalTextPosition == CENTER) {
            textR.x = iconR.width / 2 - textR.width / 2;
        } else {
            textR.x = iconR.width + gap;
        }

        int labelR_x = Math.min(iconR.x, textR.x);
        int labelR_width = Math.max(iconR.x + iconR.width, textR.x + textR.width) - labelR_x;
        int labelR_y = Math.min(iconR.y, textR.y);
        int labelR_height = Math.max(iconR.y + iconR.height, textR.y + textR.height) - labelR_y;

        int dx;
        int dy;
        if (verticalAlignment == TOP) {
            dy = viewR.y - labelR_y;
        } else if (verticalAlignment == CENTER) {
            dy = viewR.y + viewR.height / 2 - (labelR_y + labelR_height / 2);
        } else {
            dy = viewR.y + viewR.height - (labelR_y + labelR_height);
        }
        if (horizontalAlignment == LEFT) {
            dx = viewR.x - labelR_x;
        } else if (horizontalAlignment == RIGHT) {
            dx = viewR.x + viewR.width - (labelR_x + labelR_width);
        } else {
            dx = viewR.x + viewR.width / 2 - (labelR_x + labelR_width / 2);
        }

        textR.x += dx;
        textR.y += dy;
        iconR.x += dx;
        iconR.y += dy;
        return text;
    }

    /**
     * The JDK's {@code SwingUtilities2.clipString}: truncates {@code string} so it fits
     * {@code availTextWidth}, ending it with {@code "..."} when it had to be cut.
     */
    private static String clipString(FontMetrics fm, String string, int availTextWidth) {
        if (string == null || string.isEmpty()) {
            return "";
        }
        if (computeStringWidth(fm, string) <= availTextWidth) {
            return string;
        }
        String ellipsis = "...";
        int clipWidth = computeStringWidth(fm, ellipsis);
        int nChars = 0;
        for (int i = 0; i < string.length(); i++) {
            if (computeStringWidth(fm, string.substring(0, i + 1)) + clipWidth > availTextWidth) {
                break;
            }
            nChars = i + 1;
        }
        return string.substring(0, nChars) + ellipsis;
    }

    // ------------------------------------------------------------ painting

    /**
     * Paints a component into an arbitrary rectangle of {@code g} (the JDK's
     * {@code CellRendererPane} semantics, used by cell renderers).
     *
     * <p>Deviation: the port has no {@code CellRendererPane}, and adding one would be
     * wrong here — {@code CellRendererPane} overrides {@code paint} to do nothing, so a
     * renderer parented to a plain container would then be painted twice by its parent.
     * Instead the component is sized to the rectangle and painted directly under a
     * clipped copy of {@code g}, which is the observable result the JDK promises; the
     * enclosing container {@code p} is not used (it exists for signature compatibility
     * and because a renderer must stay usable outside any container).
     */
    public static void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h) {
        if (g == null || c == null || w <= 0 || h <= 0) {
            return;
        }
        c.setBounds(x, y, w, h);
        if (c instanceof Container) {
            ((Container) c).validate();
        } else {
            c.validate();
        }
        Graphics gc = g.create();
        try {
            gc.translate(x, y);
            gc.clipRect(0, 0, w, h);
            c.paint(gc);
        } finally {
            gc.dispose();
        }
    }

    /** {@link #paintComponent(Graphics, Component, Container, int, int, int, int)} with a rectangle. */
    public static void paintComponent(Graphics g, Component c, Container p, Rectangle r) {
        if (r == null) {
            return;
        }
        paintComponent(g, c, p, r.x, r.y, r.width, r.height);
    }

    /**
     * Runs {@code updateUI()} over the component tree and re-lays the root out (the JDK's
     * {@code updateComponentTreeUI}).
     */
    public static void updateComponentTreeUI(Component c) {
        if (c == null) {
            return;
        }
        updateComponentTreeUI0(c);
        c.invalidate();
        c.validate();
        c.repaint();
    }

    /** {@link #updateComponentTreeUI(Component)} for a window. */
    public static void updateComponentTreeUI(Window w) {
        updateComponentTreeUI((Component) w);
    }

    private static void updateComponentTreeUI0(Component c) {
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            jc.updateUI();
            JPopupMenu popup = jc.getComponentPopupMenu();
            if (popup != null) {
                updateComponentTreeUI(popup);
            }
        }
        Component[] children = c instanceof Container ? ((Container) c).getComponents() : null;
        if (children != null) {
            for (Component child : children) {
                updateComponentTreeUI0(child);
            }
        }
    }

    // ------------------------------------------------------------ keys/actions

    /**
     * Runs {@code action}, if any, against {@code event}, giving it the action's command
     * string (or the event's character) as {@code ActionEvent} command.
     *
     * <p>Deviation: the JDK first asks {@code action.accept(sender)}; this port's
     * {@link Action} has no {@code accept} (nothing in the shipped client references it),
     * so every action is accepted. {@code registerKeyboardAction(null, ...)} is
     * represented by a placeholder action, which therefore does run here.
     */
    public static boolean notifyAction(Action action, KeyStroke ks, KeyEvent event, Object sender, int modifiers) {
        if (action == null) {
            return false;
        }
        Object commandO = action.getValue(Action.ACTION_COMMAND_KEY);
        String command;
        if (commandO != null) {
            command = commandO.toString();
        } else if (event != null && event.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            command = String.valueOf(event.getKeyChar());
        } else {
            command = null;
        }
        long when = event != null ? event.getWhen() : System.currentTimeMillis();
        action.actionPerformed(new ActionEvent(sender, ActionEvent.ACTION_PERFORMED, command, when, modifiers));
        return true;
    }

    /**
     * The {@link InputMap} installed for {@code condition}.
     *
     * <p>The JDK distinguishes the UI's map from a user-installed one (by map parent);
     * this port keeps one map per condition in {@link JComponent}, so this is simply
     * that map.
     */
    public static InputMap getUIInputMap(JComponent component, int condition) {
        return component.getInputMap(condition);
    }

    /** Replaces the map for {@code condition}; {@code null} removes it. */
    public static void replaceUIInputMap(JComponent component, int condition, InputMap uiInputMap) {
        component.setInputMap(condition, uiInputMap);
    }

    /** The {@link ActionMap} of {@code component}. */
    public static ActionMap getUIActionMap(JComponent component) {
        return component.getActionMap();
    }

    /** Installs {@code uiActionMap}; {@code null} installs an empty map. */
    public static void replaceUIActionMap(JComponent component, ActionMap uiActionMap) {
        component.setActionMap(uiActionMap != null ? uiActionMap : new ActionMap());
    }

    // ------------------------------------------------------------ mouse buttons

    /**
     * JDK {@code checkMouseButton}: a press/release/click reports the button directly,
     * other event ids are checked against the extended modifier mask.
     */
    private static boolean checkMouseButton(MouseEvent anEvent, int mouseButton, int mouseButtonDownMask) {
        if (anEvent == null) {
            return false;
        }
        switch (anEvent.getID()) {
            case MouseEvent.MOUSE_PRESSED:
            case MouseEvent.MOUSE_RELEASED:
            case MouseEvent.MOUSE_CLICKED:
                return anEvent.getButton() == mouseButton;
            case MouseEvent.MOUSE_ENTERED:
            case MouseEvent.MOUSE_EXITED:
            case MouseEvent.MOUSE_DRAGGED:
                return (anEvent.getModifiersEx() & mouseButtonDownMask) != 0;
            default:
                return (anEvent.getModifiersEx() & mouseButtonDownMask) != 0
                        || anEvent.getButton() == mouseButton;
        }
    }

    public static boolean isLeftMouseButton(MouseEvent anEvent) {
        return checkMouseButton(anEvent, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK);
    }

    public static boolean isMiddleMouseButton(MouseEvent anEvent) {
        return checkMouseButton(anEvent, MouseEvent.BUTTON2, InputEvent.BUTTON2_DOWN_MASK);
    }

    public static boolean isRightMouseButton(MouseEvent anEvent) {
        return checkMouseButton(anEvent, MouseEvent.BUTTON3, InputEvent.BUTTON3_DOWN_MASK);
    }

    /**
     * True when the platform's menu shortcut is held.
     *
     * <p>JDK semantics: Ctrl on Windows/Linux, Command (the meta key) on macOS. The port
     * always reports the Linux/Windows answer — Android's Meta key is not delivered as a
     * modifier bit by the host, and the client's own shortcuts are Ctrl-based.
     */
    public static boolean isMenuShortcutKeyDown(InputEvent event) {
        return event != null && event.isControlDown();
    }
}
