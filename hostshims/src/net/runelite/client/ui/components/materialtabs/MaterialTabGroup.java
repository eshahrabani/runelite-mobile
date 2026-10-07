package net.runelite.client.ui.components.materialtabs;

import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.border.Border;

/**
 * Host replacement for {@code net.runelite.client.ui.components.materialtabs.MaterialTabGroup}.
 *
 * <p>Data-only: keeps the registered tabs and their display panel; no layout or painting.
 */
public class MaterialTabGroup extends JPanel
{
	private final List<MaterialTab> tabs = new ArrayList<>();

	public MaterialTabGroup()
	{
		super();
	}

	public MaterialTabGroup(JPanel display)
	{
		super();
	}

	public void addTab(MaterialTab tab)
	{
		if (tab != null)
		{
			tabs.add(tab);
		}
	}

	public MaterialTab getTab(int index)
	{
		return index >= 0 && index < tabs.size() ? tabs.get(index) : null;
	}

	public boolean select(MaterialTab tab)
	{
		return false;
	}

	@Override
	public void setBorder(Border border)
	{
		super.setBorder(border);
	}

	@Override
	public void setLayout(LayoutManager layout)
	{
		super.setLayout(layout);
	}
}
