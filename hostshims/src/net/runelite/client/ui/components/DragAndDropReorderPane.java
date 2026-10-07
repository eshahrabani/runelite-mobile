package net.runelite.client.ui.components;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLayeredPane;

/**
 * Host replacement for {@code net.runelite.client.ui.components.DragAndDropReorderPane}.
 *
 * <p>Data-only: keeps the registered {@link DragListener}s and the standard
 * {@code JLayeredPane} component list. No dragging, no layout.
 */
public class DragAndDropReorderPane extends JLayeredPane
{
	private final List<DragListener> dragListeners = new ArrayList<>();

	public DragAndDropReorderPane()
	{
		super();
	}

	@Override
	public Component add(Component component)
	{
		return super.add(component);
	}

	public void addDragListener(DragListener listener)
	{
		if (listener != null)
		{
			dragListeners.add(listener);
		}
	}

	public void removeDragListener(DragListener listener)
	{
		dragListeners.remove(listener);
	}

	public int getPosition(Component component)
	{
		return 0;
	}

	@Override
	public void revalidate()
	{
		super.revalidate();
	}

	public interface DragListener
	{
		void onDrag(Component component);
	}
}
