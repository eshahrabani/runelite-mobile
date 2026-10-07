package net.runelite.client.ui.components;

import java.awt.Color;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPopupMenu;

/**
 * Host replacement for {@code net.runelite.client.ui.components.ProgressBar}.
 *
 * <p>Data-only: keeps the current value/maximum, the marker positions and the label
 * strings. No painting.
 */
public class ProgressBar extends DimmableJPanel
{
	private int maximumValue;
	private int value;
	private List<Integer> positions = new ArrayList<>();
	private String centerLabelText;
	private String dimmedText;

	public ProgressBar()
	{
		super();
	}

	@Override
	public void paint(java.awt.Graphics graphics)
	{
	}

	@Override
	public void setDimmed(boolean dimmed)
	{
		super.setDimmed(dimmed);
	}

	public void setLeftLabel(String leftLabel)
	{
	}

	public void setRightLabel(String rightLabel)
	{
	}

	public void setCenterLabel(String centerLabel)
	{
		this.centerLabelText = centerLabel;
	}

	public void setDimmedText(String dimmedText)
	{
		this.dimmedText = dimmedText;
	}

	public int getPercentage()
	{
		return maximumValue == 0 ? 0 : (int) ((double) value / maximumValue * 100.0);
	}

	public void setMaximumValue(int maximumValue)
	{
		this.maximumValue = maximumValue;
	}

	public void setValue(int value)
	{
		this.value = value;
	}

	public void setPositions(List positions)
	{
		this.positions = positions != null ? positions : new ArrayList<>();
	}

	@Override
	public void addMouseListener(MouseListener listener)
	{
		super.addMouseListener(listener);
	}

	@Override
	public void addMouseMotionListener(MouseMotionListener listener)
	{
		super.addMouseMotionListener(listener);
	}

	@Override
	public void setBackground(Color background)
	{
		super.setBackground(background);
	}

	@Override
	public void setForeground(Color foreground)
	{
		super.setForeground(foreground);
	}

	@Override
	public void setComponentPopupMenu(JPopupMenu popupMenu)
	{
		super.setComponentPopupMenu(popupMenu);
	}

	@Override
	public void setToolTipText(String toolTipText)
	{
		super.setToolTipText(toolTipText);
	}
}
