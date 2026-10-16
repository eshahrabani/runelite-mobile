package java.awt.event;

import java.awt.AWTEvent;
import java.awt.ItemSelectable;

/**
 * AWT {@code ItemEvent}: a selectable item changed state (checkbox, combo box, list).
 */
public class ItemEvent extends AWTEvent {
    public static final int ITEM_FIRST = 701;
    public static final int ITEM_STATE_CHANGED = 701;
    public static final int ITEM_LAST = 701;

    public static final int SELECTED = 1;
    public static final int DESELECTED = 2;

    private final Object item;
    private final int stateChange;

    /** Placeholder for tools/gen_stubs.py's hand-written-supertype model. */
    public ItemEvent() {
        this(null, ITEM_STATE_CHANGED, null, SELECTED);
    }

    public ItemEvent(ItemSelectable source, int id, Object item, int stateChange) {
        super(source == null ? ItemEvent.class : source, id);
        this.item = item;
        this.stateChange = stateChange;
    }

    public ItemSelectable getItemSelectable() {
        Object source = getSource();
        return source instanceof ItemSelectable ? (ItemSelectable) source : null;
    }

    public Object getItem() {
        return item;
    }

    public int getStateChange() {
        return stateChange;
    }

    @Override
    public String paramString() {
        return (stateChange == SELECTED ? "SELECTED" : "DESELECTED") + ",item=" + item;
    }
}
