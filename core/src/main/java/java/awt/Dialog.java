package java.awt;

/**
 * AWT {@code Dialog}: a {@link Window} with a modality type.
 *
 * <p>Modality is enforced by the port's window layer: while a modal dialog is visible
 * the layer consumes input for everything below it, and
 * {@code JOptionPane.showConfirmDialog}'s nested dispatch loop blocks the calling
 * (Swing) thread until the dialog is dismissed.
 */
public class Dialog extends Window {
    public enum ModalityType {
        MODELESS,
        DOCUMENT_MODAL,
        APPLICATION_MODAL,
        TOOLKIT_MODAL
    }

    private boolean modal;
    private ModalityType modalityType = ModalityType.MODELESS;

    public Dialog() {
        super();
    }

    public Dialog(Frame owner) {
        super(owner);
    }

    public Dialog(Frame owner, boolean modal) {
        super(owner);
        setModal(modal);
    }

    public Dialog(Frame owner, String title) {
        this(owner, false);
        setTitle(title);
    }

    public Dialog(Frame owner, String title, boolean modal) {
        super(owner);
        setTitle(title);
        setModal(modal);
    }

    public Dialog(Window owner) {
        super(owner);
    }

    public Dialog(Window owner, String title) {
        super(owner);
        setTitle(title);
    }

    public Dialog(Window owner, String title, ModalityType modalityType) {
        super(owner);
        setTitle(title);
        setModalityType(modalityType);
    }

    public Dialog(Window owner, ModalityType modalityType) {
        super(owner);
        setModalityType(modalityType);
    }

    public boolean isModal() {
        return modal;
    }

    public void setModal(boolean modal) {
        this.modal = modal;
        this.modalityType = modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS;
    }

    public ModalityType getModalityType() {
        return modalityType;
    }

    public void setModalityType(ModalityType type) {
        this.modalityType = type == null ? ModalityType.MODELESS : type;
        this.modal = type != null && type != ModalityType.MODELESS;
    }

    public boolean isModalType() {
        return modal;
    }
}
