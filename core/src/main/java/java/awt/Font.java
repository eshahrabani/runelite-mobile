package java.awt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.runelite.mobile.bridge.TextBridge;

/**
 * AWT Font compatibility stub for Android runtime.
 *
 * <p>A font carries the raw TTF bytes (when it was created with
 * {@link #createFont(int, InputStream)}) plus the requested style and size.
 * Glyph work is delegated to {@link TextBridge}; the platform handle is the
 * opaque object returned by {@code TextBridge.createFont(byte[],int,int,String)}
 * and is created lazily on first use. Fonts constructed from a logical name only
 * (for example {@code new Font("Dialog", PLAIN, 12)}) inherit the TTF bytes of a
 * family previously registered through
 * {@link java.awt.GraphicsEnvironment#registerFont(Font)}, so
 * {@code FontManager}'s family round-trip keeps the real RuneScape glyphs.
 */
public class Font {

    public static final int PLAIN = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    /** Alias of {@link #PLAIN}; the roman (upright) style. */
    public static final int ROMAN = PLAIN;
    /** TrueType font format constant for {@link #createFont(int, InputStream)}. */
    public static final int TRUETYPE_FONT = 0;
    /** PostScript Type 1 font format constant. */
    public static final int TYPE1_FONT = 1;

    protected String name;
    protected int style;
    protected int size;

    /** Raw TrueType bytes, or null for a pure logical font. */
    private byte[] fontData;

    /** Opaque platform handle from {@link TextBridge}; resolved lazily. */
    private transient Object handle;
    private transient boolean handleResolved;

    public Font(String name, int style, int size) {
        this.name = name != null ? name : "Default";
        this.style = style;
        this.size = size <= 0 ? 1 : size;
        this.fontData = GraphicsEnvironment.lookupFontData(this.name);
    }

    private Font(String name, int style, int size, byte[] fontData) {
        this.name = name != null ? name : "Default";
        this.style = style;
        this.size = size <= 0 ? 1 : size;
        this.fontData = fontData;
    }

    /**
     * Creates a font from the given stream. The whole stream is read into memory
     * so the font can be reconstructed on the platform later.
     *
     * @throws IllegalArgumentException when {@code fontFormat} is not
     *         {@link #TRUETYPE_FONT} or {@link #TYPE1_FONT}
     * @throws FontFormatException when the stream is empty or too short to be a
     *         font
     * @throws IOException when the stream cannot be read
     */
    public static Font createFont(int fontFormat, InputStream fontStream)
            throws FontFormatException, IOException {
        if (fontFormat != TRUETYPE_FONT && fontFormat != TYPE1_FONT) {
            throw new IllegalArgumentException("font format");
        }
        byte[] data = readAll(fontStream);
        if (data == null || data.length < 12) {
            throw new FontFormatException("Font data is empty or too short");
        }
        String family = parseFamilyName(data);
        if (family == null || family.isEmpty()) {
            family = "Dialog";
        }
        return new Font(family, PLAIN, 1, data);
    }

    /** @return the family name, or the logical name when no TTF family was parsed. */
    public String getFamily() {
        return name;
    }

    /** @return the logical name; identical to the family in this stub. */
    public String getName() {
        return name;
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    /** Integral size as a float, mirroring {@code Font.getSize2D()}. */
    public float getSize2D() {
        return size;
    }

    public Font deriveFont(float size) {
        return new Font(name, style, Math.round(size), fontData);
    }

    public Font deriveFont(int style, float size) {
        return new Font(name, style, Math.round(size), fontData);
    }

    /** Raw TTF bytes, or null for a logical font. Used by the text bridge. */
    public byte[] getFontData() {
        return fontData;
    }

    /**
     * @return the opaque platform handle, or null when no TTF bytes are
     *         available or no renderer is installed. Resolved once per Font.
     */
    public Object getTextRendererHandle() {
        if (!handleResolved) {
            handleResolved = true;
            if (fontData != null && fontData.length > 0) {
                handle = TextBridge.createFont(fontData, style, size, name);
            }
        }
        return handle;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Font)) {
            return false;
        }
        Font other = (Font) obj;
        return style == other.style
                && size == other.size
                && (name == null ? other.name == null : name.equals(other.name))
                && Arrays.equals(fontData, other.fontData);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + style;
        result = 31 * result + size;
        return result;
    }

    @Override
    public String toString() {
        String styleName;
        if (style == PLAIN) {
            styleName = "plain";
        } else {
            StringBuilder sb = new StringBuilder();
            if ((style & BOLD) != 0) {
                sb.append("bold");
            }
            if ((style & ITALIC) != 0) {
                sb.append("italic");
            }
            styleName = sb.length() == 0 ? "plain" : sb.toString();
        }
        return getClass().getName() + "[family=" + name + ",name=" + name
                + ",style=" + styleName + ",size=" + size + "]";
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * Parses the family name out of a TrueType {@code name} table. Returns null
     * when the table is missing or unreadable; callers fall back to "Dialog".
     */
    private static String parseFamilyName(byte[] d) {
        try {
            int numTables = u16(d, 4);
            int nameTable = -1;
            for (int i = 0; i < numTables; i++) {
                int rec = 12 + i * 16;
                if (rec + 16 > d.length) {
                    break;
                }
                if (d[rec] == 'n' && d[rec + 1] == 'a' && d[rec + 2] == 'm' && d[rec + 3] == 'e') {
                    nameTable = u32(d, rec + 8);
                    break;
                }
            }
            if (nameTable < 0 || nameTable + 6 > d.length) {
                return null;
            }
            int count = u16(d, nameTable + 2);
            int stringOffset = nameTable + u16(d, nameTable + 4);
            String best = null;
            int bestScore = -1;
            for (int i = 0; i < count; i++) {
                int rec = nameTable + 6 + i * 12;
                if (rec + 12 > d.length) {
                    break;
                }
                int platformId = u16(d, rec);
                int languageId = u16(d, rec + 4);
                int nameId = u16(d, rec + 6);
                int length = u16(d, rec + 8);
                int offset = u16(d, rec + 10);
                if (nameId != 1 && nameId != 16) {
                    continue;
                }
                int start = stringOffset + offset;
                if (start < 0 || start + length > d.length || length <= 0) {
                    continue;
                }
                String value;
                if (platformId == 3 || platformId == 0) {
                    value = new String(d, start, length, StandardCharsets.UTF_16BE);
                } else {
                    value = new String(d, start, length, StandardCharsets.ISO_8859_1);
                }
                value = value.trim();
                if (value.isEmpty()) {
                    continue;
                }
                int score = (nameId == 16 ? 4 : 0)
                        + (platformId == 3 ? 2 : 0)
                        + (languageId == 0x409 ? 1 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = value;
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int u16(byte[] d, int off) {
        return ((d[off] & 0xff) << 8) | (d[off + 1] & 0xff);
    }

    private static int u32(byte[] d, int off) {
        return ((d[off] & 0xff) << 24) | ((d[off + 1] & 0xff) << 16)
                | ((d[off + 2] & 0xff) << 8) | (d[off + 3] & 0xff);
    }
}
