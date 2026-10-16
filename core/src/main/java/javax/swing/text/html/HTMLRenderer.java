package javax.swing.text.html;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-written HTML subset renderer for the port's HTML labels and editor panes.
 *
 * <p>The Android runtime has no {@code javax.swing.text.html.HTMLDocument}/{@code View}
 * machinery (and {@code javax.swing.text.html.parser} is not on the module path at all),
 * so the handful of RuneLite panels that ship HTML strings — {@code InfoPanel}'s labels
 * and {@code JRichTextPane} — are served by this class instead: a three-state pipeline of
 * parse (tags -> styled runs) → layout (runs -> wrapped lines with per-run x offsets) →
 * paint ({@link Graphics2D#drawString} with {@code Font.deriveFont}).
 *
 * <p>Supported vocabulary (every other tag is stripped, its text kept):
 * <ul>
 *   <li>{@code <html> <body style="color:…;text-align:center|right"> <p> <br>
 *       <b>/<strong> <i>/<em> <u> <font color=… size=…> <span style="color:…">
 *       <a href="…"> <center>}</li>
 *   <li>entities {@code &amp; &lt; &gt; &quot; &nbsp; &#NN; &#xNN;}</li>
 *   <li>colours {@code #rgb}, {@code #rrggbb}, {@code rgb(r,g,b)} and the common
 *       {@code red}-style names.</li>
 * </ul>
 *
 * <p>Deliberate deviations from the JDK, all of them simplifications: no CSS cascade (a
 * {@code <style>} block or a stylesheet rule never affects a run — see
 * {@link StyleSheet}), no {@code <ul>}/{@code <li>} list bullets, no tables, and no
 * font-family changes (the caller's base font is the only family). Font sizes 1..7 of
 * {@code <font size>} map to the usual HTML points; a word wider than the available
 * width overflows instead of being broken.
 *
 * <p>Coordinates: all state is renderer-local. Line 0's top is y = 0 and the first
 * glyph's left edge is x = 0, so the caller paints at {@code (insets.left, insets.top)}
 * and hit-tests with the same offset subtracted. The antialiased pixels themselves are
 * the platform text renderer's business ({@code TextBridge}); this class only picks
 * baselines.
 */
public final class HTMLRenderer {

    /** HTML point sizes for {@code <font size=N>}: {@code SIZE_TABLE[N - 1]}. */
    private static final int[] SIZE_TABLE = {8, 10, 12, 14, 18, 24, 36};

    private static final int ALIGN_LEFT = 0;
    private static final int ALIGN_CENTER = 1;
    private static final int ALIGN_RIGHT = 2;

    /** Parsed styled text runs in document order; a hard break has {@code text == null}. */
    private final List<Run> runs = new ArrayList<>();
    /** Laid-out lines (a run may be split across several lines by wrapping). */
    private final List<Line> lines = new ArrayList<>();
    /** Derived fonts by {@code (style << 8) | size}, so wrapping does not re-derive. */
    private final Map<Integer, Font> fonts = new HashMap<>();

    private final Font baseFont;
    /** Scratch graphics, used only to obtain {@link FontMetrics} (no pixels involved). */
    private final Graphics metricsSource = new Graphics();
    /** Base ascent/height of the default font, used for empty (hard break) lines. */
    private final int baseAscent;
    private final int baseHeight;

    private int layoutWidth;
    private int preferredWidth;
    private int preferredHeight;

    public HTMLRenderer(String html, Font baseFont) {
        this.baseFont = baseFont != null && baseFont.getSize() > 0
                ? baseFont : new Font("Dialog", Font.PLAIN, 12);
        FontMetrics base = metrics(this.baseFont);
        this.baseAscent = base.getAscent();
        this.baseHeight = base.getHeight();
        parse(html == null ? "" : html);
        // A renderer is usable before an explicit layout() (the JDK's View sizes itself
        // from the first getPreferredSize call the same way): lay out unwrapped.
        layout(0);
    }

    /** True when {@code text} should be rendered as HTML (starts with {@code <html}). */
    public static boolean isHtml(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (t.length() < 5) {
            return false;
        }
        return t.regionMatches(true, 0, "<html", 0, 5);
    }

    /**
     * Wraps the parsed runs into lines of at most {@code availableWidth} logical pixels
     * (word granularity), then computes per-run x offsets. {@code availableWidth <= 0}
     * disables wrapping: the content becomes one line per {@code <br>}/{@code <p>}.
     */
    public void layout(int availableWidth) {
        this.layoutWidth = availableWidth;
        lines.clear();

        List<Placement> current = new ArrayList<>();
        int lineWidth = 0;
        int space = 0;
        boolean pendingSpace = false;
        int align = ALIGN_LEFT;

        for (Run run : runs) {
            if (run.text == null) {
                flush(current, lineWidth, align);
                current = new ArrayList<>();
                lineWidth = 0;
                space = 0;
                pendingSpace = false;
                continue;
            }
            if (current.isEmpty()) {
                align = run.align;
            }
            FontMetrics fm = metrics(font(run));
            space = fm.stringWidth(" ");

            int i = 0;
            int n = run.text.length();
            while (i < n) {
                char c = run.text.charAt(i);
                if (isSpace(c)) {
                    pendingSpace = true;
                    i++;
                    continue;
                }
                int start = i;
                while (i < n && !isSpace(run.text.charAt(i))) {
                    i++;
                }
                String word = run.text.substring(start, i);
                int w = fm.stringWidth(word);
                int gap = 0;
                if (!current.isEmpty() && pendingSpace) {
                    gap = space;
                }
                if (!current.isEmpty() && availableWidth > 0
                        && lineWidth + gap + w > availableWidth) {
                    flush(current, lineWidth, align);
                    current = new ArrayList<>();
                    lineWidth = 0;
                    gap = 0;
                    align = run.align;
                }
                int x = current.isEmpty() ? 0 : lineWidth + gap;
                current.add(new Placement(word, run, x, w));
                lineWidth = x + w;
                pendingSpace = false;
            }
        }
        flush(current, lineWidth, align);

        int width = 0;
        int y = 0;
        int reference = layoutWidth > 0 ? layoutWidth : contentWidth();
        for (Line line : lines) {
            line.top = y;
            y += line.height;
            switch (line.align) {
                case ALIGN_CENTER:
                    line.x = Math.max(0, (reference - line.width) / 2);
                    break;
                case ALIGN_RIGHT:
                    line.x = Math.max(0, reference - line.width);
                    break;
                default:
                    line.x = 0;
                    break;
            }
            width = Math.max(width, line.x + line.width);
        }
        preferredWidth = width;
        preferredHeight = y;
    }

    /** The size the laid-out content needs at the width of the last {@link #layout}. */
    public Dimension getPreferredSize() {
        return new Dimension(preferredWidth, preferredHeight);
    }

    /**
     * Draws the laid-out content with its top-left corner at {@code (x, y)}. The
     * graphics' font and colour are restored before returning; each run is drawn with a
     * derived font, the run's colour (when it has one, i.e. {@code <font color>}/{@code
     * <span style>}/{@code <body style>}) and an underline when asked.
     *
     * <p>The caller owns the base colour: a run without an explicit colour is drawn with
     * whatever colour {@code g} carries, so set the component's foreground on {@code g}
     * before calling this (the JDK's text UI does the same before drawing a view).
     */
    public void paint(Graphics2D g, int x, int y) {
        Font savedFont = g.getFont();
        Color savedColor = g.getColor();
        for (Line line : lines) {
            for (int i = 0; i < line.placements.size(); i++) {
                Placement p = line.placements.get(i);
                g.setFont(font(p.run));
                if (p.run.color != null) {
                    g.setColor(p.run.color);
                }
                int px = x + line.x + p.x;
                int baseline = y + line.top + line.ascent;
                g.drawString(p.text, px, baseline);
                if (p.run.underline) {
                    g.drawLine(px, baseline + 1, px + p.width, baseline + 1);
                }
                if (p.run.color != null) {
                    g.setColor(savedColor);
                }
            }
        }
        g.setFont(savedFont);
        g.setColor(savedColor);
    }

    /**
     * The {@code href} of the {@code <a>} run under the renderer-local point
     * {@code (x, y)}, or {@code null}. The caller subtracts the same origin it painted
     * with (the component's border insets).
     */
    public String hitTest(int x, int y) {
        for (Line line : lines) {
            if (y < line.top || y >= line.top + line.height) {
                continue;
            }
            for (int i = 0; i < line.placements.size(); i++) {
                Placement p = line.placements.get(i);
                if (p.run.href != null && x >= line.x + p.x && x < line.x + p.x + p.width) {
                    return p.run.href;
                }
            }
            return null;
        }
        return null;
    }

    // -------------------------------------------------------------- layout model

    /** A styled piece of text; {@code text == null} marks a hard line break. */
    private static final class Run {
        String text;
        boolean bold;
        boolean italic;
        boolean underline;
        Color color;
        int size;
        String href;
        int align = ALIGN_LEFT;
    }

    /** One word placed on a line, in renderer-local coordinates. */
    private static final class Placement {
        final String text;
        final Run run;
        final int x;
        final int width;

        Placement(String text, Run run, int x, int width) {
            this.text = text;
            this.run = run;
            this.x = x;
            this.width = width;
        }
    }

    /** A laid-out line: its baseline height, alignment and word placements. */
    private static final class Line {
        final List<Placement> placements;
        final int width;
        final int height;
        final int ascent;
        final int align;
        int top;
        int x;

        Line(List<Placement> placements, int width, int height, int ascent, int align) {
            this.placements = placements;
            this.width = width;
            this.height = height;
            this.ascent = ascent;
            this.align = align;
        }
    }

    private void flush(List<Placement> placements, int width, int align) {
        int ascent = baseAscent;
        int height = baseHeight;
        for (int i = 0; i < placements.size(); i++) {
            FontMetrics fm = metrics(font(placements.get(i).run));
            ascent = Math.max(ascent, fm.getAscent());
            height = Math.max(height, fm.getHeight());
        }
        lines.add(new Line(placements, width, height, ascent,
                placements.isEmpty() ? align : placements.get(0).run.align));
    }

    private int contentWidth() {
        int max = 0;
        for (Line line : lines) {
            max = Math.max(max, line.width);
        }
        return max;
    }

    private Font font(Run run) {
        int style = Font.PLAIN;
        if (run.bold) {
            style |= Font.BOLD;
        }
        if (run.italic) {
            style |= Font.ITALIC;
        }
        int size = run.size > 0 ? run.size : baseFont.getSize();
        return font(style, size);
    }

    private Font font(int style, int size) {
        Integer key = Integer.valueOf((style << 8) | size);
        Font f = fonts.get(key);
        if (f == null) {
            f = baseFont.deriveFont(style, size);
            fonts.put(key, f);
        }
        return f;
    }

    private FontMetrics metrics(Font f) {
        return metricsSource.getFontMetrics(f);
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    // ---------------------------------------------------------------- parsing

    private void parse(String html) {
        int i = 0;
        int n = html.length();
        StringBuilder text = new StringBuilder();

        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        Color color = null;
        int size = 0;
        String href = null;
        int align = ALIGN_LEFT;

        while (i < n) {
            char c = html.charAt(i);
            if (c == '<') {
                if (html.startsWith("<!--", i)) {
                    int end = html.indexOf("-->", i + 4);
                    i = end < 0 ? n : end + 3;
                    continue;
                }
                int end = html.indexOf('>', i);
                if (end < 0) {
                    // Unterminated tag: treat the rest as text (browsers do the same).
                    addText(text, html.substring(i), bold, italic, underline, color, size, href, align);
                    i = n;
                    break;
                }
                String tag = html.substring(i + 1, end).trim();
                i = end + 1;
                if (tag.isEmpty()) {
                    continue;
                }
                boolean closing = tag.charAt(0) == '/';
                String name = tagName(tag);
                if (closing) {
                    if (isOneOf(name, "b", "strong")) {
                        bold = false;
                    } else if (isOneOf(name, "i", "em")) {
                        italic = false;
                    } else if (name.equals("u")) {
                        underline = false;
                    } else if (name.equals("font")) {
                        color = null;
                        size = 0;
                    } else if (name.equals("span") || name.equals("body") || name.equals("html")) {
                        color = null;
                    } else if (name.equals("a")) {
                        href = null;
                    } else if (name.equals("center")) {
                        align = ALIGN_LEFT;
                    } else if (name.equals("p")) {
                        addBreak(text, bold, italic, underline, color, size, href, align);
                    }
                    continue;
                }
                if (isOneOf(name, "b", "strong")) {
                    bold = true;
                } else if (isOneOf(name, "i", "em")) {
                    italic = true;
                } else if (name.equals("u")) {
                    underline = true;
                } else if (name.equals("br")) {
                    addBreak(text, bold, italic, underline, color, size, href, align);
                } else if (name.equals("p")) {
                    addBreak(text, bold, italic, underline, color, size, href, align);
                } else if (name.equals("center")) {
                    align = ALIGN_CENTER;
                } else if (name.equals("font")) {
                    String col = attribute(tag, "color");
                    String sz = attribute(tag, "size");
                    if (col != null) {
                        Color parsed = parseColor(col);
                        if (parsed != null) {
                            color = parsed;
                        }
                    }
                    if (sz != null) {
                        size = parseSize(sz);
                    }
                } else if (name.equals("span")) {
                    String col = styleValue(attribute(tag, "style"), "color");
                    if (col != null) {
                        Color parsed = parseColor(col);
                        if (parsed != null) {
                            color = parsed;
                        }
                    }
                } else if (name.equals("a")) {
                    href = attribute(tag, "href");
                } else if (name.equals("body")) {
                    String style = attribute(tag, "style");
                    String col = styleValue(style, "color");
                    if (col != null) {
                        Color parsed = parseColor(col);
                        if (parsed != null) {
                            color = parsed;
                        }
                    }
                    String ta = styleValue(style, "text-align");
                    if (ta != null) {
                        align = alignment(ta);
                    }
                }
                continue;
            }
            int lt = html.indexOf('<', i);
            if (lt < 0) {
                lt = n;
            }
            addText(text, html.substring(i, lt), bold, italic, underline, color, size, href, align);
            i = lt;
        }
        flushText(text, bold, italic, underline, color, size, href, align);
    }

    /** Appends a text chunk (entity-decoded) to the current run, merging equal styles. */
    private void addText(StringBuilder chunk, String raw, boolean bold, boolean italic,
                         boolean underline, Color color, int size, String href, int align) {
        chunk.append(decodeEntities(raw));
        if (chunk.length() == 0) {
            return;
        }
        // Merge only when the style is identical *and* the previous run is not a break:
        // this keeps a whole paragraph's words in one run while still separating runs of
        // different style (which is what per-run colours need).
        Run last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
        if (last != null && last.text != null && sameStyle(last, bold, italic, underline, color, size, href, align)) {
            last.text = last.text + chunk.toString();
        } else {
            Run run = new Run();
            run.text = chunk.toString();
            run.bold = bold;
            run.italic = italic;
            run.underline = underline;
            run.color = color;
            run.size = size;
            run.href = href;
            run.align = align;
            runs.add(run);
        }
        chunk.setLength(0);
    }

    private void addBreak(StringBuilder chunk, boolean bold, boolean italic, boolean underline,
                          Color color, int size, String href, int align) {
        flushText(chunk, bold, italic, underline, color, size, href, align);
        Run last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
        if (last == null || last.text != null) {
            Run run = new Run();
            run.align = align;
            runs.add(run);
        }
    }

    private void flushText(StringBuilder chunk, boolean bold, boolean italic, boolean underline,
                           Color color, int size, String href, int align) {
        addText(chunk, "", bold, italic, underline, color, size, href, align);
    }

    private static boolean sameStyle(Run run, boolean bold, boolean italic, boolean underline,
                                     Color color, int size, String href, int align) {
        return run.bold == bold && run.italic == italic && run.underline == underline
                && run.size == size && run.align == align
                && (run.color == null ? color == null : run.color.equals(color))
                && (run.href == null ? href == null : run.href.equals(href));
    }

    private static String tagName(String tag) {
        int i = 0;
        int n = tag.length();
        if (n > 0 && tag.charAt(0) == '/') {
            i = 1;
        }
        int start = i;
        while (i < n && !isSpace(tag.charAt(i)) && tag.charAt(i) != '/' && tag.charAt(i) != ';') {
            i++;
        }
        return tag.substring(start, i).toLowerCase();
    }

    /** The raw value of {@code name=…} in a tag body, unquoted (single or double). */
    private static String attribute(String tag, String name) {
        int from = 0;
        while (true) {
            int at = indexOfIgnoreCase(tag, name, from);
            if (at < 0) {
                return null;
            }
            int after = at + name.length();
            // The match must be a whole attribute name, preceded by whitespace/start.
            if (at > 0 && !isSpace(tag.charAt(at - 1))) {
                from = after;
                continue;
            }
            int k = after;
            while (k < tag.length() && isSpace(tag.charAt(k))) {
                k++;
            }
            if (k >= tag.length() || tag.charAt(k) != '=') {
                from = after;
                continue;
            }
            k++;
            while (k < tag.length() && isSpace(tag.charAt(k))) {
                k++;
            }
            if (k >= tag.length()) {
                return "";
            }
            char quote = tag.charAt(k);
            if (quote == '"' || quote == '\'') {
                int end = tag.indexOf(quote, k + 1);
                return end < 0 ? tag.substring(k + 1) : tag.substring(k + 1, end);
            }
            int end = k;
            while (end < tag.length() && !isSpace(tag.charAt(end))) {
                end++;
            }
            return tag.substring(k, end);
        }
    }

    /** The value of {@code key} in a {@code a:b; c:d} style attribute, or null. */
    private static String styleValue(String style, String key) {
        if (style == null) {
            return null;
        }
        int from = 0;
        while (true) {
            int semi = style.indexOf(';', from);
            String decl = semi < 0 ? style.substring(from) : style.substring(from, semi);
            int colon = decl.indexOf(':');
            if (colon > 0 && decl.substring(0, colon).trim().equalsIgnoreCase(key)) {
                String value = decl.substring(colon + 1).trim();
                int bang = value.indexOf('!');
                if (bang >= 0) {
                    value = value.substring(0, bang).trim();
                }
                return value;
            }
            if (semi < 0) {
                return null;
            }
            from = semi + 1;
        }
    }

    private static int alignment(String value) {
        if (value == null) {
            return ALIGN_LEFT;
        }
        if (value.equalsIgnoreCase("center") || value.equalsIgnoreCase("centre")) {
            return ALIGN_CENTER;
        }
        if (value.equalsIgnoreCase("right")) {
            return ALIGN_RIGHT;
        }
        return ALIGN_LEFT;
    }

    private int parseSize(String value) {
        String v = value.trim();
        try {
            if (v.startsWith("+") || v.startsWith("-")) {
                int delta = Integer.parseInt(v);
                int base = Math.max(1, Math.min(7, baseIndex()));
                return SIZE_TABLE[Math.max(0, Math.min(6, base - 1 + delta))];
            }
            int index = Integer.parseInt(v);
            if (index >= 1 && index <= 7) {
                return SIZE_TABLE[index - 1];
            }
        } catch (NumberFormatException ignored) {
            // fall through: invalid size leaves the inherited one
        }
        return 0;
    }

    /** The HTML size index whose point size is closest to the base font's. */
    private int baseIndex() {
        int best = 3;
        int bestDelta = Integer.MAX_VALUE;
        for (int i = 0; i < SIZE_TABLE.length; i++) {
            int delta = Math.abs(SIZE_TABLE[i] - baseFont.getSize());
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i + 1;
            }
        }
        return best;
    }

    private static String decodeEntities(String s) {
        int amp = s.indexOf('&');
        if (amp < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c != '&') {
                out.append(c);
                i++;
                continue;
            }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 10) {
                out.append(c);
                i++;
                continue;
            }
            String name = s.substring(i + 1, semi);
            String decoded = decodeEntity(name);
            if (decoded == null) {
                out.append(c);
                i++;
                continue;
            }
            out.append(decoded);
            i = semi + 1;
        }
        return out.toString();
    }

    private static String decodeEntity(String name) {
        if (name.equals("amp")) {
            return "&";
        }
        if (name.equals("lt")) {
            return "<";
        }
        if (name.equals("gt")) {
            return ">";
        }
        if (name.equals("quot")) {
            return "\"";
        }
        if (name.equals("apos")) {
            return "'";
        }
        if (name.equals("nbsp")) {
            return "\u00A0";
        }
        if (name.length() > 1 && name.charAt(0) == '#') {
            try {
                int value = name.charAt(1) == 'x' || name.charAt(1) == 'X'
                        ? Integer.parseInt(name.substring(2), 16)
                        : Integer.parseInt(name.substring(1));
                if (value >= 0 && value <= 0xFFFF) {
                    return String.valueOf((char) value);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** {@code #rgb}, {@code #rrggbb}, {@code rgb(r,g,b)} or a common colour name. */
    private static Color parseColor(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toLowerCase();
        if (v.isEmpty()) {
            return null;
        }
        if (v.charAt(0) == '#') {
            String hex = v.substring(1);
            try {
                if (hex.length() == 3) {
                    int r = Integer.parseInt(hex.substring(0, 1), 16);
                    int g = Integer.parseInt(hex.substring(1, 2), 16);
                    int b = Integer.parseInt(hex.substring(2, 3), 16);
                    return new Color(r * 17, g * 17, b * 17);
                }
                if (hex.length() == 6) {
                    int rgb = Integer.parseInt(hex, 16);
                    return new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
            return null;
        }
        if (v.startsWith("rgb(") && v.endsWith(")")) {
            String[] parts = v.substring(4, v.length() - 1).split(",");
            if (parts.length == 3) {
                try {
                    return new Color(clamp(Integer.parseInt(parts[0].trim())),
                            clamp(Integer.parseInt(parts[1].trim())),
                            clamp(Integer.parseInt(parts[2].trim())));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        return namedColor(v);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** The HTML/CSS colour keywords that show up in RuneLite's HTML strings. */
    private static Color namedColor(String name) {
        if (name.equals("black")) {
            return Color.BLACK;
        }
        if (name.equals("white")) {
            return Color.WHITE;
        }
        if (name.equals("red")) {
            return new Color(255, 0, 0);
        }
        if (name.equals("green")) {
            return new Color(0, 128, 0);
        }
        if (name.equals("lime")) {
            return new Color(0, 255, 0);
        }
        if (name.equals("blue")) {
            return new Color(0, 0, 255);
        }
        if (name.equals("yellow")) {
            return new Color(255, 255, 0);
        }
        if (name.equals("orange")) {
            return new Color(255, 165, 0);
        }
        if (name.equals("gold")) {
            return new Color(255, 215, 0);
        }
        if (name.equals("pink")) {
            return new Color(255, 192, 203);
        }
        if (name.equals("purple")) {
            return new Color(128, 0, 128);
        }
        if (name.equals("magenta") || name.equals("fuchsia")) {
            return new Color(255, 0, 255);
        }
        if (name.equals("cyan") || name.equals("aqua")) {
            return new Color(0, 255, 255);
        }
        if (name.equals("brown")) {
            return new Color(165, 42, 42);
        }
        if (name.equals("maroon")) {
            return new Color(128, 0, 0);
        }
        if (name.equals("navy")) {
            return new Color(0, 0, 128);
        }
        if (name.equals("teal")) {
            return new Color(0, 128, 128);
        }
        if (name.equals("olive")) {
            return new Color(128, 128, 0);
        }
        if (name.equals("silver")) {
            return new Color(192, 192, 192);
        }
        if (name.equals("gray") || name.equals("grey")) {
            return new Color(128, 128, 128);
        }
        if (name.equals("lightgray") || name.equals("lightgrey")) {
            return new Color(211, 211, 211);
        }
        if (name.equals("darkgray") || name.equals("darkgrey")) {
            return new Color(169, 169, 169);
        }
        return null;
    }

    private static boolean isOneOf(String name, String a, String b) {
        return name.equals(a) || name.equals(b);
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        int n = haystack.length();
        int m = needle.length();
        for (int i = Math.max(0, from); i + m <= n; i++) {
            if (haystack.regionMatches(true, i, needle, 0, m)) {
                return i;
            }
        }
        return -1;
    }
}
