package net.runelite.client.ui.components;

import javax.swing.JPanel;
import javax.swing.border.Border;

/**
 * Host replacement for {@code net.runelite.client.ui.components.PluginErrorPanel}.
 *
 * <p>Data-only: stores/ignores the content; no layout or painting.
 */
public class PluginErrorPanel extends JPanel
{
	public PluginErrorPanel()
	{
		super();
	}

	@Override
	public void setBorder(Border border)
	{
		super.setBorder(border);
	}

	public void setContent(String title, String description)
	{
	}

	@Override
	public void setVisible(boolean visible)
	{
		super.setVisible(visible);
	}
}
