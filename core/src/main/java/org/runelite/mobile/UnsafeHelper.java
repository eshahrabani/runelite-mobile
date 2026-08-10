package org.runelite.mobile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class UnsafeHelper {
    public static final int ARRAY_BYTE_BASE_OFFSET;
    public static final int ARRAY_CHAR_BASE_OFFSET;
    public static final int ARRAY_SHORT_BASE_OFFSET;
    public static final int ARRAY_INT_BASE_OFFSET;
    public static final int ARRAY_LONG_BASE_OFFSET;
    public static final int ARRAY_FLOAT_BASE_OFFSET;
    public static final int ARRAY_DOUBLE_BASE_OFFSET;
    public static final int ARRAY_OBJECT_BASE_OFFSET;
    public static final int ARRAY_BOOLEAN_BASE_OFFSET;

    public static final int ARRAY_BYTE_INDEX_SCALE;
    public static final int ARRAY_CHAR_INDEX_SCALE;
    public static final int ARRAY_SHORT_INDEX_SCALE;
    public static final int ARRAY_INT_INDEX_SCALE;
    public static final int ARRAY_LONG_INDEX_SCALE;
    public static final int ARRAY_FLOAT_INDEX_SCALE;
    public static final int ARRAY_DOUBLE_INDEX_SCALE;
    public static final int ARRAY_OBJECT_INDEX_SCALE;
    public static final int ARRAY_BOOLEAN_INDEX_SCALE;

    static {
        int byteOffset = 12;
        int charOffset = 12;
        int shortOffset = 12;
        int intOffset = 12;
        int longOffset = 16;
        int floatOffset = 12;
        int doubleOffset = 16;
        int objectOffset = 12;
        int booleanOffset = 12;

        int byteScale = 1;
        int charScale = 2;
        int shortScale = 2;
        int intScale = 4;
        int longScale = 8;
        int floatScale = 4;
        int doubleScale = 8;
        int objectScale = 4;
        int booleanScale = 1;

        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            Object unsafeInstance = theUnsafeField.get(null);

            Method arrayBaseOffsetMethod = unsafeClass.getMethod("arrayBaseOffset", Class.class);
            Method arrayIndexScaleMethod = unsafeClass.getMethod("arrayIndexScale", Class.class);

            byteOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, byte[].class)).intValue();
            charOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, char[].class)).intValue();
            shortOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, short[].class)).intValue();
            intOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, int[].class)).intValue();
            longOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, long[].class)).intValue();
            floatOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, float[].class)).intValue();
            doubleOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, double[].class)).intValue();
            objectOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, Object[].class)).intValue();
            booleanOffset = ((Number) arrayBaseOffsetMethod.invoke(unsafeInstance, boolean[].class)).intValue();

            byteScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, byte[].class)).intValue();
            charScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, char[].class)).intValue();
            shortScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, short[].class)).intValue();
            intScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, int[].class)).intValue();
            longScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, long[].class)).intValue();
            floatScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, float[].class)).intValue();
            doubleScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, double[].class)).intValue();
            objectScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, Object[].class)).intValue();
            booleanScale = ((Number) arrayIndexScaleMethod.invoke(unsafeInstance, boolean[].class)).intValue();
        } catch (Throwable t) {
            System.err.println("UnsafeHelper: Failed to query sun.misc.Unsafe via reflection, using defaults");
            t.printStackTrace();
        }

        ARRAY_BYTE_BASE_OFFSET = byteOffset;
        ARRAY_CHAR_BASE_OFFSET = charOffset;
        ARRAY_SHORT_BASE_OFFSET = shortOffset;
        ARRAY_INT_BASE_OFFSET = intOffset;
        ARRAY_LONG_BASE_OFFSET = longOffset;
        ARRAY_FLOAT_BASE_OFFSET = floatOffset;
        ARRAY_DOUBLE_BASE_OFFSET = doubleOffset;
        ARRAY_OBJECT_BASE_OFFSET = objectOffset;
        ARRAY_BOOLEAN_BASE_OFFSET = booleanOffset;

        ARRAY_BYTE_INDEX_SCALE = byteScale;
        ARRAY_CHAR_INDEX_SCALE = charScale;
        ARRAY_SHORT_INDEX_SCALE = shortScale;
        ARRAY_INT_INDEX_SCALE = intScale;
        ARRAY_LONG_INDEX_SCALE = longScale;
        ARRAY_FLOAT_INDEX_SCALE = floatScale;
        ARRAY_DOUBLE_INDEX_SCALE = doubleScale;
        ARRAY_OBJECT_INDEX_SCALE = objectScale;
        ARRAY_BOOLEAN_INDEX_SCALE = booleanScale;
    }

    public static void copyMemory(Object unsafe, Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        sun.misc.Unsafe u = (sun.misc.Unsafe) unsafe;
        long i = 0;
        
        if (srcBase == null && destBase == null) {
            for (; i <= bytes - 8; i += 8) {
                long val = u.getLong(srcOffset + i);
                u.putLong(destOffset + i, val);
            }
            for (; i < bytes; i++) {
                byte val = u.getByte(srcOffset + i);
                u.putByte(destOffset + i, val);
            }
        } else if (srcBase == null) {
            for (; i <= bytes - 8; i += 8) {
                long val = u.getLong(srcOffset + i);
                u.putLong(destBase, destOffset + i, val);
            }
            for (; i < bytes; i++) {
                byte val = u.getByte(srcOffset + i);
                u.putByte(destBase, destOffset + i, val);
            }
        } else if (destBase == null) {
            for (; i <= bytes - 8; i += 8) {
                long val = u.getLong(srcBase, srcOffset + i);
                u.putLong(destOffset + i, val);
            }
            for (; i < bytes; i++) {
                byte val = u.getByte(srcBase, srcOffset + i);
                u.putByte(destOffset + i, val);
            }
        } else {
            for (; i <= bytes - 8; i += 8) {
                long val = u.getLong(srcBase, srcOffset + i);
                u.putLong(destBase, destOffset + i, val);
            }
            for (; i < bytes; i++) {
                byte val = u.getByte(srcBase, srcOffset + i);
                u.putByte(destBase, destOffset + i, val);
            }
        }
    }

    public static void copyMemory(Object unsafe, long srcAddress, long destAddress, long bytes) {
        copyMemory(unsafe, null, srcAddress, null, destAddress, bytes);
    }
}
