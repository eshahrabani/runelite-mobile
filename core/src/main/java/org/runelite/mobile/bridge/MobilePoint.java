package org.runelite.mobile.bridge;

/**
 * MobilePoint is a platform-independent coordinate holder.
 * Replacing java.awt.Point ensures compile-time compatibility on Android.
 */
public class MobilePoint {
    public final int x;
    public final int y;

    public MobilePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "MobilePoint{" + "x=" + x + ", y=" + y + '}';
    }
}
