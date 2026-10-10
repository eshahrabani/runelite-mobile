package org.runelite.mobile;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Switch;

/**
 * The host's palette and drawable helpers, shared by {@link MainActivity} (launcher,
 * keyboard bar) and {@link SidePanel} (drawer). Colours are defined here once instead of
 * once per widget: the stone/gold scheme is the only place the two screens agree on.
 *
 * <p>Never instantiated; every member is a package-private static helper.
 */
final class UiTheme {

    /** Letterbox bars behind the game frame and the launcher scrim base. */
    static final int BG_DEEP = 0xFF12100E;
    /** Drawer body, launcher card. */
    static final int SURFACE = 0xFF1E1A16;
    /** Header band, inputs, small buttons. */
    static final int SURFACE_ALT = 0xFF2A2318;
    static final int BORDER = 0xFF4A3C28;
    /** The accent already used for the active tab. */
    static final int GOLD = 0xFFFFC83D;
    static final int GOLD_DARK = 0xFFD6A419;
    /** 40% gold, for strokes. */
    static final int GOLD_DIM = 0x66FFC83D;
    /** 20% gold, for the selected tab. */
    static final int GOLD_FILL = 0x33FFC83D;
    static final int TEXT = 0xFFE8DCC0;
    static final int TEXT_MUTED = 0xFF9C9184;
    static final int TEXT_DIM = 0xFF6E655B;
    static final int GREEN = 0xFF3FA34D;
    static final int RED = 0xFFE57373;
    static final int ORANGE = 0xFFFF9800;

    private UiTheme() {
    }

    /** Rounded rect with an optional 1-2 dp stroke. {@code radiusDp <= 0} -> no rounding. */
    static GradientDrawable rounded(int fill, int stroke, float strokeDp, float radiusDp, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        if (radiusDp > 0) {
            drawable.setCornerRadius(radiusDp * density);
        }
        if (strokeDp > 0) {
            drawable.setStroke(Math.max(1, Math.round(strokeDp * density)), stroke);
        }
        return drawable;
    }

    /**
     * Same, but per-corner radii in dp: {@code float[8]} as
     * {@code {tlX,tlY,trX,trY,brX,brY,blX,blY}}. Used for the drawer's left edge and the
     * right-edge column, which only round the corners that face the screen.
     */
    static GradientDrawable corners(int fill, int stroke, float strokeDp, float[] radiiDp, float density) {
        float[] radii = new float[8];
        for (int i = 0; i < 8; i++) {
            radii[i] = radiiDp[i] * density;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadii(radii);
        if (strokeDp > 0) {
            drawable.setStroke(Math.max(1, Math.round(strokeDp * density)), stroke);
        }
        return drawable;
    }

    /**
     * Applies a ripple to {@code view}: its current background (or
     * {@code ?android:attr/selectableItemBackground} when it has none) is wrapped in a
     * {@link RippleDrawable}, so a themed fill is not replaced by the platform's own
     * ripple background.
     */
    static void ripple(View view) {
        Drawable content = view.getBackground();
        if (content == null) {
            android.util.TypedValue out = new android.util.TypedValue();
            if (view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, out, true)) {
                view.setBackgroundResource(out.resourceId);
            }
            return;
        }
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, null));
    }

    /**
     * Gold thumb + translucent gold track when checked, muted grey thumb + dark track when
     * not: a single gold thumb for both states makes the switch's state unreadable.
     */
    static void tintSwitch(Switch toggle) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{GOLD, TEXT_MUTED}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{GOLD_FILL, BORDER}));
    }
}
