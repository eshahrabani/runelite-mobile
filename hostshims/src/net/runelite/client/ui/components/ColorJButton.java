package net.runelite.client.ui.components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseListener;
import javax.swing.JButton;

/**
 * Host replacement for {@code net.runelite.client.ui.components.ColorJButton}.
 *
 * <p>Stores the selected {@link Color}; no painting.
 */
public class ColorJButton extends JButton
{
	private Color color;

	public ColorJButton(String text, Color color)
	{
		super(text);
		this.color = color;
	}

	public void setColor(Color color)
	{
		this.color = color;
	}

	public Color getColor()
	{
		return color;
	}

	@Override
	public void paint(Graphics graphics)
	{
	}

	@Override
	public void addMouseListener(MouseListener listener)
	{
		super.addMouseListener(listener);
	}

	@Override
	public void setFocusable(boolean focusable)
	{
		super.setFocusable(focusable);
	}

	@Override
	public void setText(String text)
	{
		super.setText(text);
	}
}
