package java.awt.image;

/**
 * AWT DataBufferInt compatibility stub for Android runtime.
 */
public final class DataBufferInt extends DataBuffer {
    private final int[] data;

    public DataBufferInt(int size) {
        super(TYPE_INT, size);
        this.data = new int[size];
    }

    public DataBufferInt(int[] dataArray, int size) {
        super(TYPE_INT, size);
        this.data = dataArray;
    }

    public int[] getData() {
        return data;
    }

    public int[] getData(int bank) {
        return data;
    }
}
