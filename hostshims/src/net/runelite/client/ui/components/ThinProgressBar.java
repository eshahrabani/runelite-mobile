package net.runelite.client.ui.components;

import java.awt.Color;
import javax.swing.JPanel;

/**
 * Host replacement for {@code net.runelite.client.ui.components.ThinProgressBar}.
 *
 * <p>Data-only: keeps value/maximum; no painting.
 */
public class ThinProgressBar extends JPanel
{
	private int maximumValue;
	private int value;

	public ThinProgressBar()
	{
		super();
	}

	public double getPercentage()
	{
		return maximumValue == 0 ? 0.0 : (double) value / maximumValue * 100.0;
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

	public void setMaximumValue(int maximumValue)
	{
		this.maximumValue = maximumValue;
	}

	public int getMaximumValue()
	{
		return maximumValue;
	}

	public void setValue(int value)
	{
		this.value = value;
	}

	public int getValue()
	{
		return value;
	}

	@Override
	public void setVisible(boolean visible)
	{
		super.setVisible(visible);
	}
}
