package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.EditorKit;
import javax.swing.text.Highlighter;
import javax.swing.text.html.HTMLRenderer;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Hand-written {@code javax.swing.JEditorPane}: the rich-text component RuneLite's
 * {@code InfoPanel} and its {@code JRichTextPane} subclass use for their HTML strings.
 *
 * <p>It is hand-written because the generated stub did nothing (a {@code setText} that
 * dropped its argument and an {@code addHyperlinkListener} that forgot the listener), so
 * the "Sign in to sync settings" label could neither render nor turn a tap into
 * {@code LinkBrowser.browse}. Rendering is delegated to {@link HTMLRenderer}, the port's
 * HTML subset painter, and the pane's text is the ordinary {@code JTextComponent} text —
 * this class owns no text storage of its own.
 *
 * <p>Deliberate port deviations:
 * <ul>
 *   <li>There is no {@code HTMLDocument} or {@code View} hierarchy, so
 *       {@code setEditorKit} installs no document
 *       ({@link javax.swing.text.html.HTMLEditorKit#createDefaultDocument()} returns
 *       {@code null} here, and {@code setDocument(null)} is illegal). The content is the
 *       component's text, so nothing needs mirroring.</li>
 *   <li>Link activation is owned by the pane: it adds one mouse listener in its
 *       constructor, so a plain left tap (press + release [+ click]) over an
 *       {@code <a href>} run fires exactly one
 *       {@link HyperlinkEvent#ACTIVATED} event. A click outside an
 *       {@code <a>} run, and any non-left button, fire nothing.</li>
 *   <li>HTML content is painted by {@link HTMLRenderer} instead of a text UI, so it has
 *       no selection and no caret; plain content is still painted by
 *       {@code JTextComponent}'s own text painter (selection, caret and all).</li>
 * </ul>
 */
public class JEditorPane extends javax.swing.text.JTextComponent {

    /** The content type of a pane that was not given one, as in the JDK. */
    private static final String DEFAULT_CONTENT_TYPE = "text/plain";

    private EditorKit editorKit;
    private URL page;

    /** Per-pane content-type handlers, the JDK's {@code typeHandlers} table. */
    private final Map<String, EditorKit> typeHandlers = new HashMap<>();
    private final List<HyperlinkListener> hyperlinkListeners = new ArrayList<>();
    private final MouseListener linkHandler = new LinkHandler();

    /** HTML layout cache; {@code rendererWidth} is the width it was laid out for. */
    private HTMLRenderer renderer;
    private int rendererWidth = Integer.MIN_VALUE;
    private String rendererText;

    /** True once this press/release pair fired, so the follow-up click does not repeat. */
    private boolean linkFiredSincePress;

    public JEditorPane() {
        super();
        addMouseListener(linkHandler);
        setContentType(DEFAULT_CONTENT_TYPE);
    }

    public JEditorPane(String type, String text) {
        this();
        setContentType(type);
        setText(text);
    }

    // ------------------------------------------------------------- content

    /**
     * The pane's content: the text component's document text.
     *
     * <p>Overridden for the JDK's API shape only; the port has no separate HTML model.
     */
    @Override
    public String getText() {
        return super.getText();
    }

    /** Replaces the content and drops the cached HTML layout. */
    @Override
    public void setText(String t) {
        super.setText(t);
        invalidateRenderer();
    }

    // ----------------------------------------------------------- editor kit

    /** The kit installed for the current content type (set by the constructor). */
    public EditorKit getEditorKit() {
        return editorKit;
    }

    /**
     * Installs {@code kit} ({@code null} clears it): the outgoing kit is deinstalled, the
     * incoming one installed, and the {@code editorKit} property change is fired.
     *
     * <p>Deviation: the JDK also installs {@code kit.createDefaultDocument()} as the
     * pane's document, which discards the current text. This port's {@code HTMLEditorKit}
     * has no document to hand out (there is no {@code HTMLDocument}) and
     * {@code setDocument(null)} is illegal, so the component's document is kept and the
     * content survives a content-type switch.
     */
    public void setEditorKit(EditorKit kit) {
        EditorKit old = editorKit;
        if (old != null) {
            old.deinstall(this);
        }
        editorKit = kit;
        if (kit != null) {
            kit.install(this);
        }
        firePropertyChange("editorKit", old, kit);
        invalidateRenderer();
    }

    /** The kit that would serve {@code type}, creating and remembering one if needed. */
    public EditorKit getEditorKitForContentType(String type) {
        if (type == null) {
            return null;
        }
        String clean = normalizeContentType(type);
        EditorKit k = typeHandlers.get(clean);
        if (k == null) {
            k = createEditorKitForContentType(clean);
            if (k == null) {
                k = createDefaultEditorKit();
            }
            setEditorKitForContentType(clean, k);
        }
        return k;
    }

    /** Registers {@code k} for {@code type} on this pane. */
    public void setEditorKitForContentType(String type, EditorKit k) {
        if (type == null || k == null) {
            return;
        }
        typeHandlers.put(normalizeContentType(type), k);
    }

    /**
     * The port's handler registry: {@link HTMLEditorKit} for {@code text/html} and
     * {@code null} for anything else — the JDK looks the type up in
     * {@code UIManager}'s {@code EditorKit.<type>} class names, so unknown types fall
     * through to {@link #createDefaultEditorKit()} here too.
     */
    public static EditorKit createEditorKitForContentType(String type) {
        if (type != null && "text/html".equalsIgnoreCase(normalizeContentType(type))) {
            return new HTMLEditorKit();
        }
        return null;
    }

    /** The kit a pane without a known content type uses: plain text. */
    protected EditorKit createDefaultEditorKit() {
        return new javax.swing.text.DefaultEditorKit();
    }

    // ---------------------------------------------------------- content type

    /** The installed kit's content type, or {@code null} when no kit is installed. */
    public final String getContentType() {
        return editorKit == null ? null : editorKit.getContentType();
    }

    /**
     * Switches the pane to {@code type} (case-insensitive, {@code ;charset=…} parameters
     * ignored, like the JDK) by installing that type's kit. The JDK also replaces the
     * document here; see {@link #setEditorKit} for why this port does not.
     */
    public final void setContentType(String type) {
        if (type == null) {
            return;
        }
        String clean = normalizeContentType(type);
        if (clean.isEmpty()) {
            return;
        }
        EditorKit current = editorKit;
        if (current != null && clean.equalsIgnoreCase(current.getContentType())) {
            return;
        }
        EditorKit k = getEditorKitForContentType(clean);
        if (k != null && k != editorKit) {
            setEditorKit(k);
        }
    }

    // --------------------------------------------------------------- pages

    /** The URL last loaded by {@link #setPage(URL)}, or {@code null}. */
    public URL getPage() {
        return page;
    }

    /**
     * Loads {@code url}: its connection's content type becomes this pane's content type
     * and its bytes become the text.
     */
    public void setPage(URL url) throws IOException {
        if (url == null) {
            throw new IOException("invalid url");
        }
        URLConnection connection = url.openConnection();
        String type = connection.getContentType();
        if (type != null && !type.trim().isEmpty()) {
            setContentType(type);
        }
        String text = readAll(connection.getInputStream());
        page = url;
        setText(text);
    }

    /**
     * Loads {@code url}, which may be absolute or a local file path. The JDK resolves a
     * relative spec against the document base; this port has no document base, so a
     * relative spec is taken as a file system path.
     */
    public void setPage(String url) throws IOException {
        if (url == null) {
            throw new IOException("invalid url");
        }
        try {
            setPage(new URL(url));
        } catch (MalformedURLException e) {
            setPage(new java.io.File(url).toURI().toURL());
        }
    }

    // ------------------------------------------------------- editing state

    /**
     * Delegates to {@code JTextComponent}, which owns the flag.
     *
     * <p>Declared here for link compatibility: the generated {@code JEditorPane} stub listed
     * {@code setEditable} as its own member.
     */
    @Override
    public boolean isEditable() {
        return super.isEditable();
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
    }

    /**
     * Delegates to {@code JTextComponent}'s highlighter.
     *
     * <p>Declared here for link compatibility: the generated {@code JEditorPane} stub listed
     * {@code setHighlighter} as its own member. HTML content is painted by
     * {@link HTMLRenderer} and therefore shows no highlights.
     */
    @Override
    public void setHighlighter(Highlighter highlighter) {
        super.setHighlighter(highlighter);
    }

    // ------------------------------------------------------------ hyperlinks

    public synchronized void addHyperlinkListener(HyperlinkListener listener) {
        if (listener != null && !hyperlinkListeners.contains(listener)) {
            hyperlinkListeners.add(listener);
        }
    }

    public synchronized void removeHyperlinkListener(HyperlinkListener listener) {
        hyperlinkListeners.remove(listener);
    }

    public synchronized HyperlinkListener[] getHyperlinkListeners() {
        return hyperlinkListeners.toArray(new HyperlinkListener[0]);
    }

    /** Delivers {@code event} to every registered hyperlink listener. */
    public void fireHyperlinkUpdate(HyperlinkEvent event) {
        if (event == null) {
            return;
        }
        HyperlinkListener[] listeners;
        synchronized (this) {
            listeners = hyperlinkListeners.toArray(new HyperlinkListener[0]);
        }
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].hyperlinkUpdate(event);
        }
    }

    /**
     * The {@code href} of the link under the pane-local point {@code (x, y)}, resolving
     * the point against the same origin the content is painted at (the border insets).
     */
    private String hrefAt(int x, int y) {
        String text = getText();
        if (!isHtml(text)) {
            return null;
        }
        Insets in = getInsets();
        return renderer(currentLayoutWidth()).hitTest(x - in.left, y - in.top);
    }

    private void activate(MouseEvent e) {
        String href = hrefAt(e.getX(), e.getY());
        if (href == null) {
            return;
        }
        linkFiredSincePress = true;
        URL url = null;
        try {
            url = new URL(href);
        } catch (MalformedURLException ignored) {
            // A fragment/anchor-only href ("#x"): the JDK would resolve it against the
            // document base, which this port does not have.
        }
        fireHyperlinkUpdate(new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, url, href));
    }

    /**
     * The pane's single mouse listener. A left tap arrives as press → release → click, so
     * the release fires and the click only fires when there was no release (a
     * click-only test harness). {@code MOUSE_CLICKED} carries {@code NOBUTTON} in the
     * JDK, hence both buttons are accepted.
     */
    private final class LinkHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            linkFiredSincePress = false;
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (isLeftButton(e)) {
                activate(e);
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (isLeftButton(e) && !linkFiredSincePress) {
                activate(e);
            }
            linkFiredSincePress = false;
        }

        private boolean isLeftButton(MouseEvent e) {
            return e.getButton() == MouseEvent.BUTTON1 || e.getButton() == MouseEvent.NOBUTTON;
        }
    }

    // ------------------------------------------------------------- painting

    /**
     * The size the HTML content needs: the content laid out with this component's font at
     * the width the pane currently has (so a parent honouring the preferred size keeps
     * the current wrapping). Plain content goes through {@code JTextComponent}'s own
     * line-based size, and an explicitly set preferred size always wins.
     */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet() || !isHtml(getText())) {
            return super.getPreferredSize();
        }
        Insets in = getInsets();
        Insets m = getMargin();
        Dimension content = renderer(currentLayoutWidth()).getPreferredSize();
        return new Dimension(content.width + in.left + in.right + m.left + m.right,
                content.height + in.top + in.bottom + m.top + m.bottom);
    }

    /**
     * Paints HTML content through {@link HTMLRenderer} (there is no text UI for it) and
     * everything else through {@code JTextComponent}, which draws plain text, its
     * selection and its caret.
     */
    @Override
    protected void paintComponent(Graphics g) {
        String text = getText();
        if (!isHtml(text)) {
            super.paintComponent(g);
            return;
        }
        if (isOpaque() && getBackground() != null) {
            // JTextComponent.paintComponent would also paint the *plain* text of the
            // document under the HTML, so only the background is inherited from it.
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        if (!(g instanceof Graphics2D)) {
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        Insets in = getInsets();
        Insets m = getMargin();
        g2.setColor(isEnabled() ? getForeground() : getDisabledTextColor());
        renderer(currentLayoutWidth()).paint(g2, in.left + m.left, in.top + m.top);
    }

    @Override
    public void setFont(Font f) {
        super.setFont(f);
        invalidateRenderer();
    }

    @Override
    public void setForeground(Color c) {
        super.setForeground(c);
    }

    @Override
    public void setBorder(Border border) {
        super.setBorder(border);
        invalidateRenderer();
    }

    @Override
    public void setOpaque(boolean opaque) {
        super.setOpaque(opaque);
    }

    // ---------------------------------------------------------------- helpers

    /** {@code text} is HTML when the pane serves HTML or the string looks like markup. */
    private boolean isHtml(String text) {
        return text != null
                && ("text/html".equals(getContentType()) || HTMLRenderer.isHtml(text));
    }

    /** The width the HTML content is laid out for: the current width, or 0 (unwrapped). */
    private int currentLayoutWidth() {
        Insets in = getInsets();
        Insets m = getMargin();
        int width = getWidth() - in.left - in.right - m.left - m.right;
        return width > 0 ? width : 0;
    }

    /**
     * The cached renderer for the current text and {@code width}. The text is compared
     * (not just the width) because content can also change through the component's
     * document, bypassing {@link #setText}.
     */
    private HTMLRenderer renderer(int width) {
        String text = getText();
        if (text == null) {
            text = "";
        }
        if (renderer == null || rendererWidth != width || !text.equals(rendererText)) {
            renderer = new HTMLRenderer(text, getFont());
            renderer.layout(width);
            rendererText = text;
            rendererWidth = width;
        }
        return renderer;
    }

    private void invalidateRenderer() {
        renderer = null;
        rendererText = null;
        rendererWidth = Integer.MIN_VALUE;
    }

    private static String normalizeContentType(String type) {
        int semi = type.indexOf(';');
        return (semi < 0 ? type : type.substring(0, semi)).trim().toLowerCase();
    }

    /** Reads a stream fully as UTF-8, for {@link #setPage(URL)}. */
    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("no stream");
        }
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        int n;
        while ((n = reader.read(buffer)) > 0) {
            sb.append(buffer, 0, n);
        }
        return sb.toString();
    }
}
