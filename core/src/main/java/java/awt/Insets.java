package java.awt;

/**
 * AWT Insets compatibility stub for Android runtime.
 */
public class Insets {
    public int top;
    public int left;
    public int bottom;
    public int right;

    public Insets(int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    public void set(int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Insets)) {
            return false;
        }
        Insets i = (Insets) obj;
        return top == i.top && left == i.left && bottom == i.bottom && right == i.right;
    }

    @Override
    public int hashCode() {
        return ((top * 31 + left) * 31 + bottom) * 31 + right;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[top=" + top + ",left=" + left + ",bottom=" + bottom + ",right=" + right + "]";
    }
}
