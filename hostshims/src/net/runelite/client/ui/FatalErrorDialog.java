package net.runelite.client.ui;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.FatalErrorDialog}.
 *
 * <p>Referenced only by classes that never run on this port ({@code RuneLite},
 * {@code ClientLoader}, {@code RSAppletStub}, {@code RuntimeConfig}), but it must link.
 * Nothing here opens a window: every method stores/returns and {@code open()} is a no-op.
 */
public class FatalErrorDialog extends javax.swing.JDialog
{
	public FatalErrorDialog(String message)
	{
		super();
	}

	public void open()
	{
	}

	public FatalErrorDialog addButton(String message, Runnable action)
	{
		return this;
	}

	public FatalErrorDialog setTitle(String title, String description)
	{
		return this;
	}

	public FatalErrorDialog addHelpButtons()
	{
		return this;
	}

	public FatalErrorDialog addBuildingGuide()
	{
		return this;
	}

	public static void showNetErrorWindow(String message, Throwable error)
	{
	}
}
