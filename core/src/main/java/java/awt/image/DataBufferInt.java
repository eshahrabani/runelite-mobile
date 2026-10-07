package java.awt.image;

/**
 * AWT DataBufferInt compatibility stub for Android runtime.
 *
 * <p>Wraps the caller's {@code int[]} without copying, so a
 * {@code BufferedImage} built from it (and any raster/graphics view) shares the
 * same pixels — the injected client's frame buffer and the RuneLite overlay
 * pass must write to one array.
 */
public final class DataBufferInt extends DataBuffer {
    private final int[][] data;

    public DataBufferInt(int size) {
        this(new int[size], size);
    }

    public DataBufferInt(int[] dataArray, int size) {
        super(TYPE_INT, size);
        this.data = new int[][] {dataArray};
    }

    public DataBufferInt(int[] dataArray, int size, int offset) {
        this(dataArray, size);
        this.offsets[0] = offset;
    }

    public int[] getData() {
        return data[0];
    }

    public int[] getData(int bank) {
        return data[bank];
    }

    @Override
    public int getOffset() {
        return offsets[0];
    }

    public int getElem(int i) {
        return data[0][i + offsets[0]];
    }

    public void setElem(int i, int val) {
        data[0][i + offsets[0]] = val;
    }
}
