package net.runelite.client.ui.components.colorpicker;

import java.awt.Color;
import java.awt.Component;
import java.util.function.Consumer;
import javax.swing.JDialog;

/**
 * Host replacement for {@code net.runelite.client.ui.components.colorpicker.RuneliteColorPicker}.
 *
 * <p>Data-only: holds the selected colour and the change/close callbacks. It never opens a
 * window, so the callbacks never fire; the native side panel owns colour selection.
 */
public class RuneliteColorPicker extends JDialog
{
	private Color selectedColor = Color.BLACK;
	private Consumer<Color> onColorChange;
	private Consumer<Color> onClose;

	public RuneliteColorPicker()
	{
		super();
	}

	public Color getSelectedColor()
	{
		return selectedColor;
	}

	public void setSelectedColor(Color selectedColor)
	{
		this.selectedColor = selectedColor != null ? selectedColor : Color.BLACK;
	}

	@Override
	public void setLocationRelativeTo(Component component)
	{
	}

	public void setOnColorChange(Consumer<Color> onColorChange)
	{
		this.onColorChange = onColorChange;
	}

	public void setOnClose(Consumer<Color> onClose)
	{
		this.onClose = onClose;
	}

	@Override
	public void setVisible(boolean visible)
	{
	}
}
