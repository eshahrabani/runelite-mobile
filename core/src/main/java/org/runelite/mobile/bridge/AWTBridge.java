package org.runelite.mobile.bridge;

import java.util.ArrayList;
import java.util.List;

/**
 * AWTBridge bridges pixel rendering data and touch inputs
 * without depending on desktop-only java.awt packages.
 */
public class AWTBridge {
    public static volatile int[] activePixels;
    public static volatile int activeWidth;
    public static volatile int activeHeight;

    private final int[] rawPixels;
    private final int width;
    private final int height;

    public AWTBridge(int width, int height) {
        this.width = width;
        this.height = height;
        this.rawPixels = new int[width * height];
    }

    public int[] getRawPixels() {
        return rawPixels;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * MousePathSmoother generates human-like curves between coordinates.
     */
    public static class MousePathSmoother {

        /**
         * Generates a list of MobilePoint points representing a natural path.
         */
        public static List<MobilePoint> generatePath(MobilePoint start, MobilePoint end, int steps) {
            List<MobilePoint> path = new ArrayList<>();
            if (steps <= 1) {
                path.add(end);
                return path;
            }

            int dx = end.x - start.x;
            int dy = end.y - start.y;

            double controlFactor = 0.2 + Math.random() * 0.3;
            MobilePoint ctrl1 = new MobilePoint(
                (int) (start.x + dx * controlFactor + (Math.random() - 0.5) * 50),
                (int) (start.y + dy * controlFactor + (Math.random() - 0.5) * 50)
            );
            MobilePoint ctrl2 = new MobilePoint(
                (int) (start.x + dx * (1.0 - controlFactor) + (Math.random() - 0.5) * 50),
                (int) (start.y + dy * (1.0 - controlFactor) + (Math.random() - 0.5) * 50)
            );

            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                
                double omt = 1.0 - t;
                double omt2 = omt * omt;
                double omt3 = omt2 * omt;
                double t2 = t * t;
                double t3 = t2 * t;

                int x = (int) (omt3 * start.x + 3 * omt2 * t * ctrl1.x + 3 * omt * t2 * ctrl2.x + t3 * end.x);
                int y = (int) (omt3 * start.y + 3 * omt2 * t * ctrl1.y + 3 * omt * t2 * ctrl2.y + t3 * end.y);

                path.add(new MobilePoint(x, y));
            }

            return path;
        }
    }
}
