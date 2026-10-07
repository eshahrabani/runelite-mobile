package net.runelite.client.ui;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.Activatable}.
 *
 * <p>The class is stripped from the shipped asset dex (see {@code HOST_REPLACED_CLASSES})
 * so that this app-dex version wins through parent-first delegation. It is a pure
 * interface: the default methods keep every {@code PluginPanel} subclass loadable, and
 * the native side panel drives activation instead of Swing.
 */
public interface Activatable
{
	default void onActivate()
	{
	}

	default void onDeactivate()
	{
	}
}
