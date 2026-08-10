package org.runelite.mobile;

import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.uikit.UIApplication;
import org.robovm.apple.uikit.UIApplicationDelegateAdapter;
import org.robovm.apple.uikit.UIApplicationLaunchOptions;
import org.robovm.apple.uikit.UIScreen;
import org.robovm.apple.uikit.UIWindow;
import org.robovm.apple.uikit.UIViewController;

public class IOSLauncher extends UIApplicationDelegateAdapter {
    private UIWindow window;

    @Override
    public boolean didFinishLaunching(UIApplication application, UIApplicationLaunchOptions launchOptions) {
        // Initialize the UIKit window using standard screen bounds
        window = new UIWindow(UIScreen.getMainScreen().getBounds());
        
        UIViewController viewController = new UIViewController();
        // In the full compilation: set up a Metal or OpenGL ES context View and map the AWTBridge pixel output
        
        window.setRootViewController(viewController);
        window.makeKeyAndVisible();
        
        System.out.println("RuneLite iOS Launcher initialized successfully.");
        return true;
    }

    public static void main(String[] args) {
        try (NSAutoreleasePool pool = new NSAutoreleasePool()) {
            UIApplication.main(args, null, IOSLauncher.class);
        }
    }
}
