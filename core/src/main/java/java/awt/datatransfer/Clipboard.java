package java.awt.datatransfer;

public class Clipboard {
    private String name;
    public Clipboard(String name) { this.name = name; }
    public String getName() { return name; }
    public void setContents(Transferable contents, ClipboardOwner owner) {}
    public Transferable getContents(Object requestor) { return null; }
}
