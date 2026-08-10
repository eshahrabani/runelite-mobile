package java.applet;

import java.net.URL;

/**
 * AppletStub compatibility stub for Android runtime.
 */
public interface AppletStub {
    boolean isActive();
    URL getDocumentBase();
    URL getCodeBase();
    String getParameter(String name);
    AppletContext getAppletContext();

    void appletResize(int width, int height);
}
