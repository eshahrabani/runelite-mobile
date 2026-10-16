package javax.swing;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.net.URL;

/**
 * Swing {@code ImageIcon}: an {@link Icon} wrapping a {@link Image}.
 *
 * <p>Hand-written because the sidebar is built almost entirely from image icons
 * ({@code SpriteManager}, plugin toolbar glyphs): {@code paintIcon} has to really blit the
 * image at the current transform, and {@code getIconWidth/Height} drive every layout.
 * Loading from a {@link URL} goes through the port's {@code ImageIO}; there is no AWT
 * media tracker to wait on, so the image is read eagerly.
 */
public class ImageIcon implements Icon, javax.accessibility.Accessible {

    private final Image image;
    private final String description;

    public ImageIcon() {
        this((Image) null, null);
    }

    public ImageIcon(Image image) {
        this(image, null);
    }

    public ImageIcon(Image image, String description) {
        this.image = image;
        this.description = description;
    }

    public ImageIcon(URL location) {
        this(readUrl(location), location == null ? null : location.toString());
    }

    public ImageIcon(String filename) {
        this(filename == null ? null : new java.io.File(filename), null);
    }

    private ImageIcon(java.io.File file, String description) {
        this(readFile(file), description);
    }

    /** The wrapped image, or null when this icon was built without one. */
    public Image getImage() {
        return image;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (image != null && g != null) {
            g.drawImage(image, x, y, null);
        }
    }

    @Override
    public int getIconWidth() {
        return image == null ? 0 : image.getWidth();
    }

    @Override
    public int getIconHeight() {
        return image == null ? 0 : image.getHeight();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        // Immutable on this port: the icon is created once from the resource bytes.
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        return null;
    }

    private static Image readUrl(URL location) {
        if (location == null) {
            return null;
        }
        try (java.io.InputStream in = location.openStream()) {
            return javax.imageio.ImageIO.read(in);
        } catch (Exception e) {
            System.err.println("ImageIcon: cannot read " + location + ": " + e);
            return null;
        }
    }

    private static Image readFile(java.io.File file) {
        if (file == null) {
            return null;
        }
        try {
            return javax.imageio.ImageIO.read(file);
        } catch (Exception e) {
            System.err.println("ImageIcon: cannot read " + file + ": " + e);
            return null;
        }
    }

    @Override
    public String toString() {
        return description != null ? description : super.toString();
    }
}
