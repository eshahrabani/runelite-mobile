package org.runelite.mobile.host;

import android.util.Log;

import org.runelite.mobile.bridge.AWTBridge;
import org.runelite.mobile.bridge.PopupLayer;
import org.runelite.mobile.bridge.SwingEdt;
import org.runelite.mobile.bridge.UiBridge;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UiScale;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

/**
 * Device-run self-tests for the port's Swing runtime.
 *
 * <p>Why this exists: the whole Swing layer is hand-written (Android has no
 * {@code java.desktop}), so a missing method or a wrong layout arithmetic is invisible at
 * build time — {@code :android:verifyHostLinks} only proves the <em>links</em> resolve.
 * These cases pin the behaviours the sidebar depends on: the paint pipeline, the layout
 * managers, the widgets RuneLite's panels are built from, popups and modal dialogs, and
 * the UI scale.
 *
 * <p>Modelled on {@link GraphicsSelfTest}: a plain {@code run()} that returns the first
 * failure as a string (or null) and logs a single PASS/FAIL line. It runs on the Swing
 * dispatch thread, i.e. from {@code RuneLiteHost.startPlugins}, and drives the widgets
 * directly — every class here is an app-dex type, so no reflection is involved.
 */
public final class SwingSelfTest {
    private static final String TAG = "RuneLiteHost";

    private interface Case {
        void run() throws Throwable;
    }

    private static final Map<String, Case> CASES = new LinkedHashMap<>();

    static {
        CASES.put("threading", SwingSelfTest::threading);
        CASES.put("borderlayout", SwingSelfTest::borderLayout);
        CASES.put("gridbag", SwingSelfTest::gridBag);
        CASES.put("box", SwingSelfTest::box);
        CASES.put("grouplayout", SwingSelfTest::groupLayout);
        CASES.put("label", SwingSelfTest::label);
        CASES.put("label_html", SwingSelfTest::labelHtml);
        CASES.put("button_click", SwingSelfTest::buttonClick);
        CASES.put("checkbox", SwingSelfTest::checkbox);
        CASES.put("tabbedpane", SwingSelfTest::tabbedPane);
        CASES.put("scrollpane", SwingSelfTest::scrollPane);
        CASES.put("viewport_stretch", SwingSelfTest::viewportStretch);
        CASES.put("textfield", SwingSelfTest::textField);
        CASES.put("documentfilter", SwingSelfTest::documentFilter);
        CASES.put("spinner", SwingSelfTest::spinner);
        CASES.put("list", SwingSelfTest::list);
        CASES.put("combo", SwingSelfTest::combo);
        CASES.put("popup", SwingSelfTest::popup);
        CASES.put("optionpane", SwingSelfTest::optionPane);
        CASES.put("scale", SwingSelfTest::scale);
        CASES.put("html_editorpane", SwingSelfTest::htmlEditorPane);
        CASES.put("theme", SwingSelfTest::theme);
    }

    private SwingSelfTest() {
    }

    /**
     * Cases that put something on screen and are therefore skipped by the boot run: a modal
     * dialog would flash on every launch. {@code PluginConformance} runs them deliberately.
     */
    private static final java.util.Set<String> ON_SCREEN_CASES = new java.util.LinkedHashSet<>(
        java.util.Collections.singletonList("optionpane"));

    /**
     * Runs every off-screen case; returns null when all pass, else the first failure as
     * {@code "<case>: <detail>"}.
     */
    public static String run() {
        for (Map.Entry<String, Case> entry : CASES.entrySet()) {
            if (ON_SCREEN_CASES.contains(entry.getKey())) {
                Log.i(TAG, "SWING SELFTEST case " + entry.getKey() + " skipped at boot (on-screen case)");
                continue;
            }

            Log.i(TAG, "SWING SELFTEST case " + entry.getKey());
            try {
                entry.getValue().run();
            } catch (Throwable t) {
                String detail = t.getClass().getSimpleName() + ": " + t.getMessage();
                Log.e(TAG, "SWING SELFTEST FAIL " + entry.getKey() + ": " + detail, t);
                return entry.getKey() + ": " + detail;
            }
        }
        Log.i(TAG, "SWING SELFTEST PASS (" + CASES.size() + " cases)");
        return null;
    }

    /** Case names, for the log/status line. */
    public static java.util.Set<String> caseNames() {
        return CASES.keySet();
    }

    /** Names of the cases that need the screen (skipped by {@link #run()}). */
    public static java.util.Set<String> onScreenCaseNames() {
        return java.util.Collections.unmodifiableSet(ON_SCREEN_CASES);
    }

    /**
     * Runs one named case on the calling (dispatch) thread; null when it passed. Used by the
     * conformance pass for the on-screen cases and by adb-driven checks.
     */
    public static String runCase(String name) {
        Case test = CASES.get(name);
        if (test == null) {
            return name + ": unknown case";
        }
        try {
            test.run();
            Log.i(TAG, "SWING SELFTEST case " + name + " PASS");
            return null;
        } catch (Throwable t) {
            String detail = t.getClass().getSimpleName() + ": " + t.getMessage();
            Log.e(TAG, "SWING SELFTEST FAIL " + name + ": " + detail, t);
            return name + ": " + detail;
        }
    }

    // ------------------------------------------------------------------ cases

    private static void threading() throws Throwable {
        // This runs on the Swing dispatch thread (RuneLiteHost.startPlugins' thread), so it
        // must never block waiting for a task queued to that same thread — the JDK's own
        // nested-pump rule. SwingEdt.dispatchQueued() is the pump: it runs the queued task
        // right here, which is exactly what a live event dispatch would do.
        require(SwingEdt.isDispatchThread(), "the self-test is not running on the Swing dispatch thread");
        require(SwingUtilities.isEventDispatchThread(), "isEventDispatchThread false on the Swing dispatch thread");
        require(android.os.Looper.getMainLooper().getThread() != Thread.currentThread(),
            "the Android main thread is being used as the Swing EDT");

        final String[] name = new String[1];
        final boolean[] dispatchThread = new boolean[1];
        AWTBridge.post(() -> {
            name[0] = Thread.currentThread().getName();
            dispatchThread[0] = SwingUtilities.isEventDispatchThread();
        });
        SwingEdt.dispatchQueued();
        require(name[0] != null, "AWTBridge.post did not enqueue a runnable");
        require("Swing-EDT-rlmobile".equals(name[0]), "the queued task ran on " + name[0]);
        require(dispatchThread[0], "isEventDispatchThread false inside a queued task");
    }

    private static void borderLayout() throws Throwable {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel north = new JPanel();
        north.setPreferredSize(new Dimension(10, 20));
        JPanel west = new JPanel();
        west.setPreferredSize(new Dimension(30, 10));
        JPanel center = new JPanel();
        panel.add(north, BorderLayout.NORTH);
        panel.add(west, BorderLayout.WEST);
        panel.add(center, BorderLayout.CENTER);
        panel.setSize(300, 200);
        panel.validate();
        require(north.getBounds().equals(new Rectangle(0, 0, 300, 20)), "north " + north.getBounds());
        require(west.getBounds().equals(new Rectangle(0, 20, 30, 180)), "west " + west.getBounds());
        require(center.getBounds().equals(new Rectangle(30, 20, 270, 180)), "center " + center.getBounds());
    }

    private static void gridBag() throws Throwable {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel a = new JLabel("a");
        JLabel b = new JLabel("bbbb");
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(a, c);
        c.gridx = 1;
        panel.add(b, c);
        panel.setSize(200, 100);
        panel.validate();
        require(a.getWidth() > 0 && b.getWidth() > 0, "gridbag did not size the cells");
        require(a.getX() + a.getWidth() <= b.getX() + 1, "columns overlap: " + a.getBounds() + " " + b.getBounds());
    }

    private static void box() throws Throwable {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        JLabel top = new JLabel("top");
        JLabel bottom = new JLabel("bottom");
        panel.add(top);
        panel.add(Box.createVerticalStrut(20));
        panel.add(bottom);
        panel.setSize(100, 200);
        panel.validate();
        require(bottom.getY() >= top.getY() + top.getHeight(), "strut did not separate the labels");
    }

    private static void groupLayout() throws Throwable {
        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        JLabel one = new JLabel("one");
        JLabel two = new JLabel("two");
        JLabel three = new JLabel("three");
        layout.setHorizontalGroup(layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(one)
                .addComponent(two))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(three));
        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(one)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(two)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(three));
        panel.setSize(200, 200);
        panel.validate();
        require(one.getY() < two.getY() && two.getY() < three.getY(),
            "group layout y not increasing: " + one.getY() + "," + two.getY() + "," + three.getY());
    }

    private static void label() throws Throwable {
        JLabel label = new JLabel("x", SwingConstants.CENTER);
        Dimension small = label.getPreferredSize();
        label.setText("xxxxxxxxxx");
        Dimension large = label.getPreferredSize();
        require(large.width > small.width, "preferred width did not grow: " + small + " -> " + large);
        int painted = paintAndCount(label, 60, 20);
        require(painted > 0, "label painted nothing");
    }

    private static void labelHtml() throws Throwable {
        JLabel label = new JLabel("<html><body style='text-align:center'><b>a</b> b<br>c</body></html>");
        Dimension size = label.getPreferredSize();
        require(size.height > label.getFontMetrics(label.getFont()).getHeight() + 2,
            "html label did not wrap to two lines: " + size);
        int painted = paintAndCount(label, size.width + 4, size.height + 4);
        require(painted > 0, "html label painted nothing");
    }

    private static void buttonClick() throws Throwable {
        JButton button = new JButton("ok");
        button.setSize(Math.max(40, button.getPreferredSize().width), 24);
        AtomicInteger clicks = new AtomicInteger();
        button.addActionListener(e -> clicks.incrementAndGet());
        click(button);
        require(clicks.get() == 1, "expected 1 ActionEvent, got " + clicks.get());
    }

    private static void checkbox() throws Throwable {
        JCheckBox box = new JCheckBox("check");
        box.setSize(Math.max(40, box.getPreferredSize().width), 22);
        AtomicInteger items = new AtomicInteger();
        box.addItemListener(e -> items.incrementAndGet());
        click(box);
        require(box.isSelected(), "checkbox did not select");
        require(items.get() == 1, "expected 1 ItemEvent, got " + items.get());
    }

    private static void tabbedPane() throws Throwable {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.RIGHT);
        JPanel first = new JPanel();
        JPanel second = new JPanel();
        second.setBackground(new Color(0xFF00FF));
        JPanel third = new JPanel();
        tabs.insertTab("one", null, first, "one", 0);
        tabs.insertTab("two", null, second, "two", 1);
        tabs.insertTab("three", null, third, "three", 2);
        tabs.setSelectedIndex(-1);
        AtomicInteger changes = new AtomicInteger();
        tabs.addChangeListener(e -> changes.incrementAndGet());
        tabs.setSelectedIndex(1);
        require(tabs.getSelectedIndex() == 1, "selection not applied");
        require(changes.get() == 1, "expected 1 ChangeEvent, got " + changes.get());
        tabs.setSize(200, 400);
        tabs.validate();
        require(tabs.getTabCount() == 3, "tab count " + tabs.getTabCount());
        require(tabs.getBoundsAt(1) != null, "no tab bounds for index 1");
        Integer marker = paintedPixelAt(second, tabs, 0xFF00FF);
        require(marker != null, "the selected panel's content was not painted");
    }

    private static void scrollPane() throws Throwable {
        JPanel content = new JPanel();
        content.setPreferredSize(new Dimension(100, 1000));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setSize(120, 100);
        scroll.validate();
        int unit = scroll.getVerticalScrollBar().getUnitIncrement();
        require(unit > 0, "unit increment is " + unit);
        dispatchWheel(scroll, 10, 10, 1);
        int moved = scroll.getViewport().getViewPosition().y;
        require(moved == unit, "wheel moved " + moved + ", expected " + unit);
    }

    private static void viewportStretch() throws Throwable {
        JPanel content = new JPanel(); // not Scrollable
        content.setPreferredSize(new Dimension(50, 50));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setSize(200, 100);
        scroll.validate();
        require(content.getWidth() == 200, "view width " + content.getWidth() + " != extent 200");
    }

    private static void textField() throws Throwable {
        JTextField field = new JTextField();
        field.setSize(100, 20);
        AtomicInteger inserts = new AtomicInteger();
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                inserts.incrementAndGet();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        });
        field.setCaretPosition(0);
        type(field, 'a');
        type(field, 'b');
        require("ab".equals(field.getText()), "text is '" + field.getText() + "'");
        require(inserts.get() == 2, "expected 2 insert events, got " + inserts.get());
        field.setCaretPosition(field.getText().length());
        dispatchKey(field, java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_BACK_SPACE, '\b');
        require("a".equals(field.getText()), "backspace left '" + field.getText() + "'");
    }

    private static void documentFilter() throws Throwable {
        JTextField field = new JTextField();
        field.setSize(100, 20);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                    throws BadLocationException {
                if (text != null && text.indexOf('x') >= 0) {
                    throw new BadLocationException("x rejected", offset);
                }
                super.insertString(fb, offset, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text != null && text.indexOf('x') >= 0) {
                    throw new BadLocationException("x rejected", offset);
                }
                super.replace(fb, offset, length, text, attrs);
            }
        });
        field.setCaretPosition(0);
        type(field, 'x');
        require(field.getText().isEmpty(), "filter did not reject x: '" + field.getText() + "'");
        type(field, 'a');
        require("a".equals(field.getText()), "filter rejected a valid char: '" + field.getText() + "'");
    }

    private static void spinner() throws Throwable {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        spinner.setValue(3);
        JComponentAccess.getTextField(spinner);
        spinner.setValue(spinner.getNextValue());
        require(Integer.valueOf(4).equals(spinner.getValue()), "spinner value " + spinner.getValue());
    }

    private static void list() throws Throwable {
        JList<String> list = new JList<>(new String[] {"a", "b", "c", "d", "e"});
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSize(100, 100);
        AtomicInteger events = new AtomicInteger();
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                events.incrementAndGet();
            }
        });
        list.setSelectedIndex(2);
        require(list.getSelectedIndex() == 2, "selection " + list.getSelectedIndex());
        require(events.get() == 1, "expected 1 selection event, got " + events.get());
        require(list.getPreferredSize().height > 0, "no preferred height");
        int painted = paintAndCountList(list);
        require(painted > 0, "list painted nothing");
    }

    private static void combo() throws Throwable {
        JComboBox<String> combo = new JComboBox<>(new String[] {"one", "two", "three"});
        combo.setSize(120, 22);
        AtomicInteger items = new AtomicInteger();
        combo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                items.incrementAndGet();
            }
        });
        combo.setSelectedIndex(2);
        require(items.get() >= 1, "no ItemEvent on selection");
        int before = items.get();
        combo.showPopup();
        boolean tapped = false;
        for (PopupLayer.Entry entry : PopupLayer.entries()) {
            if (entry.component instanceof JList) {
                @SuppressWarnings("unchecked")
                JList<String> popupList = (JList<String>) entry.component;
                Rectangle row = popupList.getCellBounds(1, 1);
                if (row != null) {
                    clickAt(popupList, row.x + 2, row.y + 2);
                    tapped = true;
                }
                break;
            }
        }
        combo.hidePopup();
        if (tapped) {
            require(items.get() == before + 1, "popup tap produced " + (items.get() - before) + " events");
            require("two".equals(combo.getSelectedItem()), "popup tap selected " + combo.getSelectedItem());
        } else {
            Log.i(TAG, "SWING SELFTEST: combo popup not registered with the popup layer; tap not exercised");
        }
    }

    private static void popup() throws Throwable {
        JPopupMenu menu = new JPopupMenu();
        AtomicInteger clicks = new AtomicInteger();
        javax.swing.JMenuItem item = new javax.swing.JMenuItem("item");
        item.addActionListener(e -> clicks.incrementAndGet());
        menu.add(item);
        JPanel invoker = new JPanel();
        invoker.setSize(50, 50);
        menu.show(invoker, 10, 10);
        require(menu.isVisible(), "popup menu not visible after show()");
        boolean painted = false;
        for (PopupLayer.Entry entry : PopupLayer.entries()) {
            if (entry.component == menu) {
                painted = true;
            }
        }
        require(painted, "popup menu not registered with the popup layer");
        item.doClick();
        require(clicks.get() == 1, "expected 1 ActionEvent, got " + clicks.get());
        menu.setVisible(false);
        require(!menu.isVisible(), "popup menu still visible after hide");
    }

    private static void optionPane() throws Throwable {
        final boolean[] clicked = new boolean[1];
        final String[] problem = new String[1];
        // The dialog is modal and blocks this thread inside its own dispatch loop, so the
        // click has to come from a task queued before the dialog is shown — and a second
        // task guarantees the loop cannot hang if the first one cannot find the button.
        SwingEdt.post(() -> {
            try {
                Window dialog = null;
                long deadline = System.currentTimeMillis() + 2000;
                while (dialog == null && System.currentTimeMillis() < deadline) {
                    dialog = findDialog();
                    if (dialog == null) {
                        Thread.sleep(20);
                    }
                }
                if (dialog == null) {
                    problem[0] = "no dialog appeared";
                    return;
                }
                // showConfirmDialog(parent, message) defaults to YES_NO_CANCEL, so the
                // positive button is the first one (its value is OK_OPTION == YES_OPTION).
                JButton button = findButton(dialog, "OK");
                if (button == null) {
                    button = findButton(dialog, "Yes");
                }
                if (button == null) {
                    button = firstButton(dialog);
                }
                if (button == null) {
                    problem[0] = "no button in the dialog";
                    return;
                }
                click(button);
                clicked[0] = true;
                SwingEdt.post(() -> {
                    Window still = findDialog();
                    if (still != null) {
                        // The click did not dismiss it: close it so the caller's nested loop
                        // ends and the case fails instead of hanging the dispatch thread.
                        still.dispose();
                    }
                });
            } catch (Throwable t) {
                problem[0] = String.valueOf(t);
                Window still = findDialog();
                if (still != null) {
                    still.dispose();
                }
            }
        });
        int value = JOptionPane.showConfirmDialog(null, "selftest?");
        require(problem[0] == null, "confirm dialog: " + problem[0]);
        require(clicked[0], "the dialog's positive button was never clicked");
        require(value == JOptionPane.OK_OPTION, "confirm dialog returned " + value);
    }

    private static Window findDialog() {
        for (Window w : Window.getWindows()) {
            if (w != null && w.isVisible() && w.getClass().getName().contains("JDialog")) {
                return w;
            }
        }
        return null;
    }

    private static JButton firstButton(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton) {
                return (JButton) child;
            }
            if (child instanceof Container) {
                JButton found = firstButton((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void scale() throws Throwable {
        // The port renders the sidebar at an integer UI scale with device-resolution glyphs:
        // a 12 px logical label must paint ~scale times taller glyphs while its preferred
        // size stays logical. This is the case that catches "text does not scale" regressions
        // (icons scaled through drawImage while the glyph rasteriser kept the logical size).
        JLabel label = new JLabel("X");
        Dimension logical = label.getPreferredSize();
        // Isolate the two halves: the renderer's scaled handle, then the Graphics plumbing.
        Object handle = label.getFont() == null ? null : label.getFont().getTextRendererHandle();
        if (handle != null) {
            int plainWidth = org.runelite.mobile.bridge.TextBridge.stringWidth(handle, "X");
            Object scaledHandle = org.runelite.mobile.bridge.TextBridge.scaledFont(handle, 3f);
            int scaledWidth = org.runelite.mobile.bridge.TextBridge.stringWidth(scaledHandle, "X");
            require(scaledWidth >= plainWidth * 2,
                "scaledFont did not scale: " + plainWidth + " -> " + scaledWidth);
        } else {
            Log.i(TAG, "SWING SELFTEST: no text renderer handle yet, checking only the Graphics path");
        }
        int plain = paintedGlyphHeight(label, logical.width * 3 + 12, logical.height * 3 + 12, 1f);
        int scaled = paintedGlyphHeight(label, logical.width * 3 + 12, logical.height * 3 + 12, 3f);
        require(plain > 0, "the label painted nothing at scale 1");
        require(scaled >= plain * 2, "glyphs did not scale: plain=" + plain + " scaled=" + scaled);
        require(label.getPreferredSize().equals(logical),
            "preferred size must stay logical: " + label.getPreferredSize() + " != " + logical);
    }

    /** Height of the pixels a label paints at the given Graphics scale. */
    private static int paintedGlyphHeight(JLabel label, int width, int height, float scale) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(scale, scale);
        label.setSize(lenient(label.getPreferredSize().width, width), lenient(label.getPreferredSize().height, height));
        label.paint(g);
        g.dispose();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int top = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) != 0) {
                    if (top < 0) {
                        top = y;
                    }
                    bottom = y;
                    break;
                }
            }
        }
        return top < 0 ? 0 : bottom - top + 1;
    }

    private static void htmlEditorPane() throws Throwable {
        String html = "<html><body><a href=\"#x\">link</a></body></html>";
        javax.swing.text.html.HTMLRenderer renderer =
            new javax.swing.text.html.HTMLRenderer(html, UIManager.getFont("Label.font"));
        renderer.layout(0);
        Dimension size = renderer.getPreferredSize();
        require(size.width > 0 && size.height > 0, "renderer has no size: " + size);
        String href = null;
        for (int x = 0; x < Math.max(1, size.width) && href == null; x++) {
            for (int y = 0; y < Math.max(1, size.height); y++) {
                href = renderer.hitTest(x, y);
                if (href != null) {
                    break;
                }
            }
        }
        require("#x".equals(href), "hitTest returned " + href);
    }

    private static void theme() throws Throwable {
        Color background = UIManager.getColor("Panel.background");
        require(background != null, "Panel.background is null");
        require(background.getRGB() == 0xFF282828, "Panel.background is " + background);
    }

    // ------------------------------------------------------------------ helpers

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Paints a component into a fresh image and counts non-transparent pixels. */
    private static int paintAndCount(Component component, int width, int height) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        component.setSize(Math.max(1, width), Math.max(1, height));
        if (component instanceof Container) {
            ((Container) component).validate();
        }
        component.paint(g);
        g.dispose();
        return countPainted(image, width, height);
    }

    private static int paintAndCountList(JList<?> list) {
        int width = Math.max(1, list.getWidth());
        int height = Math.max(1, list.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // Give the list a background so "painted" means "drew rows", not "drew nothing".
        g.setColor(new Color(0xFF202020));
        g.fillRect(0, 0, width, height);
        list.paint(g);
        g.dispose();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int painted = 0;
        for (int pixel : pixels) {
            if (pixel != 0xFF202020) {
                painted++;
            }
        }
        return painted;
    }

    /** Height of the pixels a label painted at the given scale. */
    private static int paintedHeight(JLabel label, int width, int height, float scale) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(scale, scale);
        label.setSize(lenient(label.getPreferredSize().width, width), lenient(label.getPreferredSize().height, height));
        label.paint(g);
        g.dispose();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int top = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) != 0) {
                    if (top < 0) {
                        top = y;
                    }
                    bottom = y;
                    break;
                }
            }
        }
        return top < 0 ? 0 : bottom - top + 1;
    }

    private static int lenient(int preferred, int limit) {
        return preferred <= 0 ? limit : Math.min(preferred, limit);
    }

    private static int countPainted(BufferedImage image, int width, int height) {
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int painted = 0;
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                painted++;
            }
        }
        return painted;
    }

    /** Whether the component contains a pixel of the given RGB (ignoring alpha). */
    private static Integer paintedPixelAt(Component component, Container host, int rgb) {
        Rectangle bounds = component.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) {
            return null;
        }
        BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        component.paint(g);
        g.dispose();
        int[] pixels = image.getRGB(0, 0, bounds.width, bounds.height, null, 0, bounds.width);
        for (int pixel : pixels) {
            if ((pixel & 0xFFFFFF) == rgb) {
                return pixel;
            }
        }
        return null;
    }

    private static void dispatchKey(Component target, int id, int keyCode, char keyChar) {
        target.dispatchEvent(new java.awt.event.KeyEvent(target, id, System.currentTimeMillis(), 0, keyCode, keyChar));
    }

    private static void type(Component field, char c) {
        dispatchKey(field, java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_UNDEFINED, c);
        dispatchKey(field, java.awt.event.KeyEvent.KEY_TYPED, java.awt.event.KeyEvent.VK_UNDEFINED, c);
        dispatchKey(field, java.awt.event.KeyEvent.KEY_RELEASED, java.awt.event.KeyEvent.VK_UNDEFINED, c);
    }

    private static void click(Component target) {
        Point center = new Point(target.getWidth() / 2, target.getHeight() / 2);
        clickAt(target, center.x, center.y);
    }

    private static void clickAt(Component target, int x, int y) {
        long when = System.currentTimeMillis();
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_MOVED, when, 0, x, y, 0, false,
            MouseEvent.NOBUTTON));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, when, 0, x, y, 1, false,
            MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, when, 0, x, y, 1, false,
            MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, when, 0, x, y, 1, false,
            MouseEvent.BUTTON1));
    }

    private static void dispatchWheel(Component target, int x, int y, int rotation) {
        target.dispatchEvent(new java.awt.event.MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL,
            System.currentTimeMillis(), 0, x, y, 0, false,
            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation));
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton && text.equals(((JButton) child).getText())) {
                return (JButton) child;
            }
            if (child instanceof Container) {
                JButton found = findButton((Container) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Tiny helper so the spinner case can reach its editor's text field without reflection. */
    private static final class JComponentAccess {
        static void getTextField(JSpinner spinner) {
            Object editor = spinner.getEditor();
            require(editor != null, "spinner has no editor");
            if (editor instanceof JSpinner.DefaultEditor) {
                require(((JSpinner.DefaultEditor) editor).getTextField() != null,
                    "spinner editor has no text field");
            }
        }
    }
}
