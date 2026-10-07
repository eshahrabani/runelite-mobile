package net.runelite.client.ui.components;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.text.Document;

/**
 * Host replacement for {@code net.runelite.client.ui.components.IconTextField}.
 *
 * <p>Data-only: forwards text/icon state to a {@link FlatTextField} and exposes an empty
 * suggestion list model. The nested {@link Icon} enum keeps the four upstream constants
 * (plus the {@code getFile()} accessor) so the retained call sites link.
 */
public class IconTextField extends JPanel
{
	private final FlatTextField textField = new FlatTextField();
	private final DefaultListModel suggestionListModel = new DefaultListModel();
	private Icon icon;

	public IconTextField()
	{
		super();
	}

	public void addActionListener(ActionListener listener)
	{
		textField.addActionListener(listener);
	}

	public void addClearListener(Runnable listener)
	{
	}

	public void addKeyListener(KeyListener listener)
	{
		textField.addKeyListener(listener);
	}

	public void removeKeyListener(KeyListener listener)
	{
		textField.removeKeyListener(listener);
	}

	@Override
	public void addMouseListener(MouseListener listener)
	{
		super.addMouseListener(listener);
	}

	public Document getDocument()
	{
		return textField.getDocument();
	}

	@Override
	public Container getParent()
	{
		return super.getParent();
	}

	public DefaultListModel getSuggestionListModel()
	{
		return suggestionListModel;
	}

	public String getText()
	{
		return textField.getText();
	}

	@Override
	public boolean requestFocusInWindow()
	{
		return false;
	}

	@Override
	public void setBackground(Color background)
	{
		super.setBackground(background);
	}

	public void setEditable(boolean editable)
	{
		textField.setEditable(editable);
	}

	public void setHoverBackgroundColor(Color hoverBackgroundColor)
	{
		textField.setHoverBackgroundColor(hoverBackgroundColor);
	}

	public void setIcon(Icon icon)
	{
		this.icon = icon;
	}

	public void setIcon(ImageIcon icon)
	{
	}

	@Override
	public void setMinimumSize(Dimension minimumSize)
	{
		super.setMinimumSize(minimumSize);
	}

	@Override
	public void setPreferredSize(Dimension preferredSize)
	{
		super.setPreferredSize(preferredSize);
	}

	public void setText(String text)
	{
		textField.setText(text);
	}

	/**
	 * Mirrors {@code IconTextField.Icon}: the constants and {@code getFile()} are read by
	 * the retained call sites and by the icon lookup.
	 */
	public enum Icon
	{
		SEARCH("search.png"),
		LOADING("loading_spinner.gif"),
		LOADING_DARKER("loading_spinner_darker.gif"),
		ERROR("error.png");

		private final String file;

		Icon(String file)
		{
			this.file = file;
		}

		public String getFile()
		{
			return file;
		}
	}
}
