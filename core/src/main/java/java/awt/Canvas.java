package java.awt;

import java.awt.image.BufferStrategy;

/**
 * AWT Canvas compatibility stub for Android runtime.
 * Captures buffer strategy drawing context to direct to AWTBridge.
 */
public class Canvas extends Component {
    private BufferStrategy bufferStrategy;

    public Canvas() {}

    public void createBufferStrategy(int numBuffers) {
        this.bufferStrategy = new StubBufferStrategy();
    }

    public BufferStrategy getBufferStrategy() {
        return bufferStrategy;
    }

    private class StubBufferStrategy extends BufferStrategy {
        private final Graphics2D graphics = new Graphics2D();

        @Override
        public Object getCapabilities() { return null; }

        @Override
        public Graphics getDrawGraphics() {
            return graphics;
        }

        @Override
        public boolean contentsLost() { return false; }

        @Override
        public boolean contentsRestored() { return false; }

        @Override
        public void show() {
            // Intercept frame swap to transfer pixels to rendering bridge
        }
    }
}
