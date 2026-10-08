package java.awt;

/**
 * AWT Frame compatibility stub for Android runtime.
 */
public class Frame extends Window {
    private String title = "";
    private boolean resizable = true;
    /** Window state; default NORMAL (0). Nothing reads it on this port. */
    private int state = 0;

    public Frame() {}
    
    public Frame(String title) {
        this.title = title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    public boolean isResizable() {
        return resizable;
    }

    public void setState(int state) {
        this.state = state;
    }

    public Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    }
}
