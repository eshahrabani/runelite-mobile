package net.runelite.client.ui;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.MultiplexingPluginPanel}.
 *
 * <p>Upstream swaps cards in a {@code CardLayout} to push/pop nested panels. Here it is a
 * plain stack of {@link PluginPanel}s with no Swing: the constructor and the two state
 * methods keep the {@code ConfigPanel} call sites linking.
 */
public class MultiplexingPluginPanel extends PluginPanel
{
	private final Deque<PluginPanel> stateStack = new ArrayDeque<>();
	private PluginPanel current;

	public MultiplexingPluginPanel(PluginPanel root)
	{
		super(false);
		this.current = root;
	}

	public void destroy()
	{
	}

	public void pushState(PluginPanel panel)
	{
		stateStack.push(panel);
		current = panel;
	}

	public void popState()
	{
		current = stateStack.isEmpty() ? current : stateStack.pop();
	}

	protected void onAdd(PluginPanel panel)
	{
	}

	protected void onRemove(PluginPanel panel)
	{
	}

	@Override
	public void onActivate()
	{
	}

	@Override
	public void onDeactivate()
	{
	}
}
