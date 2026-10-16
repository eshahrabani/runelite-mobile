package java.awt;

/**
 * AWT {@code Frame}: a top-level {@link Window} with a title and a resizable flag.
 * The Swing {@code JFrame} the RuneLite shell builds extends this class.
 */
public class Frame extends Window {
    public static final int NORMAL = 0;
    public static final int ICONIFIED = 1;
    public static final int MAXIMIZED_HORIZ = 2;
    public static final int MAXIMIZED_VERT = 4;
    public static final int MAXIMIZED_BOTH = MAXIMIZED_HORIZ | MAXIMIZED_VERT;

    private boolean resizable = true;
    private int state = NORMAL;

    public Frame() {
        super();
    }

    public Frame(String title) {
        super();
        setTitle(title);
    }

    public Frame(Window owner) {
        super(owner);
    }

    @Override
    public void setTitle(String title) {
        super.setTitle(title);
    }

    public boolean isResizable() {
        return resizable;
    }

    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getState() {
        return state;
    }

    @Override
    public int getExtendedState() {
        return state;
    }

    public void setMaximizedBounds(Rectangle bounds) {
        // No window manager: the virtual window cannot be maximised.
    }

    public Rectangle getMaximizedBounds() {
        return null;
    }

    @Override
    public Insets getInsets() {
        // Virtual windows have no native decorations; the port draws its own title bar.
        return new Insets(0, 0, 0, 0);
    }
}
