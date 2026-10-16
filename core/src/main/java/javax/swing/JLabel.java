package javax.swing;

import java.awt.Font;
import java.awt.FontMetrics;
import javax.swing.plaf.LabelUI;

/**
 * Swing {@code JLabel}: a text and/or icon label (the most common widget in the sidebar
 * and config panels).
 *
 * <p>Hand-written because these labels are what the user actually reads: text, an icon, the
 * six alignment knobs, an icon-text gap and HTML text all have to render. Painting is
 * delegated to the installed {@link LabelUI} ({@code BasicLabelUI}, or RuneLite's
 * {@code JShadowedLabelUI} which overrides {@code paintEnabledText}), and the preferred
 * size comes from the UI (icon + text + gap + border insets; the HTML renderer's size for
 * HTML text). There is no desktop {@code UIManager} lookup at construction time here.
 */
public class JLabel extends JComponent implements SwingConstants, javax.accessibility.Accessible {

    private String text = "";
    private Icon icon;
    private Icon disabledIcon;
    private boolean disabledIconSet;

    private int horizontalAlignment = LEADING;
    private int verticalAlignment = CENTER;
    private int horizontalTextPosition = TRAILING;
    private int verticalTextPosition = CENTER;
    private int iconTextGap = 4;

    private int mnemonic = -1;
    private int displayedMnemonicIndex = -1;
    private java.awt.Component labelFor;

    public JLabel() {
        this("", null, LEADING);
    }

    public JLabel(String text) {
        this(text, null, LEADING);
    }

    public JLabel(String text, int horizontalAlignment) {
        this(text, null, horizontalAlignment);
    }

    public JLabel(Icon image) {
        this(null, image, CENTER);
    }

    public JLabel(Icon image, int horizontalAlignment) {
        this(null, image, horizontalAlignment);
    }

    public JLabel(String text, Icon icon, int horizontalAlignment) {
        super();
        setUI(new javax.swing.plaf.basic.BasicLabelUI());
        this.text = text == null ? "" : text;
        this.icon = icon;
        this.horizontalAlignment = horizontalAlignment;
        setOpaque(false);
    }

    // ------------------------------------------------------------------ UI

    public void setUI(LabelUI ui) {
        super.setUI(ui);
    }

    public LabelUI getUI() {
        return (LabelUI) super.getUI();
    }

    @Override
    public String getUIClassID() {
        return "LabelUI";
    }

    // ----------------------------------------------------------------- text

    public String getText() {
        return text;
    }

    /**
     * Sets the label text; HTML markup is rendered by the HTML path in the UI when the
     * string looks like HTML (see {@code HTMLRenderer.isHtml}).
     */
    public void setText(String text) {
        String old = this.text;
        this.text = text == null ? "" : text;
        firePropertyChange("text", old, this.text);
        revalidate();
        repaint();
    }

    // ----------------------------------------------------------------- icons

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        Icon old = this.icon;
        this.icon = icon;
        if (!disabledIconSet) {
            this.disabledIcon = null;
        }
        firePropertyChange("icon", old, icon);
        revalidate();
        repaint();
    }

    public Icon getDisabledIcon() {
        return disabledIcon;
    }

    public void setDisabledIcon(Icon disabledIcon) {
        Icon old = this.disabledIcon;
        this.disabledIcon = disabledIcon;
        this.disabledIconSet = disabledIcon != null;
        firePropertyChange("disabledIcon", old, disabledIcon);
        repaint();
    }

    // ------------------------------------------------------------- alignment

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

    // ------------------------------------------------------ mnemonic/labelFor

    public int getDisplayedMnemonic() {
        return mnemonic;
    }

    public void setDisplayedMnemonic(int key) {
        int old = mnemonic;
        mnemonic = key;
        firePropertyChange("displayedMnemonic", old, key);
    }

    public void setDisplayedMnemonic(char aChar) {
        setDisplayedMnemonic((int) aChar);
    }

    public int getDisplayedMnemonicIndex() {
        return displayedMnemonicIndex;
    }

    public void setDisplayedMnemonicIndex(int index) {
        int old = displayedMnemonicIndex;
        displayedMnemonicIndex = index;
        firePropertyChange("displayedMnemonicIndex", old, index);
    }

    public java.awt.Component getLabelFor() {
        return labelFor;
    }

    public void setLabelFor(java.awt.Component c) {
        java.awt.Component old = labelFor;
        labelFor = c;
        firePropertyChange("labelFor", old, c);
    }

    // ------------------------------------------------------------- metrics

    @Override
    public FontMetrics getFontMetrics(Font font) {
        return super.getFontMetrics(font);
    }
}
