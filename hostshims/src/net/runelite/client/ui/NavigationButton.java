package net.runelite.client.ui;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;

/**
 * Hand-written equivalent of RuneLite's Lombok {@code @Value @Builder}
 * {@code NavigationButton}.
 *
 * <p>The descriptors (including the nested {@code NavigationButtonBuilder} type name and
 * the fluent setter signatures) match what Lombok generated upstream, because plugin code
 * in the pre-compiled client jar calls
 * {@code NavigationButton.builder()....build()} against them. Everything is null-tolerant:
 * plugins rely on Lombok's behaviour of defaulting absent values rather than rejecting
 * nulls.
 */
public class NavigationButton
{
	private final BufferedImage icon;
	private final String tooltip;
	private final Runnable onClick;
	private final PluginPanel panel;
	private final int priority;
	private final Map<String, Runnable> popup;

	private NavigationButton(BufferedImage icon, String tooltip, Runnable onClick,
		PluginPanel panel, int priority, Map<String, Runnable> popup)
	{
		this.icon = icon;
		this.tooltip = tooltip;
		this.onClick = onClick;
		this.panel = panel;
		this.priority = priority;
		this.popup = popup;
	}

	public static NavigationButtonBuilder builder()
	{
		return new NavigationButtonBuilder();
	}

	public BufferedImage getIcon()
	{
		return icon;
	}

	public String getTooltip()
	{
		return tooltip;
	}

	public Runnable getOnClick()
	{
		return onClick;
	}

	public PluginPanel getPanel()
	{
		return panel;
	}

	public int getPriority()
	{
		return priority;
	}

	public Map<String, Runnable> getPopup()
	{
		return popup;
	}

	public static class NavigationButtonBuilder
	{
		private BufferedImage icon;
		private String tooltip;
		private Runnable onClick;
		private PluginPanel panel;
		private int priority;
		private Map<String, Runnable> popup;

		NavigationButtonBuilder()
		{
		}

		public NavigationButtonBuilder icon(BufferedImage icon)
		{
			this.icon = icon;
			return this;
		}

		public NavigationButtonBuilder tooltip(String tooltip)
		{
			this.tooltip = tooltip;
			return this;
		}

		public NavigationButtonBuilder onClick(Runnable onClick)
		{
			this.onClick = onClick;
			return this;
		}

		public NavigationButtonBuilder panel(PluginPanel panel)
		{
			this.panel = panel;
			return this;
		}

		public NavigationButtonBuilder priority(int priority)
		{
			this.priority = priority;
			return this;
		}

		public NavigationButtonBuilder popup(Map<String, Runnable> popup)
		{
			this.popup = popup;
			return this;
		}

		public NavigationButton build()
		{
			return new NavigationButton(
				icon,
				tooltip != null ? tooltip : "",
				onClick,
				panel,
				priority,
				popup != null ? popup : Collections.<String, Runnable>emptyMap());
		}
	}
}
