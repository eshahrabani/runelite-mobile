package java.awt.event;

/** No-op {@link ContainerListener}. */
public abstract class ContainerAdapter implements ContainerListener {
    @Override
    public void componentAdded(ContainerEvent e) {
    }

    @Override
    public void componentRemoved(ContainerEvent e) {
    }
}
