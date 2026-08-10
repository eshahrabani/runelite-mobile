package java.awt.image;

/**
 * AWT DataBuffer compatibility stub for Android runtime.
 */
public abstract class DataBuffer {
    public static final int TYPE_INT = 3;
    
    protected int dataType;
    protected int size;

    protected DataBuffer(int dataType, int size) {
        this.dataType = dataType;
        this.size = size;
    }

    public int getDataType() {
        return dataType;
    }

    public int getSize() {
        return size;
    }
}
