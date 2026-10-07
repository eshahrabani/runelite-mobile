package net.runelite.client.ui.components.materialtabs;

import java.awt.Dimension;
import java.awt.event.MouseListener;
import java.util.function.BooleanSupplier;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * Host replacement for {@code net.runelite.client.ui.components.materialtabs.MaterialTab}.
 *
 * <p>Data-only tab header; selection stores a flag, no painting or event firing.
 */
public class MaterialTab extends JLabel
{
	private String tabName;
	private BooleanSupplier onSelectEvent;
	private boolean selected;

	public MaterialTab(String name, MaterialTabGroup group, JComponent content)
	{
		super(name != null ? name : "");
		this.tabName = name;
	}

	public MaterialTab(ImageIcon icon, MaterialTabGroup group, JComponent content)
	{
		super();
		this.tabName = icon != null ? icon.toString() : "";
	}

	public boolean select()
	{
		this.selected = true;
		return true;
	}

	public void unselect()
	{
		this.selected = false;
	}

	public JComponent getContent()
	{
		return null;
	}

	public void setOnSelectEvent(BooleanSupplier onSelectEvent)
	{
		this.onSelectEvent = onSelectEvent;
	}

	public boolean isSelected()
	{
		return selected;
	}

	public void setName(String name)
	{
		this.tabName = name;
	}

	@Override
	public void addMouseListener(MouseListener listener)
	{
		super.addMouseListener(listener);
	}

	@Override
	public void setIcon(Icon icon)
	{
		super.setIcon(icon);
	}

	@Override
	public void setPreferredSize(Dimension preferredSize)
	{
		super.setPreferredSize(preferredSize);
	}

	@Override
	public void setToolTipText(String text)
	{
		super.setToolTipText(text);
	}
}
