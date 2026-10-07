package net.runelite.client.ui;

import javax.swing.JFormattedTextField;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.UnitFormatterFactory}.
 *
 * <p>Upstream decorates another formatter factory so that a value is shown with a unit
 * suffix. Text input is not part of the native side panel, so {@link #getFormatter} simply
 * returns {@code null}, which the retained call sites tolerate.
 */
public final class UnitFormatterFactory extends JFormattedTextField.AbstractFormatterFactory
{
	private final JFormattedTextField.AbstractFormatterFactory delegateFactory;
	private final String units;

	public UnitFormatterFactory(JFormattedTextField.AbstractFormatterFactory delegateFactory, String units)
	{
		super();
		this.delegateFactory = delegateFactory;
		this.units = units;
	}

	@Override
	public JFormattedTextField.AbstractFormatter getFormatter(JFormattedTextField textField)
	{
		return null;
	}
}
