package java.applet;

import java.awt.Panel;

/**
 * Applet compatibility stub for Android runtime.
 * Allows OSRS client class to be instantiated as an applet container.
 */
public class Applet extends Panel {
    private AppletStub stub;

    public Applet() {}
    
    public void init() {}
    public void start() {}
    public void stop() {}
    public void destroy() {}
    
    public void setStub(AppletStub stub) {
        this.stub = stub;
    }
    
    public AppletStub getStub() {
        return stub;
    }
}
