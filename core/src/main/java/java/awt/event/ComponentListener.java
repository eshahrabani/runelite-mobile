package java.awt.event;

public interface ComponentListener {
    void componentResized(ComponentEvent e);
    void componentMoved(ComponentEvent e);
    void componentShown(ComponentEvent e);
    void componentHidden(ComponentEvent e);
}
