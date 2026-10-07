package net.runelite.client.ui;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.ContainableFrame}.
 *
 * <p>Only the nested {@link Mode} enum is referenced by a retained class
 * ({@code RuneLiteConfig} declares a {@code @ConfigItem} of this type), and the enum
 * constant names are part of the persisted configuration, so they must match upstream
 * exactly. The frame itself carries no referenced members and is kept minimal.
 */
public class ContainableFrame extends javax.swing.JFrame
{
	public enum Mode
	{
		ALWAYS,
		RESIZING,
		NEVER
	}

	public ContainableFrame()
	{
		super();
	}
}
