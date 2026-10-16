package java.awt.event;

import java.awt.Adjustable;
import java.awt.AWTEvent;

/**
 * The event an {@link Adjustable} fires when its value changes.
 *
 * <p>Hand-written rather than generated: the shipped jars implement
 * {@link AdjustmentListener} and reference {@code AdjustmentEvent} as the listener
 * argument, and {@code javax.swing.JScrollBar} has to construct one whenever its model
 * changes. The generated file had only the placeholder no-arg constructor. The marker
 * sentence is removed so the generator leaves this file alone; the no-arg constructor is
 * kept because the generator models hand-written supertypes that way and emits
 * {@code super()} into generated subclasses.
 *
 * <p>Port deviation: none in behaviour. The {@code java.lang.annotation.Native} markers
 * on the type constants are dropped (no JNI layer on this port).
 */
public class AdjustmentEvent extends AWTEvent {

    /** Marks the first integer id for the range of adjustment event ids. */
    public static final int ADJUSTMENT_FIRST = 601;

    /** Marks the last integer id for the range of adjustment event ids. */
    public static final int ADJUSTMENT_LAST = 601;

    /** The adjustment value changed event. */
    public static final int ADJUSTMENT_VALUE_CHANGED = ADJUSTMENT_FIRST;

    /** The value changed by one unit in the positive direction. */
    public static final int UNIT_INCREMENT = 1;

    /** The value changed by one unit in the negative direction. */
    public static final int UNIT_DECREMENT = 2;

    /** The value changed by one block (page) in the negative direction. */
    public static final int BLOCK_DECREMENT = 3;

    /** The value changed by one block (page) in the positive direction. */
    public static final int BLOCK_INCREMENT = 4;

    /** The value was set directly (Swing's scroll bars report every change this way). */
    public static final int TRACK = 5;

    private final Adjustable adjustable;
    private final int value;
    private final int adjustmentType;
    private final boolean isAdjusting;

    /**
     * @param source the adjustable that changed
     * @param id     {@link #ADJUSTMENT_VALUE_CHANGED}
     * @param type   one of the {@code UNIT_*}/{@code BLOCK_*}/{@code TRACK} constants
     * @param value  the new value
     */
    public AdjustmentEvent(Adjustable source, int id, int type, int value) {
        this(source, id, type, value, false);
    }

    /**
     * @param isAdjusting true when the event is one of a series (a thumb drag in
     *                    progress), false when the value has settled
     */
    public AdjustmentEvent(Adjustable source, int id, int type, int value, boolean isAdjusting) {
        super(source, id);
        this.adjustable = source;
        this.adjustmentType = type;
        this.value = value;
        this.isAdjusting = isAdjusting;
    }

    /**
     * Placeholder constructor required by {@code tools/gen_stubs.py}: it models a
     * hand-written supertype as "a plain class with a no-arg constructor" and emits
     * {@code super()} into every generated subclass. Nothing instantiates this form; the
     * placeholder source is the class object because {@code EventObject} rejects a null
     * source (the same trick {@code AWTEvent()}'s own no-arg constructor uses).
     */
    public AdjustmentEvent() {
        super(AWTEvent.class, ADJUSTMENT_VALUE_CHANGED);
        this.adjustable = null;
        this.adjustmentType = TRACK;
        this.value = 0;
        this.isAdjusting = false;
    }

    /** The adjustable where this event originated. */
    public Adjustable getAdjustable() {
        return adjustable;
    }

    /** The value the adjustable had when the event was created. */
    public int getValue() {
        return value;
    }

    /** How the value changed: {@code UNIT_*}, {@code BLOCK_*} or {@code TRACK}. */
    public int getAdjustmentType() {
        return adjustmentType;
    }

    /** True while the value is still being adjusted (thumb drag in progress). */
    public boolean getValueIsAdjusting() {
        return isAdjusting;
    }

    @Override
    public String paramString() {
        String typeStr = getID() == ADJUSTMENT_VALUE_CHANGED ? "ADJUSTMENT_VALUE_CHANGED" : "unknown type";
        String adjTypeStr;
        switch (adjustmentType) {
            case UNIT_INCREMENT:
                adjTypeStr = "UNIT_INCREMENT";
                break;
            case UNIT_DECREMENT:
                adjTypeStr = "UNIT_DECREMENT";
                break;
            case BLOCK_INCREMENT:
                adjTypeStr = "BLOCK_INCREMENT";
                break;
            case BLOCK_DECREMENT:
                adjTypeStr = "BLOCK_DECREMENT";
                break;
            case TRACK:
                adjTypeStr = "TRACK";
                break;
            default:
                adjTypeStr = "unknown type";
        }
        return typeStr + ",adjType=" + adjTypeStr + ",value=" + value + ",isAdjusting=" + isAdjusting;
    }
}
