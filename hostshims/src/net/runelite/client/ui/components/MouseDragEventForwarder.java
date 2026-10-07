package net.runelite.client.ui.components;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Host replacement for {@code net.runelite.client.ui.components.MouseDragEventForwarder}.
 *
 * <p>Upstream re-dispatches drag events to a target component. Data-only here: it stores
 * the target and does nothing with the events.
 */
public class MouseDragEventForwarder extends MouseAdapter
{
	private final Component target;

	public MouseDragEventForwarder(Component target)
	{
		this.target = target;
	}

	@Override
	public void mousePressed(MouseEvent event)
	{
		processEvent(event);
	}

	@Override
	public void mouseDragged(MouseEvent event)
	{
		processEvent(event);
	}

	@Override
	public void mouseReleased(MouseEvent event)
	{
		processEvent(event);
	}

	private void processEvent(MouseEvent event)
	{
	}
}
