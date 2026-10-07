package net.runelite.client.ui.components;

import javax.swing.DefaultListCellRenderer;

/**
 * Host replacement for {@code net.runelite.client.ui.components.TitleCaseListCellRenderer}.
 *
 * <p>Upstream title-cases the rendered list value. Only the constructor is referenced by
 * retained classes; rendering never happens on this port.
 */
public final class TitleCaseListCellRenderer extends DefaultListCellRenderer
{
	public TitleCaseListCellRenderer()
	{
		super();
	}
}
