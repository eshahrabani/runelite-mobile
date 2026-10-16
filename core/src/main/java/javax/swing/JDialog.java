package javax.swing;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Window;

/**
 * Hand-written {@code javax.swing.JDialog}.
 *
 * <p>A {@link Dialog} with the Swing extras the client uses: a {@link JRootPane} content
 * hierarchy (inherited from {@link Window}), a default close operation, the
 * look-and-feel decoration flag, and — for this port — a {@link #doLayout()} that sizes
 * the content pane. The port's {@link JRootPane} lays the layered pane out but, because
 * {@link JLayeredPane} runs with a {@code null} layout manager, never assigns the
 * content pane a size; the desktop JDK's {@code RootLayout} does that, so the
 * compensation lives here (see {@link #doLayout()}).
 *
 * <p>Windows are virtual: {@code setVisible(true)} registers the dialog with
 * {@code UiBridge} and the app paints it into the window layer, title bar included.
 * Modality is enforced by that layer — while a modal dialog is visible, all input goes
 * to it — so {@link #isModal()}/{@link #setModal(boolean)} only have to keep the flag
 * (they are inherited from {@link Dialog}).
 *
 * <p>Members the generated stub declared that {@link Window}/{@link Dialog} already
 * provide ({@code setSize}, {@code getSize}, {@code setLocation},
 * {@code setLocationRelativeTo}, {@code setResizable}, {@code setTitle},
 * {@code setIconImages}, {@code setAlwaysOnTop}, {@code isAlwaysOnTopSupported},
 * {@code setContentPane}, {@code getContentPane}, {@code getRootPane},
 * {@code getGlassPane}/{@code setGlassPane}, {@code getLayeredPane}/
 * {@code setLayeredPane}, {@code addWindowListener}, {@code dispatchEvent}) stay
 * resolvable through those superclasses.
 */
public class JDialog extends Dialog implements WindowConstants, javax.accessibility.Accessible,
        RootPaneContainer, TransferHandler.HasGetTransferHandler {

    private static boolean defaultLookAndFeelDecorated;

    /** Field behind {@link #getTransferHandler()}; nothing installs one on this port. */
    private TransferHandler transferHandler;

    public JDialog() {
        this((Window) null, null, ModalityType.MODELESS);
    }

    public JDialog(Frame owner) {
        this(owner, null, false);
    }

    public JDialog(Frame owner, boolean modal) {
        this(owner, null, modal);
    }

    public JDialog(Frame owner, String title) {
        this(owner, title, false);
    }

    public JDialog(Frame owner, String title, boolean modal) {
        this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
    }

    public JDialog(Dialog owner) {
        this(owner, null, false);
    }

    public JDialog(Dialog owner, String title) {
        this(owner, title, false);
    }

    public JDialog(Dialog owner, String title, boolean modal) {
        this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
    }

    public JDialog(Window owner) {
        this(owner, null, ModalityType.MODELESS);
    }

    public JDialog(Window owner, String title) {
        this(owner, title, ModalityType.MODELESS);
    }

    /** The signature the shipped client's colour picker and the option pane use. */
    public JDialog(Window owner, String title, ModalityType modalityType) {
        super(owner, title, modalityType);
        // JDK default for a dialog: closing the window only hides it, so the value the
        // dialog produced stays readable after it goes away.
        setDefaultCloseOperation(HIDE_ON_CLOSE);
    }

    // ------------------------------------------------------------ decoration flag

    public static void setDefaultLookAndFeelDecorated(boolean defaultLookAndFeelDecorated) {
        JDialog.defaultLookAndFeelDecorated = defaultLookAndFeelDecorated;
    }

    public static boolean isDefaultLookAndFeelDecorated() {
        return defaultLookAndFeelDecorated;
    }

    // ------------------------------------------------------------ transfer handler

    public TransferHandler getTransferHandler() {
        return transferHandler;
    }

    public void setTransferHandler(TransferHandler newHandler) {
        this.transferHandler = newHandler;
    }

    // ------------------------------------------------------------ layout/showing

    /**
     * Lays the root pane out and gives the content pane the size of the layered pane.
     *
     * <p>Port deviation (documented on the class): {@link JRootPane#doLayout()} sizes the
     * menu bar and the layered pane but this port's {@link JRootPane} cannot size the
     * content pane (its layered pane has no layout manager), so the desktop's
     * "contentPane fills the layered pane" step has to happen here. {@code validateTree}
     * is called directly because a container without a layout manager never becomes
     * invalid again on its own.
     */
    @Override
    public void doLayout() {
        JRootPane root = getRootPane();
        if (root == null) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        root.setBounds(0, 0, w, h);
        root.validateTree();
        Container content = root.getContentPane();
        JLayeredPane layered = root.getLayeredPane();
        if (content != null) {
            int cw = layered != null && layered.getWidth() > 0 ? layered.getWidth() : w;
            int ch = layered != null && layered.getHeight() > 0 ? layered.getHeight() : h;
            content.setBounds(0, 0, cw, ch);
            content.validateTree();
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
    }

    /**
     * Re-lays the content out on a live resize.
     *
     * <p>Port deviation: {@link Container#invalidate()} does not clear the validity flag
     * of a container without a layout manager, and this window has none, so
     * {@code validate()} after {@code setSize} would be a no-op and the content pane
     * would keep its old size. {@code validateTree()} is the ungated form.
     */
    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        if (isVisible()) {
            validateTree();
        }
    }

    /**
     * Sizes the dialog to the content's preferred size, plus the title bar the window
     * layer draws.
     *
     * <p>The port's window bitmap is exactly {@code getWidth()} × {@code getHeight()};
     * the title bar is added around it by {@code UiBridge}, so unlike the desktop there
     * is no decoration inset to add here.
     */
    @Override
    public void pack() {
        Dimension pref = getRootPane() == null ? null : getRootPane().getPreferredSize();
        if (pref != null && pref.width > 0 && pref.height > 0) {
            setSize(pref.width, pref.height);
        }
        validateTree();
    }

    @Override
    public String toString() {
        return getClass().getName() + "[title=" + getTitle() + ",owner=" + getOwner() + "]";
    }
}
