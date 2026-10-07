package net.runelite.client.ui;

import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Host replacement for RuneLite's {@code net.runelite.client.ui.PluginPanel}.
 *
 * <p>Binary-compatible with the call sites inside the pre-compiled client jar: same
 * hierarchy ({@link JPanel} + {@link Activatable}), same protected constructors and the
 * same member descriptors. It is deliberately data-only — no painting, no layout maths.
 * Built-in plugin panels such as {@code ConfigPanel} still link and construct against
 * this class; their real content is presented by the native side panel (plan D).
 */
public abstract class PluginPanel extends JPanel implements Activatable
{
	public static final int PANEL_WIDTH = 225;
	public static final int SCROLLBAR_WIDTH = 17;
	public static final int BORDER_OFFSET = 6;

	private final JScrollPane scrollPane;
	private final JPanel wrappedPanel;

	protected PluginPanel()
	{
		this(false);
	}

	protected PluginPanel(boolean wrapped)
	{
		super();
		this.wrappedPanel = wrapped ? new JPanel() : this;
		this.scrollPane = new JScrollPane(this.wrappedPanel);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return wrappedPanel == this
			? new Dimension(PANEL_WIDTH, 0)
			: new Dimension(PANEL_WIDTH + SCROLLBAR_WIDTH, 0);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	protected JScrollPane getScrollPane()
	{
		return scrollPane;
	}

	public JPanel getWrappedPanel()
	{
		return wrappedPanel;
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
