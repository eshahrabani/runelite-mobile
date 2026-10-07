package net.runelite.client.ui.components;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.JPanel;

/**
 * Host replacement for RuneLite's generic {@code VirtualList<V extends Component, M>}.
 *
 * <p>Upstream is a virtualising list backed by a viewport. This shim keeps the exact
 * <em>erased</em> member descriptors (so the pre-compiled {@code PluginHubPanel$1}
 * subclass still overrides/links) but drops the generics and the viewport machinery:
 * {@code getIndexNearPosition}, {@code getItemPosition} and {@code createView} stay
 * abstract, everything else is a data-only no-op.
 */
public abstract class VirtualList extends JPanel
{
	private List model;
	private int keepUnused;

	public VirtualList()
	{
		super();
	}

	protected abstract int getIndexNearPosition(int position);

	protected abstract Rectangle getItemPosition(int index);

	protected abstract Component createView(Component existing, Object item);

	public void setModel(List model)
	{
		this.model = model;
	}

	public List getModel()
	{
		return model;
	}

	public void setKeepUnused(int keepUnused)
	{
		this.keepUnused = keepUnused;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(0, 0);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public void addNotify()
	{
	}

	@Override
	public void removeNotify()
	{
	}

	public void doLayout()
	{
	}
}
