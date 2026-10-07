package net.runelite.client.ui.components.shadowlabel;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import javax.swing.JLabel;

/**
 * Host replacement for {@code net.runelite.client.ui.components.shadowlabel.JShadowedLabel}.
 *
 * <p>Data-only: stores the shadow colour/size; the label itself never paints.
 */
public class JShadowedLabel extends JLabel
{
	private Color shadow;
	private Point shadowSize = new Point(0, 0);

	public JShadowedLabel()
	{
		super();
	}

	public JShadowedLabel(String text)
	{
		super(text);
	}

	public void setShadow(Color shadow)
	{
		this.shadow = shadow;
	}

	public Color getShadow()
	{
		return shadow;
	}

	public void setShadowSize(Point shadowSize)
	{
		this.shadowSize = shadowSize;
	}

	public Point getShadowSize()
	{
		return shadowSize;
	}

	@Override
	public void setFont(Font font)
	{
		super.setFont(font);
	}

	@Override
	public void setForeground(Color foreground)
	{
		super.setForeground(foreground);
	}

	@Override
	public void setText(String text)
	{
		super.setText(text);
	}
}
