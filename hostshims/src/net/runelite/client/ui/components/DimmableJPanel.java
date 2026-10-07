package net.runelite.client.ui.components;

import java.awt.Color;
import javax.swing.JPanel;

/**
 * Host replacement for {@code net.runelite.client.ui.components.DimmableJPanel}.
 *
 * <p>Data-only: remembers the dimmed flag and the foreground/background pairs upstream
 * switches between, but performs no repainting.
 */
public class DimmableJPanel extends JPanel
{
	private boolean dimmed;

	private Color dimmedForeground;
	private Color dimmedBackground;
	private Color undimmedForeground;
	private Color undimmedBackground;

	public DimmableJPanel()
	{
		super();
	}

	@Override
	public void setForeground(Color foreground)
	{
		super.setForeground(foreground);
	}

	@Override
	public void setBackground(Color background)
	{
		super.setBackground(background);
	}

	@Override
	public Color getForeground()
	{
		return super.getForeground();
	}

	@Override
	public Color getBackground()
	{
		return super.getBackground();
	}

	public void setDimmed(boolean dimmed)
	{
		this.dimmed = dimmed;
	}

	public boolean isDimmed()
	{
		return dimmed;
	}
}
