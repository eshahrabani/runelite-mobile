package java.awt.image;

/**
 * AWT DataBuffer compatibility stub for Android runtime.
 */
public abstract class DataBuffer {
    public static final int TYPE_BYTE = 0;
    public static final int TYPE_USHORT = 1;
    public static final int TYPE_SHORT = 2;
    public static final int TYPE_INT = 3;
    public static final int TYPE_FLOAT = 4;
    public static final int TYPE_DOUBLE = 5;

    protected int dataType;
    protected int size;
    protected int[] offsets;

    protected DataBuffer(int dataType, int size) {
        this(dataType, size, 1);
    }

    protected DataBuffer(int dataType, int size, int numBanks) {
        this.dataType = dataType;
        this.size = size;
        this.offsets = new int[numBanks];
    }

    public int getDataType() {
        return dataType;
    }

    public int getSize() {
        return size;
    }

    public int getNumBanks() {
        return offsets.length;
    }

    public int getOffset() {
        return offsets[0];
    }
}
