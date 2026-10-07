package java.awt.image;

/**
 * AWT PackedColorModel compatibility stub for Android runtime.
 */
public abstract class PackedColorModel extends ColorModel {
    protected int[] masks;

    protected PackedColorModel(int bits, int[] masks, boolean hasAlpha, boolean isAlphaPremultiplied) {
        super(bits, hasAlpha ? 4 : 3, hasAlpha, isAlphaPremultiplied);
        this.masks = masks;
    }

    public final int[] getMasks() {
        return masks.clone();
    }
}
