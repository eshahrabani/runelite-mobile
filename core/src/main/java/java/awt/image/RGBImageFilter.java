package java.awt.image;

/**
 * {@code java.awt.image.RGBImageFilter} stub.
 *
 * <p>The client jar never names this class, but {@code javax.swing.GrayFilter} (the one
 * member RuneLite's {@code ImageUtil} calls) declares it as its superclass, so the type
 * has to exist for GrayFilter to link on the device; a missing superclass is a
 * {@code NoClassDefFoundError} at class-load time. Nothing here paints — GrayFilter's
 * static {@code createDisabledImage} reimplements the desaturation and never touches the
 * real filter state, so the abstract surface the desktop class would poll (
 * {@code filterRGB}, the ImageFilter constants) is not needed, and JavaDoc-only
 * superclass {@code ImageFilter} is not stubbed either.
 */
public abstract class RGBImageFilter {

    protected RGBImageFilter() {
    }
}
