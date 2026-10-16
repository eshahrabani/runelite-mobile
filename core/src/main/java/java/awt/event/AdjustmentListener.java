package java.awt.event;

import java.util.EventListener;

/**
 * The listener {@link java.awt.Adjustable} implementations notify when their value
 * changes (the compatibility channel Swing scroll bars keep alongside
 * {@code BoundedRangeModel}'s {@code ChangeListener}s).
 *
 * <p>Hand-written rather than generated: the shipped jars implement this interface
 * ({@code net.runelite.client.plugins.devtools.*Inspector$1} does, for instance) and the
 * generated file declared no {@code adjustmentValueChanged} at all, so a real method is
 * required for those classes to link and for {@code JScrollBar} to fire. The marker
 * sentence is removed so the generator leaves this file alone.
 */
public interface AdjustmentListener extends EventListener {

    /** The adjustable's value changed; {@code e} carries the new value and its origin. */
    void adjustmentValueChanged(AdjustmentEvent e);
}
