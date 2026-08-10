package java.awt;

/**
 * AWT Frame compatibility stub for Android runtime.
 */
public class Frame extends Window {
    private String title = "";
    private boolean resizable = true;

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

    public Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    }
}
