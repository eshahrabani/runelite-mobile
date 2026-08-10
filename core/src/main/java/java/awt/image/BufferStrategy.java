package java.awt.image;

import java.awt.Graphics;

/**
 * AWT BufferStrategy compatibility stub for Android runtime.
 */
public abstract class BufferStrategy {
    public BufferStrategy() {}
    public abstract Object getCapabilities();
    public abstract Graphics getDrawGraphics();
    public abstract boolean contentsLost();
    public abstract boolean contentsRestored();
    public abstract void show();
}
