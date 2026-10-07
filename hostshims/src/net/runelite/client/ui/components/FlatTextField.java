package net.runelite.client.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.KeyListener;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.text.Document;

/**
 * Host replacement for {@code net.runelite.client.ui.components.FlatTextField}.
 *
 * <p>Wraps a real (but non-painting) {@link JTextField} and stores the colour state.
 */
public class FlatTextField extends JPanel
{
	private final JTextField textField;
	private Color backgroundColor;
	private Color hoverBackgroundColor;
	private boolean blocked;

	public FlatTextField()
	{
		super();
		this.textField = new JTextField();
	}

	public void addActionListener(ActionListener listener)
	{
		if (textField != null)
		{
			textField.addActionListener(listener);
		}
	}

	public String getText()
	{
		return textField != null ? textField.getText() : "";
	}

	public void setText(String text)
	{
		if (textField != null)
		{
			textField.setText(text);
		}
	}

	@Override
	public void addKeyListener(KeyListener listener)
	{
		if (textField != null)
		{
			textField.addKeyListener(listener);
		}
	}

	public void removeKeyListener(KeyListener listener)
	{
		if (textField != null)
		{
			textField.removeKeyListener(listener);
		}
	}

	@Override
	public void setBackground(Color background)
	{
		this.backgroundColor = background;
		super.setBackground(background);
	}

	public void setBackground(Color background, boolean overwrite)
	{
		setBackground(background);
	}

	@Override
	public void setBorder(Border border)
	{
		super.setBorder(border);
	}

	@Override
	public boolean requestFocusInWindow()
	{
		return false;
	}

	public void setHoverBackgroundColor(Color hoverBackgroundColor)
	{
		this.hoverBackgroundColor = hoverBackgroundColor;
	}

	public void setEditable(boolean editable)
	{
		if (textField != null)
		{
			textField.setEditable(editable);
		}
	}

	public Document getDocument()
	{
		return textField != null ? textField.getDocument() : null;
	}

	public JTextField getTextField()
	{
		return textField;
	}

	public Color getBackgroundColor()
	{
		return backgroundColor;
	}

	public Color getHoverBackgroundColor()
	{
		return hoverBackgroundColor;
	}

	public boolean isBlocked()
	{
		return blocked;
	}

	@Override
	public void setPreferredSize(Dimension preferredSize)
	{
		super.setPreferredSize(preferredSize);
	}
}
